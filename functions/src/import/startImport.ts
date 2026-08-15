import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { isHistoricalImportJobStale } from "./importJobGuards";
import { parseFallbackWeightUnit } from "./weightUnit";

/**
 * Canonical client layout for historical import objects:
 *   imports/{uid}/{jobId}/raw/{fileName}
 *   imports/{uid}/{jobId}/sample/{fileName}
 *
 * Rejects path traversal, wrong kind folders, mismatched job ids, and any path that
 * is not under the authenticated caller's prefix. This is stronger than a bare prefix
 * check: the Admin SDK can read any bucket object, so the path shape is the ACL.
 */
export function assertOwnedImportStoragePaths(
  uid: string,
  rawStoragePath: string,
  sampleStoragePath: string,
): void {
  const rawJobId = parseOwnedImportPath(uid, rawStoragePath, "raw");
  const sampleJobId = parseOwnedImportPath(uid, sampleStoragePath, "sample");
  if (rawJobId !== sampleJobId) {
    throw new HttpsError(
      "invalid-argument",
      "rawStoragePath and sampleStoragePath must share the same jobId segment.",
    );
  }
}

/**
 * @returns jobId segment under imports/{uid}/
 */
export function parseOwnedImportPath(
  uid: string,
  storagePath: string,
  expectedKind: "raw" | "sample",
): string {
  if (typeof storagePath !== "string" || storagePath.length === 0) {
    throw new HttpsError("invalid-argument", "Storage path is required.");
  }
  // Reject traversal and non-posix separators before any split.
  if (
    storagePath.includes("..") ||
    storagePath.includes("\\") ||
    storagePath.includes("\0") ||
    storagePath.startsWith("/") ||
    storagePath.includes("//")
  ) {
    throw new HttpsError(
      "invalid-argument",
      "Storage path must be a relative posix path without traversal.",
    );
  }

  const prefix = `imports/${uid}/`;
  if (!storagePath.startsWith(prefix)) {
    throw new HttpsError(
      "invalid-argument",
      `Storage path must start with ${prefix}`,
    );
  }

  const rest = storagePath.slice(prefix.length);
  const parts = rest.split("/");
  // jobId / kind / fileName[+optional nested name segments]
  if (parts.length < 3) {
    throw new HttpsError(
      "invalid-argument",
      `Storage path must look like imports/{uid}/{jobId}/${expectedKind}/{fileName}`,
    );
  }

  const [jobId, kind, ...fileParts] = parts;
  if (!jobId || jobId === "." || jobId.includes("/")) {
    throw new HttpsError("invalid-argument", "Storage path jobId segment is invalid.");
  }
  // Job ids are client UUIDs; allow alphanumeric + hyphen/underscore only.
  if (!/^[A-Za-z0-9_-]+$/.test(jobId)) {
    throw new HttpsError("invalid-argument", "Storage path jobId segment is invalid.");
  }
  if (kind !== expectedKind) {
    throw new HttpsError(
      "invalid-argument",
      `Storage path must use the /${expectedKind}/ folder (found /${kind}/).`,
    );
  }
  if (
    fileParts.length === 0 ||
    fileParts.some((p) => !p || p === "." || p === ".." || p.includes("\\"))
  ) {
    throw new HttpsError(
      "invalid-argument",
      "Storage path file name segment is missing or invalid.",
    );
  }

  return jobId;
}

/**
 * Enqueue a historical training-log import job. The client uploads raw + sample to Storage first,
 * then calls this with the Storage paths; this writes the job doc that `processImportJob` picks up.
 *
 * Other pending_agent/agent_running jobs for this user are always superseded (failed). A new
 * startImport is the user's signal that they abandoned the previous attempt (cancel/stop, failed
 * client, or retry). Only failing stale jobs left cancelled-but-fresh jobs blocking retries with
 * "another import is already in progress" for up to HISTORICAL_IMPORT_STALE_JOB_MS.
 */
export const startImport = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  await assertUserEntitled(uid);
  const data = request.data as {
    jobId?: string;
    rawStoragePath?: string;
    sampleStoragePath?: string;
    /** Optional "Lb" | "Kg" — used when converted sets omit weightUnit. Defaults Lb. */
    fallbackWeightUnit?: string;
  };
  if (!data.jobId || !data.rawStoragePath || !data.sampleStoragePath) {
    throw new HttpsError(
      "invalid-argument",
      "jobId, rawStoragePath, and sampleStoragePath are required.",
    );
  }
  assertOwnedImportStoragePaths(uid, data.rawStoragePath, data.sampleStoragePath);
  const fallbackWeightUnit = parseFallbackWeightUnit(data.fallbackWeightUnit);

  // Job id in the path must match the job doc id the client claims.
  const pathJobId = parseOwnedImportPath(uid, data.rawStoragePath, "raw");
  if (pathJobId !== data.jobId) {
    throw new HttpsError(
      "invalid-argument",
      "jobId must match the jobId segment in rawStoragePath and sampleStoragePath.",
    );
  }

  const db = admin.firestore();
  const activeQuery = await db
    .collection(`users/${uid}/importJobs`)
    .where("status", "in", ["pending_agent", "agent_running"])
    .limit(5)
    .get();

  for (const doc of activeQuery.docs) {
    if (doc.id === data.jobId) {
      continue;
    }
    const jobData = doc.data();
    const stale = isHistoricalImportJobStale(jobData);
    await doc.ref.update({
      status: "failed",
      progressPhase: "failed",
      progressMessage: stale ? "Import timed out" : "Import replaced",
      errorMessage: stale
        ? "Previous import timed out. Please try again."
        : "Previous import was replaced by a new import.",
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  const jobRef = db.doc(`users/${uid}/importJobs/${data.jobId}`);
  const existing = await jobRef.get();
  if (existing.exists) {
    const existingData = existing.data();
    if (
      existingData?.status === "agent_running" &&
      !isHistoricalImportJobStale(existingData)
    ) {
      throw new HttpsError("failed-precondition", "Import job is already running.");
    }
  }

  await jobRef.set(
    {
      status: "pending_agent",
      rawStoragePath: data.rawStoragePath,
      sampleStoragePath: data.sampleStoragePath,
      fallbackWeightUnit,
      agentTurnCount: 0,
      progressPhase: "reading_data",
      progressMessage: "Preparing import…",
      progressUpdatedAt: FieldValue.serverTimestamp(),
      createdAt: existing.exists ? existing.data()?.createdAt : FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      errorMessage: FieldValue.delete(),
    },
    { merge: true },
  );

  return { jobId: data.jobId, status: "pending_agent" };
});

export { parseFallbackWeightUnit } from "./weightUnit";

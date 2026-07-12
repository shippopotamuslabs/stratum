import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { isSynthesisJobStale } from "./synthesisJobGuards";
import { assertUnderMonthlyQuota } from "./synthesisQuota";

/**
 * Validate that [requestStoragePath] is under `synthesis/{uid}/{jobId}/` and is not a
 * path-escape attempt. Client uploads the request JSON first, then calls this callable.
 */
export function isValidSynthesisRequestStoragePath(
  uid: string,
  jobId: string,
  requestStoragePath: string,
): boolean {
  if (typeof requestStoragePath !== "string" || requestStoragePath.length === 0) {
    return false;
  }
  if (requestStoragePath.includes("..") || requestStoragePath.includes("//")) {
    return false;
  }
  if (requestStoragePath.startsWith("/") || requestStoragePath.includes("\\")) {
    return false;
  }
  const prefix = `synthesis/${uid}/${jobId}/`;
  return requestStoragePath.startsWith(prefix) && requestStoragePath.length > prefix.length;
}

/**
 * Enqueue a program-synthesis job. The client uploads the CP-SAT request document to Storage
 * first, then calls this with the Storage path; this writes the job doc that
 * `processProgramSynthesisJob` dispatches to the JVM Cloud Run solver (Phase 3).
 *
 * Enforces monthly fair-use quota (10 successful / UTC month) and a single active job per uid.
 * Stale pending/running jobs are failed so the user can retry after a worker timeout.
 */
export const startProgramSynthesis = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as {
    jobId?: string;
    requestStoragePath?: string;
  };
  if (!data.jobId || !data.requestStoragePath) {
    throw new HttpsError(
      "invalid-argument",
      "jobId and requestStoragePath are required.",
    );
  }
  if (typeof data.jobId !== "string" || data.jobId.length === 0 || data.jobId.includes("/")) {
    throw new HttpsError("invalid-argument", "jobId is invalid.");
  }
  if (!isValidSynthesisRequestStoragePath(uid, data.jobId, data.requestStoragePath)) {
    throw new HttpsError(
      "invalid-argument",
      `requestStoragePath must be under synthesis/${uid}/${data.jobId}/.`,
    );
  }

  await assertUnderMonthlyQuota(uid);

  const db = admin.firestore();
  const activeQuery = await db
    .collection(`users/${uid}/synthesisJobs`)
    .where("status", "in", ["pending", "running"])
    .limit(5)
    .get();

  for (const doc of activeQuery.docs) {
    if (doc.id === data.jobId) {
      continue;
    }
    const jobData = doc.data();
    if (isSynthesisJobStale(jobData)) {
      await doc.ref.update({
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Program synthesis timed out",
        errorMessage: "Previous program synthesis timed out. Please try again.",
        updatedAt: FieldValue.serverTimestamp(),
      });
      continue;
    }
    throw new HttpsError(
      "failed-precondition",
      "Another program synthesis is already in progress.",
    );
  }

  const jobRef = db.doc(`users/${uid}/synthesisJobs/${data.jobId}`);
  const existing = await jobRef.get();
  if (existing.exists) {
    const existingData = existing.data();
    if (
      (existingData?.status === "running" || existingData?.status === "pending") &&
      !isSynthesisJobStale(existingData)
    ) {
      throw new HttpsError(
        "failed-precondition",
        "Program synthesis job is already in progress.",
      );
    }
  }

  await jobRef.set(
    {
      status: "pending",
      requestStoragePath: data.requestStoragePath,
      progressPhase: "queued",
      progressMessage: "Queued for program synthesis…",
      progressUpdatedAt: FieldValue.serverTimestamp(),
      createdAt: existing.exists ? existing.data()?.createdAt : FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      errorMessage: FieldValue.delete(),
      resultStoragePath: FieldValue.delete(),
      // Reset so a retry of the same jobId can count a new success if it becomes ready again.
      quotaCounted: false,
    },
    { merge: true },
  );

  return { jobId: data.jobId, status: "pending" };
});

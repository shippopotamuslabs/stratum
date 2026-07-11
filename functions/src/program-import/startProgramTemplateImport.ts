import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { isProgramTemplateJobStale } from "./programTemplateJobGuards";

/**
 * Enqueue a program-template import job. The client uploads the raw file to Storage first (via the
 * inference adapter), then calls this with the Storage path; this writes the job doc that the
 * `processProgramTemplateJob` trigger picks up.
 *
 * Mirrors `startImport.ts` but targets the `programImportJobs` collection. Includes the "another
 * job in progress" guard so only one program import runs per user at a time. Stale running/pending
 * jobs (e.g. after a Cloud Function hard-timeout) are failed so the user can retry.
 */
export const startProgramTemplateImport = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as {
    jobId?: string;
    rawStoragePath?: string;
    fileName?: string;
  };
  if (!data.jobId || !data.rawStoragePath) {
    throw new HttpsError(
      "invalid-argument",
      "jobId and rawStoragePath are required.",
    );
  }

  const db = admin.firestore();
  const activeQuery = await db
    .collection(`users/${uid}/programImportJobs`)
    .where("status", "in", ["pending", "running"])
    .limit(5)
    .get();

  for (const doc of activeQuery.docs) {
    if (doc.id === data.jobId) {
      continue;
    }
    const jobData = doc.data();
    if (isProgramTemplateJobStale(jobData)) {
      await doc.ref.update({
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Import timed out",
        errorMessage: "Previous program import timed out. Please try again.",
        updatedAt: FieldValue.serverTimestamp(),
      });
      continue;
    }
    throw new HttpsError("failed-precondition", "Another program import is already in progress.");
  }

  const jobRef = db.doc(`users/${uid}/programImportJobs/${data.jobId}`);
  const existing = await jobRef.get();
  if (existing.exists) {
    const existingData = existing.data();
    if (existingData?.status === "running" && !isProgramTemplateJobStale(existingData)) {
      throw new HttpsError("failed-precondition", "Program import job is already running.");
    }
  }

  await jobRef.set(
    {
      status: "pending",
      rawStoragePath: data.rawStoragePath,
      fileName: data.fileName ?? null,
      agentTurnCount: 0,
      progressPhase: "sniffing",
      progressMessage: "Preparing import…",
      progressUpdatedAt: FieldValue.serverTimestamp(),
      createdAt: existing.exists ? existing.data()?.createdAt : FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      errorMessage: FieldValue.delete(),
    },
    { merge: true },
  );

  return { jobId: data.jobId, status: "pending" };
});

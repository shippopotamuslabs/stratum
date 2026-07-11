import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { isHistoricalImportJobStale } from "./importJobGuards";

/**
 * Enqueue a historical training-log import job. The client uploads raw + sample to Storage first,
 * then calls this with the Storage paths; this writes the job doc that `processImportJob` picks up.
 *
 * Stale pending_agent/agent_running jobs (e.g. after a Cloud Function hard-timeout) are failed so
 * the user can retry instead of being permanently blocked by "another import is already in progress."
 */
export const startImport = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as {
    jobId?: string;
    rawStoragePath?: string;
    sampleStoragePath?: string;
  };
  if (!data.jobId || !data.rawStoragePath || !data.sampleStoragePath) {
    throw new HttpsError(
      "invalid-argument",
      "jobId, rawStoragePath, and sampleStoragePath are required.",
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
    if (isHistoricalImportJobStale(jobData)) {
      await doc.ref.update({
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Import timed out",
        errorMessage: "Previous import timed out. Please try again.",
        updatedAt: FieldValue.serverTimestamp(),
      });
      continue;
    }
    throw new HttpsError("failed-precondition", "Another import is already in progress.");
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

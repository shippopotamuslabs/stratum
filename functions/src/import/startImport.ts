import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { isHistoricalImportJobStale } from "./importJobGuards";

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

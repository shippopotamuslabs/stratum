import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";

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
    .limit(1)
    .get();
  if (!activeQuery.empty && activeQuery.docs[0]?.id !== data.jobId) {
    throw new HttpsError("failed-precondition", "Another import is already in progress.");
  }

  const jobRef = db.doc(`users/${uid}/importJobs/${data.jobId}`);
  const existing = await jobRef.get();
  if (existing.exists && existing.data()?.status === "agent_running") {
    throw new HttpsError("failed-precondition", "Import job is already running.");
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
    },
    { merge: true },
  );

  return { jobId: data.jobId, status: "pending_agent" };
});

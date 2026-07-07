import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";

/**
 * Enqueue a program-template import job. The client uploads the raw file to Storage first (via the
 * inference adapter), then calls this with the Storage path; this writes the job doc that the
 * `processProgramTemplateJob` trigger picks up.
 *
 * Mirrors `startImport.ts` but targets the `programImportJobs` collection. Includes the "another
 * job in progress" guard so only one program import runs per user at a time.
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
    .limit(1)
    .get();
  if (!activeQuery.empty && activeQuery.docs[0]?.id !== data.jobId) {
    throw new HttpsError("failed-precondition", "Another program import is already in progress.");
  }

  const jobRef = db.doc(`users/${uid}/programImportJobs/${data.jobId}`);
  const existing = await jobRef.get();
  if (existing.exists && existing.data()?.status === "running") {
    throw new HttpsError("failed-precondition", "Program import job is already running.");
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
    },
    { merge: true },
  );

  return { jobId: data.jobId, status: "pending" };
});

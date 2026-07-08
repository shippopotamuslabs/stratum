import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";

/**
 * Status poller for program-template import jobs. Mirrors `getImportJobStatus.ts` but reads from
 * the `programImportJobs` collection and returns the template Storage path (no row/session counts —
 * a program template is a small structured document).
 */
export const getProgramTemplateJobStatus = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as { jobId?: string };
  if (!data.jobId) {
    throw new HttpsError("invalid-argument", "jobId is required.");
  }

  const snap = await admin.firestore().doc(`users/${uid}/programImportJobs/${data.jobId}`).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Program import job not found.");
  }
  const job = snap.data()!;
  const latest = job.latestConversion as { storagePath?: string } | undefined;

  return {
    status: job.status,
    progressPhase: job.progressPhase ?? null,
    progressMessage: job.progressMessage ?? null,
    programTemplateStoragePath: latest?.storagePath ?? null,
    errorMessage: job.errorMessage ?? null,
  };
});

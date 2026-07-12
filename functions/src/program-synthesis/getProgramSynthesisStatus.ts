import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";

/**
 * Status poller for program-synthesis jobs. Mirrors `getProgramTemplateJobStatus.ts` but
 * reads from `synthesisJobs` and returns the solver result Storage path when ready.
 */
export const getProgramSynthesisStatus = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as { jobId?: string };
  if (!data.jobId) {
    throw new HttpsError("invalid-argument", "jobId is required.");
  }

  const snap = await admin.firestore().doc(`users/${uid}/synthesisJobs/${data.jobId}`).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Program synthesis job not found.");
  }
  const job = snap.data()!;

  return {
    status: job.status,
    progressPhase: job.progressPhase ?? null,
    progressMessage: job.progressMessage ?? null,
    resultStoragePath: (job.resultStoragePath as string | undefined) ?? null,
    errorMessage: job.errorMessage ?? null,
  };
});

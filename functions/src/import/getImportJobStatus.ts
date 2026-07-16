import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { sanitizeClientProgressMessage } from "./schemas";
import type { ImportProgressPhase } from "./schemas";

export const getImportJobStatus = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const data = request.data as { jobId?: string };
  if (!data.jobId) {
    throw new HttpsError("invalid-argument", "jobId is required.");
  }

  const snap = await admin.firestore().doc(`users/${uid}/importJobs/${data.jobId}`).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Import job not found.");
  }
  const job = snap.data()!;
  const latest = job.latestConversion as
    | {
        storagePath: string;
        rowCount: number;
        sessionCount: number;
        format?: string;
      }
    | undefined;

  return {
    status: job.status,
    progressPhase: job.progressPhase ?? null,
    progressMessage: sanitizeClientProgressMessage(
      job.progressMessage as string | undefined,
      job.progressPhase as ImportProgressPhase | undefined,
    ),
    latestConversion: latest
      ? {
          storagePath: latest.storagePath,
          rowCount: latest.rowCount,
          sessionCount: latest.sessionCount,
          ...(typeof latest.format === "string" ? { format: latest.format } : {}),
        }
      : null,
    errorMessage: job.errorMessage ?? null,
    lastToolError: job.lastToolError ?? null,
    lastToolName: job.lastToolName ?? null,
  };
});

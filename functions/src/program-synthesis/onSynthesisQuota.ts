import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { incrementSynthesisSuccessIfNeeded } from "./synthesisQuota";

/**
 * Idempotent monthly-quota increment when a synthesis job becomes `ready`.
 *
 * The JVM Cloud Run worker writes `status: "ready"` (and result path). This trigger owns
 * fair-use accounting so quota logic stays entirely in Node:
 * - only fires on transition into `ready`
 * - `incrementSynthesisSuccessIfNeeded` sets `quotaCounted: true` in the same transaction
 *   as the counter bump, so retries / double-triggers never double-count
 */
export const onSynthesisJobWritten = onDocumentWritten(
  {
    document: "users/{uid}/synthesisJobs/{jobId}",
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) {
      return;
    }
    const afterData = after.data();
    if (!afterData || afterData.status !== "ready") {
      return;
    }
    if (afterData.quotaCounted === true) {
      return;
    }
    const beforeData = event.data?.before?.data();
    // Already ready with counting still pending can retry; skip only if before was ready
    // *and* already counted (handled above). Allow before===ready && !quotaCounted so a
    // partial failure of the previous attempt can recover.
    if (beforeData?.status === "ready" && beforeData.quotaCounted === true) {
      return;
    }

    const uid = event.params.uid as string;
    const jobId = event.params.jobId as string;
    try {
      const counted = await incrementSynthesisSuccessIfNeeded(uid, jobId);
      if (counted) {
        console.info(`synthesis quota counted for ${uid}/${jobId}`);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      console.error(`synthesis quota increment failed for ${uid}/${jobId}: ${message}`);
      // Re-throw so Cloud Functions retries the trigger; the transaction is idempotent.
      throw err;
    }
  },
);

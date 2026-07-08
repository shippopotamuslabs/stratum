import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { createAdminToolStorage } from "../import/toolStorage";
import { deterministicProgramConvert } from "./deterministic/deterministicProgramConvert";
import { ProgramTemplateTooLargeError } from "./programTemplateLimits";

/** Rejection written when no deterministic parser recognizes the file. */
const UNSUPPORTED_FORMAT_MESSAGE =
  "Stratum can import Alpha Progression plan exports and MacroFactor program exports.";

/**
 * Trigger for program-template import jobs. Picks up a `pending` job and runs the deterministic
 * parser (Alpha Progression plan CSV / MacroFactor program XLSX). Any other file — or any parse
 * failure — is rejected as unsupported. There is no LLM fallback: the two supported formats are
 * parsed exactly, and everything else is a user-visible "unsupported file" rejection.
 *
 * Mirrors `processImportJob.ts` but targets `programImportJobs` and the program-template pipeline.
 */
export const processProgramTemplateJob = onDocumentWritten(
  {
    document: "users/{uid}/programImportJobs/{jobId}",
    memory: "1GiB",
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) {
      return;
    }
    const data = after.data();
    if (!data || data.status !== "pending") {
      return;
    }
    const before = event.data?.before?.data();
    if (before?.status === "pending") {
      return;
    }

    const uid = event.params.uid;
    const jobId = event.params.jobId;
    const jobRef = admin.firestore().doc(`users/${uid}/programImportJobs/${jobId}`);

    // Deterministic parse only. Known formats convert in milliseconds. An oversized file is an
    // abuse guard and surfaces as a distinct "too large" rejection. Anything else — unrecognized
    // format, parse failure, or validation failure — is rejected as unsupported; from the user's
    // perspective the file simply isn't one of the two formats Stratum can read.
    try {
      const deterministic = await deterministicProgramConvert({
        uid,
        jobId,
        jobRef,
        storage: createAdminToolStorage(),
      });
      if (deterministic) {
        return;
      }
      await rejectUnsupported(jobRef);
      return;
    } catch (err) {
      if (err instanceof ProgramTemplateTooLargeError) {
        await jobRef.update({
          status: "rejected",
          progressPhase: "failed",
          progressMessage: "File too large",
          errorMessage: err.message,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return;
      }
      const message = err instanceof Error ? err.message : String(err);
      console.warn(`deterministicProgramConvert threw for ${uid}/${jobId}: ${message}`);
      await rejectUnsupported(jobRef);
    }
  },
);

/** Write the terminal `rejected` status for a file no deterministic parser could handle. */
async function rejectUnsupported(jobRef: admin.firestore.DocumentReference): Promise<void> {
  await jobRef.update({
    status: "rejected",
    progressPhase: "failed",
    progressMessage: "Unsupported file format",
    errorMessage: UNSUPPORTED_FORMAT_MESSAGE,
    updatedAt: FieldValue.serverTimestamp(),
  });
}

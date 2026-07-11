import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { createAdminToolStorage } from "../import/toolStorage";
import { updateJobIfPresent } from "../import/safeJobUpdate";
import { deterministicProgramConvert } from "./deterministic/deterministicProgramConvert";
import { customLlmProgramConvert } from "./customLlmProgramConvert";
import { ProgramTemplateTooLargeError } from "./programTemplateLimits";

/**
 * Trigger for program-template import jobs.
 *
 * 1. Claim the job as `running` so concurrent guards and heartbeats see live work.
 * 2. Deterministic parsers (Alpha Progression plan CSV / MacroFactor program XLSX).
 * 3. On miss: normalize spreadsheet → feasibility gate → region slice → single-shot LLM JSON.
 * Calculator workbooks, garbage, and non-programs are rejected before any OpenRouter call.
 */
export const processProgramTemplateJob = onDocumentWritten(
  {
    document: "users/{uid}/programImportJobs/{jobId}",
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"],
    timeoutSeconds: 300,
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
    const storage = createAdminToolStorage();

    // Claim promptly so a hard timeout leaves a timestamped `running` doc that start* can stale-out.
    const claimed = await admin.firestore().runTransaction(async (tx) => {
      const snap = await tx.get(jobRef);
      if (!snap.exists || snap.data()?.status !== "pending") {
        return false;
      }
      tx.update(jobRef, {
        status: "running",
        progressPhase: "sniffing",
        progressMessage: "Preparing import…",
        progressUpdatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
    if (!claimed) {
      return;
    }

    // Account deletion may have cascade-removed the job after claim.
    if (!(await jobRef.get()).exists) {
      console.info(`program import job ${uid}/${jobId} gone after claim; aborting`);
      return;
    }

    // 1. Deterministic known formats (milliseconds, no LLM).
    try {
      const deterministic = await deterministicProgramConvert({
        uid,
        jobId,
        jobRef,
        storage,
      });
      if (deterministic) {
        return;
      }
    } catch (err) {
      if (err instanceof ProgramTemplateTooLargeError) {
        await updateJobIfPresent(jobRef, {
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
      // Fall through to custom path for unexpected parse errors on unknown shapes.
    }

    // 2. Custom spreadsheet path (feasibility may reject without LLM).
    try {
      const outcome = await customLlmProgramConvert({
        uid,
        jobId,
        jobRef,
        storage,
      });
      if (outcome.status === "ready") {
        return;
      }
      if (outcome.status === "rejected") {
        await updateJobIfPresent(jobRef, {
          status: "rejected",
          progressPhase: "failed",
          progressMessage: outcome.progressMessage,
          errorMessage: outcome.errorMessage,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return;
      }
      await updateJobIfPresent(jobRef, {
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Import failed",
        errorMessage: outcome.errorMessage,
        updatedAt: FieldValue.serverTimestamp(),
      });
    } catch (err) {
      if (err instanceof ProgramTemplateTooLargeError) {
        await updateJobIfPresent(jobRef, {
          status: "rejected",
          progressPhase: "failed",
          progressMessage: "File too large",
          errorMessage: err.message,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return;
      }
      const message = err instanceof Error ? err.message : String(err);
      console.warn(`customLlmProgramConvert threw for ${uid}/${jobId}: ${message}`);
      await updateJobIfPresent(jobRef, {
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Import failed",
        errorMessage: message,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

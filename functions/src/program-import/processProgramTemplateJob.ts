import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { createAdminToolStorage } from "../import/toolStorage";
import { deterministicProgramConvert } from "./deterministic/deterministicProgramConvert";
import {
  runProgramTemplateAgentSession,
  trimErrorMessage,
} from "./ProgramTemplateAgentSession";
import { assertWithinSizeLimit, ProgramTemplateTooLargeError } from "./programTemplateLimits";

/**
 * Trigger for program-template import jobs. Picks up a `pending` job, runs the deterministic parser
 * first (Alpha Progression plan CSV / MacroFactor program XLSX), and falls back to the LLM
 * conversion agent for anything unrecognized. Files that the agent determines are not programs are
 * marked `rejected` rather than `failed`.
 *
 * Mirrors `processImportJob.ts` but targets `programImportJobs` and the program-template pipeline.
 */
export const processProgramTemplateJob = onDocumentWritten(
  {
    document: "users/{uid}/programImportJobs/{jobId}",
    secrets: ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"],
    timeoutSeconds: 540,
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

    // Deterministic path first: known program-template formats convert in milliseconds, no LLM.
    // A ProgramTemplateTooLargeError is an abuse guard — reject the job and do NOT fall through to
    // the LLM path (which would burn budget on the oversized file).
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
    }

    // LLM fallback for unrecognized-but-plausible files. Re-check the size cap before any
    // OpenRouter call in case the deterministic branch returned null (unknown format) but the
    // file is oversized — a direct Callable bypass must not reach the LLM with a huge upload.
    try {
      const storage = createAdminToolStorage();
      const jobSnap = await jobRef.get();
      const rawPath = jobSnap.data()?.rawStoragePath;
      if (typeof rawPath === "string") {
        const rawBytes = await storage.download(rawPath);
        assertWithinSizeLimit(rawBytes.length);
      }
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
      // Best-effort pre-check; non-size errors fall through to the agent session.
    }

    // LLM fallback for unrecognized-but-plausible files.
    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
    if (!apiKey) {
      await jobRef.update({
        status: "failed",
        errorMessage: "OPENROUTER_API_KEY missing.",
        progressPhase: "failed",
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }

    try {
      const result = await runProgramTemplateAgentSession(uid, jobId, apiKey, model);
      if (result.status === "failed") {
        await jobRef.update({
          status: "failed",
          errorMessage: trimErrorMessage(result.errorMessage ?? "Import failed."),
          progressPhase: "failed",
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
      // `ready` and `rejected` statuses are written by the agent session itself.
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      await jobRef.update({
        status: "failed",
        errorMessage: trimErrorMessage(message),
        progressPhase: "failed",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { runImportAgentSession } from "./ImportAgentSession";
import { createAdminToolStorage } from "./toolStorage";
import { deterministicConvert } from "./deterministic/deterministicConvert";

export const processImportJob = onDocumentWritten(
  {
    document: "users/{uid}/importJobs/{jobId}",
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
    if (!data || data.status !== "pending_agent") {
      return;
    }
    const before = event.data?.before?.data();
    if (before?.status === "pending_agent") {
      return;
    }

    const uid = event.params.uid;
    const jobId = event.params.jobId;
    const jobRef = admin.firestore().doc(`users/${uid}/importJobs/${jobId}`);

    // Try a deterministic parser first: for known export formats (Hevy, Strong,
    // MacroFactor, Alpha Progression) this produces the same normalized.json the
    // agent would, in milliseconds and without an LLM call. On any miss it
    // returns null and we fall through to the agent unchanged.
    try {
      const deterministic = await deterministicConvert({
        uid,
        jobId,
        jobRef,
        storage: createAdminToolStorage(),
      });
      if (deterministic) {
        return;
      }
    } catch (err) {
      // Infrastructure failure (Storage/Firestore) is non-fatal here — the
      // agent path below has its own error handling and will mark the job
      // failed if it cannot proceed. Log and continue.
      const message = err instanceof Error ? err.message : String(err);
      console.warn(`deterministicConvert threw for ${uid}/${jobId}: ${message}`);
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
    if (!apiKey) {
      await after.ref.update({
        status: "failed",
        errorMessage: "OPENROUTER_API_KEY missing.",
        progressPhase: "failed",
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }

    try {
      const result = await runImportAgentSession(uid, jobId, apiKey, model);
      if (result.status === "failed") {
        await jobRef.update({
          status: "failed",
          errorMessage: result.errorMessage ?? "Import failed.",
          progressPhase: "failed",
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      await jobRef.update({
        status: "failed",
        errorMessage: message,
        progressPhase: "failed",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { runImportAgentSession } from "./ImportAgentSession";

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

    const jobRef = admin.firestore().doc(`users/${uid}/importJobs/${jobId}`);
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

import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { runImportAgentSession } from "./ImportAgentSession";
import { createAdminToolStorage } from "./toolStorage";
import { deterministicConvert } from "./deterministic/deterministicConvert";
import { assessHistoryImportFeasibility } from "./feasibility/assessHistoryImportFeasibility";
import { updateJobIfPresent } from "./safeJobUpdate";

/**
 * Trigger for historical training-log import jobs.
 *
 * 1. Claim the job as `agent_running` so concurrent guards and heartbeats see live work.
 * 2. Deterministic parsers (Hevy / Strong / MacroFactor / Alpha Progression).
 * 3. On miss: light feasibility gate (reject empty/garbage without OpenRouter).
 * 4. On candidate: multi-turn OpenRouter agent + Python sandbox converter.
 */
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
    // Only process jobs that are waiting to be claimed. Concurrent writers (startImport retries,
    // progress heartbeats) may leave status as pending_agent across multiple document events —
    // do NOT skip when before.status was also pending_agent. That guard permanently stuck retries
    // that re-set the same job to pending_agent (UI frozen on "Preparing import…"). Idempotency is
    // the transaction claim below: only one invocation wins the pending_agent → agent_running flip.
    if (!data || data.status !== "pending_agent") {
      return;
    }

    const uid = event.params.uid;
    const jobId = event.params.jobId;
    const jobRef = admin.firestore().doc(`users/${uid}/importJobs/${jobId}`);
    const storage = createAdminToolStorage();

    // Claim promptly so a hard timeout leaves a timestamped `agent_running` doc that startImport
    // can stale-out. Deterministic and agent paths both run under this in-flight status.
    const claimed = await admin.firestore().runTransaction(async (tx) => {
      const snap = await tx.get(jobRef);
      if (!snap.exists || snap.data()?.status !== "pending_agent") {
        return false;
      }
      tx.update(jobRef, {
        status: "agent_running",
        progressPhase: "reading_data",
        progressMessage: "Reading your data…",
        progressUpdatedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
    if (!claimed) {
      return;
    }

    // Try a deterministic parser first: for known export formats (Hevy, Strong,
    // MacroFactor, Alpha Progression) this produces the same normalized.json the
    // agent would, in milliseconds and without an LLM call. On any miss it
    // returns null and we fall through.
    try {
      const deterministic = await deterministicConvert({
        uid,
        jobId,
        jobRef,
        storage,
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

    // Light pre-agent gate: empty / binary garbage / structureless text → rejected (no OpenRouter).
    try {
      const jobSnap = await jobRef.get();
      const rawStoragePath = jobSnap.data()?.rawStoragePath;
      if (typeof rawStoragePath === "string" && rawStoragePath.length > 0) {
        const rawBytes = await storage.download(rawStoragePath);
        const fileName =
          typeof jobSnap.data()?.fileName === "string"
            ? (jobSnap.data()?.fileName as string)
            : "";
        const feasibility = assessHistoryImportFeasibility(rawBytes, fileName);
        if (feasibility.decision === "reject") {
          console.info(
            `history import rejected ${uid}/${jobId}: ${feasibility.reasonCode} — ${feasibility.message}`,
          );
          await updateJobIfPresent(jobRef, {
            status: "rejected",
            progressPhase: "failed",
            progressMessage: "Unsupported file format",
            errorMessage: feasibility.message,
            rejectReasonCode: feasibility.reasonCode,
            updatedAt: FieldValue.serverTimestamp(),
          });
          return;
        }
      }
    } catch (err) {
      // Download/read failures fall through to the agent path, which will fail with its own handling.
      const message = err instanceof Error ? err.message : String(err);
      console.warn(`history feasibility check threw for ${uid}/${jobId}: ${message}`);
    }

    // Account deletion may have cascade-removed the job after claim.
    if (!(await jobRef.get()).exists) {
      console.info(`import job ${uid}/${jobId} gone after claim; aborting`);
      return;
    }

    const apiKey = process.env.OPENROUTER_API_KEY;
    const model = process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
    if (!apiKey) {
      await updateJobIfPresent(jobRef, {
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
        await updateJobIfPresent(jobRef, {
          status: "failed",
          errorMessage: result.errorMessage ?? "Import failed.",
          progressPhase: "failed",
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      await updateJobIfPresent(jobRef, {
        status: "failed",
        errorMessage: message,
        progressPhase: "failed",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

import OpenAI from "openai";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  importAgentSessionId,
} from "../openrouterSession";
import { getStorage } from "firebase-admin/storage";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import type { JobRef } from "./jobRef";
import { updateJobIfPresent } from "./safeJobUpdate";
import { dispatchTool, type ToolContext } from "./tools";
import { TOOL_DEFINITIONS } from "./schemas";
import {
  buildImportAgentSystemPrompt,
  buildImportAgentUserPrompt,
} from "./importDomainRules";
import { parseFallbackWeightUnit } from "./weightUnit";

export const MAX_TURNS = 15;
export const MAX_BLOB_NUDGES = 5;
const MAX_TOOL_ERROR_SUMMARY_CHARS = 500;
const MAX_USER_ERROR_MESSAGE_CHARS = 600;

export interface LastToolFailure {
  toolName: string;
  errorSummary: string;
}

export interface SessionResult {
  status: "ready" | "failed";
  errorMessage?: string;
}

export interface ImportAgentJob {
  sampleStoragePath: string;
  rawStoragePath: string;
  fullSliceStoragePath?: string;
  scriptStoragePath?: string;
  latestConversion?: { storagePath?: string };
  /** User import-session fallback when file omits units. */
  fallbackWeightUnit?: "Lb" | "Kg" | string;
}

export interface ChatMessage {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null;
  tool_call_id?: string;
  tool_calls?: OpenAI.Chat.Completions.ChatCompletionMessageToolCall[];
}

export interface ImportAgentSessionDeps {
  getJob(): Promise<ImportAgentJob | undefined>;
  updateJob(patch: Record<string, unknown>): Promise<void>;
  readJobConversionPath(): Promise<string | undefined>;
  downloadSample(path: string): Promise<string>;
  createCompletion(
    messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
  ): Promise<OpenAI.Chat.Completions.ChatCompletion>;
  dispatchTool: typeof dispatchTool;
  buildToolContext(job: ImportAgentJob, jobRef: JobRef): ToolContext;
  jobRef: JobRef;
}

export async function runImportAgentSessionWithDeps(
  deps: ImportAgentSessionDeps,
): Promise<SessionResult> {
  const job = await deps.getJob();
  if (!job) {
    return { status: "failed", errorMessage: "Job not found." };
  }

  await deps.updateJob({
    status: "agent_running",
    progressPhase: "reading_data",
    progressMessage: "Reading your data…",
    progressUpdatedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  const sampleText = (await deps.downloadSample(job.sampleStoragePath)).slice(0, 50_000);
  const fallbackUnit = parseFallbackWeightUnit(job.fallbackWeightUnit);

  const messages: ChatMessage[] = [
    { role: "system", content: buildImportAgentSystemPrompt(fallbackUnit) },
    {
      role: "user",
      content: buildImportAgentUserPrompt(sampleText),
    },
  ];

  let blobNudges = 0;
  let turnCount = 0;
  let lastConversionPath: string | undefined = job.latestConversion?.storagePath;
  let lastToolFailure: LastToolFailure | undefined;

  while (turnCount < MAX_TURNS) {
    turnCount += 1;
    await deps.updateJob({
      agentTurnCount: turnCount,
      updatedAt: FieldValue.serverTimestamp(),
    });

    const completion = await deps.createCompletion(
      messages as OpenAI.Chat.Completions.ChatCompletionMessageParam[],
    );

    const choice = completion.choices[0]?.message;
    if (!choice) {
      return { status: "failed", errorMessage: "Empty model response." };
    }

    const toolCalls = choice.tool_calls ?? [];
    if (toolCalls.length === 0) {
      messages.push({ role: "assistant", content: choice.content ?? "" });
      const nudgeResult = await handleBlobGate(
        deps.updateJob.bind(deps),
        messages,
        blobNudges,
        lastConversionPath,
        false,
        lastToolFailure,
      );
      blobNudges = nudgeResult.blobNudges;
      if (nudgeResult.done) {
        return nudgeResult.result!;
      }
      continue;
    }

    messages.push({
      role: "assistant",
      content: choice.content,
      tool_calls: toolCalls,
    });

    let conversionUpdated = false;
    let hadFullExecute = false;
    const toolCtx = deps.buildToolContext(job, deps.jobRef);
    for (const call of toolCalls) {
      if (call.type !== "function") {
        continue;
      }
      if (call.function.name === "execute_on_full") {
        hadFullExecute = true;
      }
      let args: Record<string, unknown> = {};
      try {
        args = JSON.parse(call.function.arguments) as Record<string, unknown>;
      } catch {
        args = {};
      }
      const result = await deps.dispatchTool(call.function.name, args, toolCtx);
      if (result.conversionUpdated) {
        conversionUpdated = true;
        lastConversionPath = await deps.readJobConversionPath();
      }
      const content = truncateToolOutput(result.content);
      messages.push({
        role: "tool",
        tool_call_id: call.id,
        content: result.isError ? `ERROR: ${content}` : content,
      });
      if (result.isError && isTrackedConversionTool(call.function.name)) {
        lastToolFailure = {
          toolName: call.function.name,
          errorSummary: summarizeToolError(content),
        };
        await deps.updateJob({
          lastToolError: lastToolFailure.errorSummary,
          lastToolName: lastToolFailure.toolName,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    }

    if (conversionUpdated && lastConversionPath) {
      await deps.updateJob({
        status: "ready",
        progressMessage: "Conversion complete",
        updatedAt: FieldValue.serverTimestamp(),
      });
      return { status: "ready" };
    }

    const nudgeResult = await handleBlobGate(
      deps.updateJob.bind(deps),
      messages,
      blobNudges,
      lastConversionPath,
      hadFullExecute,
      lastToolFailure,
    );
    blobNudges = nudgeResult.blobNudges;
    if (nudgeResult.done) {
      return nudgeResult.result!;
    }
  }

  return { status: "failed", errorMessage: "Agent exceeded maximum turns." };
}

export async function runImportAgentSession(
  uid: string,
  jobId: string,
  apiKey: string,
  model: string,
): Promise<SessionResult> {
  const db = getFirestore();
  const jobRef = db.doc(`users/${uid}/importJobs/${jobId}`);
  const sessionId = importAgentSessionId(uid, jobId);
  const client = createOpenRouterClient(apiKey, sessionId);

  return runImportAgentSessionWithDeps({
    jobRef: jobRef as unknown as JobRef,
    async getJob() {
      const snap = await jobRef.get();
      const data = snap.data();
      if (!data) {
        return undefined;
      }
      return {
        sampleStoragePath: data.sampleStoragePath as string,
        rawStoragePath: data.rawStoragePath as string,
        fullSliceStoragePath: data.fullSliceStoragePath as string | undefined,
        scriptStoragePath: data.scriptStoragePath as string | undefined,
        latestConversion: data.latestConversion as { storagePath?: string } | undefined,
        fallbackWeightUnit: data.fallbackWeightUnit as string | undefined,
      };
    },
    async updateJob(patch) {
      await updateJobIfPresent(jobRef, patch);
    },
    async readJobConversionPath() {
      const snap = await jobRef.get();
      const latest = snap.data()?.latestConversion as { storagePath?: string } | undefined;
      return latest?.storagePath;
    },
    async downloadSample(path) {
      const bucket = getStorage().bucket();
      const [sampleBytes] = await bucket.file(path).download();
      return sampleBytes.toString("utf8");
    },
    async createCompletion(messages) {
      return client.chat.completions.create({
        model,
        messages,
        tools: TOOL_DEFINITIONS,
        ...buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true }),
      } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
    },
    dispatchTool,
    buildToolContext(job, ref) {
      return {
        uid,
        jobId,
        jobRef: ref,
        sampleStoragePath: job.sampleStoragePath,
        rawStoragePath: job.rawStoragePath,
        fullSliceStoragePath: job.fullSliceStoragePath,
      };
    },
  });
}

export async function handleBlobGate(
  updateJob: (patch: Record<string, unknown>) => Promise<void>,
  messages: ChatMessage[],
  blobNudges: number,
  lastConversionPath: string | undefined,
  afterFullAttempt = false,
  lastToolFailure?: LastToolFailure,
): Promise<{
  blobNudges: number;
  done: boolean;
  result?: SessionResult;
}> {
  if (lastConversionPath) {
    return { blobNudges, done: false };
  }
  if (!afterFullAttempt && blobNudges === 0) {
    return { blobNudges, done: false };
  }
  if (blobNudges >= MAX_BLOB_NUDGES) {
    const errorMessage = buildBlobGateFailureMessage(lastToolFailure);
    await updateJob({
      status: "failed",
      progressPhase: "failed",
      errorMessage,
      lastToolError: lastToolFailure?.errorSummary ?? null,
      lastToolName: lastToolFailure?.toolName ?? null,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { blobNudges, done: true, result: { status: "failed", errorMessage } };
  }
  const next = blobNudges + 1;
  messages.push({
    role: "user",
    content: buildBlobGateNudgeMessage(lastToolFailure),
  });
  return { blobNudges: next, done: false };
}

function isTrackedConversionTool(toolName: string): boolean {
  return toolName === "execute_on_full" || toolName === "execute_on_sample";
}

export function summarizeToolError(content: string): string {
  const withoutErrorPrefix = content.replace(/^ERROR:\s*/i, "").trim();
  const singleLine = withoutErrorPrefix.replace(/\s+/g, " ");
  if (singleLine.length <= MAX_TOOL_ERROR_SUMMARY_CHARS) {
    return singleLine;
  }
  return `${singleLine.slice(0, MAX_TOOL_ERROR_SUMMARY_CHARS)}…`;
}

export function buildBlobGateFailureMessage(lastToolFailure?: LastToolFailure): string {
  if (!lastToolFailure) {
    return "Conversion produced no output after multiple attempts.";
  }
  const detail =
    `Last error from ${formatToolName(lastToolFailure.toolName)}: ${lastToolFailure.errorSummary}`;
  const message = `Conversion failed after ${MAX_BLOB_NUDGES} attempts. ${detail}`;
  return truncateUserErrorMessage(message);
}

export function buildBlobGateNudgeMessage(lastToolFailure?: LastToolFailure): string {
  if (!lastToolFailure) {
    return (
      "execute_on_full did not produce conversion output. " +
      "Run execute_on_sample until it passes validation, then execute_on_full again."
    );
  }
  return (
    `execute_on_full did not produce conversion output. ` +
    `Last ${formatToolName(lastToolFailure.toolName)} error: ${lastToolFailure.errorSummary} ` +
    "Fix the converter, verify with execute_on_sample, then run execute_on_full again."
  );
}

function formatToolName(toolName: string): string {
  switch (toolName) {
    case "execute_on_full":
      return "execute_on_full";
    case "execute_on_sample":
      return "execute_on_sample";
    default:
      return toolName;
  }
}

function truncateUserErrorMessage(message: string): string {
  if (message.length <= MAX_USER_ERROR_MESSAGE_CHARS) {
    return message;
  }
  return `${message.slice(0, MAX_USER_ERROR_MESSAGE_CHARS)}…`;
}

export function truncateToolOutput(content: string, maxLen = 8000): string {
  if (content.length <= maxLen) {
    return content;
  }
  return content.slice(0, maxLen) + "\n…[truncated]";
}

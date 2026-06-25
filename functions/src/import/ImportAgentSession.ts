import OpenAI from "openai";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  importAgentSessionId,
} from "../openrouterSession";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import type { JobRef } from "./jobRef";
import { dispatchTool, type ToolContext } from "./tools";
import {
  IMPORT_AGENT_SYSTEM_PROMPT,
  TOOL_DEFINITIONS,
} from "./schemas";
import { buildImportAgentUserPrompt } from "./importDomainRules";

export const MAX_TURNS = 15;
export const MAX_BLOB_NUDGES = 3;

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

  const messages: ChatMessage[] = [
    { role: "system", content: IMPORT_AGENT_SYSTEM_PROMPT },
    {
      role: "user",
      content: buildImportAgentUserPrompt(sampleText),
    },
  ];

  let blobNudges = 0;
  let turnCount = 0;
  let lastConversionPath: string | undefined = job.latestConversion?.storagePath;

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
  const db = admin.firestore();
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
      };
    },
    async updateJob(patch) {
      await jobRef.update(patch);
    },
    async readJobConversionPath() {
      const snap = await jobRef.get();
      const latest = snap.data()?.latestConversion as { storagePath?: string } | undefined;
      return latest?.storagePath;
    },
    async downloadSample(path) {
      const bucket = admin.storage().bucket();
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
    const errorMessage = "Conversion produced no output after multiple attempts.";
    await updateJob({
      status: "failed",
      progressPhase: "failed",
      errorMessage,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { blobNudges, done: true, result: { status: "failed", errorMessage } };
  }
  const next = blobNudges + 1;
  messages.push({
    role: "user",
    content: "Nothing was produced. Continue.",
  });
  return { blobNudges: next, done: false };
}

export function truncateToolOutput(content: string, maxLen = 8000): string {
  if (content.length <= maxLen) {
    return content;
  }
  return content.slice(0, maxLen) + "\n…[truncated]";
}

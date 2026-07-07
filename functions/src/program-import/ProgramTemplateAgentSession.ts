import OpenAI from "openai";
import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { executeConversionScript } from "../import/pythonSandbox";
import type { JobRef } from "../import/jobRef";
import type { ToolStorage } from "../import/toolStorage";
import { createAdminToolStorage } from "../import/toolStorage";
import {
  buildProgramGenerationChatCompletionExtras,
  createOpenRouterClient,
  importAgentSessionId,
} from "../openrouterSession";
import {
  PROGRAM_TEMPLATE_AGENT_SYSTEM_PROMPT,
  PROGRAM_TEMPLATE_TOOL_DEFINITIONS,
  buildProgramTemplateAgentUserPrompt,
} from "./programTemplateDomainRules";
import { validateProgramTemplate } from "./deterministic/types";
import type { NormalizedProgramTemplate } from "./deterministic/types";

export const MAX_TURNS = 10;
const MAX_TOOL_OUTPUT_CHARS = 4000;
const MAX_ERROR_MESSAGE_CHARS = 600;

export interface ProgramTemplateSessionResult {
  status: "ready" | "rejected" | "failed";
  errorMessage?: string;
}

export interface ProgramTemplateAgentJob {
  rawStoragePath: string;
  scriptStoragePath?: string;
  latestConversion?: { storagePath?: string };
}

export interface ChatMessage {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null;
  tool_call_id?: string;
  tool_calls?: OpenAI.Chat.Completions.ChatCompletionMessageToolCall[];
}

export interface ProgramTemplateAgentSessionDeps {
  getJob(): Promise<ProgramTemplateAgentJob | undefined>;
  updateJob(patch: Record<string, unknown>): Promise<void>;
  readJobConversionPath(): Promise<string | undefined>;
  downloadRaw(path: string): Promise<string>;
  createCompletion(
    messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
  ): Promise<OpenAI.Chat.Completions.ChatCompletion>;
}

function truncateToolOutput(text: string): string {
  return text.length > MAX_TOOL_OUTPUT_CHARS ? text.slice(0, MAX_TOOL_OUTPUT_CHARS) + "…" : text;
}

/**
 * Run the program-template LLM agent. Reuses the same write-script/test mechanics as the
 * historical-import agent, but runs the script against the FULL file (programs are small) and
 * validates against the program-template schema. An empty `workouts[]` result is treated as a
 * rejection (the file is not a program) rather than a failure.
 */
export async function runProgramTemplateAgentSessionWithDeps(
  deps: ProgramTemplateAgentSessionDeps,
  uid: string,
  jobId: string,
  jobRef: JobRef,
): Promise<ProgramTemplateSessionResult> {
  const job = await deps.getJob();
  if (!job) {
    return { status: "failed", errorMessage: "Job not found." };
  }

  await deps.updateJob({
    status: "running",
    progressPhase: "script_generating",
    progressMessage: "Analyzing your file…",
    progressUpdatedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  const rawText = (await deps.downloadRaw(job.rawStoragePath)).slice(0, 200_000);

  const messages: ChatMessage[] = [
    { role: "system", content: PROGRAM_TEMPLATE_AGENT_SYSTEM_PROMPT },
    { role: "user", content: buildProgramTemplateAgentUserPrompt(rawText) },
  ];

  let turnCount = 0;
  let lastConversionPath: string | undefined = job.latestConversion?.storagePath;
  const storage: ToolStorage = createAdminToolStorage();

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
      return {
        status: "failed",
        errorMessage: "Model did not call a tool. Try a different file.",
      };
    }

    messages.push({
      role: "assistant",
      content: choice.content,
      tool_calls: toolCalls,
    });

    for (const call of toolCalls) {
      if (call.type !== "function") continue;
      let args: Record<string, unknown> = {};
      try {
        args = JSON.parse(call.function.arguments) as Record<string, unknown>;
      } catch {
        args = {};
      }

      const result = await dispatchProgramTemplateTool(
        call.function.name,
        args,
        { uid, jobId, jobRef, rawStoragePath: job.rawStoragePath, storage },
      );
      const content = truncateToolOutput(result.content);
      messages.push({
        role: "tool",
        tool_call_id: call.id,
        content: result.isError ? `ERROR: ${content}` : content,
      });

      if (result.conversionUpdated && result.template) {
        lastConversionPath = result.conversionPath;
        // Rejection: the model correctly determined the file is not a program.
        if (result.template.workouts.length === 0) {
          await deps.updateJob({
            status: "rejected",
            progressPhase: "failed",
            progressMessage: "This file does not look like a workout program.",
            errorMessage: "This file does not look like a workout program.",
            updatedAt: FieldValue.serverTimestamp(),
          });
          return {
            status: "rejected",
            errorMessage: "This file does not look like a workout program.",
          };
        }
        await deps.updateJob({
          status: "ready",
          progressPhase: "parsing",
          progressMessage: `Imported ${result.template.workouts.length} workouts`,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return { status: "ready" };
      }
    }
  }

  return {
    status: "failed",
    errorMessage: "Could not convert this file. Try a different file.",
  };
}

interface ToolDispatchResult {
  content: string;
  isError: boolean;
  conversionUpdated: boolean;
  template?: NormalizedProgramTemplate;
  conversionPath?: string;
}

interface ProgramTemplateToolContext {
  uid: string;
  jobId: string;
  jobRef: JobRef;
  rawStoragePath: string;
  storage: ToolStorage;
}

async function dispatchProgramTemplateTool(
  toolName: string,
  args: Record<string, unknown>,
  ctx: ProgramTemplateToolContext,
): Promise<ToolDispatchResult> {
  switch (toolName) {
    case "write_conversion_script":
      return writeConversionScript(args, ctx);
    case "execute_conversion":
      return executeConversion(args, ctx);
    default:
      return {
        content: `Unknown tool: ${toolName}`,
        isError: true,
        conversionUpdated: false,
      };
  }
}

async function writeConversionScript(
  args: Record<string, unknown>,
  ctx: ProgramTemplateToolContext,
): Promise<ToolDispatchResult> {
  const script = args.script;
  if (typeof script !== "string" || script.trim().length === 0) {
    return { content: "script is required", isError: true, conversionUpdated: false };
  }
  const scriptPath = `programImports/${ctx.uid}/${ctx.jobId}/script/converter.py`;
  await ctx.storage.save(scriptPath, script, "text/plain");
  await ctx.jobRef.update({
    scriptStoragePath: scriptPath,
    progressPhase: "script_generating",
    updatedAt: FieldValue.serverTimestamp(),
  });
  return {
    content: JSON.stringify({ scriptStoragePath: scriptPath }),
    isError: false,
    conversionUpdated: false,
  };
}

async function executeConversion(
  args: Record<string, unknown>,
  ctx: ProgramTemplateToolContext,
): Promise<ToolDispatchResult> {
  const jobSnap = await ctx.jobRef.get();
  const jobData = jobSnap.data() ?? {};
  const scriptPath =
    typeof args.scriptStoragePath === "string" ? args.scriptStoragePath : (jobData.scriptStoragePath as string | undefined);
  if (!scriptPath || typeof scriptPath !== "string") {
    return {
      content: "No script saved yet. Call write_conversion_script first.",
      isError: true,
      conversionUpdated: false,
    };
  }

  const [scriptBytes] = await ctx.storage.bucket().file(scriptPath).download();
  const [inputBytes] = await ctx.storage.bucket().file(ctx.rawStoragePath).download();
  const script = scriptBytes.toString("utf8");
  const inputText = inputBytes.toString("utf8");

  let result;
  try {
    result = executeConversionScript(script, inputText);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return { content: message, isError: true, conversionUpdated: false };
  }

  let template: NormalizedProgramTemplate;
  try {
    template = validateProgramTemplate(JSON.parse(result.outputJson));
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return { content: `Validation error: ${message}`, isError: true, conversionUpdated: false };
  }

  const ts = Date.now();
  const conversionPath = `programImports/${ctx.uid}/${ctx.jobId}/conversions/${ts}/template.json`;
  const outputBytes = Buffer.from(JSON.stringify(template), "utf8");
  await ctx.storage.save(conversionPath, outputBytes, "application/json");
  await ctx.jobRef.update({
    latestConversion: {
      storagePath: conversionPath,
      format: "agent",
      sizeBytes: outputBytes.length,
      createdAt: Timestamp.now(),
    },
    progressPhase: "script_testing",
    updatedAt: FieldValue.serverTimestamp(),
  });

  return {
    content: JSON.stringify({ storagePath: conversionPath, workouts: template.workouts.length }),
    isError: false,
    conversionUpdated: true,
    template,
    conversionPath,
  };
}

export async function runProgramTemplateAgentSession(
  uid: string,
  jobId: string,
  apiKey: string,
  model: string,
): Promise<ProgramTemplateSessionResult> {
  const db = admin.firestore();
  const jobRef = db.doc(`users/${uid}/programImportJobs/${jobId}`) as unknown as JobRef;
  const sessionId = importAgentSessionId(uid, jobId);
  const client = createOpenRouterClient(apiKey, sessionId);

  return runProgramTemplateAgentSessionWithDeps(
    {
      async getJob() {
        const snap = await (jobRef.get() as Promise<{ data: () => Record<string, unknown> | undefined }>);
        const data = snap.data();
        if (!data) return undefined;
        return {
          rawStoragePath: data.rawStoragePath as string,
          scriptStoragePath: data.scriptStoragePath as string | undefined,
          latestConversion: data.latestConversion as { storagePath?: string } | undefined,
        };
      },
      async updateJob(patch) {
        await (jobRef.update(patch) as Promise<unknown>);
      },
      async readJobConversionPath() {
        const snap = await (jobRef.get() as Promise<{ data: () => Record<string, unknown> | undefined }>);
        const latest = snap.data()?.latestConversion as { storagePath?: string } | undefined;
        return latest?.storagePath;
      },
      async downloadRaw(path) {
        const bucket = admin.storage().bucket();
        const [bytes] = await bucket.file(path).download();
        return bytes.toString("utf8");
      },
      async createCompletion(messages) {
        return client.chat.completions.create({
          model,
          messages,
          tools: PROGRAM_TEMPLATE_TOOL_DEFINITIONS,
          ...buildProgramGenerationChatCompletionExtras(sessionId),
        } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
      },
    },
    uid,
    jobId,
    jobRef,
  );
}

/** Truncate an error message for client display. */
export function trimErrorMessage(message: string): string {
  return message.length > MAX_ERROR_MESSAGE_CHARS
    ? message.slice(0, MAX_ERROR_MESSAGE_CHARS) + "…"
    : message;
}

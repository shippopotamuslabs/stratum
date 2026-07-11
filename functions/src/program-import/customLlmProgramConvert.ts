/**
 * Custom spreadsheet → NormalizedProgramTemplate via normalize → feasibility → slice → LLM.
 * Called only after deterministic parsers miss.
 */
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import OpenAI from "openai";
import type { DeterministicStorage, DeterministicJobRef } from "../import/deterministic/deterministicConvert";
import {
  buildOpenRouterChatCompletionExtras,
  createOpenRouterClient,
  programTemplateImportSessionId,
} from "../openrouterSession";
import { normalizeWorkbook } from "./normalize/workbookNormalize";
import { assessProgramImportFeasibility } from "./feasibility/assessProgramImportFeasibility";
import { sliceProgramRegion } from "./normalize/sliceProgramRegion";
import {
  convertProgramTemplateWithLlm,
  buildProgramTemplateCompletionParams,
} from "./llm/programTemplateLlmConvert";
import { assertWithinSizeLimit } from "./programTemplateLimits";
import type { NormalizedProgramTemplate } from "./deterministic/types";

export interface CustomLlmProgramConvertDeps {
  uid: string;
  jobId: string;
  jobRef: DeterministicJobRef;
  storage: DeterministicStorage;
  /** Injected for tests; production builds OpenRouter from env. */
  createCompletion?: (
    messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[],
  ) => Promise<OpenAI.Chat.Completions.ChatCompletion>;
  apiKey?: string;
  model?: string;
}

export type CustomLlmConvertOutcome =
  | { status: "ready"; template: NormalizedProgramTemplate }
  | { status: "rejected"; errorMessage: string; progressMessage: string }
  | { status: "failed"; errorMessage: string };

/**
 * Run the custom LLM program-import pipeline. Updates job progress along the way.
 * Does not re-check deterministic formats — caller already tried those.
 */
export async function customLlmProgramConvert(
  deps: CustomLlmProgramConvertDeps,
): Promise<CustomLlmConvertOutcome> {
  const jobSnap = await deps.jobRef.get();
  const jobData = jobSnap.data();
  const rawStoragePath = jobData?.rawStoragePath;
  if (typeof rawStoragePath !== "string" || rawStoragePath.length === 0) {
    return {
      status: "rejected",
      errorMessage: "Missing upload path.",
      progressMessage: "Unsupported file format",
    };
  }

  const rawBytes = await deps.storage.download(rawStoragePath);
  assertWithinSizeLimit(rawBytes.length);

  const fileName =
    typeof jobData?.fileName === "string" ? (jobData.fileName as string) : null;

  await deps.jobRef.update({
    status: "running",
    progressPhase: "analyzing",
    progressMessage: "Reading your spreadsheet…",
    progressUpdatedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  const workbook = normalizeWorkbook(rawBytes);
  const feasibility = assessProgramImportFeasibility(workbook);
  if (feasibility.decision === "reject" || feasibility.bestSheetIndex === null) {
    return {
      status: "rejected",
      errorMessage: feasibility.message,
      progressMessage: "Unsupported file format",
    };
  }

  const sliced = sliceProgramRegion(workbook, feasibility.bestSheetIndex);
  if (!sliced.viewText.trim() || sliced.liftRowEstimate < 2) {
    return {
      status: "rejected",
      errorMessage:
        "This spreadsheet does not contain enough program structure to import.",
      progressMessage: "Unsupported file format",
    };
  }

  const apiKey = deps.apiKey ?? process.env.OPENROUTER_API_KEY;
  const model = deps.model ?? process.env.OPENROUTER_MODEL ?? "deepseek/deepseek-v4-pro";
  if (!apiKey && !deps.createCompletion) {
    return {
      status: "failed",
      errorMessage: "OPENROUTER_API_KEY missing.",
    };
  }

  await deps.jobRef.update({
    status: "running",
    progressPhase: "converting",
    progressMessage: "Converting your program…",
    progressUpdatedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  let createCompletion = deps.createCompletion;
  if (!createCompletion) {
    const sessionId = programTemplateImportSessionId(deps.uid, deps.jobId);
    const client = createOpenRouterClient(apiKey!, sessionId);
    const extras = buildOpenRouterChatCompletionExtras(sessionId);
    createCompletion = async (messages) => {
      const params = buildProgramTemplateCompletionParams(model, messages, extras);
      return client.chat.completions.create(params);
    };
  }

  const llmResult = await convertProgramTemplateWithLlm(
    sliced.viewText,
    { createCompletion },
    { fileName },
  );

  if (!llmResult.ok) {
    if (llmResult.kind === "model") {
      return {
        status: "failed",
        errorMessage: llmResult.error,
      };
    }
    return {
      status: "rejected",
      errorMessage:
        "We could not convert this spreadsheet into a workout program. " +
        "Try an Alpha Progression or MacroFactor export, or a simpler day/lift/sets table.",
      progressMessage: "Could not convert program",
    };
  }

  const template = llmResult.template;
  const ts = Date.now();
  const conversionPath = `programImports/${deps.uid}/${deps.jobId}/conversions/${ts}/template.json`;
  const outputBytes = Buffer.from(JSON.stringify(template), "utf8");
  await deps.storage.save(conversionPath, outputBytes, "application/json");

  const workoutCount = template.workouts.filter((w) => !w.restDay).length;
  await deps.jobRef.update({
    status: "ready",
    progressPhase: "parsing",
    progressMessage: `Imported ${workoutCount} workouts`,
    latestConversion: {
      storagePath: conversionPath,
      format: "llm_custom",
      sizeBytes: outputBytes.length,
      createdAt: Timestamp.now(),
    },
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { status: "ready", template };
}

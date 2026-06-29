/**
 * Program generation v3 pipeline: deterministic RP slot builder + optional LLM lift swap.
 */
import type OpenAI from "openai";
import type { GenerationContext } from "./programPayloadContext";
import { parseGenerationContext } from "./programPayloadContext";
import { planWeeklyVolume } from "./volumePlanner";
import { buildProgramWithSelections } from "./slotBasedProgramBuilder";
import { repairWeeklyVolume } from "./programIntegration";
import { validateProgramMetrics } from "./programValidation";
import { parseAndNormalizeProgram, type ProgramNormalizationRequest } from "./programSchema";
import {
  maybeSwapLifts,
  type ChatCompletionLike as LiftSwapClient,
} from "./liftSwapPass";
import {
  logPhase0Precompute,
  logPipelineOutcome,
  logPipelineTotal,
  logProgramGenerationMetricAttempt,
  logSplitPlannerResult,
  type ProgramGenerationOutcome,
} from "./programGenerationDiagnostics";

/** OpenAI client seam (matches the v1 generateProgramWithDeps seam for testability). */
export interface ChatCompletionLike {
  chat: {
    completions: {
      create: (
        params: OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming,
      ) => Promise<OpenAI.Chat.Completions.ChatCompletion>;
    };
  };
}

/** Progress callback for the pipeline (mirrors the v1 marker writes). */
export type PipelineProgressCallback = (
  phase: "split_planning" | "building_workouts" | "integrating",
) => Promise<void> | void;

/** Options for the pipeline (test seams). */
export interface PipelineOptions {
  /** @deprecated Split planning is fully deterministic; kept for test compatibility. */
  skipSplitPlanner?: boolean;
  /** Skip the final metric validation (for unit tests). */
  skipFinalValidation?: boolean;
  /** Skip optional LLM lift swap pass. */
  skipLiftSwap?: boolean;
  progress?: PipelineProgressCallback;
}

/**
 * Runs the full generation pipeline. Returns a normalized program document ready to persist.
 */
export async function generateProgramPipeline(
  client: ChatCompletionLike,
  model: string,
  payload: string,
  sessionId: string,
  normalizationRequest: ProgramNormalizationRequest,
  options: PipelineOptions = {},
): Promise<unknown> {
  const pipelineStartMs = Date.now();
  const context = parseGenerationContext(payload);
  const progress = options.progress ?? (() => {});

  await progress("split_planning");
  const splitPlannerStartMs = Date.now();
  const weeklyTargets = planWeeklyVolume(context);
  const splitPlannerMs = Date.now() - splitPlannerStartMs;
  logSplitPlannerResult(sessionId, false, true, false);

  await progress("building_workouts");
  const buildersStartMs = Date.now();
  const { program: built, selectionsBySession } = buildProgramWithSelections(context, weeklyTargets);
  const phase0Ms = Date.now() - pipelineStartMs;
  logPhase0Precompute(sessionId, phase0Ms, built.workouts.length, context.enabledLiftIds.size);

  await progress("integrating");
  const integrateStartMs = Date.now();
  let draft = repairWeeklyVolume(
    { name: built.name, cycleLength: built.cycleLength, workouts: built.workouts },
    weeklyTargets,
    context,
  );

  if (!options.skipLiftSwap) {
    draft = await maybeSwapLifts(
      client as LiftSwapClient,
      model,
      draft,
      context,
      selectionsBySession,
      sessionId,
    );
  }

  let normalized = parseAndNormalizeProgram({ program: draft }, normalizationRequest);
  if (options.skipFinalValidation) {
    logPipelineTotal(sessionId, {
      splitPlannerMs,
      buildersMs: Date.now() - buildersStartMs,
      integrateMs: Date.now() - integrateStartMs,
      totalMs: Date.now() - pipelineStartMs,
    }, 0);
    return normalized;
  }

  let report = validateProgramMetrics(normalized, context);
  logProgramGenerationMetricAttempt(
    sessionId,
    0,
    report.pass,
    report.issues.length,
    report.issues.map((issue) => issue.code),
  );

  const outcome: ProgramGenerationOutcome = options.skipLiftSwap ? "full_deterministic_fallback" : "llm";
  logPipelineOutcome(
    sessionId,
    outcome,
    report.pass,
    report.issues.length,
    report.issues.map((issue) => issue.code),
  );
  logPipelineTotal(sessionId, {
    splitPlannerMs,
    buildersMs: Date.now() - buildersStartMs,
    integrateMs: Date.now() - integrateStartMs,
    totalMs: Date.now() - pipelineStartMs,
  }, 0);
  return normalized;
}

// Legacy exports for tests that imported split planner / builder helpers.
export type { GenerationContext };

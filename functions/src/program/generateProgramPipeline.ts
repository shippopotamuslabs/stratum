/**
 * Program generation v2 pipeline (OS-7 v2).
 *
 * Orchestrates the 3-phase generation: deterministic precompute (Phase 0) → one bounded LLM
 * call for the WHOLE program (Phase 1) → deterministic integration + repair (Phase 2).
 * Replaces the v1 monolithic 12-round-trip tool + JSON loop AND the earlier N-per-workout
 * builder fan-out. The LLM is reduced to a single bounded judgment call — exercise selection
 * across all days, deliberately varying sibling days — and one split-planner call for 5–6 day
 * programs; everything with a unit (sets, minutes, MEV, MRV, frequency, RIR, scheme) is
 * deterministic.
 *
 * The single whole-program call fixes two defects the per-workout fan-out had:
 *   1. Sibling-day cloning (Upper A ≈ Upper B): a per-day builder cannot see the other days, so
 *      it cannot deliberately vary them; one call sees all days and is instructed to vary.
 *   2. Latency: one provider hop replaces the N-call fan-out and its per-call timeout cascade.
 *
 * The pipeline always returns a valid program: a builder failure falls through to the
 * deterministic fallback; if final validation fails, one in-chat correction turn is attempted
 * (assistant program + user feedback listing issues); if that also fails, the whole program is
 * rebuilt from the deterministic fallback. No 6-attempt loops.
 */
import type OpenAI from "openai";
import {
  buildOpenRouterChatCompletionExtras,
} from "../openrouterSession";
import type { GenerationContext } from "./programPayloadContext";
import { parseGenerationContext } from "./programPayloadContext";
import { schemeForTrainingAge } from "./programRules";
import {
  availablePatternsByMuscle,
  computeAllWeeklyTargets,
} from "./volumeTargetPlanner";
import { allocateDayPlans, type DayPlan } from "./splitAllocator";
import { buildDayCandidates, type LiftCandidate } from "./liftPriority";
import { repBandsForLifts, type RepBand } from "./repBandPlanner";
import {
  deterministicFallbackWorkout,
  mergeWorkouts,
  repairWeeklyVolume,
  type WorkoutBuilderResult,
} from "./programIntegration";
import { validateProgramMetrics, type ProgramWorkoutDraft } from "./programValidation";
import { parseAndNormalizeProgram, type ProgramNormalizationRequest } from "./programSchema";
import {
  buildSplitPlannerInput,
  SPLIT_PLANNER_SYSTEM_PROMPT,
  splitPlannerOutputSchema,
} from "./splitPlannerPrompt";
import {
  buildProgramBuilderInput,
  buildProgramBuilderRetryMessage,
  PROGRAM_BUILDER_SYSTEM_PROMPT,
  programBuilderOutputSchema,
} from "./programBuilderPrompt";
import {
  logPhase0Precompute,
  logPipelineOutcome,
  logPipelineTotal,
  logProgramBuilderResult,
  logProgramGenerationMetricAttempt,
  logSplitPlannerResult,
  type ProgramBuilderFailureReason,
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
  /** Skip the LLM split planner and use the deterministic allocator even for 5–6 days. */
  skipSplitPlanner?: boolean;
  /** Skip the final metric validation + global re-gen (for unit tests). */
  skipFinalValidation?: boolean;
  /** Per-call timeout for the whole-program builder (ms, default 240_000). */
  builderTimeoutMs?: number;
  progress?: PipelineProgressCallback;
}

/** Threshold above which the LLM split planner is invoked (5–6 days). */
const SPLIT_PLANNER_DAY_THRESHOLD = 4;

/** Max attempts for the global re-gen escape hatch. */
const MAX_GLOBAL_REGEN_ATTEMPTS = 1;

/** Default timeout for the whole-program builder call (large lift pools can exceed 120s). */
const DEFAULT_BUILDER_TIMEOUT_MS = 480_000;

/** Continues the builder chat with the model's prior JSON plus validation feedback. */
export interface ProgramBuilderRetryContext {
  assistantContent: string;
  feedbackMessage: string;
}

export interface ProgramBuilderRunResult {
  results: WorkoutBuilderResult[];
  perDayFallbackCount: number;
  llmWorkoutCount: number;
  failureReason: ProgramBuilderFailureReason;
  /** Raw JSON text from the model when the call succeeded; used for in-chat correction. */
  assistantContent: string | null;
}

/** Modest sampling temperature so parallel sibling days do not collapse to the same argmax. */
const BUILDER_TEMPERATURE = 0.6;

/**
 * Runs the full generation pipeline. Returns a normalized program document ready to persist.
 * The payload is the onboarding JSON string built by `ProgramGenerationPromptBuilder` (unchanged
 * from v1) — Phase 0 parses it via `parseGenerationContext`.
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

  // Phase 0: deterministic precompute.
  const phase0StartMs = Date.now();
  const weeklyTargets = computeAllWeeklyTargets(context);
  const patternsByMuscle = availablePatternsByMuscle(context);
  await progress("split_planning");
  const splitPlannerStartMs = Date.now();

  // Phase 1a: split planning. Deterministic for ≤4 days; LLM for 5–6 (with allocator fallback).
  const useLlmSplitPlanner =
    !options.skipSplitPlanner && context.trainingDaysPerWeek > SPLIT_PLANNER_DAY_THRESHOLD;
  let dayPlans: DayPlan[];
  let splitPlannerUsedLlm = false;
  let splitPlannerValid = true;
  let splitPlannerFallback = false;
  if (useLlmSplitPlanner) {
    splitPlannerUsedLlm = true;
    dayPlans = await runSplitPlanner(client, model, context, weeklyTargets, patternsByMuscle, sessionId);
    splitPlannerValid = dayPlans.length > 0;
    if (dayPlans.length === 0) {
      splitPlannerFallback = true;
      dayPlans = allocateDayPlans({ ...context, trainingDaysPerWeek: SPLIT_PLANNER_DAY_THRESHOLD }, weeklyTargets);
    }
  } else {
    dayPlans = allocateDayPlans(context, weeklyTargets);
  }
  const splitPlannerMs = Date.now() - splitPlannerStartMs;
  logSplitPlannerResult(sessionId, splitPlannerUsedLlm, splitPlannerValid, splitPlannerFallback);

  // Phase 1b: per-day candidates + rep bands.
  const progressionScheme = schemeForTrainingAge(context.trainingAge);
  const candidatesByDay = dayPlans.map((plan) => buildDayCandidates(plan, context));
  const repBandsByDay = dayPlans.map((plan, i) => repBandsForLifts(candidatesByDay[i].map((c) => c.liftId), context));
  const phase0Ms = Date.now() - phase0StartMs;
  logPhase0Precompute(
    sessionId,
    phase0Ms,
    dayPlans.length,
    candidatesByDay.reduce((sum, day) => sum + day.length, 0),
  );

  // Phase 1c: single whole-program builder.
  await progress("building_workouts");
  const buildersStartMs = Date.now();
  const initialBuilder = await runProgramBuilder(
    client,
    model,
    dayPlans,
    candidatesByDay,
    repBandsByDay,
    progressionScheme,
    sessionId,
    context,
    options,
    0,
  );
  let results = initialBuilder.results;
  let builderFallbackCount = initialBuilder.perDayFallbackCount;
  let builderAssistantContent = initialBuilder.assistantContent;

  // Phase 2: integration + repair (includes intra-workout dedup in repairWorkout).
  await progress("integrating");
  const integrateStartMs = Date.now();
  let program = mergeWorkouts(results, context);
  program = repairWeeklyVolume(program, weeklyTargets, context);

  // Final gate: normalize + validate. One global re-gen attempt on failure.
  // parseAndNormalizeProgram expects { program: { workouts: [...] } } (v1 wire shape).
  let normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
  if (options.skipFinalValidation) {
    logPipelineTotal(sessionId, {
      splitPlannerMs,
      buildersMs: Date.now() - buildersStartMs,
      integrateMs: Date.now() - integrateStartMs,
      totalMs: Date.now() - pipelineStartMs,
    }, builderFallbackCount);
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
  let outcome: ProgramGenerationOutcome = builderFallbackCount === dayPlans.length ? "full_deterministic_fallback" : "llm";
  if (report.pass) {
    logPipelineOutcome(
      sessionId,
      outcome,
      true,
      report.issues.length,
      report.issues.map((issue) => issue.code),
    );
    logPipelineTotal(sessionId, {
      splitPlannerMs,
      buildersMs: Date.now() - buildersStartMs,
      integrateMs: Date.now() - integrateStartMs,
      totalMs: Date.now() - pipelineStartMs,
    }, builderFallbackCount);
    return normalized;
  }

  for (let attempt = 0; attempt < MAX_GLOBAL_REGEN_ATTEMPTS && !report.pass; attempt++) {
    if (!builderAssistantContent) {
      break;
    }
    // In-chat correction: assistant keeps its prior program JSON; user lists validation issues.
    const retryBuilder = await runProgramBuilder(
      client,
      model,
      dayPlans,
      candidatesByDay,
      repBandsByDay,
      progressionScheme,
      sessionId,
      context,
      options,
      attempt + 1,
      {
        assistantContent: builderAssistantContent,
        feedbackMessage: buildProgramBuilderRetryMessage(
          report.issues,
          context.trainingDaysPerWeek,
        ),
      },
    );
    if (retryBuilder.failureReason === "none" && retryBuilder.llmWorkoutCount > 0) {
      results = retryBuilder.results;
      builderFallbackCount = retryBuilder.perDayFallbackCount;
      if (retryBuilder.assistantContent) {
        builderAssistantContent = retryBuilder.assistantContent;
      }
      program = mergeWorkouts(results, context);
      program = repairWeeklyVolume(program, weeklyTargets, context);
      normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
      report = validateProgramMetrics(normalized, context);
      if (report.pass) {
        outcome = "llm_after_regen";
      }
    }
    logProgramGenerationMetricAttempt(
      sessionId,
      attempt + 1,
      report.pass,
      report.issues.length,
      report.issues.map((issue) => issue.code),
    );
  }

  if (!report.pass) {
    // Last resort: deterministic fallback only when no LLM program exists. If the first builder
    // call produced workouts, keep that draft rather than replacing it with rank-n picks.
    if (initialBuilder.llmWorkoutCount > 0) {
      program = mergeWorkouts(initialBuilder.results, context);
      program = repairWeeklyVolume(program, weeklyTargets, context);
      normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
      report = validateProgramMetrics(normalized, context);
      if (outcome !== "llm_after_regen") {
        outcome = "llm";
      }
    } else {
      program = fullDeterministicFallback(dayPlans, candidatesByDay, repBandsByDay, context);
      normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
      report = validateProgramMetrics(normalized, context);
      builderFallbackCount = dayPlans.length;
      outcome = "full_deterministic_fallback";
    }
    logProgramGenerationMetricAttempt(
      sessionId,
      MAX_GLOBAL_REGEN_ATTEMPTS + 1,
      report.pass,
      report.issues.length,
      report.issues.map((issue) => issue.code),
    );
  }

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
  }, builderFallbackCount);
  return normalized;
}
async function runSplitPlanner(
  client: ChatCompletionLike,
  model: string,
  context: GenerationContext,
  weeklyTargets: ReturnType<typeof computeAllWeeklyTargets>,
  patternsByMuscle: Map<string, string[]>,
  sessionId: string,
): Promise<DayPlan[]> {
  const extras = buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true });
  const input = buildSplitPlannerInput(context, weeklyTargets, patternsByMuscle);
  const messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [
    { role: "system", content: SPLIT_PLANNER_SYSTEM_PROMPT },
    { role: "user", content: JSON.stringify(input) },
  ];

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const completion = await client.chat.completions.create({
        model,
        messages,
        response_format: { type: "json_schema", json_schema: splitPlannerOutputSchema() },
        ...extras,
      } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming);
      const content = completion.choices[0]?.message?.content;
      if (!content) continue;
      const parsed = JSON.parse(content) as { days?: unknown };
      const dayPlans = toDayPlans(parsed, context);
      if (dayPlans.length > 0 && validateDayPlans(dayPlans, weeklyTargets, context)) {
        return dayPlans;
      }
      // Validation failed — feed back and retry once.
      messages.push({
        role: "user",
        content: "The day budgets do not sum to weekly targets or violate frequency/time constraints. Repartition and return valid JSON.",
      });
    } catch {
      // Parse failure or API error — retry once, then fall back.
    }
  }
  return [];
}

/** Converts the split-planner JSON output to DayPlan[]. */
function toDayPlans(parsed: { days?: unknown }, context: GenerationContext): DayPlan[] {
  if (!Array.isArray(parsed.days)) return [];
  return (parsed.days as Array<Record<string, unknown>>).map((day, i) => ({
    dayIndex: i,
    weekday: typeof day.weekday === "string" ? day.weekday : context.weekdays[i] ?? null,
    name: typeof day.theme === "string" ? day.theme : `Day ${i + 1}`,
    theme: typeof day.theme === "string" ? day.theme : `Day ${i + 1}`,
    timeBudgetHardSets:
      context.sessionLengthMinutes != null
        ? Math.floor((context.sessionLengthMinutes * 1.08) / 3.5)
        : null,
    muscleBudgets: Array.isArray(day.muscleBudgets)
      ? (day.muscleBudgets as Array<Record<string, unknown>>).map((b) => ({
        muscle: String(b.muscle ?? ""),
        sets: Number(b.sets ?? 0),
        min: Number(b.min ?? 0),
        aim: Number(b.aim ?? 0),
      }))
      : [],
    suggestedPatterns: Array.isArray(day.suggestedPatterns)
      ? (day.suggestedPatterns as Array<Record<string, unknown>>).map((s) => ({
        pattern: String(s.pattern ?? ""),
        muscleFocus: String(s.muscleFocus ?? ""),
      }))
      : [],
  }));
}

/** Validates that day plans sum to weekly targets and respect frequency (lightweight pre-check). */
function validateDayPlans(
  dayPlans: DayPlan[],
  weeklyTargets: Map<string, { min: number; aim: number; max: number }>,
  context: GenerationContext,
): boolean {
  // Sum per-muscle sets across days.
  const sums = new Map<string, number>();
  for (const plan of dayPlans) {
    for (const budget of plan.muscleBudgets) {
      sums.set(budget.muscle, (sums.get(budget.muscle) ?? 0) + budget.sets);
    }
  }
  // Every weekly target muscle should be within [min, max] (loose tolerance).
  for (const [muscle, target] of weeklyTargets) {
    const sum = sums.get(muscle) ?? 0;
    if (sum < target.min - 2 || sum > target.max + 2) return false;
  }
  // Major muscles on ≥2 days.
  for (const muscle of ["Chest", "Back", "Quad", "Hamstring", "Glute"]) {
    if (!weeklyTargets.has(muscle)) continue;
    const freq = dayPlans.filter((p) => p.muscleBudgets.some((b) => b.muscle === muscle)).length;
    if (context.trainingDaysPerWeek >= 3 && freq < 2) return false;
  }
  return dayPlans.length === context.trainingDaysPerWeek;
}

/**
 * Runs ONE whole-program builder call. The builder picks a lift for every slot of every day,
 * deliberately varying sibling days. If the call fails or returns an unusable program, every day
 * falls back to the deterministic fallback workout (sibling-rotated) via [mergeWorkouts].
 */
async function runProgramBuilder(
  client: ChatCompletionLike,
  model: string,
  dayPlans: DayPlan[],
  candidatesByDay: LiftCandidate[][],
  repBandsByDay: Map<string, RepBand>[],
  progressionScheme: "Linear" | "Double",
  sessionId: string,
  context: GenerationContext,
  options: PipelineOptions,
  attempt: number,
  retryContext?: ProgramBuilderRetryContext,
): Promise<ProgramBuilderRunResult> {
  const timeoutMs = options.builderTimeoutMs ?? DEFAULT_BUILDER_TIMEOUT_MS;
  const extras = buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true });
  const input = buildProgramBuilderInput(dayPlans, candidatesByDay, repBandsByDay, progressionScheme);
  const messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [
    { role: "system", content: PROGRAM_BUILDER_SYSTEM_PROMPT },
    { role: "user", content: JSON.stringify(input) },
  ];
  if (retryContext) {
    messages.push({ role: "assistant", content: retryContext.assistantContent });
    messages.push({ role: "user", content: retryContext.feedbackMessage });
  }

  let raw: unknown = null;
  let assistantContent: string | null = null;
  let failureReason: ProgramBuilderFailureReason = "none";
  const builderStartMs = Date.now();
  try {
    const completion = await withTimeout(
      client.chat.completions.create({
        model,
        messages,
        temperature: BUILDER_TEMPERATURE,
        // Maximum reasoning effort for GLM-5.2's program synthesis — this is the high-judgment
        // call (lift selection, cross-day variety, compound-first ordering). Scoped to the builder
        // only; the split planner is a lighter partitioning call. Once-per-onboarding, so the
        // extra latency/cost is acceptable. OpenRouter normalizes "xhigh" across providers.
        reasoning: { effort: "xhigh" },
        response_format: { type: "json_schema", json_schema: programBuilderOutputSchema() },
        ...extras,
      } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming),
      timeoutMs,
    );
    const content = completion.choices[0]?.message?.content;
    if (!content) {
      failureReason = "empty_content";
    } else {
      assistantContent = content;
      try {
        raw = JSON.parse(content);
      } catch {
        raw = null;
        failureReason = "invalid_json";
      }
    }
  } catch (error) {
    raw = null;
    failureReason = error instanceof Error && error.message.startsWith("timeout after")
      ? "timeout"
      : "api_error";
  }
  const builderLatencyMs = Date.now() - builderStartMs;
  const split = splitProgramToBuilderResults(raw, dayPlans, candidatesByDay, repBandsByDay, context);
  logProgramBuilderResult(
    sessionId,
    attempt,
    builderLatencyMs,
    failureReason,
    split.llmWorkoutCount,
    split.perDayFallbackCount,
  );
  return {
    ...split,
    failureReason,
    assistantContent,
  };
}

/**
 * Splits the single whole-program builder response into per-day [WorkoutBuilderResult] entries so
 * the existing [mergeWorkouts] + per-workout repair path is reused unchanged. Workouts are paired
 * to their [DayPlan] by position. A missing/empty workout (short response, malformed entry, or a
 * total builder failure) is replaced with the deterministic fallback for that day — sibling-rotated
 * so sibling days still differ even when the LLM didn't return them.
 */
function splitProgramToBuilderResults(
  raw: unknown,
  dayPlans: DayPlan[],
  candidatesByDay: LiftCandidate[][],
  repBandsByDay: Map<string, RepBand>[],
  context: GenerationContext,
): { results: WorkoutBuilderResult[]; llmWorkoutCount: number; perDayFallbackCount: number } {
  const workouts = extractWorkouts(raw, dayPlans.length);
  let llmWorkoutCount = 0;
  let perDayFallbackCount = 0;
  const results = dayPlans.map((plan, i) => {
    const workoutJson = i < workouts.length ? workouts[i] : null;
    if (hasWorkoutLifts(workoutJson)) {
      llmWorkoutCount++;
      return {
        dayPlan: plan,
        dayIndex: i,
        allDayPlans: dayPlans,
        candidates: candidatesByDay[i],
        repBands: repBandsByDay[i],
        raw: { workout: workoutJson },
      };
    }
    perDayFallbackCount++;
    const fallback = deterministicFallbackWorkout(plan, candidatesByDay[i], repBandsByDay[i], context, dayPlans, i);
    return {
      dayPlan: plan,
      dayIndex: i,
      allDayPlans: dayPlans,
      candidates: candidatesByDay[i],
      repBands: repBandsByDay[i],
      raw: { workout: fallback },
    };
  });
  return { results, llmWorkoutCount, perDayFallbackCount };
}

/**
 * Extracts the workouts array from a whole-program builder response, accepting either the
 * `{ program: { workouts: [...] } }` shape (what the schema requests) or a bare
 * `{ workouts: [...] }`. Returns an array padded to [expected] with null so every day has a slot.
 */
function extractWorkouts(raw: unknown, expected: number): unknown[] {
  const empty = Array.from({ length: expected }, () => null);
  if (!raw || typeof raw !== "object") return empty;
  const body = raw as Record<string, unknown>;
  const program = (body.program ?? body) as Record<string, unknown> | undefined;
  if (!program || typeof program !== "object") return empty;
  const workouts = Array.isArray(program.workouts) ? program.workouts : [];
  const out = empty.slice();
  workouts.forEach((w, i) => {
    if (i < expected) out[i] = w;
  });
  return out;
}

/** Whether a workout object (bare or wrapped) has at least one workout lift entry. */
function hasWorkoutLifts(raw: unknown): boolean {
  if (!raw || typeof raw !== "object") return false;
  const body = raw as Record<string, unknown>;
  const workout = (body.workout ?? body) as Record<string, unknown> | undefined;
  if (!workout || typeof workout !== "object") return false;
  const lifts = Array.isArray(workout.workoutLifts) ? workout.workoutLifts : [];
  return lifts.length > 0;
}

/** Rejects after [ms] if the promise hasn't settled (abort for the single builder call). */
function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timeout after ${ms}ms`)), ms);
    promise.then(
      (v) => { clearTimeout(timer); resolve(v); },
      (e) => { clearTimeout(timer); reject(e); },
    );
  });
}

/** Full deterministic fallback: every workout rebuilt from rank-n candidates (sibling-rotated). */
function fullDeterministicFallback(
  dayPlans: DayPlan[],
  candidatesByDay: LiftCandidate[][],
  repBandsByDay: Map<string, RepBand>[],
  context: GenerationContext,
): { name: string; workouts: ProgramWorkoutDraft[] } {
  const workouts = dayPlans.map((plan, i) =>
    deterministicFallbackWorkout(plan, candidatesByDay[i], repBandsByDay[i], context, dayPlans, i),
  );
  return { name: "Stratum Program", workouts };
}

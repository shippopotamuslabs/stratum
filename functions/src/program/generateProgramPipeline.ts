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
 * deterministic fallback; a global re-gen is attempted once if final validation fails; if that
 * also fails, the whole program is rebuilt from the deterministic fallback. No 6-attempt loops.
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
  PROGRAM_BUILDER_SYSTEM_PROMPT,
  programBuilderOutputSchema,
} from "./programBuilderPrompt";

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
  /** Per-call timeout for the whole-program builder (ms, default 120_000). */
  builderTimeoutMs?: number;
  progress?: PipelineProgressCallback;
}

/** Threshold above which the LLM split planner is invoked (5–6 days). */
const SPLIT_PLANNER_DAY_THRESHOLD = 4;

/** Max attempts for the global re-gen escape hatch. */
const MAX_GLOBAL_REGEN_ATTEMPTS = 1;

/** Default timeout for the single whole-program builder call (replaces the 90s × N fan-out). */
const DEFAULT_BUILDER_TIMEOUT_MS = 120_000;

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
  const context = parseGenerationContext(payload);
  const progress = options.progress ?? (() => {});

  // Phase 0: deterministic precompute.
  const weeklyTargets = computeAllWeeklyTargets(context);
  const patternsByMuscle = availablePatternsByMuscle(context);
  await progress("split_planning");

  // Phase 1a: split planning. Deterministic for ≤4 days; LLM for 5–6 (with allocator fallback).
  const useLlmSplitPlanner =
    !options.skipSplitPlanner && context.trainingDaysPerWeek > SPLIT_PLANNER_DAY_THRESHOLD;
  let dayPlans: DayPlan[];
  if (useLlmSplitPlanner) {
    dayPlans = await runSplitPlanner(client, model, context, weeklyTargets, patternsByMuscle, sessionId);
    // If the planner failed, fall back to the deterministic allocator (relaxed to 4-day UL).
    if (dayPlans.length === 0) {
      dayPlans = allocateDayPlans({ ...context, trainingDaysPerWeek: SPLIT_PLANNER_DAY_THRESHOLD }, weeklyTargets);
    }
  } else {
    dayPlans = allocateDayPlans(context, weeklyTargets);
  }

  // Phase 1b: per-day candidates + rep bands.
  const progressionScheme = schemeForTrainingAge(context.trainingAge);
  const candidatesByDay = dayPlans.map((plan) => buildDayCandidates(plan, context));
  const repBandsByDay = dayPlans.map((plan, i) => repBandsForLifts(candidatesByDay[i].map((c) => c.liftId), context));

  // Phase 1c: single whole-program builder.
  await progress("building_workouts");
  const results = await runProgramBuilder(
    client,
    model,
    dayPlans,
    candidatesByDay,
    repBandsByDay,
    progressionScheme,
    sessionId,
    context,
    options,
  );

  // Phase 2: integration + repair (includes intra-workout dedup in repairWorkout).
  await progress("integrating");
  let program = mergeWorkouts(results, context);
  program = repairWeeklyVolume(program, weeklyTargets, context);

  // Final gate: normalize + validate. One global re-gen attempt on failure.
  // parseAndNormalizeProgram expects { program: { workouts: [...] } } (v1 wire shape).
  let normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
  if (options.skipFinalValidation) {
    return normalized;
  }
  let report = validateProgramMetrics(normalized, context);
  if (report.pass) {
    return normalized;
  }

  for (let attempt = 0; attempt < MAX_GLOBAL_REGEN_ATTEMPTS && !report.pass; attempt++) {
    // Global re-gen: re-run the whole-program builder with a "fix these errors" nudge.
    const retryResults = await runProgramBuilder(
      client,
      model,
      dayPlans,
      candidatesByDay,
      repBandsByDay,
      progressionScheme,
      sessionId,
      context,
      options,
      report.issues,
    );
    program = mergeWorkouts(retryResults, context);
    program = repairWeeklyVolume(program, weeklyTargets, context);
    normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
    report = validateProgramMetrics(normalized, context);
  }

  if (!report.pass) {
    // Last resort: full deterministic fallback. Every workout rebuilt from rank-n candidates
    // (sibling-rotated) so sibling days still vary even without the LLM.
    program = fullDeterministicFallback(dayPlans, candidatesByDay, repBandsByDay, context);
    normalized = parseAndNormalizeProgram({ program }, normalizationRequest);
  }
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
    patternSlots: Array.isArray(day.patternSlots)
      ? (day.patternSlots as Array<Record<string, unknown>>).map((s) => ({
        pattern: String(s.pattern ?? ""),
        muscleFocus: String(s.muscleFocus ?? ""),
        minSets: Number(s.minSets ?? 1),
        maxSets: Number(s.maxSets ?? 6),
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
  validationIssues?: unknown[],
): Promise<WorkoutBuilderResult[]> {
  const timeoutMs = options.builderTimeoutMs ?? DEFAULT_BUILDER_TIMEOUT_MS;
  const extras = buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true });
  const input = buildProgramBuilderInput(dayPlans, candidatesByDay, repBandsByDay, progressionScheme);
  const messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [
    { role: "system", content: PROGRAM_BUILDER_SYSTEM_PROMPT },
    { role: "user", content: JSON.stringify(input) },
  ];
  if (validationIssues) {
    messages.push({
      role: "user",
      content: `Previous program had validation errors: ${JSON.stringify(validationIssues)}. Fix and regenerate a complete program with ${dayPlans.length} workouts.`,
    });
  }

  let raw: unknown = null;
  try {
    const completion = await withTimeout(
      client.chat.completions.create({
        model,
        messages,
        temperature: BUILDER_TEMPERATURE,
        response_format: { type: "json_schema", json_schema: programBuilderOutputSchema() },
        ...extras,
      } as OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming),
      timeoutMs,
    );
    const content = completion.choices[0]?.message?.content;
    if (content) {
      try {
        raw = JSON.parse(content);
      } catch {
        raw = null;
      }
    }
  } catch {
    raw = null; // timeout or API error → fallback
  }

  return splitProgramToBuilderResults(raw, dayPlans, candidatesByDay, repBandsByDay, context);
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
): WorkoutBuilderResult[] {
  const workouts = extractWorkouts(raw, dayPlans.length);
  return dayPlans.map((plan, i) => {
    const workoutJson = i < workouts.length ? workouts[i] : null;
    if (hasWorkoutLifts(workoutJson)) {
      // Wrap as a per-workout object shaped like the old builder's { workout: {...} } so
      // toWorkoutDraft (called inside mergeWorkouts → repairWorkout) can read it unchanged.
      return {
        dayPlan: plan,
        candidates: candidatesByDay[i],
        repBands: repBandsByDay[i],
        raw: { workout: workoutJson },
      };
    }
    // Missing/empty day → deterministic fallback (sibling-rotated) so siblings still vary.
    const fallback = deterministicFallbackWorkout(plan, candidatesByDay[i], repBandsByDay[i], context, dayPlans, i);
    return {
      dayPlan: plan,
      candidates: candidatesByDay[i],
      repBands: repBandsByDay[i],
      raw: { workout: fallback },
    };
  });
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

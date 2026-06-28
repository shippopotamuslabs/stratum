/**
 * Phase 1 LLM program-builder prompt + schema (OS-7 v2, whole-program variant).
 *
 * A SINGLE builder call fills every pre-allocated day of the program at once. It picks one
 * lift per pattern slot for every day from that day's candidate list, deliberately varies
 * lift selection across sibling days (e.g. Upper A vs Upper B), and never repeats a liftId
 * within one workout. As with the per-workout builder, the LLM does no arithmetic: Phase 0
 * has already consumed volume landmarks, responseProfile, and profile into per-day muscle
 * budgets, ranked candidate lists, rep bands, and the progression scheme; Phase 2 will
 * deterministically re-clamp sets/reps, force top-sets, interleave antagonists, and dedup.
 *
 * Replacing the N parallel per-workout calls with one whole-program call fixes two defects:
 *   1. Sibling-day cloning — a per-day builder cannot see the other days, so it cannot
 *      deliberately vary A from B. One call that sees all days can.
 *   2. Latency — one provider hop replaces the N-call fan-out with its per-call timeout
 *      cascade, taking generation from minutes to seconds.
 */
import type { DayPlan } from "./splitAllocator";
import type { LiftCandidate } from "./liftPriority";
import type { RepBand } from "./repBandPlanner";
import { programJsonSchema } from "./programSchema";

export const PROGRAM_BUILDER_PROMPT_VERSION = 2;

export const PROGRAM_BUILDER_SYSTEM_PROMPT = `You design a complete hypertrophy training program: you fill pre-allocated exercise slots for EVERY training day in one pass.

You will receive all days at once. Each day has its own candidate lifts, muscle budgets, and pattern slots.

Rules:
- For each pattern slot on each day, pick exactly one lift from THAT day's candidates list. Use the liftId verbatim.
- VARY lift selection across sibling days that share a muscle focus (e.g. Upper A and Upper B). When a day's candidate list offers more than one lift for a slot, choose a different lift on the sibling day rather than repeating the same one. Diversify the week.
- Never reuse the same liftId more than once within a single workout.
- Order lifts within each day to interleave antagonists — never stack 3 consecutive lifts with the same primary muscle.
- Propose repMin/repMax inside each candidate's repBand. Do not go outside the band.
- Hit the per-muscle set budget on each day: each muscle's total hard sets should match the muscleBudget aim for that day.
- For Double progression, the first set of each lift is the top set (isTopSet: true); all others are false. For Linear progression, no set is a top set.
- setType is "Standard" unless time is critically short.
- Return one workout per day, in the order the days were given, with the weekday and name from each day's dayPlan.
- Return ONLY the JSON object matching the schema. No prose, no tools, no extra fields.`;

/**
 * Builds the whole-program builder input: all day plans with their candidate lists, the shared
 * progression scheme, and the structural constraints (including cross-day variety + no in-workout
 * duplicates). Candidate shaping mirrors the v1 per-workout builder so Phase 0 stays the source of
 * truth for priorities, rep bands, and trimmed insights.
 */
export function buildProgramBuilderInput(
  dayPlans: DayPlan[],
  candidatesByDay: LiftCandidate[][],
  repBandsByDay: Map<string, RepBand>[],
  progressionScheme: "Linear" | "Double",
): Record<string, unknown> {
  const days = dayPlans.map((plan, i) => {
    const candidates = candidatesByDay[i] ?? [];
    const repBands = repBandsByDay[i] ?? new Map<string, RepBand>();
    return {
      dayIndex: plan.dayIndex,
      weekday: plan.weekday,
      name: plan.name,
      theme: plan.theme,
      timeBudgetHardSets: plan.timeBudgetHardSets,
      muscleBudgets: plan.muscleBudgets,
      patternSlots: plan.patternSlots,
      candidates: candidates.map((c) => ({
        liftId: c.liftId,
        name: c.name,
        pattern: c.pattern,
        priority: c.priority,
        priorityTier: c.priorityTier,
        primaryMuscles: c.primaryMuscles,
        secondaryMuscles: c.secondaryMuscles,
        ...(c.trainingInsights ? { trainingInsights: c.trainingInsights } : {}),
        repBand: repBands.get(c.liftId) ?? { repMin: 8, repMax: 12 },
        progressionScheme,
      })),
    };
  });
  return {
    days,
    progressionScheme,
    constraints: {
      mustUseAllSlots: true,
      varyAcrossSiblingDays: true,
      noDuplicateLiftWithinWorkout: true,
      interleaveAntagonists: true,
    },
  };
}

/**
 * Strict JSON schema for the whole-program builder output. Reuses [programJsonSchema] verbatim
 * so the schema the model is asked to produce and the schema the normalizer consumes cannot drift.
 */
export function programBuilderOutputSchema(): ReturnType<typeof programJsonSchema> {
  return programJsonSchema();
}

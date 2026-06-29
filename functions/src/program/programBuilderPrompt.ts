/**
 * Phase 1 LLM program-builder prompt + schema (OS-7 v2, whole-program variant).
 *
 * A SINGLE builder call designs a complete hypertrophy program across every training day. It owns
 * the judgment work: which lifts to select, how to distribute a muscle's set budget across one or
 * several lifts, and how to order them within a day. Phase 0 hands it the hard constraints
 * (per-muscle set budgets that sum to the weekly targets, per-day time caps, scored candidate
 * lifts, rep bands, the progression scheme) and a light `suggestedPatterns` advisory listing the
 * movement patterns a balanced day of that theme would cover. Phase 2 deterministically re-clamps
 * sets/reps, forces top-sets, interleaves antagonists, dedups, and validates volume.
 *
 * The builder is NOT told to fill rigid one-lift slots — that collapsed specialized muscles into a
 * single exercise and constrained selection artificially. Instead it is told to hit each muscle's
 * set budget using whatever combination of lifts it judges best from the candidate pool.
 */
import type { DayPlan } from "./splitAllocator";
import type { LiftCandidate } from "./liftPriority";
import type { RepBand } from "./repBandPlanner";
import { programJsonSchema } from "./programSchema";

export const PROGRAM_BUILDER_PROMPT_VERSION = 3;

export const PROGRAM_BUILDER_SYSTEM_PROMPT = `You design a complete hypertrophy training program. You receive every training day at once and build a balanced, varied program for all of them.

For each day you are given: the muscles it must train and the hard-set budget (sets, min, aim) for each, the candidate lifts you may choose from, suggested movement patterns for that day's theme, the rep band per lift, and a per-day time cap. You pick the lifts, decide how many sets each gets, and order them.

How to build each day:
- HIT THE MUSCLE BUDGETS. Each muscle's total hard sets on the day should match its muscleBudget aim. This is the primary constraint — every other instruction serves it.
- Use ONLY liftId values from that day's candidates list, verbatim. Never invent a liftId.
- Follow the suggestedPatterns as a structural guide to which movement patterns a balanced day of that theme should cover (e.g. an Upper day: a horizontal push, a vertical pull, an incline press, a row, arm isolation, lateral delt work). You are not limited to one lift per pattern — if a muscle needs more volume than one lift can sensibly provide, spread its sets across two complementary lifts (e.g. triceps: a pushdown + an overhead extension; chest: a flat press + an incline press). Conversely, a single compound can satisfy much of a muscle's budget.
- VARY lift selection across sibling days that share a theme (e.g. Upper A vs Upper B). When the candidate pool offers more than one good option for a movement, choose a different one on the sibling day rather than repeating it. Diversify the week.
- Never reuse the same liftId more than once within a single workout.
- Distribute sets sensibly: compounds typically 3–5 working sets, isolation/accessory 2–4. Don't dump a muscle's entire budget onto one exercise when several good candidates exist.
- Order lifts within each day by training importance. Place heavy compound lifts (candidates with isCompound: true — squats, deadlifts, presses, rows, pull-ups, overhead presses) before isolation/accessory work, because compounds demand the most focus and load and are far harder when fatigued. The exception is pre-exhaustion or specialization: if a day targets a specific muscle the user is specializing in (e.g. arms), you MAY lead with a targeted isolation lift to pre-fatigue it, then follow with compounds. Use judgment — but when in doubt, compound-first. Never put a heavy deadlift or squat last in a workout.
- Interleave antagonists within the ordering — never stack 3 consecutive lifts with the same primary muscle.
- Propose repMin/repMax inside each candidate's repBand. Do not go outside the band.
- Respect the per-day time cap (timeBudgetHardSets): the total number of hard sets across all lifts on a day should fit within it.
- For Double progression, the first set of each lift is the top set (isTopSet: true); all others are false. For Linear progression, no set is a top set.
- setType is "Standard" unless time is critically short.
- Return one workout per day, in the order the days were given, with the weekday and name from each day's plan.
- Return ONLY the JSON object matching the schema. No prose, no tools, no extra fields.`;

/**
 * Builds the whole-program builder input: all day plans with their candidate lists, the shared
 * progression scheme, and the structural constraints (cross-day variety + no in-workout duplicates).
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
      suggestedPatterns: plan.suggestedPatterns,
      candidates: candidates.map((c) => ({
        liftId: c.liftId,
        name: c.name,
        pattern: c.pattern,
        priority: c.priority,
        priorityTier: c.priorityTier,
        isCompound: c.isCompound,
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
      varyAcrossSiblingDays: true,
      noDuplicateLiftWithinWorkout: true,
      compoundBeforeAccessory: true,
      interleaveAntagonists: true,
      hitMuscleBudgets: true,
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

/** Issue row from [validateProgramMetrics] for retry feedback. */
export interface ProgramBuilderValidationIssue {
  code: string;
  severity: string;
  message: string;
}

/**
 * User follow-up after the first builder response failed metric validation. Keeps the model in the
 * same chat: it should tweak the program it just returned, not rebuild from scratch.
 */
export function buildProgramBuilderRetryMessage(
  issues: ProgramBuilderValidationIssue[],
  trainingDaysPerWeek: number,
): string {
  const errors = issues.filter((issue) => issue.severity === "error");
  const warnings = issues.filter((issue) => issue.severity === "warning");
  const lines = [
    "There were a few issues with the program. Please correct these and return the full updated program JSON.",
    "",
    ...errors.map((issue) => `- [ERROR ${issue.code}] ${issue.message}`),
    ...warnings.map((issue) => `- [WARN ${issue.code}] ${issue.message}`),
    "",
    `Keep all ${trainingDaysPerWeek} workouts. Make targeted tweaks — adjust set counts, lift choices, or ordering — rather than rebuilding from scratch.`,
    "Use only liftId values from the candidate lists you were given.",
  ];
  return lines.join("\n");
}

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

export const PROGRAM_BUILDER_PROMPT_VERSION = 4;

export const PROGRAM_BUILDER_SYSTEM_PROMPT = `You design a complete hypertrophy training program. You receive every training day at once and build a balanced, varied program for all of them.

You are given: the training days list, the progression scheme, and a global candidates list of available exercises. For each day, you are given the muscles it must train, the hard-set budget (sets, min, aim) for each, suggested movement patterns for that day's theme, a per-day time cap, and a targetExerciseCount — the maximum number of distinct lifts allowed in that workout.

SESSION SIZE (critical):
- targetExerciseCount is a HARD CAP on distinct lifts per workout. Typical 4-day upper/lower: Lower days ≈5 exercises, Upper days ≈7–8. Never exceed targetExerciseCount.
- suggestedPatterns are COVERAGE HINTS, not slots to fill. Do NOT add one lift per pattern. Pick the smallest set of lifts that hits the muscle budgets.
- Specialization (e.g. arms): add SETS to 1 bicep + 1 tricep exercise. Do NOT add a second curl or a third tricep movement.
- Lower body: exactly ONE hip-hinge / deadlift-variant lift per workout (never RDL + conventional deadlift + stiff-leg in the same session). One squat-pattern lift. One quad isolation OR lunge. One ham isolation. Calves optional (one exercise).
- Upper body: at most ONE vertical pull + ONE horizontal pull. At most TWO chest presses (e.g. flat + incline). ONE lateral-delt exercise. ONE rear-delt OR face-pull, not both. ONE bicep + ONE tricep isolation.

How to build each day:
- HIT THE MUSCLE BUDGETS with as FEW lifts as possible. Prefer 3–5 working sets on compounds and 2–3 on isolations over adding more exercises.
- Use ONLY liftId values from the global candidates list, verbatim. Never invent a liftId.
- VARY lift selection across sibling days that share a theme (e.g. Upper A vs Upper B). Choose different lifts on the sibling day when the pool offers good alternatives.
- Never reuse the same liftId more than once within a single workout.
- Order lifts by training importance: heavy compounds (isCompound: true) before isolation/accessory work. Exception: arm specialization may lead with a curl or tricep isolation, then compounds. Never put a heavy deadlift or squat last.
- Interleave antagonists — never stack 3 consecutive lifts with the same primary muscle.
- Propose repMin/repMax inside each candidate's repBand. Do not go outside the band.
- Respect timeBudgetHardSets: total hard sets must fit within the cap.
- For Double progression, the first set of each lift is the top set (isTopSet: true); all others false. For Linear, no top sets.
- setType is "Standard" unless time is critically short.
- Return one workout per day, in the order the days were given, with the weekday and name from each day's plan.
- Return ONLY the JSON object matching the schema. No prose, no tools, no extra fields.`;

/**
 * Builds the whole-program builder input: all day plans, the shared global candidate pool,
 * progression scheme, and structural constraints.
 */
export function buildProgramBuilderInput(
  dayPlans: DayPlan[],
  globalCandidates: LiftCandidate[],
  globalRepBands: Map<string, RepBand>,
  progressionScheme: "Linear" | "Double",
): Record<string, unknown> {
  const days = dayPlans.map((plan) => {
    return {
      dayIndex: plan.dayIndex,
      weekday: plan.weekday,
      name: plan.name,
      theme: plan.theme,
      timeBudgetHardSets: plan.timeBudgetHardSets,
      targetExerciseCount: plan.targetExerciseCount,
      muscleBudgets: plan.muscleBudgets,
      suggestedPatterns: plan.suggestedPatterns,
    };
  });
  return {
    days,
    progressionScheme,
    candidates: globalCandidates.map((c) => ({
      liftId: c.liftId,
      name: c.name,
      pattern: c.pattern,
      priority: c.priority,
      priorityTier: c.priorityTier,
      isCompound: c.isCompound,
      primaryMuscles: c.primaryMuscles,
      secondaryMuscles: c.secondaryMuscles,
      ...(c.trainingInsights ? { trainingInsights: c.trainingInsights } : {}),
      repBand: globalRepBands.get(c.liftId) ?? { repMin: 8, repMax: 12 },
      progressionScheme,
    })),
    constraints: {
      varyAcrossSiblingDays: true,
      noDuplicateLiftWithinWorkout: true,
      compoundBeforeAccessory: true,
      interleaveAntagonists: true,
      hitMuscleBudgets: true,
      respectTargetExerciseCount: true,
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
    "Do not exceed each day's targetExerciseCount. Prefer fewer lifts with more sets.",
  ];
  return lines.join("\n");
}

/**
 * Deterministic Phase 2 integration + repair (OS-7 v2).
 *
 * Merges N per-workout builder results into a program document, repairs each workout
 * deterministically (drop unknown liftIds, clamp reps to bands, force one Double top set, merge
 * duplicate liftId entries, backfill under-min muscles from the muscle budgets, trim to the time
 * budget, interleave antagonists), runs a global weekly-volume solver, and provides a deterministic
 * fallback workout when an LLM builder fails or returns empty.
 *
 * Repair is driven by the day's MUSCLE BUDGETS (per-muscle min/aim), not by rigid pattern slots.
 * The builder owns how many lifts and how many sets each; Phase 2 only guarantees the invariants:
 * every hard constraint (min sets per muscle, time cap, top-set rule, no duplicates, valid ids)
 * holds regardless of what the model returned.
 */
import type { GenerationContext, LiftContext } from "./programPayloadContext";
import {
  isPullPattern,
  isPushPattern,
  MAJOR_MUSCLES,
  MINUTES_PER_HARD_SET,
  PRIMARY_SET_CREDIT,
  SECONDARY_SET_CREDIT,
} from "./programPayloadContext";
import { MAJOR_COMPOUND_PATTERNS, schemeForTrainingAge, sessionExerciseCapForTheme } from "./programRules";
import {
  computeWorkoutMuscleVolume,
  validateProgramMetrics,
  type ProgramDraft,
  type ProgramWorkoutDraft,
  type ProgramLiftDraft,
} from "./programValidation";
import { SESSION_MUSCLE_SET_CAP, MIN_SPECIALIZED_DIRECT_SETS, sessionHardSetCapForLength } from "./rpReferenceTables";
import type { DayPlan, MuscleBudget, SuggestedPattern } from "./splitAllocator";
import type { LiftCandidate } from "./liftPriority";
import { compareLiftCandidates } from "./liftPriority";
import { liftCatalogQualityAdjustment } from "./liftCatalogQuality";
import type { RepBand } from "./repBandPlanner";

/** A builder result for one day: the raw LLM output (may be malformed/empty) + the day plan. */
export interface WorkoutBuilderResult {
  dayPlan: DayPlan;
  /** Index of this day in the program (for sibling-rotation fallback). */
  dayIndex: number;
  /** All day plans in the program (for sibling-rotation fallback). */
  allDayPlans: DayPlan[];
  candidates: LiftCandidate[];
  repBands: Map<string, RepBand>;
  /** The raw LLM output (parsed JSON or null when the call failed/returned invalid JSON). */
  raw: unknown;
}

/** Merges per-workout results into a ProgramDraft for the final normalizer. */
export function mergeWorkouts(results: WorkoutBuilderResult[], context: GenerationContext): ProgramDraft {
  const workouts: ProgramWorkoutDraft[] = results.map((result, index) => {
    const plan = result.dayPlan;
    const draft = toWorkoutDraft(result.raw, plan);
    const useFallback =
      (draft.workoutLifts?.length ?? 0) === 0 && result.candidates.length > 0;
    const base = useFallback
      ? deterministicFallbackWorkout(
          plan,
          result.candidates,
          result.repBands,
          context,
          result.allDayPlans,
          result.dayIndex,
        )
      : draft;
    let repaired = repairWorkout(base, plan, result.candidates, result.repBands, context);
    if ((repaired.workoutLifts?.length ?? 0) === 0 && result.candidates.length > 0) {
      repaired = deterministicFallbackWorkout(
        plan,
        result.candidates,
        result.repBands,
        context,
        result.allDayPlans,
        result.dayIndex,
      );
    }
    return {
      name: repaired.name ?? plan.name,
      weekday: plan.weekday,
      position: index,
      workoutLifts: repaired.workoutLifts,
    };
  });
  return { name: "Stratum Program", workouts };
}

/** Extracts a workout draft from raw LLM output, falling back to an empty draft when malformed. */
function toWorkoutDraft(raw: unknown, plan: DayPlan): ProgramWorkoutDraft {
  if (!raw || typeof raw !== "object") {
    return { name: plan.name, workoutLifts: [] };
  }
  const body = raw as Record<string, unknown>;
  const workout = (body.workout ?? body) as Record<string, unknown>;
  if (!workout || typeof workout !== "object") {
    return { name: plan.name, workoutLifts: [] };
  }
  const lifts = Array.isArray(workout.workoutLifts) ? workout.workoutLifts : [];
  return {
    name: typeof workout.name === "string" ? workout.name : plan.name,
    workoutLifts: lifts.filter((l): l is ProgramLiftDraft => !!l && typeof l === "object"),
  };
}

/**
 * Per-muscle set accounting for one workout. Primary hits count 1.0; secondary hits count 0.5
 * (matches the validator's credit model). Used by the backfill + trim passes to hit muscle mins.
 */
function muscleCredit(
  lift: LiftCandidate,
  setCount: number,
): Record<string, number> {
  const out: Record<string, number> = {};
  for (const m of lift.primaryMuscles) out[m] = (out[m] ?? 0) + setCount * PRIMARY_SET_CREDIT;
  for (const m of lift.secondaryMuscles) {
    if (lift.primaryMuscles.includes(m)) continue;
    out[m] = (out[m] ?? 0) + setCount * SECONDARY_SET_CREDIT;
  }
  return out;
}

/**
 * Deterministically repairs one workout:
 *  - drops lifts whose liftId is not in the candidate set (unknown/hallucinated ids),
 *  - clamps reps to the candidate's rep band,
 *  - forces exactly one top set for Double-progression lifts,
 *  - merges duplicate liftId entries (the LLM can emit the same lift twice),
 *  - backfills sets on the highest-priority lift(s) when a muscle is under its per-day min,
 *  - drops the lowest-priority sets when total hard sets exceed the time budget,
 *  - interleaves antagonists if 3+ consecutive lifts share a primary muscle.
 */
export function repairWorkout(
  draft: ProgramWorkoutDraft,
  plan: DayPlan,
  candidates: LiftCandidate[],
  repBands: Map<string, RepBand>,
  context: GenerationContext,
): ProgramWorkoutDraft {
  const candidateById = new Map(candidates.map((c) => [c.liftId, c]));
  const scheme = schemeForTrainingAge(context.trainingAge);

  // 1. Drop unknown liftIds; clamp set counts + reps per candidate.
  const repairedLifts: ProgramLiftDraft[] = [];
  for (const lift of draft.workoutLifts ?? []) {
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    const candidate = candidateById.get(liftId);
    if (!candidate) continue; // drop unknown
    const band = repBands.get(liftId);
    const rawSets = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
    const repairedSets = rawSets.map((set, i) => repairSet(set, band, scheme, i === 0, candidate, context));
    if (repairedSets.length === 0) continue;

    repairedLifts.push({
      liftId,
      position: typeof lift.position === "number" ? lift.position : repairedLifts.length,
      workoutLiftSets: repairedSets,
    });
  }

  // 1b. Dedup: collapse entries sharing a liftId into one. Merge their sets, re-enforce one top set.
  const dedupedLifts = mergeDuplicateLifts(repairedLifts, candidateById, scheme);

  // 1c. Collapse redundant structure (extra hip hinges, arm variants, duplicate pulls) before backfill.
  consolidateWorkoutStructure(dedupedLifts, candidateById);

  // 2. Backfill under-min muscles: for each muscle budget, if total credits < min, add sets to the
  //    highest-priority candidate lift that trains it (creating it if absent).
  backfillMuscleMins(dedupedLifts, plan, candidates, repBands, scheme, context);

  // 3. Trim over-budget total hard sets (drop lowest-priority sets first).
  const timeBudget = plan.timeBudgetHardSets;
  if (timeBudget != null) {
    trimToTimeBudget(dedupedLifts, timeBudget, candidateById);
  }

  // 3b. Cap exercise count so LLM and fallback paths cannot balloon past commercial session size.
  trimToExerciseCap(dedupedLifts, sessionExerciseCap(plan), candidateById);

  // 4. Reorder to interleave antagonists if 3+ consecutive share a primary muscle.
  const interleaved = interleaveAntagonists(dedupedLifts, candidateById);

  return { name: draft.name, workoutLifts: interleaved };
}

/** Repairs a single set: clamps reps to band, forces RIR/top-set per scheme. */
function repairSet(
  set: unknown,
  band: RepBand | undefined,
  scheme: "Linear" | "Double",
  isFirstSet: boolean,
  candidate: LiftCandidate,
  context: GenerationContext,
): NonNullable<ProgramLiftDraft["workoutLiftSets"]>[number] {
  const raw = (set && typeof set === "object" ? set : {}) as Record<string, unknown>;
  const isMajorCompound = MAJOR_COMPOUND_PATTERNS.has(candidate.pattern);
  // For Double scheme, the first set of a lift is the top set; all others are forced non-top.
  // For Linear, no sets are top sets.
  const isTopSet = scheme === "Double" ? isFirstSet : false;
  const rirGoal = isTopSet ? 2 : (isMajorCompound ? clampRir(raw.rirGoal) : 0);
  const rawRepMin = typeof raw.repMin === "number" ? raw.repMin : band?.repMin ?? 8;
  const rawRepMax = typeof raw.repMax === "number" ? raw.repMax : band?.repMax ?? 12;
  const repMin = band ? Math.max(band.repMin, Math.min(band.repMax, rawRepMin)) : rawRepMin;
  const repMax = band ? Math.max(repMin, Math.min(band.repMax, rawRepMax)) : Math.max(repMin, rawRepMax);
  const setType = raw.setType === "MyoRep" || raw.setType === "DropSet" ? raw.setType : "Standard";
  return {
    position: typeof raw.position === "number" ? raw.position : 0,
    setType: setType as string,
    repMin,
    repMax,
    rirGoal,
    isTopSet,
  };
}

function clampRir(raw: unknown): number {
  const n = typeof raw === "number" ? raw : 0;
  return n >= 1 && n <= 2 ? n : 2;
}

/**
 * Collapses [lifts] entries that share a liftId into one: the first occurrence keeps its liftId
 * and absorbs the sets of every later duplicate, positions are reassigned sequentially, and the
 * top-set rule is re-enforced for Double progression (first set top, rest non-top).
 */
function mergeDuplicateLifts(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
  scheme: "Linear" | "Double",
): ProgramLiftDraft[] {
  if (lifts.length <= 1) return lifts;
  const byLiftId = new Map<string, ProgramLiftDraft>();
  const order: string[] = [];
  for (const lift of lifts) {
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    if (liftId === "") {
      order.push(`__untyped_${order.length}`);
      byLiftId.set(order[order.length - 1], lift);
      continue;
    }
    const existing = byLiftId.get(liftId);
    if (!existing) {
      order.push(liftId);
      byLiftId.set(liftId, { ...lift });
      continue;
    }
    const incoming = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
    const merged = Array.isArray(existing.workoutLiftSets) ? [...existing.workoutLiftSets] : [];
    merged.push(...incoming);
    existing.workoutLiftSets = merged;
  }

  const out: ProgramLiftDraft[] = [];
  for (const key of order) {
    const lift = byLiftId.get(key)!;
    const rawSets = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
    const sets = rawSets.map((set, i) => {
      const s = (set && typeof set === "object" ? { ...set } : {}) as Record<string, unknown>;
      if (scheme === "Double") s.isTopSet = i === 0;
      else if ("isTopSet" in s) s.isTopSet = false;
      return s;
    });
    out.push({ ...lift, position: out.length, workoutLiftSets: sets });
  }
  return out;
}

/**
 * Counts set credits per muscle for a workout's lifts. Primary muscles count 1.0/set; secondary
 * muscles count 0.5/set (matching the validator).
 */
function countCreditsByMuscle(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
): Map<string, number> {
  const credits = new Map<string, number>();
  for (const lift of lifts) {
    const candidate = candidateById.get(lift.liftId ?? "");
    if (!candidate) continue;
    const setCount = lift.workoutLiftSets?.length ?? 0;
    for (const [muscle, credit] of Object.entries(muscleCredit(candidate, setCount))) {
      credits.set(muscle, (credits.get(muscle) ?? 0) + credit);
    }
  }
  return credits;
}

/**
 * For every muscle budget below its per-day min, adds sets to the highest-priority candidate that
 * trains it (found or created). This is the safety net that guarantees a muscle hits at least its
 * minimum volume even when the builder under-allocated.
 */
function backfillMuscleMins(
  lifts: ProgramLiftDraft[],
  plan: DayPlan,
  candidates: LiftCandidate[],
  repBands: Map<string, RepBand>,
  scheme: "Linear" | "Double",
  context: GenerationContext,
): void {
  const candidateById = new Map(candidates.map((c) => [c.liftId, c]));
  const credits = countCreditsByMuscle(lifts, candidateById);
  const usedHeavyPatterns = usedHeavyPatternsFromLifts(lifts, candidateById);
  const exerciseCap = sessionExerciseCap(plan);
  for (const budget of plan.muscleBudgets) {
    const current = credits.get(budget.muscle) ?? 0;
    if (current >= budget.min) continue;
    const top = topCandidateForMusclePreferInWorkout(
      budget.muscle,
      lifts,
      candidates,
      candidateById,
      {
        excludePatterns: usedHeavyPatterns,
        allowNewLift: lifts.length < exerciseCap,
      },
    );
    if (!top) continue;
    const deficit = Math.ceil(budget.min - current);
    addBackoffSets(lifts, top, deficit, repBands, scheme, context);
    if (SINGLE_INSTANCE_PATTERNS.has(top.pattern)) usedHeavyPatterns.add(top.pattern);
    credits.set(
      budget.muscle,
      (credits.get(budget.muscle) ?? 0) + deficit * (top.primaryMuscles.includes(budget.muscle) ? PRIMARY_SET_CREDIT : SECONDARY_SET_CREDIT),
    );
  }
}

/** Prefer a lift already in the workout; only introduce a new lift when under the exercise cap. */
function topCandidateForMusclePreferInWorkout(
  muscle: string,
  lifts: ProgramLiftDraft[],
  candidates: LiftCandidate[],
  candidateById: Map<string, LiftCandidate>,
  opts: { excludePatterns?: Set<string>; allowNewLift?: boolean },
): LiftCandidate | undefined {
  const inWorkout = lifts
    .map((l) => candidateById.get(l.liftId ?? ""))
    .filter(
      (c): c is LiftCandidate =>
        c != null &&
        (c.primaryMuscles.includes(muscle) || c.secondaryMuscles.includes(muscle)),
    )
    .sort(compareLiftCandidates)[0];
  if (inWorkout) return inWorkout;
  if (opts.allowNewLift === false) return undefined;
  return topCandidateForMuscle(muscle, candidates, { excludePatterns: opts.excludePatterns });
}

/** The highest-priority candidate whose primary OR secondary muscles include [muscle]. */
function topCandidateForMuscle(
  muscle: string,
  candidates: LiftCandidate[],
  opts?: { excludeLiftIds?: Set<string>; excludePatterns?: Set<string> },
): LiftCandidate | undefined {
  const excludeLiftIds = opts?.excludeLiftIds ?? new Set<string>();
  const excludePatterns = opts?.excludePatterns ?? new Set<string>();
  const available = candidates.filter((c) => {
    if (excludeLiftIds.has(c.liftId)) return false;
    if (SINGLE_INSTANCE_PATTERNS.has(c.pattern) && excludePatterns.has(c.pattern)) return false;
    if (isHipHingeCandidate(c) && excludePatterns.has("HipHinge")) return false;
    return true;
  });
  // Prefer a lift where the muscle is PRIMARY (full credit) before settling for secondary.
  const primary = available
    .filter((c) => c.primaryMuscles.includes(muscle))
    .sort(compareLiftCandidates)[0];
  if (primary) return primary;
  return available
    .filter((c) => c.secondaryMuscles.includes(muscle))
    .sort(compareLiftCandidates)[0];
}

function usedHeavyPatternsFromLifts(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
): Set<string> {
  const patterns = new Set<string>();
  for (const lift of lifts) {
    const pattern = candidateById.get(lift.liftId ?? "")?.pattern;
    if (pattern != null && SINGLE_INSTANCE_PATTERNS.has(pattern)) {
      patterns.add(pattern);
    }
  }
  return patterns;
}

/** Adds [count] backoff sets to [lift] (found or created) for [candidate]. */
function addBackoffSets(
  lifts: ProgramLiftDraft[],
  candidate: LiftCandidate,
  count: number,
  repBands: Map<string, RepBand>,
  scheme: "Linear" | "Double",
  context: GenerationContext,
): void {
  let lift = lifts.find((l) => l.liftId === candidate.liftId);
  if (!lift) {
    lift = { liftId: candidate.liftId, position: lifts.length, workoutLiftSets: [] };
    lifts.push(lift);
  }
  const band = repBands.get(candidate.liftId);
  const existing = lift.workoutLiftSets ?? [];
  for (let i = 0; i < count; i++) {
    const isFirst = existing.length === 0 && i === 0;
    existing.push(repairSet({}, band, scheme, isFirst, candidate, context));
  }
  lift.workoutLiftSets = existing;
}

/** Drops lowest-priority lifts until the workout is within the exercise cap. */
function trimToExerciseCap(
  lifts: ProgramLiftDraft[],
  cap: number,
  candidateById: Map<string, LiftCandidate>,
): void {
  while (lifts.length > cap) {
    const sorted = [...lifts].sort((a, b) => {
      const ca = candidateById.get(a.liftId ?? "");
      const cb = candidateById.get(b.liftId ?? "");
      const aCompound = ca?.isCompound === true ? 1 : 0;
      const bCompound = cb?.isCompound === true ? 1 : 0;
      if (aCompound !== bCompound) return aCompound - bCompound;
      return (ca?.priority ?? 0) - (cb?.priority ?? 0);
    });
    const drop = sorted[0];
    lifts.splice(lifts.indexOf(drop), 1);
  }
  lifts.forEach((lift, i) => {
    lift.position = i;
  });
}

/** Drops lowest-priority sets until total hard sets fit the time budget. */
function trimToTimeBudget(
  lifts: ProgramLiftDraft[],
  timeBudget: number,
  candidateById: Map<string, LiftCandidate>,
): void {
  let total = lifts.reduce((s, l) => s + (l.workoutLiftSets?.length ?? 0), 0);
  if (total <= timeBudget) return;
  const sorted = [...lifts].sort(
    (a, b) => (candidateById.get(a.liftId ?? "")?.priority ?? 0) - (candidateById.get(b.liftId ?? "")?.priority ?? 0),
  );
  for (const lift of sorted) {
    if (total <= timeBudget) break;
    const sets = lift.workoutLiftSets ?? [];
    while (sets.length > 1 && total > timeBudget) {
      sets.pop();
      total--;
    }
  }
  for (const lift of sorted) {
    if (total <= timeBudget) break;
    const sets = lift.workoutLiftSets ?? [];
    if (sets.length > 0 && total > timeBudget) {
      sets.length = 0;
      total--;
    }
  }
}

/** Reorders lifts so no 3 consecutive share the same primary muscle (interleave antagonists). */
function interleaveAntagonists(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
): ProgramLiftDraft[] {
  const primaryKey = (lift: ProgramLiftDraft) =>
    candidateById.get(lift.liftId ?? "")?.primaryMuscles.join("|") ?? lift.liftId ?? "";
  const result: ProgramLiftDraft[] = [];
  const remaining = [...lifts];
  let lastKey = "";
  let run = 0;
  while (remaining.length > 0) {
    let pick = remaining.find((l) => primaryKey(l) !== lastKey);
    if (!pick) pick = remaining[0];
    const key = primaryKey(pick);
    if (key === lastKey) run++;
    else run = 1;
    result.push(pick);
    remaining.splice(remaining.indexOf(pick), 1);
    lastKey = key;
    if (run >= 3 && remaining.length > 0) {
      const alt = remaining.find((l) => primaryKey(l) !== key);
      if (alt) {
        result.push(alt);
        remaining.splice(remaining.indexOf(alt), 1);
        lastKey = primaryKey(alt);
        run = 1;
      }
    }
  }
  return result.map((l, i) => ({ ...l, position: i }));
}

/**
 * Global weekly-volume solver: computes the weekly hard-set credits per muscle, compares to the
 * weekly target, and adjusts the highest-priority lift across the week for any deficit.
 * Runs after all per-workout repairs.
 */
export function repairWeeklyVolume(
  program: ProgramDraft,
  weeklyTargets: Map<string, { min: number; aim: number; max: number }>,
  context: GenerationContext,
): ProgramDraft {
  const report = validateProgramMetrics(program, context);
  const muscleVolume = report.summary.weeklyMuscleVolume;
  for (const [muscle, target] of weeklyTargets) {
    const current = muscleVolume[muscle] ?? 0;
    if (current >= target.min && current <= target.max) continue;
    if (current < target.min) {
      addSetsForMuscle(program, muscle, Math.ceil(target.min - current), context);
    }
  }
  trimSessionMuscleCreditCaps(program, context);
  trimWorkoutsToSessionDuration(program, context);
  return program;
}

/**
 * Trims sets until each workout fits the hard-set cap derived from session length (+5 min slack).
 */
export function trimWorkoutsToSessionDuration(
  program: ProgramDraft,
  context: GenerationContext,
): void {
  const setCap = sessionHardSetCapForLength(context.sessionLengthMinutes);
  if (setCap == null) return;
  for (const workout of program.workouts ?? []) {
    trimWorkoutToSessionDuration(workout, setCap, context);
  }
}

function workoutHardSetCount(workout: ProgramWorkoutDraft): number {
  return (workout.workoutLifts ?? []).reduce(
    (sum, lift) => sum + (lift.workoutLiftSets?.length ?? 0),
    0,
  );
}

function trimWorkoutToSessionDuration(
  workout: ProgramWorkoutDraft,
  setCap: number,
  context: GenerationContext,
): void {
  const ISOLATION_PATTERNS = new Set([
    "ElbowFlexion",
    "ElbowExtension",
    "KneeFlexion",
    "KneeExtension",
    "PlantarFlexion",
    "ShoulderAbduction",
    "ShoulderHorizontalAbduction",
  ]);

  for (let guard = 0; guard < 500 && workoutHardSetCount(workout) > setCap; guard++) {
    const drop = liftToDropForSessionDuration(workout, context, ISOLATION_PATTERNS);
    if (!drop?.workoutLiftSets?.length) break;
    drop.workoutLiftSets = drop.workoutLiftSets.slice(0, -1);
  }
}

function liftToDropForSessionDuration(
  workout: ProgramWorkoutDraft,
  context: GenerationContext,
  isolationPatterns: Set<string>,
): ProgramLiftDraft | undefined {
  const specialized = new Set(
    Object.entries(context.specialization)
      .filter(([, level]) => level === "Specialize")
      .map(([muscle]) => muscle),
  );

  type Candidate = { lift: ProgramLiftDraft; score: number };
  const candidates: Candidate[] = [];
  for (const lift of workout.workoutLifts ?? []) {
    const sets = lift.workoutLiftSets ?? [];
    if (sets.length === 0) continue;
    const meta = context.liftsById.get(lift.liftId ?? "");
    if (!meta) continue;

    const primarySpecialized = meta.primaryMuscles.some((m) => specialized.has(m));
    let score: number;
    if (primarySpecialized && isolationPatterns.has(meta.movementPattern)) {
      if (sets.length <= MIN_SPECIALIZED_DIRECT_SETS) continue;
      score = 4;
    } else if (isolationPatterns.has(meta.movementPattern)) {
      score = 0;
    } else if (meta.secondaryMuscles.some((m) => specialized.has(m))) {
      score = 1;
    } else if (MAJOR_COMPOUND_PATTERNS.has(meta.movementPattern)) {
      score = sets.length > 1 ? 3 : 5;
    } else {
      score = 2;
    }
    candidates.push({ lift, score });
  }

  candidates.sort((a, b) => a.score - b.score);
  return candidates[0]?.lift;
}

/**
 * Enforces the Weightology-aligned per-session ceiling (≤8 fractional hard-set credits per muscle).
 * For specialized muscles, trims secondary compound overlap before direct isolation work.
 */
export function trimSessionMuscleCreditCaps(
  program: ProgramDraft,
  context: GenerationContext,
): void {
  for (const workout of program.workouts ?? []) {
    trimWorkoutMuscleCredits(workout, context);
  }
}

function trimWorkoutMuscleCredits(workout: ProgramWorkoutDraft, context: GenerationContext): void {
  const ISOLATION_PATTERNS = new Set([
    "ElbowFlexion",
    "ElbowExtension",
    "KneeFlexion",
    "KneeExtension",
    "PlantarFlexion",
    "ShoulderAbduction",
    "ShoulderHorizontalAbduction",
  ]);

  for (let guard = 0; guard < 500; guard++) {
    const volume = computeWorkoutMuscleVolume(workout, context);
    const overEntry = Object.entries(volume).find(([, credits]) => credits > SESSION_MUSCLE_SET_CAP + 0.01);
    if (!overEntry) return;
    const [muscle] = overEntry;

    const isSpecializedMuscle = (context.specialization[muscle] ?? "Maintenance") === "Specialize";

    type TrimCandidate = { lift: ProgramLiftDraft; score: number };
    const candidates: TrimCandidate[] = [];
    for (const lift of workout.workoutLifts ?? []) {
      const sets = lift.workoutLiftSets ?? [];
      if (sets.length === 0) continue;
      const meta = context.liftsById.get(lift.liftId ?? "");
      if (!meta) continue;
      const isPrimary = meta.primaryMuscles.includes(muscle);
      const isSecondary = !isPrimary && meta.secondaryMuscles.includes(muscle);
      if (!isPrimary && !isSecondary) continue;

      if (isSpecializedMuscle && isPrimary && ISOLATION_PATTERNS.has(meta.movementPattern)) {
        if (sets.length <= MIN_SPECIALIZED_DIRECT_SETS) continue;
      }

      let score: number;
      if (isSpecializedMuscle) {
        if (isSecondary) score = 0;
        else if (ISOLATION_PATTERNS.has(meta.movementPattern)) score = 3;
        else if (MAJOR_COMPOUND_PATTERNS.has(meta.movementPattern)) score = 2;
        else score = 1;
      } else if (isSecondary) {
        score = 3;
      } else if (ISOLATION_PATTERNS.has(meta.movementPattern)) {
        score = 0;
      } else if (MAJOR_COMPOUND_PATTERNS.has(meta.movementPattern)) {
        score = 2;
      } else {
        score = 1;
      }
      candidates.push({ lift, score });
    }

    candidates.sort((a, b) => a.score - b.score);
    const target = candidates[0]?.lift;
    if (!target?.workoutLiftSets || target.workoutLiftSets.length === 0) return;
    target.workoutLiftSets = target.workoutLiftSets.slice(0, -1);
  }
}

/** Adds [count] sets across workouts/lifts that train [muscle] (primary preferred). */
function addSetsForMuscle(program: ProgramDraft, muscle: string, count: number, context: GenerationContext): void {
  const candidates: Array<{ workout: ProgramWorkoutDraft; lift: ProgramLiftDraft; primary: boolean }> = [];
  for (const workout of program.workouts) {
    for (const lift of workout.workoutLifts ?? []) {
      const meta = context.liftsById.get(lift.liftId ?? "");
      if (!meta) continue;
      const primary = meta.primaryMuscles.includes(muscle);
      const secondary = meta.secondaryMuscles.includes(muscle);
      if (primary || secondary) {
        candidates.push({ workout, lift, primary });
      }
    }
  }
  candidates.sort((a, b) => {
    const primaryDiff = Number(b.primary) - Number(a.primary);
    if (primaryDiff !== 0) return primaryDiff;
    const aSets = a.lift.workoutLiftSets?.length ?? 0;
    const bSets = b.lift.workoutLiftSets?.length ?? 0;
    if (aSets !== bSets) return aSets - bSets;
    const aMeta = context.liftsById.get(a.lift.liftId ?? "");
    const bMeta = context.liftsById.get(b.lift.liftId ?? "");
    const aQuality = aMeta ? liftCatalogQualityAdjustment(aMeta) : 0;
    const bQuality = bMeta ? liftCatalogQualityAdjustment(bMeta) : 0;
    return bQuality - aQuality;
  });

  let remaining = count;
  let idx = 0;
  const MAX_SETS_PER_LIFT = 4;
  while (remaining > 0 && candidates.length > 0 && idx < candidates.length * 8) {
    const entry = candidates[idx % candidates.length]!;
    const sets = entry.lift.workoutLiftSets ?? [];
    if (sets.length >= MAX_SETS_PER_LIFT) {
      idx++;
      continue;
    }
    const setCap = sessionHardSetCapForLength(context.sessionLengthMinutes);
    if (setCap != null && workoutHardSetCount(entry.workout) >= setCap) {
      idx++;
      continue;
    }
    const setsBefore = sets.length;
    sets.push({
      position: setsBefore,
      setType: "Standard",
      repMin: 8,
      repMax: 12,
      rirGoal: 2,
      isTopSet: false,
    });
    entry.lift.workoutLiftSets = sets;
    if (workoutExceedsSessionMuscleCap(entry.workout, context)) {
      sets.pop();
      entry.lift.workoutLiftSets = sets;
      idx++;
      continue;
    }
    remaining--;
    idx++;
  }
}

function workoutExceedsSessionMuscleCap(
  workout: ProgramWorkoutDraft,
  context: GenerationContext,
): boolean {
  const volume = computeWorkoutMuscleVolume(workout, context);
  return Object.values(volume).some((credits) => credits > SESSION_MUSCLE_SET_CAP + 0.01);
}

/** Whether [lift] trains [muscle] as primary or secondary. */
function trainsMuscle(lift: ProgramLiftDraft, muscle: string, context: GenerationContext): boolean {
  const meta = context.liftsById.get(lift.liftId ?? "");
  if (!meta) return false;
  return meta.primaryMuscles.includes(muscle) || meta.secondaryMuscles.includes(muscle);
}

/** Patterns that should not appear twice on the same day (e.g. RDL + conventional deadlift). */
const SINGLE_INSTANCE_PATTERNS = new Set(["HipHinge", "HipExtension", "SquatPattern"]);

const PULL_PATTERNS = new Set(["HorizontalPull", "VerticalPull"]);

/** Matches catalog ids/names for hip-hinge deadlift variants (pattern alone is not enough). */
const HIP_HINGE_LIFT_ID = /deadlift|romanian|stiff-?leg|good-morning/i;

/** Hard cap on distinct exercises for this session. */
function sessionExerciseCap(plan: DayPlan): number {
  return plan.targetExerciseCount ?? sessionExerciseCapForTheme(plan.theme);
}

/**
 * Drops redundant lifts the LLM (or fallback) tends to over-generate: multiple hip hinges, arm
 * variants, duplicate pulls, etc. Merges dropped sets into the kept lift so volume is preserved.
 */
function consolidateWorkoutStructure(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
): void {
  dropExtraByPredicate(lifts, candidateById, (c) => isHipHingeCandidate(c), 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "SquatPattern", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "VerticalPull" && c.primaryMuscles.includes("Back"), 1);
  dropExtraByPredicate(
    lifts,
    candidateById,
    (c) => c.pattern === "HorizontalPull" && c.primaryMuscles.includes("Back"),
    1,
  );
  dropExtraByPredicate(
    lifts,
    candidateById,
    (c) =>
      c.pattern === "HorizontalPress" ||
      c.pattern === "InclinePress" ||
      c.pattern === "VerticalPress",
    3,
  );
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "ElbowFlexion", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "ElbowExtension", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "ShoulderHorizontalAbduction", 1);
  dropExtraByPredicate(
    lifts,
    candidateById,
    (c) => c.primaryMuscles.includes("PosteriorDeltoid"),
    1,
  );
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "ShoulderAbduction", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "SpinalFlexion", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "PlantarFlexion", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "HipExtension", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "KneeExtension", 1);
  dropExtraByPredicate(lifts, candidateById, (c) => c.pattern === "KneeFlexion", 1);
}

function dropExtraByPredicate(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
  matches: (candidate: LiftCandidate) => boolean,
  keep: number,
): void {
  if (keep < 1) return;
  const matching = lifts
    .map((lift) => ({ lift, candidate: candidateById.get(lift.liftId ?? "") }))
    .filter((entry): entry is { lift: ProgramLiftDraft; candidate: LiftCandidate } =>
      entry.candidate != null && matches(entry.candidate),
    )
    .sort((a, b) => {
      const aCompound = a.candidate.isCompound ? 1 : 0;
      const bCompound = b.candidate.isCompound ? 1 : 0;
      if (aCompound !== bCompound) return bCompound - aCompound;
      return compareLiftCandidates(b.candidate, a.candidate);
    });
  for (let i = keep; i < matching.length; i++) {
    const drop = matching[i].lift;
    const keepLift = matching[0].lift;
    mergeWorkoutLiftSets(keepLift, drop);
    const index = lifts.indexOf(drop);
    if (index >= 0) lifts.splice(index, 1);
  }
}

function mergeWorkoutLiftSets(target: ProgramLiftDraft, source: ProgramLiftDraft): void {
  const merged = [...(target.workoutLiftSets ?? []), ...(source.workoutLiftSets ?? [])];
  target.workoutLiftSets = merged.map((set, position) => {
    const raw = set && typeof set === "object" ? set : {};
    return { ...raw, position };
  });
}

function isHipHingeCandidate(candidate: LiftCandidate): boolean {
  return candidate.pattern === "HipHinge" || HIP_HINGE_LIFT_ID.test(candidate.liftId) || HIP_HINGE_LIFT_ID.test(candidate.name);
}

function isRedundantPull(pattern: string, selected: LiftCandidate[]): boolean {
  if (!PULL_PATTERNS.has(pattern)) return false;
  return selected.some((c) => c.pattern === pattern);
}

function isRedundantQuadIsolation(pattern: string, selected: LiftCandidate[]): boolean {
  if (pattern !== "KneeExtension" && pattern !== "KneeFlexion") return false;
  return selected.some((c) => c.pattern === "SquatPattern");
}

/**
 * Orders suggested patterns for fallback selection: compounds and specialized muscles first,
 * redundant pulls / quad isolations deprioritized.
 */
function fallbackPatternOrder(plan: DayPlan, context: GenerationContext): SuggestedPattern[] {
  const specialized = new Set(
    Object.entries(context.specialization)
      .filter(([, level]) => level === "Specialize")
      .map(([muscle]) => muscle),
  );
  const priority = (slot: SuggestedPattern): number => {
    if (specialized.has(slot.muscleFocus) && (slot.pattern === "ElbowFlexion" || slot.pattern === "ElbowExtension")) {
      return 0;
    }
    if (slot.pattern === "HorizontalPress" || slot.pattern === "SquatPattern" || slot.pattern === "HipHinge") {
      return 1;
    }
    if (slot.pattern === "HorizontalPull" || slot.pattern === "VerticalPull") return 2;
    if (slot.pattern === "ElbowFlexion" || slot.pattern === "ElbowExtension") return 3;
    if (slot.pattern === "ShoulderAbduction" || slot.pattern === "ShoulderHorizontalAbduction") return 4;
    if (slot.pattern === "InclinePress" || slot.pattern === "VerticalPress") return 5;
    if (slot.pattern === "KneeExtension" || slot.pattern === "KneeFlexion") return 6;
    if (slot.pattern === "PlantarFlexion" || slot.pattern === "HipExtension") return 7;
    return 8;
  };
  return [...plan.suggestedPatterns].sort((a, b) => priority(a) - priority(b));
}

/**
 * Deterministic fallback workout: covers every fillable suggested pattern, adds extra lifts for
 * specialized muscles, distributes per-muscle budgets across multiple exercises, and uses full
 * rep bands. Used when the LLM builder fails, times out, or returns empty.
 *
 * Sibling rotation: when [allPlans] and [dayIndex] are supplied, the candidate picked for each
 * pattern is rotated by the day's sibling index so sibling days differ in the fallback path.
 */
export function deterministicFallbackWorkout(
  plan: DayPlan,
  candidates: LiftCandidate[],
  repBands: Map<string, RepBand>,
  context: GenerationContext,
  allPlans?: DayPlan[],
  dayIndex?: number,
): ProgramWorkoutDraft {
  const scheme = schemeForTrainingAge(context.trainingAge);
  const siblingIndex = allPlans != null && dayIndex != null ? siblingIndexFor(plan, allPlans, dayIndex) : 0;
  const selected = selectFallbackLifts(plan, candidates, context, siblingIndex);
  const setCounts = allocateFallbackSetCounts(plan, selected, context);
  const ordered = [...selected].sort((a, b) => {
    if (a.isCompound !== b.isCompound) return a.isCompound ? -1 : 1;
    return compareLiftCandidates(a, b);
  });

  let topSetAssigned = false;
  const lifts: ProgramLiftDraft[] = ordered.map((candidate, liftIndex) => {
    const setCount = setCounts.get(candidate.liftId) ?? 2;
    const band = repBands.get(candidate.liftId);
    const repMin = band?.repMin ?? 8;
    const repMax = band?.repMax ?? 12;
    const sets = Array.from({ length: setCount }, (_, i) => {
      const isTopSet = scheme === "Double" && !topSetAssigned && i === 0;
      if (isTopSet) topSetAssigned = true;
      return {
        position: i,
        setType: "Standard" as const,
        repMin,
        repMax,
        rirGoal: isTopSet ? 2 : 0,
        isTopSet,
      };
    });
    return { liftId: candidate.liftId, position: liftIndex, workoutLiftSets: sets };
  });

  return { name: plan.name, workoutLifts: lifts };
}

/**
 * Picks fallback lifts: a focused subset of suggested patterns (capped at ~5–7 exercises),
 * sibling-rotated, with at most one pull and one hip-hinge per day.
 */
function selectFallbackLifts(
  plan: DayPlan,
  candidates: LiftCandidate[],
  context: GenerationContext,
  siblingIndex: number,
): LiftCandidate[] {
  const cap = sessionExerciseCap(plan);
  const selected: LiftCandidate[] = [];
  const usedLiftIds = new Set<string>();
  const usedHeavyPatterns = new Set<string>();
  let hasHipHinge = false;

  const addCandidate = (pick: LiftCandidate | undefined): void => {
    if (!pick || usedLiftIds.has(pick.liftId)) return;
    if (isHipHingeCandidate(pick) && hasHipHinge) return;
    if (SINGLE_INSTANCE_PATTERNS.has(pick.pattern) && usedHeavyPatterns.has(pick.pattern)) return;
    usedLiftIds.add(pick.liftId);
    if (SINGLE_INSTANCE_PATTERNS.has(pick.pattern)) usedHeavyPatterns.add(pick.pattern);
    if (isHipHingeCandidate(pick)) hasHipHinge = true;
    selected.push(pick);
  };

  for (const slot of fallbackPatternOrder(plan, context)) {
    if (selected.length >= cap) break;
    if (isRedundantPull(slot.pattern, selected)) continue;
    if (isRedundantQuadIsolation(slot.pattern, selected)) continue;
    const ranked = rankCandidatesForPattern(candidates, slot.pattern);
    addCandidate(pickFallbackCandidate(ranked, siblingIndex, selected.length, usedLiftIds));
  }

  return selected;
}

/**
 * Distributes each muscle budget's hard-set credits across the selected lifts that train it.
 * Specialized muscles split across two lifts when possible; secondary-only coverage uses the
 * 0.5 credit model.
 */
function allocateFallbackSetCounts(
  plan: DayPlan,
  selected: LiftCandidate[],
  context: GenerationContext,
): Map<string, number> {
  const setCounts = new Map<string, number>();
  const bump = (liftId: string, sets: number): void => {
    setCounts.set(liftId, Math.max(setCounts.get(liftId) ?? 0, sets));
  };

  for (const budget of plan.muscleBudgets) {
    const isSpecialized = (context.specialization[budget.muscle] ?? "Maintenance") === "Specialize";
    const targetSets = Math.max(budget.min, Math.round(budget.sets));
    const primaryLifts = selected
      .filter((c) => c.primaryMuscles.includes(budget.muscle))
      .sort(compareLiftCandidates);
    const secondaryLifts = selected
      .filter((c) => !c.primaryMuscles.includes(budget.muscle) && c.secondaryMuscles.includes(budget.muscle))
      .sort(compareLiftCandidates);

    if (primaryLifts.length > 0) {
      distributePrimaryMuscleSets(targetSets, primaryLifts, isSpecialized, bump);
      continue;
    }
    if (secondaryLifts.length === 0) continue;
    const setsNeeded = Math.ceil(targetSets / SECONDARY_SET_CREDIT);
    const maxPerLift = maxSetsPerFallbackLift(secondaryLifts[0], isSpecialized);
    bump(secondaryLifts[0].liftId, Math.max(2, Math.min(maxPerLift, setsNeeded)));
  }

  const timeBudget = plan.timeBudgetHardSets;
  if (timeBudget != null) {
    let total = [...setCounts.values()].reduce((sum, n) => sum + n, 0);
    if (total < timeBudget) {
      const specializedMuscles = plan.muscleBudgets
        .filter((b) => (context.specialization[b.muscle] ?? "Maintenance") === "Specialize")
        .map((b) => b.muscle);
      const growOrder = selected
        .filter((c) => c.primaryMuscles.some((m) => specializedMuscles.includes(m)))
        .sort(compareLiftCandidates);
      const ordered = growOrder.length > 0 ? growOrder : [...selected].sort(compareLiftCandidates);
      let guard = 0;
      while (total < timeBudget && guard++ < timeBudget * ordered.length) {
        let progressed = false;
        for (const lift of ordered) {
          if (total >= timeBudget) break;
          const current = setCounts.get(lift.liftId) ?? 2;
          const cap = maxSetsPerFallbackLift(lift, true);
          if (current >= cap) continue;
          bump(lift.liftId, current + 1);
          total++;
          progressed = true;
        }
        if (!progressed) break;
      }
    }
  }

  for (const candidate of selected) {
    if (!setCounts.has(candidate.liftId)) {
      setCounts.set(candidate.liftId, 2);
    }
  }
  return setCounts;
}

function distributePrimaryMuscleSets(
  targetSets: number,
  lifts: LiftCandidate[],
  isSpecialized: boolean,
  bump: (liftId: string, sets: number) => void,
): void {
  if (lifts.length === 0) return;
  if (isSpecialized && lifts.length >= 2 && targetSets >= 5) {
    const firstSets = Math.max(3, Math.min(maxSetsPerFallbackLift(lifts[0], true), Math.ceil(targetSets * 0.6)));
    const secondSets = Math.max(2, Math.min(maxSetsPerFallbackLift(lifts[1], true), targetSets - firstSets));
    bump(lifts[0].liftId, firstSets);
    bump(lifts[1].liftId, secondSets);
    return;
  }
  const maxPerLift = maxSetsPerFallbackLift(lifts[0], isSpecialized);
  bump(lifts[0].liftId, Math.max(2, Math.min(maxPerLift, targetSets)));
}

function maxSetsPerFallbackLift(candidate: LiftCandidate, isSpecialized: boolean): number {
  if (candidate.isCompound) return isSpecialized ? 5 : 4;
  return isSpecialized ? 4 : 3;
}

/** Ranks candidates for a pattern by priority, deduped by liftId. */
function rankCandidatesForPattern(candidates: LiftCandidate[], pattern: string): LiftCandidate[] {
  const seen = new Set<string>();
  return candidates
    .filter((c) => c.pattern === pattern && (seen.has(c.liftId) ? false : (seen.add(c.liftId), true)))
    .sort(compareLiftCandidates);
}

/**
 * Picks the fallback lift for one pattern. [siblingIndex] rotates the primary pick across sibling
 * days; [pickIndex] advances through ranks within a day. Skips liftIds already used in this workout.
 */
function pickFallbackCandidate(
  ranked: LiftCandidate[],
  siblingIndex: number,
  pickIndex: number,
  usedLiftIds: Set<string>,
): LiftCandidate | undefined {
  if (ranked.length === 0) return undefined;
  for (let step = 0; step < ranked.length; step++) {
    const rankOffset = siblingIndex + pickIndex + step;
    const index = rankOffset % ranked.length;
    const candidate = ranked[index];
    if (!usedLiftIds.has(candidate.liftId)) return candidate;
  }
  return undefined;
}

/**
 * The 0-based index of [plan] among its sibling days (days sharing the same theme) in [allPlans].
 * Used to rotate the fallback's candidate pick so siblings differ.
 */
function siblingIndexFor(plan: DayPlan, allPlans: DayPlan[], dayIndex: number): number {
  const getBaseTheme = (theme: string) => theme.replace(/\s*[A-Z]$/, "").trim();
  const key = getBaseTheme(plan.theme);
  let index = 0;
  for (let i = 0; i < dayIndex; i++) {
    if (getBaseTheme(allPlans[i].theme) === key) index++;
  }
  return index;
}

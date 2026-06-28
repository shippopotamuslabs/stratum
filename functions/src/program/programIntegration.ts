/**
 * Deterministic Phase 2 integration + repair (OS-7 v2).
 *
 * Merges N per-workout builder results into a program document, repairs each workout
 * deterministically (clamp set counts to the day's time budget, backfill under-min muscles,
 * clamp reps to bands, force one Double top set, interleave antagonists, drop unknown liftIds),
 * runs a global weekly-volume solver, and provides a deterministic fallback workout when an LLM
 * builder fails or returns empty.
 *
 * The final gate is [parseAndNormalizeProgram] + [validateProgramMetrics], reused unchanged from
 * v1. This module produces the pre-normalization draft those functions consume.
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
import { MAJOR_COMPOUND_PATTERNS, schemeForTrainingAge } from "./programRules";
import { validateProgramMetrics, type ProgramDraft, type ProgramWorkoutDraft, type ProgramLiftDraft } from "./programValidation";
import type { DayPlan, MuscleBudget, PatternSlot } from "./splitAllocator";
import type { LiftCandidate } from "./liftPriority";
import type { RepBand } from "./repBandPlanner";

/** A builder result for one day: the raw LLM output (may be malformed/empty) + the day plan. */
export interface WorkoutBuilderResult {
  dayPlan: DayPlan;
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
    const repaired = repairWorkout(draft, plan, result.candidates, result.repBands, context);
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
 * Deterministically repairs one workout:
 *  - drops lifts whose liftId is not in the candidate set (unknown/hallucinated ids),
 *  - clamps each lift's set count to the pattern slot's maxSets,
 *  - clamps reps to the candidate's rep band,
 *  - forces exactly one top set for Double-progression lifts,
 *  - merges duplicate liftId entries (the LLM can emit the same lift in two slots),
 *  - backfills sets on the highest-priority lift when a muscle is under its per-day min,
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
  const slotByPattern = new Map(plan.patternSlots.map((s) => [s.pattern, s]));
  const scheme = schemeForTrainingAge(context.trainingAge);

  // 1. Drop unknown liftIds; clamp set counts + reps per candidate/slot.
  const repairedLifts: ProgramLiftDraft[] = [];
  for (const lift of draft.workoutLifts ?? []) {
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    const candidate = candidateById.get(liftId);
    if (!candidate) continue; // drop unknown
    const slot = slotByPattern.get(candidate.pattern) ?? slotByPattern.values().next().value;
    const maxSets = slot?.maxSets ?? 6;
    const band = repBands.get(liftId);

    const rawSets = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
    const trimmed = rawSets.slice(0, maxSets);
    const repairedSets = trimmed.map((set, i) => repairSet(set, band, scheme, i === 0, candidate, context));
    if (repairedSets.length === 0) continue;

    repairedLifts.push({
      liftId,
      position: typeof lift.position === "number" ? lift.position : repairedLifts.length,
      workoutLiftSets: repairedSets,
    });
  }

  // 1b. Dedup: collapse entries sharing a liftId (an LLM can emit the same lift in two slots).
  //      Merge their sets into the first occurrence, clamp to the slot's maxSets, force one top
  //      set (Double), reassign positions.
  const dedupedLifts = mergeDuplicateLifts(repairedLifts, candidateById, slotByPattern, scheme);

  // 2. Backfill under-min muscles: for each muscle budget, if total sets < min, add sets to the
  //    highest-priority candidate lift for that muscle.
  const muscleSetCounts = countSetsByMuscle(dedupedLifts, candidateById);
  for (const budget of plan.muscleBudgets) {
    const current = muscleSetCounts.get(budget.muscle) ?? 0;
    if (current >= budget.min) continue;
    const deficit = Math.ceil(budget.min - current);
    const top = topCandidateForMuscle(budget.muscle, candidates);
    if (!top) continue;
    addBackoffSets(dedupedLifts, top, deficit, repBands, scheme, context);
  }

  // 3. Trim over-budget total hard sets (drop lowest-priority sets first).
  const timeBudget = plan.timeBudgetHardSets;
  if (timeBudget != null) {
    trimToTimeBudget(dedupedLifts, timeBudget, candidateById);
  }

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
 * and absorbs the sets of every later duplicate, the merged set list is clamped to the candidate
 * slot's maxSets, and positions are reassigned sequentially. Returns the lifts unchanged when
 * there are no duplicates. This is the deterministic fix for an LLM emitting the same lift twice
 * in one workout — merging (not dropping) preserves the volume the model intended. After merging,
 * the top-set rule is re-enforced for Double progression: the first set is the top set, all others
 * are forced non-top, so a merged lift (two entries each with their own top set) still satisfies the
 * normalizer's "exactly one top set" invariant.
 */
function mergeDuplicateLifts(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
  slotByPattern: Map<string, PatternSlot>,
  scheme: "Linear" | "Double",
): ProgramLiftDraft[] {
  if (lifts.length <= 1) return lifts;
  const byLiftId = new Map<string, ProgramLiftDraft>();
  const order: string[] = [];
  for (const lift of lifts) {
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    if (liftId === "") {
      // Untyped lift — keep verbatim (candidate validation already dropped unknowns).
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
    // Merge this duplicate's sets into the first occurrence.
    const incoming = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
    const merged = Array.isArray(existing.workoutLiftSets) ? [...existing.workoutLiftSets] : [];
    merged.push(...incoming);
    existing.workoutLiftSets = merged;
  }

  // Clamp each merged lift's sets to its slot's maxSets, enforce one top set, reassign positions.
  const out: ProgramLiftDraft[] = [];
  for (const key of order) {
    const lift = byLiftId.get(key)!;
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    const pattern = liftId !== "" ? candidateById.get(liftId)?.pattern : undefined;
    const slot = pattern ? slotByPattern.get(pattern) : undefined;
    const maxSets = slot?.maxSets ?? 6;
    const rawSets = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets.slice(0, maxSets) : [];
    // Re-enforce the top-set rule: merging duplicates that each carried a top set leaves ≥2 top sets,
    // which would violate the normalizer. For Double, force the first set top and the rest non-top.
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

/** Counts hard sets per muscle for a workout's lifts (primary credit only, for budget check). */
function countSetsByMuscle(
  lifts: ProgramLiftDraft[],
  candidateById: Map<string, LiftCandidate>,
): Map<string, number> {
  const counts = new Map<string, number>();
  for (const lift of lifts) {
    const candidate = candidateById.get(lift.liftId ?? "");
    if (!candidate) continue;
    const setCount = lift.workoutLiftSets?.length ?? 0;
    for (const muscle of candidate.primaryMuscles) {
      counts.set(muscle, (counts.get(muscle) ?? 0) + setCount);
    }
  }
  return counts;
}

/** The highest-priority candidate whose primary muscles include [muscle]. */
function topCandidateForMuscle(muscle: string, candidates: LiftCandidate[]): LiftCandidate | undefined {
  return candidates
    .filter((c) => c.primaryMuscles.includes(muscle))
    .sort((a, b) => b.priority - a.priority)[0];
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
  const isMajorCompound = MAJOR_COMPOUND_PATTERNS.has(candidate.pattern);
  const existing = lift.workoutLiftSets ?? [];
  for (let i = 0; i < count; i++) {
    const isFirst = existing.length === 0 && i === 0;
    existing.push(repairSet({}, band, scheme, isFirst, candidate, context));
  }
  lift.workoutLiftSets = existing;
}

/** Drops lowest-priority sets until total hard sets fit the time budget. */
function trimToTimeBudget(
  lifts: ProgramLiftDraft[],
  timeBudget: number,
  candidateById: Map<string, LiftCandidate>,
): void {
  let total = lifts.reduce((s, l) => s + (l.workoutLiftSets?.length ?? 0), 0);
  if (total <= timeBudget) return;
  // Sort lifts by priority ascending (lowest first) so we drop from the least relevant.
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
  // If still over, drop entire lowest-priority lifts (keeping ≥1 set each).
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
    // Prefer a lift whose primary key differs from the last placed.
    let pick = remaining.find((l) => primaryKey(l) !== lastKey);
    if (!pick) pick = remaining[0];
    const key = primaryKey(pick);
    if (key === lastKey) run++;
    else run = 1;
    result.push(pick);
    remaining.splice(remaining.indexOf(pick), 1);
    lastKey = key;
    // If a run of 3 is forming, try to swap the next pick; this simple pass suffices for ≤7 lifts.
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
 * weekly target, and adjusts the highest-priority lift across the week for any deficit/surplus.
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
      // Deficit: add sets to the highest-priority lift for this muscle across the week.
      // For v1, add one set to the first workout that trains this muscle (deterministic, simple).
      addSetsForMuscle(program, muscle, Math.ceil(target.min - current), context);
    }
    // Over-MRV is left to the per-workout trim + final validation; a deterministic cap pass
    // could be added here in v2.
  }
  return program;
}

/** Adds [count] sets to the first workout that trains [muscle] (primary or secondary). */
function addSetsForMuscle(program: ProgramDraft, muscle: string, count: number, context: GenerationContext): void {
  for (const workout of program.workouts) {
    const lift = (workout.workoutLifts ?? []).find((l) => trainsMuscle(l, muscle, context));
    if (!lift) continue;
    const sets = lift.workoutLiftSets ?? [];
    for (let i = 0; i < count; i++) {
      sets.push({ position: sets.length + i, setType: "Standard", repMin: 8, repMax: 12, rirGoal: 2, isTopSet: false });
    }
    return;
  }
}

/** Whether [lift] trains [muscle] as primary or secondary. */
function trainsMuscle(lift: ProgramLiftDraft, muscle: string, context: GenerationContext): boolean {
  const meta = context.liftsById.get(lift.liftId ?? "");
  if (!meta) return false;
  return meta.primaryMuscles.includes(muscle) || meta.secondaryMuscles.includes(muscle);
}

/**
 * Deterministic fallback workout: one lift per pattern slot, band-midpoint reps, one top set for
 * Double. Used when the LLM builder fails or returns empty.
 *
 * Sibling rotation: when [allPlans] and [dayIndex] are supplied, the candidate picked for each slot
 * is rotated by the day's sibling index — sibling 0 (e.g. Upper A) gets the top-ranked candidate,
 * sibling 1 (e.g. Upper B) gets the runner-up, and so on. Siblings are days that share the same
 * sorted set of slot patterns. This guarantees sibling days differ in the fallback path whenever a
 * pattern has ≥2 candidates (the same guarantee the whole-program LLM call provides on the happy
 * path). Without [allPlans], rank 0 is always picked (the original behavior, used by legacy callers).
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
  const lifts: ProgramLiftDraft[] = [];
  for (const slot of plan.patternSlots) {
    // Rank candidates for this slot, deduped by liftId (buildDayCandidates repeats a candidate once
    // per slot that matches its pattern). Rotating by sibling index then picks a distinct lift per sibling.
    const seen = new Set<string>();
    const ranked = candidates
      .filter((c) => c.pattern === slot.pattern && (seen.has(c.liftId) ? false : (seen.add(c.liftId), true)))
      .sort((a, b) => b.priority - a.priority);
    const top = ranked[Math.min(siblingIndex, ranked.length - 1)] ?? ranked[0];
    if (!top) continue;
    const band = repBands.get(top.liftId);
    const repMin = band ? band.repMin : 8;
    const repMax = band ? Math.round((band.repMin + band.repMax) / 2) : 12;
    const setCount = Math.max(slot.minSets, 2);
    const sets = Array.from({ length: setCount }, (_, i) => ({
      position: i,
      setType: "Standard",
      repMin,
      repMax,
      rirGoal: scheme === "Double" && i === 0 ? 2 : 0,
      isTopSet: scheme === "Double" && i === 0,
    }));
    lifts.push({ liftId: top.liftId, position: lifts.length, workoutLiftSets: sets });
  }
  return { name: plan.name, workoutLifts: lifts };
}

/**
 * The 0-based index of [plan] among its sibling days (days sharing the same sorted slot-pattern
 * set) in [allPlans]. Used to rotate the fallback's candidate pick so siblings differ. Upper A is
 * sibling 0, Upper B is sibling 1, etc. Days with a unique pattern set are always sibling 0.
 */
function siblingIndexFor(plan: DayPlan, allPlans: DayPlan[], dayIndex: number): number {
  const key = patternKey(plan);
  let index = 0;
  for (let i = 0; i < dayIndex; i++) {
    if (patternKey(allPlans[i]) === key) index++;
  }
  return index;
}

/** A stable grouping key for sibling days: their sorted, joined slot patterns. */
function patternKey(plan: DayPlan): string {
  return plan.patternSlots.map((s) => s.pattern).sort().join("|");
}
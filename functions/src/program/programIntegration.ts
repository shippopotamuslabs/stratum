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
import { MAJOR_COMPOUND_PATTERNS, schemeForTrainingAge } from "./programRules";
import { validateProgramMetrics, type ProgramDraft, type ProgramWorkoutDraft, type ProgramLiftDraft } from "./programValidation";
import type { DayPlan, MuscleBudget } from "./splitAllocator";
import type { LiftCandidate } from "./liftPriority";
import { compareLiftCandidates } from "./liftPriority";
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
    let repaired = repairWorkout(draft, plan, result.candidates, result.repBands, context);
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

  // 2. Backfill under-min muscles: for each muscle budget, if total credits < min, add sets to the
  //    highest-priority candidate lift that trains it (creating it if absent).
  backfillMuscleMins(dedupedLifts, plan.muscleBudgets, candidates, repBands, scheme, context);

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
  budgets: MuscleBudget[],
  candidates: LiftCandidate[],
  repBands: Map<string, RepBand>,
  scheme: "Linear" | "Double",
  context: GenerationContext,
): void {
  const candidateById = new Map(candidates.map((c) => [c.liftId, c]));
  const credits = countCreditsByMuscle(lifts, candidateById);
  for (const budget of budgets) {
    const current = credits.get(budget.muscle) ?? 0;
    if (current >= budget.min) continue;
    const top = topCandidateForMuscle(budget.muscle, candidates);
    if (!top) continue;
    const deficit = Math.ceil(budget.min - current);
    addBackoffSets(lifts, top, deficit, repBands, scheme, context);
    // Re-count so subsequent budgets see the added sets (a backfill lift can cover multiple muscles).
    credits.set(
      budget.muscle,
      (credits.get(budget.muscle) ?? 0) + deficit * (top.primaryMuscles.includes(budget.muscle) ? PRIMARY_SET_CREDIT : SECONDARY_SET_CREDIT),
    );
  }
}

/** The highest-priority candidate whose primary OR secondary muscles include [muscle]. */
function topCandidateForMuscle(muscle: string, candidates: LiftCandidate[]): LiftCandidate | undefined {
  // Prefer a lift where the muscle is PRIMARY (full credit) before settling for secondary.
  const primary = candidates
    .filter((c) => c.primaryMuscles.includes(muscle))
    .sort(compareLiftCandidates)[0];
  if (primary) return primary;
  return candidates
    .filter((c) => c.secondaryMuscles.includes(muscle))
    .sort(compareLiftCandidates)[0];
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
 * Deterministic fallback workout: builds one lift per suggested pattern (compound-first), giving
 * each a working-set count proportional to the muscle's per-day budget so the muscle mins are met.
 * Used when the LLM builder fails or returns empty.
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
  const budgetByMuscle = new Map(plan.muscleBudgets.map((b) => [b.muscle, b]));
  const lifts: ProgramLiftDraft[] = [];
  const usedLiftIds = new Set<string>();
  const allocatedSetsByMuscle = new Map<string, number>();

  // Drive by suggested patterns (compound-first order) so the fallback covers the day's structure.
  for (const slot of plan.suggestedPatterns) {
    const budget = budgetByMuscle.get(slot.muscleFocus);
    if (budget) {
      const allocated = allocatedSetsByMuscle.get(slot.muscleFocus) ?? 0;
      if (allocated >= budget.sets) continue;
    }
    const ranked = rankCandidatesForPattern(candidates, slot.pattern);
    const pick = pickFallbackCandidate(ranked, siblingIndex, lifts.length, usedLiftIds);
    if (!pick) continue;
    
    usedLiftIds.add(pick.liftId);
    let setCount = 3;
    if (budget) {
      const allocated = allocatedSetsByMuscle.get(slot.muscleFocus) ?? 0;
      const remaining = budget.sets - allocated;
      setCount = Math.max(2, Math.min(pick.isCompound ? 3 : 4, Math.round(remaining)));
      allocatedSetsByMuscle.set(slot.muscleFocus, allocated + setCount);
    }
    const band = repBands.get(pick.liftId);
    const repMin = band ? band.repMin : 8;
    const repMax = band ? Math.round((band.repMin + band.repMax) / 2) : 12;
    const sets = Array.from({ length: setCount }, (_, i) => ({
      position: i,
      setType: "Standard",
      repMin,
      repMax,
      rirGoal: scheme === "Double" && i === 0 ? 2 : 0,
      isTopSet: scheme === "Double" && i === 0,
    }));
    lifts.push({ liftId: pick.liftId, position: lifts.length, workoutLiftSets: sets });
  }

  // Any muscle budget still unfulfilled (no suggested pattern matched, or pattern had no candidate)
  // gets a lift from the candidate pool by primary muscle.
  for (const budget of plan.muscleBudgets) {
    const allocated = allocatedSetsByMuscle.get(budget.muscle) ?? 0;
    if (allocated >= budget.sets) continue;
    const pick = candidates
      .filter((c) => c.primaryMuscles.includes(budget.muscle) && !usedLiftIds.has(c.liftId))
      .sort(compareLiftCandidates)[0];
    if (!pick) continue;
    
    usedLiftIds.add(pick.liftId);
    const remaining = budget.sets - allocated;
    const setCount = Math.max(2, Math.min(pick.isCompound ? 3 : 4, Math.round(remaining)));
    allocatedSetsByMuscle.set(budget.muscle, allocated + setCount);
    
    const band = repBands.get(pick.liftId);
    const repMin = band ? band.repMin : 8;
    const repMax = band ? Math.round((band.repMin + band.repMax) / 2) : 12;
    const sets = Array.from({ length: setCount }, (_, i) => ({
      position: i,
      setType: "Standard",
      repMin,
      repMax,
      rirGoal: scheme === "Double" && i === 0 ? 2 : 0,
      isTopSet: scheme === "Double" && i === 0,
    }));
    lifts.push({ liftId: pick.liftId, position: lifts.length, workoutLiftSets: sets });
  }

  return { name: plan.name, workoutLifts: lifts };
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

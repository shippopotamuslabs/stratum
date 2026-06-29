/**
 * Deterministic Phase 0 volume-target planning (OS-7 v2).
 *
 * Owns per-muscle weekly volume targets (MEV/MAV/MRV/PV merged with specialization,
 * lastTrained, and volume-response signals) and the set of movement patterns the
 * user has enabled lifts for. Pure functions over [GenerationContext] — no LLM, no I/O.
 *
 * [resolveMuscleWeeklyTarget] is the extracted + renamed form of the former private
 * `resolveVolumeTarget` in `programValidation.ts`; the behavior is identical so the
 * validators keep a single source of truth.
 */
import {
  type GenerationContext,
  MAJOR_MUSCLES,
} from "./programPayloadContext";

/** Per-muscle weekly hard-set credit target. `min` is MEV (or MAV−tolerance for Specialize),
 * `aim` is the productive target, `max` is the MRV ceiling. */
export interface WeeklyVolumeTarget {
  min: number;
  aim: number;
  max: number;
}

export type WeeklyVolumeTargets = Map<string, WeeklyVolumeTarget>;

/** Hard-set credit tolerance applied to MEV/MAV/MRV bounds (matches the validators). */
const VOLUME_TOLERANCE_SETS = 2;

/**
 * Per-muscle weekly volume target for [muscle] given specialization, return-to-training
 * conservatism, and volume-response evidence. Extracted verbatim from the former private
 * `resolveVolumeTarget` in `programValidation.ts` — do not change behavior without
 * updating the matching validator tests.
 */
function resolveUnscaledTarget(
  muscle: string,
  specializationLevel: string,
  context: GenerationContext,
): WeeklyVolumeTarget {
  const landmark = context.volumeLandmarksByMuscle.get(muscle);
  const mev = landmark?.estimatedMev ?? 8;
  const mav = landmark?.estimatedMav ?? 14;
  const mrv = landmark?.estimatedMrv ?? 20;
  const pv = landmark?.estimatedPv ?? 11;

  const lastTrained = context.lastTrained;
  const conservative =
    lastTrained === "SixToTwelveMonthsAgo" ||
    lastTrained === "MoreThanAYearAgo" ||
    lastTrained === "Never";

  const volumeResponse = context.volumeResponsesByMuscle.get(muscle);
  const respondsToVolume =
    volumeResponse != null &&
    volumeResponse.correlationSlope > 0 &&
    volumeResponse.evidence === "Proven";

  if (specializationLevel === "Specialize") {
    const aim = respondsToVolume ? mrv : Math.min(mrv, mav + (mrv - mav) * 0.6);
    return {
      min: Math.max(mev, mav - VOLUME_TOLERANCE_SETS),
      aim,
      max: mrv,
    };
  }

  if (conservative) {
    return { min: mev, aim: pv, max: mav };
  }

  return { min: mev, aim: mav, max: mrv };
}

function trainableMuscles(context: GenerationContext): string[] {
  const patternsByMuscle = availablePatternsByMuscle(context);
  return targetableMuscles(context).filter(
    (m) => (patternsByMuscle.get(m)?.length ?? 0) > 0,
  );
}

export function resolveMuscleWeeklyTarget(
  muscle: string,
  specializationLevel: string,
  context: GenerationContext,
): WeeklyVolumeTarget {
  const unscaled = resolveUnscaledTarget(muscle, specializationLevel, context);
  
  if (context.sessionLengthMinutes == null || context.sessionLengthMinutes <= 0) {
    return unscaled;
  }
  
  const timeBudget = Math.floor((context.sessionLengthMinutes * 1.08) / 3.5);
  const weeklyTimeBudget = context.trainingDaysPerWeek * timeBudget;
  
  let totalUnscaledAim = 0;
  for (const m of trainableMuscles(context)) {
    const lvl = context.specialization[m] ?? "Maintenance";
    const target = resolveUnscaledTarget(m, lvl, context);
    totalUnscaledAim += target.aim;
  }
  
  if (totalUnscaledAim > weeklyTimeBudget) {
    const scale = weeklyTimeBudget / totalUnscaledAim;
    return {
      min: Math.max(2, Math.round(unscaled.min * scale * 2) / 2),
      aim: Math.max(2, Math.round(unscaled.aim * scale * 2) / 2),
      max: Math.max(2, Math.round(unscaled.max * scale * 2) / 2),
    };
  }
  
  return unscaled;
}

/**
 * The muscles Phase 0 must plan volume for: every major muscle, any muscle with a
 * personal/literature landmark, and any muscle the user explicitly specialized or omitted.
 * Omitted muscles are excluded from targeting (they receive zero volume).
 */
function targetableMuscles(context: GenerationContext): string[] {
  const muscles = new Set<string>(MAJOR_MUSCLES);
  for (const muscle of context.volumeLandmarksByMuscle.keys()) {
    muscles.add(muscle);
  }
  for (const muscle of Object.keys(context.specialization)) {
    muscles.add(muscle);
  }
  return [...muscles].filter(
    (muscle) => (context.specialization[muscle] ?? "Maintenance") !== "Omit",
  );
}

/**
 * Computes the per-muscle weekly volume target for every non-Omitted muscle, keyed by muscle.
 * Consumed by the split allocator (Phase 0) and the LLM split planner (Phase 1, 5–6 days).
 */
export function computeAllWeeklyTargets(
  context: GenerationContext,
): WeeklyVolumeTargets {
  const targets: WeeklyVolumeTargets = new Map();
  for (const muscle of targetableMuscles(context)) {
    const level = context.specialization[muscle] ?? "Maintenance";
    targets.set(muscle, resolveMuscleWeeklyTarget(muscle, level, context));
  }
  return targets;
}

/**
 * For each muscle, the movement patterns the user has at least one enabled lift for. Used by
 * the split allocator's feasibility guard (never plan a slot you can't fill) and the LLM split
 * planner (knows a "Chest touch" day is possible). Patterns are drawn from the payload's
 * proven/available lift pool — both tiers are already filtered to enabled lifts on the client.
 */
export function availablePatternsByMuscle(
  context: GenerationContext,
): Map<string, string[]> {
  const byMuscle = new Map<string, Set<string>>();
  for (const lift of context.liftsById.values()) {
    for (const muscle of [...lift.primaryMuscles, ...lift.secondaryMuscles]) {
      let set = byMuscle.get(muscle);
      if (!set) {
        set = new Set<string>();
        byMuscle.set(muscle, set);
      }
      set.add(lift.movementPattern);
    }
  }
  const out = new Map<string, string[]>();
  for (const [muscle, patterns] of byMuscle) {
    out.set(muscle, [...patterns]);
  }
  return out;
}
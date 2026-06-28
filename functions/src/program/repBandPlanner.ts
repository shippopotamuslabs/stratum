/**
 * Deterministic Phase 0 rep-band planning (OS-7 v2).
 *
 * Computes a per-lift rep range band `{ repMin, repMax }` from (trainingAge, lift class) using the
 * rules previously embedded in the system prompt, then adjusts by `fatigueDecaySlope`:
 *   - steep decay (< −1.0) → shift band DOWN 1–2 reps (steeper fatigue → fewer grind reps),
 *   - flat/gentle (> −0.3) + Improving → shift band UP 1 rep (can tolerate more volume per set).
 * Bands are clamped to the lift-class floor/ceiling. The LLM workout builder picks a starting
 * rep range inside this band; Phase 2 clamps anything outside it.
 */
import type { GenerationContext, LiftContext } from "./programPayloadContext";
import { MAJOR_COMPOUND_PATTERNS } from "./programRules";

export interface RepBand {
  repMin: number;
  repMax: number;
}

/** Lift class for rep-band selection: major compounds get lower rep ranges than isolations. */
function isCompound(lift: LiftContext): boolean {
  return MAJOR_COMPOUND_PATTERNS.has(lift.movementPattern);
}

/** True when trainingAge is Beginner — the only tier with stricter rep ranges. */
function isBeginner(context: GenerationContext): boolean {
  return context.trainingAge === "Beginner";
}

/**
 * Base rep band from the (trainingAge, lift class) table that was previously in the system prompt:
 *   Beginner compound 6–10, Beginner isolation 10–15,
 *   Intermediate+ compound 6–12, Intermediate+ isolation 10–20.
 */
function baseBand(lift: LiftContext, context: GenerationContext): RepBand {
  const compound = isCompound(lift);
  if (isBeginner(context)) {
    return compound ? { repMin: 6, repMax: 10 } : { repMin: 10, repMax: 15 };
  }
  return compound ? { repMin: 6, repMax: 12 } : { repMin: 10, repMax: 20 };
}

/** Floor/ceiling clamps per lift class so fatigue-decay shifts never escape the productive range. */
function classBounds(lift: LiftContext): { floor: number; ceiling: number } {
  return isCompound(lift)
    ? { floor: 5, ceiling: 12 }
    : { floor: 8, ceiling: 25 };
}

/**
 * Computes the deterministic rep band for [lift] under [context], adjusting for fatigue decay
 * and progress trend. Returns a band the LLM builder picks within and Phase 2 clamps to.
 */
export function repBandFor(lift: LiftContext, context: GenerationContext): RepBand {
  const base = baseBand(lift, context);
  const slope = lift.fatigueDecaySlope;
  const trend = lift.progressTrend;
  const { floor, ceiling } = classBounds(lift);

  let repMin = base.repMin;
  let repMax = base.repMax;

  if (slope != null) {
    if (slope < -1.0) {
      // Steep within-session fatigue: lower the top of the band so back-off sets don't grind.
      const shift = slope < -2.0 ? 2 : 1;
      repMax = repMax - shift;
      repMin = Math.max(floor, repMin - 1);
    } else if (slope > -0.3 && trend === "Improving") {
      // Gentle fatigue + improving: tolerate one more rep at the top.
      repMax = repMax + 1;
    }
  }

  // Clamp to class bounds and ensure repMin ≤ repMax.
  repMin = Math.max(floor, Math.min(ceiling, repMin));
  repMax = Math.max(floor, Math.min(ceiling, repMax));
  if (repMin > repMax) {
    repMin = repMax;
  }
  return { repMin, repMax };
}

/**
 * Computes rep bands for a set of lifts keyed by liftId. Used by the pipeline to attach a band
 * to each builder candidate.
 */
export function repBandsForLifts(
  liftIds: string[],
  context: GenerationContext,
): Map<string, RepBand> {
  const out = new Map<string, RepBand>();
  for (const id of liftIds) {
    const lift = context.liftsById.get(id);
    if (!lift) continue;
    out.set(id, repBandFor(lift, context));
  }
  return out;
}
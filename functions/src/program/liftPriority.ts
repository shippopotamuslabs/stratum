/**
 * Deterministic Phase 0 lift priority + per-day candidate shaping (OS-7 v2).
 *
 * Denormalizes a single 0.0–1.0 priority score onto each lift so the workout builders never
 * cross-reference `responseProfile.liftEffectiveness` by ID (the documented v1 pain). The
 * score blends proven tier, session recency, within-pattern effectiveness rank, progress trend,
 * evidence tier, and insight presence.
 *
 * [buildDayCandidates] filters the enabled lift pool to a day's pattern slots, scores each lift,
 * caps to top-K per pattern slot, and trims `trainingInsights` to the fatigue-decay + trend fields
 * the builder actually needs (dropping `estimatedOneRm`/`sessionCount` from the builder payload).
 */
import type { GenerationContext, LiftContext } from "./programPayloadContext";
import type { DayPlan, PatternSlot } from "./splitAllocator";

/** A lift with its denormalized priority score and trimmed insights, ready for a builder call. */
export interface LiftCandidate {
  liftId: string;
  name: string;
  pattern: string;
  /** 0.0–1.0 priority score (higher = more relevant for this day). */
  priority: number;
  /** Coarse tier label for the prompt: "proven" or "available". */
  priorityTier: "proven" | "available";
  primaryMuscles: string[];
  secondaryMuscles: string[];
  /** Trimmed insights: only fatigue decay + progress trend. Undefined when the lift has no insights. */
  trainingInsights?: {
    fatigueDecaySlope?: number;
    fatigueDecayIntercept?: number;
    progressTrend?: string;
  };
}

/** Maximum candidates per pattern slot sent to a builder. */
export const CANDIDATES_PER_SLOT = 5;

/**
 * Computes the 0.0–1.0 priority score for [lift] under [context]. Weights are tunable; the point
 * is a single number so the builder never cross-references effectiveness/profile maps.
 *
 *   0.30 × provenTier        (proven=1.0, available=0.3)
 *   0.20 × recencyScore      (log-scaled sessionCount, capped at 20 sessions)
 *   0.20 × effectivenessScore (1/rank from liftEffectiveness; 0.4 when no rank)
 *   0.15 × progressTrendScore (Improving=1.0, Stagnant=0.5, Regressing=0.2, null=0.4)
 *   0.10 × evidenceTier      (Proven=1.0, Hypothesis=0.4, InsufficientData=0.2, none=0.3)
 *   0.05 × hasInsights       (1.0 if trainingInsights present, else 0.3)
 */
export function scoreLift(lift: LiftContext, context: GenerationContext): number {
  const provenTier = lift.proven === true ? 1.0 : 0.3;

  const sessionCount = lift.sessionCount ?? 0;
  const recencyScore = sessionCount > 0 ? Math.min(1.0, Math.log10(sessionCount + 1) / Math.log10(21)) : 0.0;

  const effectiveness = context.liftEffectivenessByLiftId.get(lift.id);
  const effectivenessScore =
    effectiveness && effectiveness.rank > 0 ? 1 / effectiveness.rank : 0.4;

  const trend = lift.progressTrend;
  const progressTrendScore =
    trend === "Improving" ? 1.0 :
    trend === "Stagnant" ? 0.5 :
    trend === "Regressing" ? 0.2 :
    0.4;

  const evidence = effectiveness?.evidence;
  const evidenceTier =
    evidence === "Proven" ? 1.0 :
    evidence === "Hypothesis" ? 0.4 :
    evidence === "InsufficientData" ? 0.2 :
    0.3;

  const hasInsights = lift.sessionCount != null || lift.progressTrend != null ? 1.0 : 0.3;

  return (
    0.30 * provenTier +
    0.20 * recencyScore +
    0.20 * effectivenessScore +
    0.15 * progressTrendScore +
    0.10 * evidenceTier +
    0.05 * hasInsights
  );
}

/**
 * Builds the candidate lift list for one day: enabled lifts whose movement pattern matches any
 * pattern slot on this day, scored and capped to [CANDIDATES_PER_SLOT] per slot, sorted by
 * priority descending. Insights are trimmed to fatigue-decay slope/intercept + progress trend
 * only — `estimatedOneRm` and `sessionCount` are dropped from the builder payload (Phase 0 already
 * consumed them into the priority score and rep bands).
 */
export function buildDayCandidates(
  dayPlan: DayPlan,
  context: GenerationContext,
): LiftCandidate[] {
  const slotPatterns = new Set(dayPlan.patternSlots.map((s) => s.pattern));
  const byPattern = new Map<string, LiftCandidate[]>();

  for (const lift of context.liftsById.values()) {
    if (!slotPatterns.has(lift.movementPattern)) continue;
    const candidate = toCandidate(lift, context);
    let bucket = byPattern.get(lift.movementPattern);
    if (!bucket) {
      bucket = [];
      byPattern.set(lift.movementPattern, bucket);
    }
    bucket.push(candidate);
  }

  // Sort each pattern bucket by priority desc, then cap to top-K per slot.
  const out: LiftCandidate[] = [];
  for (const slot of dayPlan.patternSlots) {
    const bucket = (byPattern.get(slot.pattern) ?? [])
      .sort((a, b) => b.priority - a.priority)
      .slice(0, CANDIDATES_PER_SLOT);
    out.push(...bucket);
  }
  return out;
}

/** Maps a [LiftContext] to a [LiftCandidate], trimming insights to the builder-relevant fields. */
function toCandidate(lift: LiftContext, context: GenerationContext): LiftCandidate {
  const priority = scoreLift(lift, context);
  const insights = trimInsights(lift);
  return {
    liftId: lift.id,
    name: liftName(lift),
    pattern: lift.movementPattern,
    priority: Math.round(priority * 1000) / 1000,
    priorityTier: lift.proven === true ? "proven" : "available",
    primaryMuscles: lift.primaryMuscles,
    secondaryMuscles: lift.secondaryMuscles,
    ...(insights ? { trainingInsights: insights } : {}),
  };
}

/** Returns the lift's display name, falling back to the id when the payload omitted a name. */
function liftName(lift: LiftContext): string {
  return lift.name || lift.id;
}

/** Trims trainingInsights to fatigueDecaySlope/Intercept + progressTrend only. */
function trimInsights(lift: LiftContext): LiftCandidate["trainingInsights"] {
  if (
    lift.sessionCount == null &&
    lift.progressTrend == null &&
    lift.fatigueDecaySlope == null &&
    lift.fatigueDecayIntercept == null
  ) {
    return undefined;
  }
  const out: NonNullable<LiftCandidate["trainingInsights"]> = {};
  if (lift.progressTrend != null) out.progressTrend = lift.progressTrend;
  if (lift.fatigueDecaySlope != null) out.fatigueDecaySlope = lift.fatigueDecaySlope;
  if (lift.fatigueDecayIntercept != null) out.fatigueDecayIntercept = lift.fatigueDecayIntercept;
  return Object.keys(out).length > 0 ? out : undefined;
}
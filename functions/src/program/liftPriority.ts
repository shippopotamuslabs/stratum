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
import { MAJOR_COMPOUND_PATTERNS } from "./programRules";

/** A lift with its denormalized priority score and trimmed insights, ready for a builder call. */
export interface LiftCandidate {
  liftId: string;
  name: string;
  pattern: string;
  /** 0.0–1.0 priority score (higher = more relevant for this day). */
  priority: number;
  /** Coarse tier label for the prompt: "proven" or "available". */
  priorityTier: "proven" | "available";
  /** Whether this lift is a major compound (multi-joint) — derived from [pattern] via
   *  [MAJOR_COMPOUND_PATTERNS]. Surfaced to the builder so it can order compounds before
   *  isolation/accessory work. */
  isCompound: boolean;
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

/** Weight for the mainstream-name bonus blended into [scoreLift]. */
const MAINSTREAM_NAME_WEIGHT = 0.12;

/**
 * 0.0–1.0 score favoring bread-and-butter lifts over obscure yuhonas catalog tail entries.
 * Used as a tie-breaker when proven-tier and insight signals are absent (typical for availableLifts).
 */
export function mainstreamNameScore(name: string): number {
  const n = name.toLowerCase();
  if (/^(alternating|alternate)\b/.test(n)) return 0.05;
  if (
    /\bbalance board\b|around the world|renegade row|see-saw|flutter kick|wind sprint|spell caster|judo flip|landmine 180|bottoms-up|bottoms up/.test(
      n,
    )
  ) {
    return 0.1;
  }
  if (
    /barbell (bench press|squat|deadlift|row|hip thrust|curl|overhead press|incline|full squat|front squat|romanian)/.test(
      n,
    )
  ) {
    return 1.0;
  }
  if (
    /^(bench press|squat|deadlift|chin-up|chin up|pull-up|pull up|lat pulldown|leg press|romanian deadlift|seated row|cable row|overhead press|military press|hip thrust|leg curl|leg extension|calf raise|barbell row|incline bench|decline bench)/.test(
      n,
    )
  ) {
    return 1.0;
  }
  if (/\b(machine|smith machine)\b/.test(n)) return 0.85;
  if (/\bdumbbell\b/.test(n)) return 0.75;
  if (/\bcable\b/.test(n)) return 0.7;
  return 0.5;
}

/** Sort comparator for [LiftCandidate]s: priority desc, then mainstream name, then stable name. */
export function compareLiftCandidates(a: LiftCandidate, b: LiftCandidate): number {
  if (b.priority !== a.priority) return b.priority - a.priority;
  const mainstream = mainstreamNameScore(b.name) - mainstreamNameScore(a.name);
  if (mainstream !== 0) return mainstream;
  return a.name.localeCompare(b.name);
}

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
 *   0.12 × mainstreamName    (bread-and-butter lift names when insight signals are sparse)
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

  const mainstream = mainstreamNameScore(lift.name);

  return (
    0.30 * provenTier +
    0.20 * recencyScore +
    0.20 * effectivenessScore +
    0.15 * progressTrendScore +
    0.10 * evidenceTier +
    0.05 * hasInsights +
    MAINSTREAM_NAME_WEIGHT * mainstream
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
      .sort(compareLiftCandidates)
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
    isCompound: MAJOR_COMPOUND_PATTERNS.has(lift.movementPattern),
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
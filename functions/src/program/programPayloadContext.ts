/**
 * Parses the onboarding JSON payload into a [GenerationContext] for metric validation and tools.
 */

export interface LiftContext {
  id: string;
  name: string;
  movementPattern: string;
  primaryMuscles: string[];
  secondaryMuscles: string[];
  progressTrend?: string;
  /** Whether the lift is in the proven tier (matched from logged history) vs the available pool. */
  proven?: boolean;
  /** Number of logged sessions for this lift (from trainingInsights); 0 when no insights. */
  sessionCount?: number;
  /** Within-pattern effectiveness rank (1 = best) from responseProfile.liftEffectiveness; undefined if none. */
  effectivenessRank?: number;
  /** Effectiveness evidence tier from responseProfile.liftEffectiveness. */
  effectivenessEvidence?: string;
  /** Within-session fatigue decay slope from trainingInsights (steeper = faster fatigue). */
  fatigueDecaySlope?: number;
  /** Within-session fatigue decay intercept from trainingInsights. */
  fatigueDecayIntercept?: number;
}

export interface VolumeLandmarkContext {
  muscle: string;
  estimatedMev: number;
  estimatedMav: number;
  estimatedMrv: number;
  estimatedPv: number;
  source: "personal" | "literature";
}

export interface VolumeResponseContext {
  muscle: string;
  correlationSlope: number;
  evidence: string;
}

export interface LiftEffectivenessEntry {
  liftId: string;
  pattern: string;
  rank: number;
  effectivenessScore?: number;
  evidence: string;
}

export interface GenerationContext {
  trainingAge: string;
  lastTrained: string | null;
  sessionLengthMinutes: number | null;
  trainingDaysPerWeek: number;
  weekdays: string[];
  specialization: Record<string, string>;
  liftsById: Map<string, LiftContext>;
  volumeLandmarksByMuscle: Map<string, VolumeLandmarkContext>;
  volumeResponsesByMuscle: Map<string, VolumeResponseContext>;
  enabledLiftIds: Set<string>;
  movementPatternByLiftId: Map<string, string>;
  /** Per-lift effectiveness rank/evidence from responseProfile.liftEffectiveness, keyed by liftId. */
  liftEffectivenessByLiftId: Map<string, LiftEffectivenessEntry>;
}

const PUSH_PATTERNS = new Set(["HorizontalPress", "VerticalPress", "InclinePress"]);
const PULL_PATTERNS = new Set(["HorizontalPull", "VerticalPull"]);

export const MAJOR_MUSCLES = [
  "Chest",
  "Back",
  "Quad",
  "Hamstring",
  "Glute",
  "Tricep",
  "Bicep",
  "LateralDeltoid",
  "PosteriorDeltoid",
  "AnteriorDeltoid",
] as const;

export const SECONDARY_SET_CREDIT = 0.5;
export const PRIMARY_SET_CREDIT = 1.0;
export const MINUTES_PER_HARD_SET = 3.5;

export function isPushPattern(pattern: string): boolean {
  return PUSH_PATTERNS.has(pattern);
}

export function isPullPattern(pattern: string): boolean {
  return PULL_PATTERNS.has(pattern);
}

function readLift(raw: unknown, provenTier: boolean): LiftContext | null {
  if (!raw || typeof raw !== "object") return null;
  const lift = raw as Record<string, unknown>;
  const id = typeof lift.id === "string" ? lift.id : null;
  if (!id) return null;
  const name = typeof lift.name === "string" ? lift.name : id;
  const movementPattern = typeof lift.movementPattern === "string" ? lift.movementPattern : "";
  const primaryMuscles = readStringArray(lift.primaryMuscles);
  const secondaryMuscles = readStringArray(lift.secondaryMuscles);
  const insights =
    lift.trainingInsights && typeof lift.trainingInsights === "object"
      ? (lift.trainingInsights as Record<string, unknown>)
      : null;
  const progressTrend =
    insights && typeof insights.progressTrend === "string" ? insights.progressTrend : undefined;
  const sessionCount =
    insights && typeof insights.sessionCount === "number" ? insights.sessionCount : undefined;
  const fatigueDecaySlope =
    insights && typeof insights.fatigueDecaySlope === "number" ? insights.fatigueDecaySlope : undefined;
  const fatigueDecayIntercept =
    insights && typeof insights.fatigueDecayIntercept === "number" ? insights.fatigueDecayIntercept : undefined;
  return {
    id,
    name,
    movementPattern,
    primaryMuscles,
    secondaryMuscles,
    progressTrend,
    proven: provenTier || (lift.proven === true),
    sessionCount,
    fatigueDecaySlope,
    fatigueDecayIntercept,
  };
}

function readStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string");
}

function readLandmark(
  raw: unknown,
  source: "personal" | "literature",
): VolumeLandmarkContext | null {
  if (!raw || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const muscle = typeof row.muscle === "string" ? row.muscle : null;
  if (!muscle) return null;
  return {
    muscle,
    estimatedMev: readNumber(row.estimatedMev, 8),
    estimatedMav: readNumber(row.estimatedMav, 14),
    estimatedMrv: readNumber(row.estimatedMrv, 20),
    estimatedPv: readNumber(row.estimatedPv, 11),
    source,
  };
}

function readNumber(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function mergeLandmark(
  into: Map<string, VolumeLandmarkContext>,
  landmark: VolumeLandmarkContext | null,
  preferPersonal: boolean,
): void {
  if (!landmark) return;
  const existing = into.get(landmark.muscle);
  if (!existing) {
    into.set(landmark.muscle, landmark);
    return;
  }
  if (preferPersonal && landmark.source === "personal") {
    into.set(landmark.muscle, landmark);
  } else if (existing.source !== "personal") {
    into.set(landmark.muscle, landmark);
  }
}

export function parseGenerationContext(payload: string): GenerationContext {
  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    throw new Error("payload must be valid JSON");
  }
  const body = parsed as Record<string, unknown>;
  const profile =
    body.profile && typeof body.profile === "object"
      ? (body.profile as Record<string, unknown>)
      : {};
  const trainingAge = typeof profile.trainingAge === "string" ? profile.trainingAge : "";
  const lastTrained =
    typeof profile.lastTrained === "string" ? profile.lastTrained : null;
  const sessionLengthMinutes =
    typeof profile.sessionLengthMinutes === "number" ? profile.sessionLengthMinutes : null;
  const trainingDaysPerWeek =
    typeof profile.trainingDaysPerWeek === "number"
      ? profile.trainingDaysPerWeek
      : readWeekdayCount(profile.weekdays);
  const weekdays = readWeekdays(profile.weekdays);
  const specialization = readSpecialization(body.specialization);

  const liftsById = new Map<string, LiftContext>();
  const enabledLiftIds = new Set<string>();
  const movementPatternByLiftId = new Map<string, string>();
  for (const lift of [
    ...readLiftArray(body.provenLifts, true),
    ...readLiftArray(body.availableLifts, false),
  ]) {
    liftsById.set(lift.id, lift);
    enabledLiftIds.add(lift.id);
    movementPatternByLiftId.set(lift.id, lift.movementPattern);
  }

  const volumeLandmarksByMuscle = new Map<string, VolumeLandmarkContext>();
  const responseProfile =
    body.responseProfile && typeof body.responseProfile === "object"
      ? (body.responseProfile as Record<string, unknown>)
      : null;
  if (responseProfile && Array.isArray(responseProfile.volumeLandmarks)) {
    for (const row of responseProfile.volumeLandmarks) {
      mergeLandmark(volumeLandmarksByMuscle, readLandmark(row, "personal"), true);
    }
  }
  if (Array.isArray(body.literatureVolumeLandmarks)) {
    for (const row of body.literatureVolumeLandmarks) {
      mergeLandmark(volumeLandmarksByMuscle, readLandmark(row, "literature"), false);
    }
  }

  const volumeResponsesByMuscle = new Map<string, VolumeResponseContext>();
  if (responseProfile && Array.isArray(responseProfile.volumeResponses)) {
    for (const row of responseProfile.volumeResponses) {
      if (!row || typeof row !== "object") continue;
      const entry = row as Record<string, unknown>;
      const muscle = typeof entry.muscle === "string" ? entry.muscle : null;
      if (!muscle) continue;
      volumeResponsesByMuscle.set(muscle, {
        muscle,
        correlationSlope: readNumber(entry.correlationSlope, 0),
        evidence: typeof entry.evidence === "string" ? entry.evidence : "Hypothesis",
      });
    }
  }

  const liftEffectivenessByLiftId = new Map<string, LiftEffectivenessEntry>();
  if (responseProfile && Array.isArray(responseProfile.liftEffectiveness)) {
    for (const row of responseProfile.liftEffectiveness) {
      if (!row || typeof row !== "object") continue;
      const entry = row as Record<string, unknown>;
      const liftId = typeof entry.liftId === "string" ? entry.liftId : null;
      if (!liftId) continue;
      const rank = typeof entry.rank === "number" ? entry.rank : 0;
      liftEffectivenessByLiftId.set(liftId, {
        liftId,
        pattern: typeof entry.pattern === "string" ? entry.pattern : "",
        rank,
        effectivenessScore:
          typeof entry.effectivenessScore === "number" ? entry.effectivenessScore : undefined,
        evidence: typeof entry.evidence === "string" ? entry.evidence : "Hypothesis",
      });
    }
  }

  return {
    trainingAge,
    lastTrained,
    sessionLengthMinutes,
    trainingDaysPerWeek,
    weekdays,
    specialization,
    liftsById,
    volumeLandmarksByMuscle,
    volumeResponsesByMuscle,
    enabledLiftIds,
    movementPatternByLiftId,
    liftEffectivenessByLiftId,
  };
}

function readLiftArray(value: unknown, provenTier: boolean): LiftContext[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((raw) => readLift(raw, provenTier))
    .filter((lift): lift is LiftContext => lift != null);
}

function readWeekdayCount(weekdays: unknown): number {
  if (typeof weekdays !== "string" || weekdays.trim().length === 0) return 0;
  return weekdays.split(",").filter((day) => day.trim().length > 0).length;
}

function readWeekdays(weekdays: unknown): string[] {
  if (typeof weekdays !== "string" || weekdays.trim().length === 0) return [];
  return weekdays.split(",").map((day) => day.trim()).filter((day) => day.length > 0);
}

function readSpecialization(value: unknown): Record<string, string> {
  if (!value || typeof value !== "object") return {};
  const out: Record<string, string> = {};
  for (const [muscle, level] of Object.entries(value as Record<string, unknown>)) {
    if (typeof level === "string") out[muscle] = level;
  }
  return out;
}

export function toNormalizationRequest(context: GenerationContext): {
  trainingAge: string;
  enabledLiftIds: Set<string>;
  movementPatternByLiftId: Map<string, string>;
} {
  return {
    trainingAge: context.trainingAge,
    enabledLiftIds: context.enabledLiftIds,
    movementPatternByLiftId: context.movementPatternByLiftId,
  };
}

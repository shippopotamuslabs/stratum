/**
 * RP-aligned weekly volume planning from literature/personal landmarks (MEV/MAV/MRV/PV).
 *
 * Session length and the 8-set/muscle/workout cap are enforced downstream in slot allocation
 * and validation — not by scaling maintenance weekly targets when arms specialize.
 */
import type { GenerationContext } from "./programPayloadContext";
import {
  availablePatternsByMuscle,
  type WeeklyVolumeTarget,
  type WeeklyVolumeTargets,
} from "./volumeTargetPlanner";
import { sessionHardSetCapForLength } from "./rpReferenceTables";
import { SPLIT_CATALOG, type SplitTemplate } from "./splitCatalog";
import type { SessionTypeId } from "./sessionTypeCatalog";

const SPECIALIZATION_AIM_CAP = 20;
const VOLUME_TOLERANCE_SETS = 2;

const LOWER_BODY_MUSCLES = new Set(["Quad", "Hamstring", "Glute", "Calf", "LowerBack"]);

const LOWER_SESSION_TYPES = new Set<SessionTypeId>(["Lower", "QuadsCalves", "GlutesHams"]);
const UPPER_SESSION_TYPES = new Set<SessionTypeId>([
  "Upper",
  "UpperPush",
  "UpperPull",
  "Chest",
  "Back",
  "Shoulders",
  "Arms",
  "ChestTris",
  "BackBis",
]);
const MIXED_SESSION_TYPES = new Set<SessionTypeId>(["FullBody", "FullPush", "FullPull"]);

export type TrainingDomain = "lower" | "upper";

export function muscleTrainingDomain(muscle: string): TrainingDomain {
  return LOWER_BODY_MUSCLES.has(muscle) ? "lower" : "upper";
}

function sessionTrainingDomain(sessionType: SessionTypeId): TrainingDomain | "mixed" {
  if (LOWER_SESSION_TYPES.has(sessionType)) return "lower";
  if (UPPER_SESSION_TYPES.has(sessionType)) return "upper";
  if (MIXED_SESSION_TYPES.has(sessionType)) return "mixed";
  return "upper";
}

function defaultSplitTemplate(trainingDaysPerWeek: number): SplitTemplate | null {
  const matches = SPLIT_CATALOG.filter((s) => s.trainingDaysPerWeek === trainingDaysPerWeek);
  if (matches.length === 0) return null;
  return (
    matches.find((s) => s.isDefault && s.frequencyTier === 2) ??
    matches.find((s) => s.isDefault) ??
    matches[0] ??
    null
  );
}

/** Weekly session counts by domain, inferred from the default split for this training frequency. */
export function weeklySessionCountsByDomain(context: GenerationContext): {
  lower: number;
  upper: number;
  mixed: number;
} {
  const template = defaultSplitTemplate(context.trainingDaysPerWeek);
  if (template == null) {
    return { lower: 0, upper: context.trainingDaysPerWeek, mixed: 0 };
  }
  const cycleLen = template.sessionTypes.length;
  const ratio = context.trainingDaysPerWeek / cycleLen;
  let lower = 0;
  let upper = 0;
  let mixed = 0;
  for (const sessionType of template.sessionTypes) {
    const domain = sessionTrainingDomain(sessionType);
    if (domain === "lower") lower += ratio;
    else if (domain === "upper") upper += ratio;
    else mixed += ratio;
  }
  return { lower, upper, mixed };
}

function perSessionHardSetCap(context: GenerationContext): number | null {
  return sessionHardSetCapForLength(context.sessionLengthMinutes);
}

/** Per-domain weekly hard-set budget from session length × domain session count (informational). */
export function domainWeeklyBudget(
  context: GenerationContext,
  domain: TrainingDomain,
): number | null {
  const perSession = perSessionHardSetCap(context);
  if (perSession == null) return null;
  const counts = weeklySessionCountsByDomain(context);
  const dedicated = domain === "lower" ? counts.lower : counts.upper;
  const mixedShare = counts.mixed * 0.5;
  return Math.round((dedicated + mixedShare) * perSession);
}

function resolveUnscaled(
  muscle: string,
  level: string,
  context: GenerationContext,
): WeeklyVolumeTarget {
  const landmark = context.volumeLandmarksByMuscle.get(muscle);
  const mev = landmark?.estimatedMev ?? 8;
  const mav = landmark?.estimatedMav ?? 14;
  const mrv = landmark?.estimatedMrv ?? 20;
  const pv = landmark?.estimatedPv ?? 11;

  const conservative =
    context.lastTrained === "SixToTwelveMonthsAgo" ||
    context.lastTrained === "MoreThanAYearAgo" ||
    context.lastTrained === "Never";

  const volumeResponse = context.volumeResponsesByMuscle.get(muscle);
  const respondsToVolume =
    volumeResponse != null &&
    volumeResponse.correlationSlope > 0 &&
    volumeResponse.evidence === "Proven";

  if (level === "Specialize") {
    const aim = Math.min(SPECIALIZATION_AIM_CAP, respondsToVolume ? mrv : mav + (mrv - mav) * 0.6);
    return {
      min: Math.max(mev, mav - VOLUME_TOLERANCE_SETS),
      aim: Math.round(aim),
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
  const muscles = new Set<string>();
  for (const muscle of context.volumeLandmarksByMuscle.keys()) muscles.add(muscle);
  for (const muscle of Object.keys(context.specialization)) muscles.add(muscle);
  return [...muscles].filter((m) => {
    if ((context.specialization[m] ?? "Maintenance") === "Omit") return false;
    return (patternsByMuscle.get(m)?.length ?? 0) > 0;
  });
}

/**
 * Plans weekly volume per muscle from landmarks. Per-session caps and session-length limits
 * are applied in slot allocation — weekly maintenance targets are not scaled down for arm spec.
 */
export function planWeeklyVolume(context: GenerationContext): WeeklyVolumeTargets {
  const muscles = trainableMuscles(context);
  const out: WeeklyVolumeTargets = new Map();

  for (const muscle of muscles) {
    const level = context.specialization[muscle] ?? "Maintenance";
    out.set(muscle, resolveUnscaled(muscle, level, context));
  }

  return out;
}

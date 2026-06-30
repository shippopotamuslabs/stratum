/**
 * Ripped Body / RP hypertrophy reference constants for deterministic program generation.
 * Per-session muscle cap aligns with Weightology meta-analytic guidance (~6–8 hard sets
 * per muscle per session with long rest intervals).
 * @see https://rippedbody.com/how-to-build-training-programs/
 * @see https://weightology.net/the-ultimate-guide-to-training-volume/
 */

/** Max hard sets per muscle per workout before junk-volume territory (Stratum default). */
export const SESSION_MUSCLE_SET_CAP = 8;

/** Session time tolerance when computing per-day hard-set budget (legacy ratio — prefer sessionHardSetCapForLength). */
export const DURATION_TOLERANCE_RATIO = 1.08;

/** Max estimated/allocated minutes above the user's stated session length. */
export const SESSION_DURATION_SLACK_MINUTES = 5;

/** Hard-set cap for one workout from stated session length (+ slack). */
export function sessionHardSetCapForLength(sessionLengthMinutes: number | null | undefined): number | null {
  if (sessionLengthMinutes == null || sessionLengthMinutes <= 0) return null;
  return Math.floor((sessionLengthMinutes + SESSION_DURATION_SLACK_MINUTES) / MINUTES_PER_HARD_SET);
}

/** Duration budget in minutes (session length + slack) for validation. */
export function sessionDurationBudgetMinutes(sessionLengthMinutes: number): number {
  return sessionLengthMinutes + SESSION_DURATION_SLACK_MINUTES;
}

/** Minutes credited per hard set (matches validators). */
export const MINUTES_PER_HARD_SET = 3.5;

/** TABLE 7.6 — weekly fractional sets → minimum frequency tier. */
export type FrequencyTier = 1 | 2 | 3;

export function frequencyTierForWeeklySets(weeklySets: number): FrequencyTier {
  if (weeklySets <= 10) return 1;
  if (weeklySets <= 20) return 2;
  return 3;
}

/** TABLE 7.4 volume tiers by weekly sets per muscle (approximate). */
export const VOLUME_TIME_TIERS = [
  { setsMin: 4, setsMax: 8, label: "Minimal", hoursMin: 1, hoursMax: 2.5 },
  { setsMin: 9, setsMax: 12, label: "Low", hoursMin: 3, hoursMax: 4.5 },
  { setsMin: 13, setsMax: 16, label: "Medium", hoursMin: 5, hoursMax: 6.5 },
  { setsMin: 17, setsMax: 20, label: "High", hoursMin: 7, hoursMax: 8.5 },
  { setsMin: 21, setsMax: 30, label: "Very High", hoursMin: 9, hoursMax: 14 },
] as const;

/** TABLE 7.7 rep/RIR exercise classes. */
export type RpRepProfile =
  | "lowerFreeWeightCompound"
  | "lowerMachineOrUpperPress"
  | "upperMachineOrPull"
  | "isolation";

export interface RpRepRirBand {
  repMin: number;
  repMax: number;
  topSetRir: number;
  backOffRirMin: number;
  backOffRirMax: number;
}

export const RP_REP_RIR_BANDS: Record<RpRepProfile, RpRepRirBand> = {
  lowerFreeWeightCompound: { repMin: 4, repMax: 8, topSetRir: 4, backOffRirMin: 2, backOffRirMax: 4 },
  lowerMachineOrUpperPress: { repMin: 4, repMax: 12, topSetRir: 3, backOffRirMin: 1, backOffRirMax: 3 },
  upperMachineOrPull: { repMin: 6, repMax: 15, topSetRir: 2, backOffRirMin: 0, backOffRirMax: 2 },
  isolation: { repMin: 8, repMax: 20, topSetRir: 1, backOffRirMin: 0, backOffRirMax: 1 },
};

export const PRIMARY_SET_CREDIT = 1;
export const SECONDARY_SET_CREDIT = 0.5;

/** Typical secondary muscle credits by movement pattern (slot-level estimate during allocation). */
export const PATTERN_SECONDARY_MUSCLE_CREDITS: Record<string, Array<{ muscle: string; creditPerSet: number }>> = {
  VerticalPull: [{ muscle: "Bicep", creditPerSet: SECONDARY_SET_CREDIT }],
  HorizontalPull: [{ muscle: "Bicep", creditPerSet: SECONDARY_SET_CREDIT }],
  HorizontalPress: [{ muscle: "Tricep", creditPerSet: SECONDARY_SET_CREDIT }],
  InclinePress: [{ muscle: "Tricep", creditPerSet: SECONDARY_SET_CREDIT }],
  VerticalPress: [{ muscle: "Tricep", creditPerSet: SECONDARY_SET_CREDIT }],
};

/** Minimum direct (primary) sets for a specialized muscle's isolation slot in one session. */
export const MIN_SPECIALIZED_DIRECT_SETS = 4;

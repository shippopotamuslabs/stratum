/**
 * Deterministic program metric validation (OS-7). Used by the server post-pass (Phase 2) and
 * (historically) the generation tools. Per-muscle weekly target resolution lives in
 * [volumeTargetPlanner] (Phase 0); the validators re-import it so there is a single source of
 * truth for volume bounds.
 */
import {
  type GenerationContext,
  isPullPattern,
  isPushPattern,
  MAJOR_MUSCLES,
  MINUTES_PER_HARD_SET,
  PRIMARY_SET_CREDIT,
  SECONDARY_SET_CREDIT,
} from "./programPayloadContext";
import { resolveMuscleWeeklyTarget as resolveVolumeTarget } from "./volumeTargetPlanner";
import { frequencyTierForWeeklySets, SESSION_MUSCLE_SET_CAP, sessionDurationBudgetMinutes } from "./rpReferenceTables";

// Re-export so existing imports of `resolveVolumeTarget` (if any) keep working during the
// pipeline migration. Prefer importing from [volumeTargetPlanner] in new code.
export { resolveMuscleWeeklyTarget as resolveVolumeTarget } from "./volumeTargetPlanner";

export type ValidationSeverity = "error" | "warning";

export interface ValidationIssue {
  code: string;
  severity: ValidationSeverity;
  message: string;
  details?: Record<string, unknown>;
}

export interface ValidationReport {
  pass: boolean;
  issues: ValidationIssue[];
  summary: {
    weeklyMuscleVolume: Record<string, number>;
    workoutHardSets: number[];
    estimatedWorkoutMinutes: number[];
    muscleWeeklyFrequency: Record<string, number>;
    weeklyPushSets: number;
    weeklyPullSets: number;
  };
}

export interface ProgramDraft {
  name?: string;
  cycleLength?: number;
  workouts: ProgramWorkoutDraft[];
}

export interface ProgramWorkoutDraft {
  name?: string;
  weekday?: string | null;
  position?: number;
  workoutLifts?: ProgramLiftDraft[];
}

export interface ProgramLiftDraft {
  liftId?: string;
  position?: number;
  workoutLiftSets?: unknown[];
}

/** Split allocator / repair use a tight time cap; validation accepts a wider fuzzy band. */
const DURATION_TOLERANCE_RATIO = 1.08;
/** Validation session overrun allowance: at most [SESSION_DURATION_SLACK_MINUTES] over stated length. */
export const DURATION_VALIDATION_RATIO = 1.18;
/** Relative weekly-volume slack below min / above max (≈ practical programming noise). */
export const VOLUME_RELATIVE_TOLERANCE = 0.2;
/** Minimum hard-set slack regardless of target size. */
export const VOLUME_ABSOLUTE_TOLERANCE = 3;
const PUSH_PULL_TOLERANCE_RATIO = 0.35;
/** Share of weekly volume in one workout before flagging imbalance (warning only). */
const WORKOUT_VOLUME_SHARE_THRESHOLD = 0.85;

export function validateProgramMetrics(
  program: unknown,
  context: GenerationContext,
): ValidationReport {
  const draft = normalizeProgramDraft(program);
  const weeklyMuscleVolume = computeWeeklyMuscleVolume(draft, context);
  const workoutHardSets = draft.workouts.map(countWorkoutHardSets);
  const estimatedWorkoutMinutes = workoutHardSets.map(
    (sets) => sets * MINUTES_PER_HARD_SET,
  );
  const muscleWeeklyFrequency = computeMuscleWeeklyFrequency(draft, context);
  const { pushSets, pullSets } = computeWeeklyPushPullVolume(draft, context);

  const issues: ValidationIssue[] = [
    ...validateWorkoutDuration(draft, context, estimatedWorkoutMinutes),
    ...validateWeeklyMuscleVolume(weeklyMuscleVolume, context),
    ...validateWorkoutMuscleVolume(draft, context, weeklyMuscleVolume),
    ...validateSessionMuscleVolumeCap(draft, context),
    ...validateMuscleFrequency(muscleWeeklyFrequency, weeklyMuscleVolume, context),
    ...validateNoDuplicateLifts(draft),
    ...validateExerciseOrdering(draft, context),
    ...validatePushPullBalance(pushSets, pullSets, context),
  ];

  const hasErrors = issues.some((issue) => issue.severity === "error");
  return {
    pass: !hasErrors,
    issues,
    summary: {
      weeklyMuscleVolume,
      workoutHardSets,
      estimatedWorkoutMinutes,
      muscleWeeklyFrequency,
      weeklyPushSets: pushSets,
      weeklyPullSets: pullSets,
    },
  };
}

export function validateWorkoutDuration(
  program: ProgramDraft,
  context: GenerationContext,
  estimatedMinutes: number[] | null = null,
): ValidationIssue[] {
  if (context.sessionLengthMinutes == null || context.sessionLengthMinutes <= 0) {
    return [];
  }
  const minutes =
    estimatedMinutes ??
    program.workouts.map((workout) => countWorkoutHardSets(workout) * MINUTES_PER_HARD_SET);
  const budget = sessionDurationBudgetMinutes(context.sessionLengthMinutes);
  const issues: ValidationIssue[] = [];
  program.workouts.forEach((workout, index) => {
    const estimate = minutes[index] ?? 0;
    if (estimate > budget) {
      issues.push({
        code: "WORKOUT_DURATION_EXCEEDED",
        severity: "error",
        message: `Workout "${workout.name ?? index}" estimated ${estimate.toFixed(1)} min exceeds budget ${context.sessionLengthMinutes} min (cap ${budget.toFixed(1)} with tolerance).`,
        details: {
          workoutIndex: index,
          estimatedMinutes: estimate,
          budgetMinutes: context.sessionLengthMinutes,
        },
      });
    }
  });
  return issues;
}

/**
 * Fuzzy weekly-volume band: allows ~20% (min 3 sets) below [min] and above [max]. Hypertrophy
 * programming is not exact — validation should catch only material misses, not minor drift.
 */
export function weeklyVolumeToleranceBand(target: {
  min: number;
  aim: number;
  max: number;
}): { minFloor: number; maxCeiling: number } {
  const relative = Math.max(
    VOLUME_ABSOLUTE_TOLERANCE,
    target.aim * VOLUME_RELATIVE_TOLERANCE,
    target.max * (VOLUME_RELATIVE_TOLERANCE * 0.75),
  );
  return {
    minFloor: Math.max(0, target.min - relative),
    maxCeiling: target.max + relative,
  };
}

export function validateWeeklyMuscleVolume(
  weeklyMuscleVolume: Record<string, number>,
  context: GenerationContext,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const musclesToCheck = new Set<string>([
    ...Object.keys(weeklyMuscleVolume),
    ...MAJOR_MUSCLES,
  ]);

  for (const muscle of musclesToCheck) {
    const level = context.specialization[muscle] ?? "Maintenance";
    if (level === "Omit") {
      if ((weeklyMuscleVolume[muscle] ?? 0) > 0.01) {
        issues.push({
          code: "OMITTED_MUSCLE_HAS_VOLUME",
          severity: "error",
          message: `Muscle ${muscle} is Omitted but received ${weeklyMuscleVolume[muscle]?.toFixed(1)} weekly hard-set credits.`,
        });
      }
      continue;
    }

    const volume = weeklyMuscleVolume[muscle] ?? 0;
    if (volume <= 0.01) continue;

    const target = resolveVolumeTarget(muscle, level, context);
    const band = weeklyVolumeToleranceBand(target);
    if (volume < band.minFloor) {
      issues.push({
        code: "WEEKLY_VOLUME_BELOW_TARGET",
        severity: "error",
        message: `${muscle} weekly volume ${volume.toFixed(1)} is materially below target minimum ${target.min.toFixed(1)} (${level}, aim ${target.aim.toFixed(1)}; fuzzy floor ${band.minFloor.toFixed(1)}).`,
        details: { muscle, volume, target, band },
      });
    }
    if (volume > band.maxCeiling) {
      issues.push({
        code: "WEEKLY_VOLUME_ABOVE_MRV",
        severity: "error",
        message: `${muscle} weekly volume ${volume.toFixed(1)} materially exceeds MRV ceiling ${target.max.toFixed(1)} (fuzzy cap ${band.maxCeiling.toFixed(1)}).`,
        details: { muscle, volume, target, band },
      });
    }
  }
  return issues;
}

export function validateWorkoutMuscleVolume(
  program: ProgramDraft,
  context: GenerationContext,
  weeklyMuscleVolume: Record<string, number>,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  program.workouts.forEach((workout, workoutIndex) => {
    const workoutVolume = computeWorkoutMuscleVolume(workout, context);
    for (const [muscle, weeklyTotal] of Object.entries(weeklyMuscleVolume)) {
      if (weeklyTotal <= 0) continue;
      const inWorkout = workoutVolume[muscle] ?? 0;
      if (inWorkout <= 0) continue;
      const share = inWorkout / weeklyTotal;
      if (share > WORKOUT_VOLUME_SHARE_THRESHOLD) {
        issues.push({
          code: "WORKOUT_VOLUME_IMBALANCED",
          severity: "warning",
          message: `${muscle}: ${(share * 100).toFixed(0)}% of weekly volume is in workout "${workout.name ?? workoutIndex}" — distribute across sessions.`,
          details: { muscle, workoutIndex, share, inWorkout, weeklyTotal },
        });
      }
    }
  });
  return issues;
}

export function validateSessionMuscleVolumeCap(
  program: ProgramDraft,
  context: GenerationContext,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  program.workouts.forEach((workout, workoutIndex) => {
    const workoutVolume = computeWorkoutMuscleVolume(workout, context);
    for (const [muscle, credits] of Object.entries(workoutVolume)) {
      if (credits > SESSION_MUSCLE_SET_CAP + 0.01) {
        issues.push({
          code: "SESSION_MUSCLE_VOLUME_HIGH",
          severity: "error",
          message: `${muscle}: ${credits.toFixed(1)} fractional sets in workout "${workout.name ?? workoutIndex}" exceeds ${SESSION_MUSCLE_SET_CAP} session cap.`,
          details: { muscle, workoutIndex, credits, cap: SESSION_MUSCLE_SET_CAP },
        });
      }
    }
  });
  return issues;
}

export function validateMuscleFrequency(
  muscleWeeklyFrequency: Record<string, number>,
  weeklyMuscleVolume: Record<string, number>,
  context: GenerationContext,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  for (const muscle of MAJOR_MUSCLES) {
    const level = context.specialization[muscle] ?? "Maintenance";
    if (level === "Omit") continue;
    const frequency = muscleWeeklyFrequency[muscle] ?? 0;
    const weeklyAim = weeklyMuscleVolume[muscle] ?? 0;
    if (weeklyAim <= 0) continue;
    const tier = frequencyTierForWeeklySets(weeklyAim);
    const minRequired = tier === 1 ? 1 : tier === 2 ? 2 : 3;
    if (frequency < minRequired) {
      issues.push({
        code: "MUSCLE_FREQUENCY_LOW",
        severity: level === "Specialize" ? "error" : "warning",
        message: `${muscle} is trained ${frequency}× per cycle; prefer ≥${minRequired}× for ${weeklyAim.toFixed(0)} weekly sets (TABLE 7.6).`,
        details: { muscle, frequency, minRequired, weeklyAim },
      });
    }
  }
  return issues;
}

/**
 * Flags any workout that contains the same liftId more than once. Defense-in-depth for the
 * [repairWorkout] dedup pass: if a duplicate ever slips past repair (e.g. a normalized program fed
 * straight into validation), this surfaces it so the global re-gen path can react.
 */
export function validateNoDuplicateLifts(program: ProgramDraft): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  program.workouts.forEach((workout, workoutIndex) => {
    const seen = new Set<string>();
    const dupes = new Set<string>();
    for (const lift of workout.workoutLifts ?? []) {
      const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
      if (liftId === "") continue;
      if (seen.has(liftId)) dupes.add(liftId);
      else seen.add(liftId);
    }
    if (dupes.size > 0) {
      issues.push({
        code: "DUPLICATE_LIFT_IN_WORKOUT",
        severity: "error",
        message: `Workout "${workout.name ?? workoutIndex}" repeats liftId(s) ${[...dupes].join(", ")} within the same workout.`,
        details: { workoutIndex, liftIds: [...dupes] },
      });
    }
  });
  return issues;
}

export function validateExerciseOrdering(
  program: ProgramDraft,
  context: GenerationContext,
): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  program.workouts.forEach((workout, workoutIndex) => {
    const lifts = [...(workout.workoutLifts ?? [])].sort(
      (a, b) => (a.position ?? 0) - (b.position ?? 0),
    );
    let consecutiveSamePrimary = 0;
    let lastPrimaryKey = "";
    for (const lift of lifts) {
      const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
      const meta = context.liftsById.get(liftId);
      const primaryKey = meta?.primaryMuscles.join("|") ?? liftId;
      if (primaryKey.length === 0) continue;
      if (primaryKey === lastPrimaryKey) {
        consecutiveSamePrimary += 1;
      } else {
        consecutiveSamePrimary = 1;
        lastPrimaryKey = primaryKey;
      }
      if (consecutiveSamePrimary >= 3) {
        issues.push({
          code: "EXERCISE_ORDER_STACKED",
          severity: "warning",
          message: `Workout "${workout.name ?? workoutIndex}" stacks ${consecutiveSamePrimary} consecutive exercises for the same primary muscle (${primaryKey}). Consider interleaving antagonists.`,
          details: { workoutIndex, liftId, primaryKey },
        });
        break;
      }
    }
  });
  return issues;
}

export function validatePushPullBalance(
  pushSets: number,
  pullSets: number,
  _context: GenerationContext,
): ValidationIssue[] {
  const total = pushSets + pullSets;
  if (total < 6) return [];
  const imbalance = Math.abs(pushSets - pullSets) / total;
  if (imbalance > PUSH_PULL_TOLERANCE_RATIO) {
    return [
      {
        code: "PUSH_PULL_IMBALANCE",
        severity: "warning",
        message: `Push/pull weekly set credits ${pushSets.toFixed(1)} push vs ${pullSets.toFixed(1)} pull (${(imbalance * 100).toFixed(0)}% imbalance; keep within ${PUSH_PULL_TOLERANCE_RATIO * 100}%).`,
        details: { pushSets, pullSets, imbalance },
      },
    ];
  }
  return [];
}

export function formatValidationFeedback(report: ValidationReport): string {
  const errors = report.issues.filter((issue) => issue.severity === "error");
  const warnings = report.issues.filter((issue) => issue.severity === "warning");
  const lines = [
    "Program metric validation failed. Fix these issues and regenerate:",
    ...errors.map((issue) => `- [ERROR ${issue.code}] ${issue.message}`),
    ...warnings.map((issue) => `- [WARN ${issue.code}] ${issue.message}`),
    "",
    "Summary:",
    `- Weekly muscle volume (hard-set credits): ${JSON.stringify(report.summary.weeklyMuscleVolume)}`,
    `- Workout hard sets: ${JSON.stringify(report.summary.workoutHardSets)}`,
    `- Estimated workout minutes: ${JSON.stringify(report.summary.estimatedWorkoutMinutes)}`,
    `- Muscle weekly frequency: ${JSON.stringify(report.summary.muscleWeeklyFrequency)}`,
    `- Push/pull credits: ${report.summary.weeklyPushSets.toFixed(1)} / ${report.summary.weeklyPullSets.toFixed(1)}`,
  ];
  return lines.join("\n");
}

export function buildMetricRetryFeedback(
  report: ValidationReport,
  context: GenerationContext,
  rejectedLiftIds: string[],
): string {
  const lines = [
    formatValidationFeedback(report),
    "",
    `Regenerate a COMPLETE program with exactly ${context.trainingDaysPerWeek} workouts.`,
    "Hit at least MEV weekly hard-set credits per muscle; train each major muscle ≥2×/week.",
    "Use only liftId values from provenLifts/availableLifts in the onboarding payload.",
  ];
  if (rejectedLiftIds.length > 0) {
    lines.push(
      `These liftId values were removed because they are not enabled: ${rejectedLiftIds.join(", ")}.`,
    );
  }
  lines.push("Call validate_program_metrics on your draft and fix every ERROR before returning JSON.");
  return lines.join("\n");
}

function normalizeProgramDraft(program: unknown): ProgramDraft {
  if (!program || typeof program !== "object") {
    return { workouts: [] };
  }
  const body = program as Record<string, unknown>;
  const workouts = Array.isArray(body.workouts) ? body.workouts : [];
  return {
    name: typeof body.name === "string" ? body.name : "Program",
    workouts: workouts
      .filter((workout): workout is ProgramWorkoutDraft => !!workout && typeof workout === "object")
      .map((workout) => ({
        name: typeof workout.name === "string" ? workout.name : "Workout",
        weekday: typeof workout.weekday === "string" ? workout.weekday : null,
        position: typeof workout.position === "number" ? workout.position : 0,
        workoutLifts: Array.isArray(workout.workoutLifts) ? workout.workoutLifts : [],
      })),
  };
}

function countWorkoutHardSets(workout: ProgramWorkoutDraft): number {
  let total = 0;
  for (const lift of workout.workoutLifts ?? []) {
    total += Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets.length : 0;
  }
  return total;
}

function computeWeeklyMuscleVolume(
  program: ProgramDraft,
  context: GenerationContext,
): Record<string, number> {
  const totals: Record<string, number> = {};
  for (const workout of program.workouts) {
    const workoutVolume = computeWorkoutMuscleVolume(workout, context);
    for (const [muscle, credits] of Object.entries(workoutVolume)) {
      totals[muscle] = (totals[muscle] ?? 0) + credits;
    }
  }
  return totals;
}

/** Fractional hard-set credits per muscle in one workout (primary 1.0, secondary 0.5). */
export function computeWorkoutMuscleVolume(
  workout: ProgramWorkoutDraft,
  context: GenerationContext,
): Record<string, number> {
  const totals: Record<string, number> = {};
  for (const lift of workout.workoutLifts ?? []) {
    const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
    const setCount = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets.length : 0;
    if (setCount === 0) continue;
    const meta = context.liftsById.get(liftId);
    if (!meta) continue;
    for (const muscle of meta.primaryMuscles) {
      totals[muscle] = (totals[muscle] ?? 0) + setCount * PRIMARY_SET_CREDIT;
    }
    for (const muscle of meta.secondaryMuscles) {
      if (meta.primaryMuscles.includes(muscle)) continue;
      totals[muscle] = (totals[muscle] ?? 0) + setCount * SECONDARY_SET_CREDIT;
    }
  }
  return totals;
}

function computeMuscleWeeklyFrequency(
  program: ProgramDraft,
  context: GenerationContext,
): Record<string, number> {
  const frequency: Record<string, number> = {};
  program.workouts.forEach((workout) => {
    const muscles = new Set(Object.keys(computeWorkoutMuscleVolume(workout, context)));
    for (const muscle of muscles) {
      frequency[muscle] = (frequency[muscle] ?? 0) + 1;
    }
  });
  return frequency;
}

function computeWeeklyPushPullVolume(
  program: ProgramDraft,
  context: GenerationContext,
): { pushSets: number; pullSets: number } {
  let pushSets = 0;
  let pullSets = 0;
  for (const workout of program.workouts) {
    for (const lift of workout.workoutLifts ?? []) {
      const liftId = typeof lift.liftId === "string" ? lift.liftId : "";
      const setCount = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets.length : 0;
      if (setCount === 0) continue;
      const pattern = context.movementPatternByLiftId.get(liftId) ?? "";
      if (isPushPattern(pattern)) pushSets += setCount;
      if (isPullPattern(pattern)) pullSets += setCount;
    }
  }
  return { pushSets, pullSets };
}

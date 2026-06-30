/**
 * Picks split template from TABLE 7.2 with heuristic tie-break and recovery-aware ordering.
 */
import type { GenerationContext } from "./programPayloadContext";
import type { WeeklyVolumeTargets } from "./volumeTargetPlanner";
import { requiredFrequencyTier, validateCycleFrequency } from "./frequencyPlanner";
import { splitsForDaysAndTier, type SplitTemplate } from "./splitCatalog";
import {
  displayNameForSessionType,
  primaryMusclesForSessionType,
  slotsForSessionType,
  type SessionTypeId,
} from "./sessionTypeCatalog";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

export interface ResolvedSessionPlan {
  sessionType: SessionTypeId;
  name: string;
  slots: WorkoutSlotTemplate[];
  siblingIndex: number;
  position: number;
}

export interface ResolvedSplit {
  template: SplitTemplate;
  sessions: ResolvedSessionPlan[];
  cycleLength: number;
}

const WEEKDAY_ORDER = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function minWeekdayGap(weekdays: string[]): number {
  if (weekdays.length < 2) return 1;
  const indices = weekdays
    .map((d) => WEEKDAY_ORDER.indexOf(d))
    .filter((i) => i >= 0)
    .sort((a, b) => a - b);
  if (indices.length < 2) return 1;
  let minGap = 7;
  for (let i = 1; i < indices.length; i++) {
    minGap = Math.min(minGap, indices[i]! - indices[i - 1]!);
  }
  const wrap = 7 - indices[indices.length - 1]! + indices[0]!;
  return Math.min(minGap, wrap);
}

function recoveryScore(sessionTypes: SessionTypeId[], weekdays: string[]): number {
  const minGap = minWeekdayGap(weekdays);
  const musclePositions = new Map<string, number[]>();
  sessionTypes.forEach((st, idx) => {
    for (const muscle of primaryMusclesForSessionType(st)) {
      let arr = musclePositions.get(muscle);
      if (!arr) {
        arr = [];
        musclePositions.set(muscle, arr);
      }
      arr.push(idx);
    }
  });
  let penalty = 0;
  for (const positions of musclePositions.values()) {
    if (positions.length < 2) continue;
    for (let i = 1; i < positions.length; i++) {
      const gap = positions[i]! - positions[i - 1]!;
      if (gap < minGap) penalty += (minGap - gap) * 2;
    }
  }
  return 100 - penalty;
}

function volumeFitScore(
  template: SplitTemplate,
  weeklyTargets: WeeklyVolumeTargets,
  context: GenerationContext,
): number {
  const sessionMuscles = template.sessionTypes.map((st) => primaryMusclesForSessionType(st));
  if (!validateCycleFrequency(sessionMuscles, weeklyTargets, context)) {
    return 0;
  }
  return template.isDefault ? 10 : 5;
}

function scoreTemplate(
  template: SplitTemplate,
  context: GenerationContext,
  weeklyTargets: WeeklyVolumeTargets,
): number {
  return (
    recoveryScore(template.sessionTypes, context.weekdays) +
    volumeFitScore(template, weeklyTargets, context) +
    (template.isDefault ? 5 : 0)
  );
}

function siblingIndexForSessionType(sessionTypes: SessionTypeId[], index: number): number {
  const type = sessionTypes[index]!;
  let count = 0;
  for (let i = 0; i < index; i++) {
    if (sessionTypes[i] === type) count++;
  }
  return count;
}

function buildSessions(template: SplitTemplate, context: GenerationContext): ResolvedSessionPlan[] {
  const typeCounts = new Map<SessionTypeId, number>();
  return template.sessionTypes.map((sessionType, position) => {
    const siblingIndex = siblingIndexForSessionType(template.sessionTypes, position);
    const count = typeCounts.get(sessionType) ?? 0;
    typeCounts.set(sessionType, count + 1);
    return {
      sessionType,
      name: displayNameForSessionType(sessionType, siblingIndex),
      slots: slotsForSessionType(sessionType, context).map((s) => ({ ...s })),
      siblingIndex,
      position,
    };
  });
}

export function resolveSplit(
  context: GenerationContext,
  weeklyTargets: WeeklyVolumeTargets,
): ResolvedSplit {
  const requiredTier = requiredFrequencyTier(weeklyTargets);
  let candidates = splitsForDaysAndTier(context.trainingDaysPerWeek, requiredTier);
  if (candidates.length === 0) {
    candidates = splitsForDaysAndTier(context.trainingDaysPerWeek, 1);
  }
  if (candidates.length === 0) {
    throw new Error(`No split template for ${context.trainingDaysPerWeek} training days`);
  }

  const ranked = [...candidates].sort(
    (a, b) => scoreTemplate(b, context, weeklyTargets) - scoreTemplate(a, context, weeklyTargets),
  );
  const template = ranked[0]!;
  const sessions = buildSessions(template, context);
  return {
    template,
    sessions,
    cycleLength: sessions.length,
  };
}

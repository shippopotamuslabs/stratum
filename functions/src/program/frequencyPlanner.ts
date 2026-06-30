/**
 * TABLE 7.6 frequency planning — derives required split frequency tier from weekly volume.
 */
import { type GenerationContext, MAJOR_MUSCLES } from "./programPayloadContext";
import { availablePatternsByMuscle } from "./volumeTargetPlanner";
import { frequencyTierForWeeklySets, type FrequencyTier } from "./rpReferenceTables";
import type { WeeklyVolumeTarget, WeeklyVolumeTargets } from "./volumeTargetPlanner";

export function requiredFrequencyTier(weeklyTargets: WeeklyVolumeTargets): FrequencyTier {
  let maxTier: FrequencyTier = 1;
  for (const [, target] of weeklyTargets) {
    const tier = frequencyTierForWeeklySets(target.aim);
    if (tier > maxTier) maxTier = tier;
  }
  return maxTier;
}

/** Sessions in cycle that train [muscle] as primary (from filled session plans). */
export function muscleSessionsInCycle(
  sessionMuscles: string[][],
  muscle: string,
): number {
  return sessionMuscles.filter((muscles) => muscles.includes(muscle)).length;
}

export function validateCycleFrequency(
  sessionMuscles: string[][],
  weeklyTargets: WeeklyVolumeTargets,
  context: GenerationContext,
): boolean {
  const cycleLength = sessionMuscles.length;
  for (const muscle of trainableMuscles(context)) {
    const target = weeklyTargets.get(muscle);
    if (!target || target.aim <= 0) continue;
    const tier = frequencyTierForWeeklySets(target.aim);
    const sessions = muscleSessionsInCycle(sessionMuscles, muscle);
    const minRequired = tier === 1 ? 1 : tier === 2 ? 2 : 3;
    if (sessions < minRequired && cycleLength >= minRequired) {
      return false;
    }
  }
  return true;
}

function trainableMuscles(context: GenerationContext): string[] {
  const patternsByMuscle = availablePatternsByMuscle(context);
  return [...MAJOR_MUSCLES].filter((m) => {
    const level = context.specialization[m] ?? "Maintenance";
    if (level === "Omit") return false;
    return (patternsByMuscle.get(m)?.length ?? 0) > 0;
  });
}

export function aggregateWeeklyAim(weeklyTargets: WeeklyVolumeTargets): number {
  let total = 0;
  for (const [, t] of weeklyTargets) total += t.aim;
  return total;
}

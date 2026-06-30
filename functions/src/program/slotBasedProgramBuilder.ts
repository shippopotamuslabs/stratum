/**
 * Deterministic RP slot-based program builder (replaces whole-program LLM architect).
 */
import type { GenerationContext } from "./programPayloadContext";
import type { WeeklyVolumeTargets } from "./volumeTargetPlanner";
import { planWeeklyVolume } from "./volumePlanner";
import { resolveSplit } from "./splitResolver";
import { allocateSlotSetCounts } from "./slotSetAllocator";
import { selectLiftForSlot } from "./liftSlotSelector";
import { assembleWorkouts } from "./workoutAssembler";
import type { ProgramDraft } from "./programValidation";

export interface BuiltProgram extends ProgramDraft {
  cycleLength: number;
}

export interface BuildProgramResult {
  program: BuiltProgram;
  selectionsBySession: import("./liftSlotSelector").SelectedSlotLift[][];
}

export function buildProgramWithSelections(
  context: GenerationContext,
  weeklyTargets?: WeeklyVolumeTargets,
): BuildProgramResult {
  const targets = weeklyTargets ?? planWeeklyVolume(context);
  const resolved = resolveSplit(context, targets);
  const sessionPlans = allocateSlotSetCounts(resolved.sessions, targets, context);

  const usedLiftIds = new Set<string>();
  const lastUsedSession = new Map<string, number>();
  const minCycleGap = 2;

  const selectionsBySession = sessionPlans.map((plan, sessionIndex) => {
    const selections = [];
    for (const slot of plan.filledSlots) {
      const sel = selectLiftForSlot(
        slot,
        context,
        plan.siblingIndex,
        usedLiftIds,
        minCycleGap,
        sessionIndex,
        lastUsedSession,
      );
      if (sel) selections.push(sel);
    }
    return selections;
  });

  const workouts = assembleWorkouts(sessionPlans, selectionsBySession, context);
  const cycleLength = resolved.cycleLength;

  if (workouts.length !== cycleLength) {
    throw new Error(`cycleLength ${cycleLength} != workouts ${workouts.length}`);
  }

  return {
    program: {
      name: `Hypertrophy — ${cycleLength}-session cycle`,
      cycleLength,
      workouts,
    },
    selectionsBySession,
  };
}

export function buildProgramFromSlots(
  context: GenerationContext,
  weeklyTargets?: WeeklyVolumeTargets,
): BuiltProgram {
  return buildProgramWithSelections(context, weeklyTargets).program;
}

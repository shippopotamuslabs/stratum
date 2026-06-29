/**
 * Assembles workout drafts with interleaved ordering and specialization-first slots.
 */
import type { GenerationContext } from "./programPayloadContext";
import { schemeForTrainingAge } from "./programRules";
import type { ProgramLiftDraft, ProgramWorkoutDraft } from "./programValidation";
import type { SessionSlotPlan } from "./slotSetAllocator";
import type { SelectedSlotLift } from "./liftSlotSelector";
import { repRirForSlot } from "./repRirPlanner";

const ANTAGONIST_PAIRS: Array<[string, string]> = [
  ["Chest", "Back"],
  ["Bicep", "Tricep"],
  ["Quad", "Hamstring"],
];

const PULL_PATTERNS = new Set([
  "VerticalPull",
  "HorizontalPull",
  "ElbowFlexion",
  "KneeFlexion",
  "HipHinge",
]);

const PUSH_PATTERNS = new Set([
  "HorizontalPress",
  "InclinePress",
  "VerticalPress",
  "ElbowExtension",
  "SquatPattern",
  "KneeExtension",
]);

type PushPullSide = "push" | "pull";

function isAntagonist(a: string, b: string): boolean {
  return ANTAGONIST_PAIRS.some(([x, y]) => (a === x && b === y) || (a === y && b === x));
}

function specializedMuscles(context: GenerationContext): Set<string> {
  const out = new Set<string>();
  for (const [muscle, level] of Object.entries(context.specialization)) {
    if (level === "Specialize") out.add(muscle);
  }
  return out;
}

function pushPullSide(selection: SelectedSlotLift): PushPullSide | null {
  const pattern = selection.slot.pattern;
  if (PULL_PATTERNS.has(pattern)) return "pull";
  if (PUSH_PATTERNS.has(pattern)) return "push";
  return null;
}

function oppositePushPullSide(side: PushPullSide): PushPullSide {
  return side === "push" ? "pull" : "push";
}

interface InterleaveContext {
  lastMuscle: string | null;
  lastSide: PushPullSide | null;
}

function pickIndex(
  selections: SelectedSlotLift[],
  used: Set<number>,
  predicate: (selection: SelectedSlotLift, index: number) => boolean,
): number {
  return selections.findIndex((selection, index) => !used.has(index) && predicate(selection, index));
}

/** Greedy push/pull + antagonist interleave; avoids stacking the same side when alternatives exist. */
function interleaveSelections(
  selections: SelectedSlotLift[],
  start: InterleaveContext = { lastMuscle: null, lastSide: null },
): SelectedSlotLift[] {
  const ordered: SelectedSlotLift[] = [];
  const used = new Set<number>();
  let lastMuscle = start.lastMuscle;
  let lastSide = start.lastSide;

  while (ordered.length < selections.length) {
    let picked = -1;

    if (ordered.length === 0 && lastSide != null) {
      const bridge = oppositePushPullSide(lastSide);
      picked = pickIndex(selections, used, (selection) => pushPullSide(selection) === bridge);
    }

    if (picked < 0 && lastSide != null) {
      const want = oppositePushPullSide(lastSide);
      picked = pickIndex(selections, used, (selection) => pushPullSide(selection) === want);
    }

    if (picked < 0 && lastMuscle != null) {
      picked = pickIndex(
        selections,
        used,
        (selection) => isAntagonist(lastMuscle!, selection.slot.primaryMuscle),
      );
    }

    if (picked < 0 && lastMuscle != null) {
      picked = pickIndex(
        selections,
        used,
        (selection) => selection.slot.primaryMuscle !== lastMuscle,
      );
    }

    if (picked < 0) {
      picked = pickIndex(selections, used, () => true);
    }

    if (picked < 0) break;

    used.add(picked);
    const selection = selections[picked]!;
    ordered.push(selection);
    lastMuscle = selection.slot.primaryMuscle;
    const side = pushPullSide(selection);
    if (side != null) lastSide = side;
  }

  return ordered;
}

function orderSelections(
  selections: SelectedSlotLift[],
  context: GenerationContext,
): SelectedSlotLift[] {
  const specialized = specializedMuscles(context);
  const spec = selections.filter((s) => specialized.has(s.slot.primaryMuscle));
  const rest = selections.filter((s) => !specialized.has(s.slot.primaryMuscle));

  const orderedSpec = interleaveSelections(spec);
  const lastSpec = orderedSpec[orderedSpec.length - 1];
  const compoundStart: InterleaveContext = {
    lastMuscle: lastSpec?.slot.primaryMuscle ?? null,
    lastSide: lastSpec != null ? pushPullSide(lastSpec) : null,
  };
  return [...orderedSpec, ...interleaveSelections(rest, compoundStart)];
}

function buildLiftDrafts(
  selections: SelectedSlotLift[],
  scheme: "Linear" | "Double",
): ProgramLiftDraft[] {
  return selections.map((sel, liftIndex) => {
    const { repMin, repMax, topSetRir, backOffRir } = repRirForSlot(sel.slot, sel.lift);
    const setCount = sel.slot.setCount;
    const sets = Array.from({ length: setCount }, (_, i) => {
      const isTopSet = scheme === "Double" && i === 0;
      return {
        position: i,
        setType: "Standard" as const,
        repMin,
        repMax,
        rirGoal: isTopSet ? topSetRir : backOffRir,
        isTopSet,
      };
    });
    return {
      liftId: sel.lift.id,
      position: liftIndex,
      workoutLiftSets: sets,
    };
  });
}

export function assembleWorkouts(
  plans: SessionSlotPlan[],
  selectionsBySession: SelectedSlotLift[][],
  context: GenerationContext,
): ProgramWorkoutDraft[] {
  const scheme = schemeForTrainingAge(context.trainingAge);
  return plans.map((plan, idx) => {
    const selections = orderSelections(selectionsBySession[idx] ?? [], context);
    return {
      name: plan.name,
      weekday: null,
      position: plan.position,
      workoutLifts: buildLiftDrafts(selections, scheme),
    };
  });
}

/** @internal Exported for unit tests. */
export function pushPullSideForTest(selection: SelectedSlotLift): PushPullSide | null {
  return pushPullSide(selection);
}

/** @internal Exported for unit tests. */
export function orderSelectionsForTest(
  selections: SelectedSlotLift[],
  context: GenerationContext,
): SelectedSlotLift[] {
  return orderSelections(selections, context);
}

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import type { SelectedSlotLift } from "./liftSlotSelector";
import type { FilledSlot } from "./slotSetAllocator";
import { assembleWorkouts, orderSelectionsForTest } from "./workoutAssembler";

function slot(
  slotId: string,
  muscle: string,
  role: FilledSlot["role"],
): FilledSlot {
  return {
    slotId,
    role,
    pattern: "HorizontalPress",
    primaryMuscle: muscle,
    repProfile: "isolation",
    setCount: 3,
  };
}

function selWithPattern(
  slotId: string,
  muscle: string,
  role: FilledSlot["role"],
  liftId: string,
  pattern: string,
): SelectedSlotLift {
  const s: FilledSlot = {
    slotId,
    role,
    pattern,
    primaryMuscle: muscle,
    repProfile: "isolation",
    setCount: 3,
  };
  return {
    slot: s,
    lift: {
      id: liftId,
      name: liftId,
      movementPattern: pattern,
      movementPatterns: [pattern],
      primaryMuscles: [muscle],
      secondaryMuscles: [],
      proven: false,
    },
    candidates: [],
  };
}

function sel(slotId: string, muscle: string, role: FilledSlot["role"], liftId: string): SelectedSlotLift {
  return selWithPattern(slotId, muscle, role, liftId, "HorizontalPress");
}

describe("workoutAssembler", () => {
  it("places specialized arm slots before chest and back compounds", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: { Bicep: "Specialize", Tricep: "Specialize" },
        provenLifts: [],
        availableLifts: [],
        literatureVolumeLandmarks: [],
      }),
    );
    const selections = [
      sel("h-press", "Chest", "anchor", "bench"),
      sel("v-pull", "Back", "secondary_compound", "chin"),
      sel("h-pull", "Back", "secondary_compound", "row"),
      sel("incline", "Chest", "secondary_compound", "inc"),
      sel("bicep", "Bicep", "isolation", "curl"),
      sel("tricep", "Tricep", "isolation", "tri"),
    ];
    const workouts = assembleWorkouts(
      [{ sessionType: "Upper", name: "Upper A", slots: [], siblingIndex: 0, position: 0, filledSlots: [] }],
      [selections],
      context,
    );
    const ids = (workouts[0]?.workoutLifts ?? []).map((l) => l.liftId);
    assert.ok(ids.indexOf("curl") < ids.indexOf("bench"));
    assert.ok(ids.indexOf("tri") < ids.indexOf("bench"));
  });

  it("interleaves chest and back after specialized arms instead of stacking pulls", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: { Bicep: "Specialize", Tricep: "Specialize" },
        provenLifts: [],
        availableLifts: [],
        literatureVolumeLandmarks: [],
      }),
    );
    const fly = selWithPattern("incline", "Chest", "secondary_compound", "fly", "InclinePress");
    const pull = selWithPattern("v-pull", "Back", "secondary_compound", "pulldown", "VerticalPull");
    const row = selWithPattern("h-pull", "Back", "secondary_compound", "row", "HorizontalPull");
    const selections = [
      fly,
      pull,
      row,
      selWithPattern("bicep", "Bicep", "isolation", "curl", "ElbowFlexion"),
      selWithPattern("tricep", "Tricep", "isolation", "tri", "ElbowExtension"),
    ];
    const workouts = assembleWorkouts(
      [{ sessionType: "Upper", name: "Upper B", slots: [], siblingIndex: 1, position: 1, filledSlots: [] }],
      [selections],
      context,
    );
    const muscles = (workouts[0]?.workoutLifts ?? []).map((l) => {
      if (l.liftId === "fly") return "Chest";
      if (l.liftId === "curl") return "Bicep";
      if (l.liftId === "tri") return "Tricep";
      return "Back";
    });
    assert.deepEqual(muscles.slice(0, 2), ["Bicep", "Tricep"]);
    const compounds = muscles.slice(2);
    assert.deepEqual(compounds, ["Back", "Chest", "Back"], `expected pull-push-pull, got ${compounds.join(" -> ")}`);
  });

  it("does not stack chest/back compounds back-to-back when alternatives exist", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: {},
        provenLifts: [],
        availableLifts: [],
        literatureVolumeLandmarks: [],
      }),
    );
    const selections = [
      selWithPattern("h-press", "Chest", "anchor", "bench", "HorizontalPress"),
      selWithPattern("v-pull", "Back", "secondary_compound", "chin", "VerticalPull"),
      selWithPattern("h-pull", "Back", "secondary_compound", "row", "HorizontalPull"),
      selWithPattern("incline", "Chest", "secondary_compound", "inc", "InclinePress"),
    ];
    const workouts = assembleWorkouts(
      [{ sessionType: "Upper", name: "Upper A", slots: [], siblingIndex: 0, position: 0, filledSlots: [] }],
      [selections],
      context,
    );
    const muscles = (workouts[0]?.workoutLifts ?? []).map((l) => {
      if (l.liftId === "bench" || l.liftId === "inc") return "Chest";
      return "Back";
    });
    for (let i = 1; i < muscles.length; i++) {
      if (muscles[i] === "Back" && muscles[i - 1] === "Back") {
        assert.fail(`stacked back at ${i - 1} and ${i}: ${muscles.join(" -> ")}`);
      }
    }
  });

  it("does not stack push compounds after tricep isolation when bridging into compounds", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: { Bicep: "Specialize", Tricep: "Specialize" },
        provenLifts: [],
        availableLifts: [],
        literatureVolumeLandmarks: [],
      }),
    );
    const selections = [
      selWithPattern("h-press", "Chest", "anchor", "bench", "HorizontalPress"),
      selWithPattern("v-pull", "Back", "secondary_compound", "pulldown", "VerticalPull"),
      selWithPattern("h-pull", "Back", "secondary_compound", "row", "HorizontalPull"),
      selWithPattern("bicep", "Bicep", "isolation", "curl", "ElbowFlexion"),
      selWithPattern("tricep", "Tricep", "isolation", "tri", "ElbowExtension"),
    ];
    const ordered = orderSelectionsForTest(selections, context);
    const labels = ordered.map((selection) => selection.lift.id);
    assert.deepEqual(
      labels,
      ["curl", "tri", "pulldown", "bench", "row"],
      `expected arms then pull before push, got ${labels.join(" -> ")}`,
    );
  });
});

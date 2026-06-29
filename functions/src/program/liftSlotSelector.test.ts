import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { candidatesForSlot, scoreLiftForSlot, selectLiftForSlot } from "./liftSlotSelector";
import type { FilledSlot } from "./slotSetAllocator";
import type { WorkoutSlotTemplate } from "./sessionTypeCatalog";

function context() {
  return parseGenerationContext(
    JSON.stringify({
      profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, trainingDaysPerWeek: 4 },
      specialization: {},
      provenLifts: [
        { id: "bench", name: "Barbell Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], mainstream: true },
        { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
        { id: "machine-press", name: "Hammer Strength Chest Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], equipment: "machine" },
      ],
      availableLifts: [],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
      ],
    }),
  );
}

const anchorSlot: WorkoutSlotTemplate = {
  slotId: "h-press",
  pattern: "HorizontalPress",
  primaryMuscle: "Chest",
  role: "anchor",
  repProfile: "lowerMachineOrUpperPress",
};

describe("liftSlotSelector", () => {
  it("anchor slot prefers barbell bench over machine press", () => {
    const ctx = context();
    const ranked = candidatesForSlot(anchorSlot, ctx).sort(
      (a, b) => scoreLiftForSlot(b, anchorSlot, ctx, 0) - scoreLiftForSlot(a, anchorSlot, ctx, 0),
    );
    assert.equal(ranked[0]?.id, "bench");
  });

  it("length-bias sibling B favors lengthened lift when configured", () => {
    const ctx = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: {},
        provenLifts: [
          { id: "curl-short", name: "Spider Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], muscleLengthBias: "Shortened" },
          { id: "curl-long", name: "Incline Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], muscleLengthBias: "Lengthened" },
        ],
        availableLifts: [],
        literatureVolumeLandmarks: [{ muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 }],
      }),
    );
    const slot: WorkoutSlotTemplate = {
      slotId: "bicep",
      pattern: "ElbowFlexion",
      primaryMuscle: "Bicep",
      role: "isolation",
      repProfile: "isolation",
      lengthBiasPreference: "Lengthened",
      siblingVariant: "B",
    };
    const filled: FilledSlot = { ...slot, setCount: 3 };
    const selected = selectLiftForSlot(filled, ctx, 1, new Set(), 2, 0, new Map());
    assert.equal(selected?.lift.id, "curl-long");
  });

  it("tricep isolation slot rejects chest-primary presses that also list ElbowExtension", () => {
    const ctx = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Intermediate" },
        specialization: { Tricep: "Specialize" },
        provenLifts: [
          { id: "cable-chest-press", name: "Cable Chest Press", movementPattern: "HorizontalPress", movementPatterns: ["HorizontalPress", "ElbowExtension"], primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
          { id: "tricep-ext", name: "Cable Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
        ],
        availableLifts: [],
        literatureVolumeLandmarks: [{ muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 }],
      }),
    );
    const slot: WorkoutSlotTemplate = {
      slotId: "tricep",
      pattern: "ElbowExtension",
      primaryMuscle: "Tricep",
      role: "isolation",
      repProfile: "isolation",
    };
    const filled: FilledSlot = { ...slot, setCount: 6 };
    const selected = selectLiftForSlot(filled, ctx, 0, new Set(), 2, 0, new Map());
    assert.equal(selected?.lift.id, "tricep-ext");
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  CUSTOM_LIFT_INFERENCE_MAX_BATCH,
  DEFAULT_CUSTOM_INCREMENT_LB,
  DEFAULT_EQUIPMENT_NAME,
  parseInferredCustomLifts,
  primaryMusclesForPattern,
} from "./customLiftInferenceSchema";

/** Helper: seed loading fields for fixtures (pattern-only LLM schema). */
function seedLoading(kind: string) {
  return {
    equipmentKind: kind,
    customEquipmentName: null,
    customIncrementLb: null,
  };
}

describe("parseInferredCustomLifts", () => {
  it("keeps valid rows for requested exercise names with seed loading", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Katana Triceps Extension",
            movementPattern: "ElbowExtension",
            ...seedLoading("cable"),
          },
          {
            name: "Not Requested",
            movementPattern: "ElbowFlexion",
            ...seedLoading("dumbbell"),
          },
        ],
      },
      new Set(["Katana Triceps Extension"]),
    );

    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.name, "Katana Triceps Extension");
    assert.equal(rows[0]?.movementPattern, "ElbowExtension");
    assert.deepEqual(rows[0]?.primaryMuscles, ["Tricep"]);
    assert.deepEqual(rows[0]?.loading, { type: "seed", equipmentId: "cable" });
  });

  it("maps plate-loaded dual-horn presses to double-weight-plates", () => {
    const name = "Seated Neutral Grip Plate-Loaded Machine Press";
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name,
            movementPattern: "HorizontalPress",
            ...seedLoading("double-weight-plates"),
          },
        ],
      },
      new Set([name]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.loading, {
      type: "seed",
      equipmentId: "double-weight-plates",
    });
  });

  it("parses explicit custom loading with name and increment", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Pro2 Pec Fly",
            movementPattern: "ShoulderAdduction",
            equipmentKind: "custom",
            customEquipmentName: "Life Fitness Pro2 Stack",
            customIncrementLb: 12.5,
          },
        ],
      },
      new Set(["Pro2 Pec Fly"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.loading, {
      type: "custom",
      name: "Life Fitness Pro2 Stack",
      incrementLb: 12.5,
    });
    assert.deepEqual(rows[0]?.primaryMuscles, ["Chest"]);
  });

  it("falls back blank custom name to shared Default equipment and +5 increment", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Mystery Brand Press",
            movementPattern: "HorizontalPress",
            equipmentKind: "custom",
            customEquipmentName: null,
            customIncrementLb: null,
          },
        ],
      },
      new Set(["Mystery Brand Press"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.loading, {
      type: "custom",
      name: DEFAULT_EQUIPMENT_NAME,
      incrementLb: DEFAULT_CUSTOM_INCREMENT_LB,
    });
  });

  it("invalid equipmentKind becomes shared Default equipment without fuzzy seed matching", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Unknown Gizmo",
            movementPattern: "ElbowExtension",
            equipmentKind: "not-a-real-kind",
            customEquipmentName: null,
            customIncrementLb: null,
          },
        ],
      },
      new Set(["Unknown Gizmo"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.loading, {
      type: "custom",
      name: DEFAULT_EQUIPMENT_NAME,
      incrementLb: DEFAULT_CUSTOM_INCREMENT_LB,
    });
  });

  it("clamps absurd custom increments to default 5", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Wild Stack",
            movementPattern: "HorizontalPress",
            equipmentKind: "custom",
            customEquipmentName: "Wild Stack",
            customIncrementLb: 1_000_000,
          },
        ],
      },
      new Set(["Wild Stack"]),
    );

    assert.deepEqual(rows[0]?.loading, {
      type: "custom",
      name: "Wild Stack",
      incrementLb: DEFAULT_CUSTOM_INCREMENT_LB,
    });
  });

  it("rejects rows with invalid movement patterns", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Mystery Move",
            movementPattern: "NotReal",
            ...seedLoading("cable"),
          },
        ],
      },
      new Set(["Mystery Move"]),
    );

    assert.equal(rows.length, 0);
  });

  it("ignores LLM primary muscle fields and derives from pattern", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Machine Crunch",
            movementPattern: "SpinalFlexion",
            // Legacy/stale model fields — must not be trusted.
            primaryMuscles: ["Quad"],
            secondaryMuscles: ["Hamstring"],
            ...seedLoading("pin-loaded-machine"),
          },
        ],
      },
      new Set(["Machine Crunch"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.primaryMuscles, ["Ab"]);
    assert.deepEqual(rows[0]?.secondaryMuscles, []);
  });

  it("caps batch size constant matches domain", () => {
    assert.equal(CUSTOM_LIFT_INFERENCE_MAX_BATCH, 75);
  });

  it("adds bicep secondary for pull patterns", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Neutral Wide Grip Cable Row",
            movementPattern: "HorizontalPull",
            ...seedLoading("cable"),
          },
        ],
      },
      new Set(["Neutral Wide Grip Cable Row"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.primaryMuscles, ["Back"]);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Bicep"]);
  });

  it("adds tricep secondary for compound push patterns", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Machine Chest Press",
            movementPattern: "HorizontalPress",
            ...seedLoading("pin-loaded-machine"),
          },
        ],
      },
      new Set(["Machine Chest Press"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Tricep"]);
  });

  it("derives hinge muscles as glute + hamstring", () => {
    assert.deepEqual(primaryMusclesForPattern("HipHinge"), ["Glute", "Hamstring"]);
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Weighted Roman Chair Hip Hinge",
            movementPattern: "HipHinge",
            ...seedLoading("bodyweight"),
          },
        ],
      },
      new Set(["Weighted Roman Chair Hip Hinge"]),
    );
    assert.deepEqual(rows[0]?.primaryMuscles, ["Glute", "Hamstring"]);
  });
});

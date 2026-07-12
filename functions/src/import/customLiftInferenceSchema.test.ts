import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  CUSTOM_LIFT_INFERENCE_MAX_BATCH,
  DEFAULT_CUSTOM_INCREMENT_LB,
  DEFAULT_EQUIPMENT_NAME,
  parseInferredCustomLifts,
} from "./customLiftInferenceSchema";

/** Helper: seed loading fields for fixtures. */
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
            primaryMuscles: ["Tricep"],
            secondaryMuscles: [],
            ...seedLoading("cable"),
          },
          {
            name: "Not Requested",
            movementPattern: "ElbowFlexion",
            primaryMuscles: ["Bicep"],
            secondaryMuscles: [],
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
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
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
            movementPattern: "HorizontalPress",
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
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
  });

  it("falls back blank custom name to shared Default equipment and +5 increment", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Mystery Brand Press",
            movementPattern: "HorizontalPress",
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
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
            primaryMuscles: ["Tricep"],
            secondaryMuscles: [],
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
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
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

  it("rejects rows with invalid enums or missing primary muscles", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Mystery Move",
            movementPattern: "NotReal",
            primaryMuscles: ["Tricep"],
            secondaryMuscles: [],
            ...seedLoading("cable"),
          },
          {
            name: "Mystery Move",
            movementPattern: "ElbowExtension",
            primaryMuscles: [],
            secondaryMuscles: [],
            ...seedLoading("cable"),
          },
        ],
      },
      new Set(["Mystery Move"]),
    );

    assert.equal(rows.length, 0);
  });

  it("caps batch size constant matches domain", () => {
    assert.equal(CUSTOM_LIFT_INFERENCE_MAX_BATCH, 75);
  });

  it("adds bicep secondary for pull patterns when the LLM omits it", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Neutral Wide Grip Cable Row",
            movementPattern: "HorizontalPull",
            primaryMuscles: ["Back"],
            secondaryMuscles: [],
            ...seedLoading("cable"),
          },
        ],
      },
      new Set(["Neutral Wide Grip Cable Row"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Bicep"]);
  });

  it("adds tricep secondary for compound push patterns when the LLM omits it", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Machine Chest Press",
            movementPattern: "HorizontalPress",
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
            ...seedLoading("pin-loaded-machine"),
          },
        ],
      },
      new Set(["Machine Chest Press"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Tricep"]);
  });

  it("keeps up to 4 secondary muscles for compound lifts", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Close-Grip Bench Press",
            movementPattern: "HorizontalPress",
            primaryMuscles: ["Chest"],
            secondaryMuscles: ["Tricep", "AnteriorDeltoid", "Bicep", "Back"],
            ...seedLoading("double-weight-plates"),
          },
        ],
      },
      new Set(["Close-Grip Bench Press"]),
    );

    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.secondaryMuscles.length, 4);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Tricep", "AnteriorDeltoid", "Bicep", "Back"]);
  });

  it("dedupes a secondary muscle already listed as primary", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Hip Thrust",
            movementPattern: "HipExtension",
            primaryMuscles: ["Glute"],
            secondaryMuscles: ["Glute", "Hamstring"],
            ...seedLoading("double-weight-plates"),
          },
        ],
      },
      new Set(["Hip Thrust"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Hamstring"]);
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  CUSTOM_LIFT_INFERENCE_MAX_BATCH,
  parseInferredCustomLifts,
} from "./customLiftInferenceSchema";

describe("parseInferredCustomLifts", () => {
  it("keeps valid rows for requested exercise names", () => {
    const rows = parseInferredCustomLifts(
      {
        lifts: [
          {
            name: "Katana Triceps Extension",
            movementPattern: "TricepIso",
            primaryMuscles: ["Tricep"],
            secondaryMuscles: [],
            equipment: "cable",
          },
          {
            name: "Not Requested",
            movementPattern: "BicepIso",
            primaryMuscles: ["Bicep"],
            secondaryMuscles: [],
            equipment: null,
          },
        ],
      },
      new Set(["Katana Triceps Extension"]),
    );

    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.name, "Katana Triceps Extension");
    assert.equal(rows[0]?.movementPattern, "TricepIso");
    assert.deepEqual(rows[0]?.primaryMuscles, ["Tricep"]);
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
            equipment: null,
          },
          {
            name: "Mystery Move",
            movementPattern: "TricepIso",
            primaryMuscles: [],
            secondaryMuscles: [],
            equipment: null,
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
            equipment: "cable",
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
            movementPattern: "HorizontalPush",
            primaryMuscles: ["Chest"],
            secondaryMuscles: [],
            equipment: "machine",
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
            movementPattern: "HorizontalPush",
            primaryMuscles: ["Chest"],
            secondaryMuscles: ["Tricep", "AnteriorDeltoid", "Bicep", "Back"],
            equipment: "barbell",
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
            movementPattern: "GluteIso",
            primaryMuscles: ["Glute"],
            secondaryMuscles: ["Glute", "Hamstring"],
            equipment: "barbell",
          },
        ],
      },
      new Set(["Hip Thrust"]),
    );

    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.secondaryMuscles, ["Hamstring"]);
  });
});

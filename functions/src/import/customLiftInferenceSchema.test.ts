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
});

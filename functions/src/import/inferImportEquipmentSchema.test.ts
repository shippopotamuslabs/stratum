import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH,
  parseInferredEquipment,
} from "./inferImportEquipmentSchema";

describe("parseInferredEquipment", () => {
  it("keeps valid rows for requested exercise names", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          {
            name: "Lat Pulldown - Underhand (Cable)",
            equipment: ["CableStation", "LatPulldownMachine"],
            confidence: 0.9,
          },
          {
            name: "Not Requested",
            equipment: ["CableStation"],
            confidence: 0.5,
          },
        ],
      },
      new Set(["Lat Pulldown - Underhand (Cable)"]),
    );

    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.name, "Lat Pulldown - Underhand (Cable)");
    assert.deepEqual(rows[0]?.equipment, ["CableStation", "LatPulldownMachine"]);
    assert.equal(rows[0]?.confidence, 0.9);
  });

  it("drops equipment entries not in the enum", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          {
            name: "Mystery Cable Thing",
            equipment: ["CableStation", "NotAnEnum", "CableStation"],
            confidence: 0.5,
          },
        ],
      },
      new Set(["Mystery Cable Thing"]),
    );
    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.equipment, ["CableStation"]);
  });

  it("BM25-recovers hallucinated equipment labels before dropping the row", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          {
            name: "Cable Lateral Raise",
            equipment: ["CableMachine"],
            confidence: 0.4,
          },
        ],
      },
      new Set(["Cable Lateral Raise"]),
    );
    assert.equal(rows.length, 1);
    assert.deepEqual(rows[0]?.equipment, ["CableStation"]);
  });

  it("BM25-falls back to exercise name when every equipment label is invalid", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          {
            name: "Seated Cable Row",
            equipment: ["NotAnEnum"],
            confidence: 0.2,
          },
        ],
      },
      new Set(["Seated Cable Row"]),
    );
    assert.equal(rows.length, 1);
    assert.ok(rows[0]?.equipment.includes("SeatedCableRowStation"));
  });

  it("omits rows with no valid equipment after filtering", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          { name: "Bodyweight Move", equipment: ["Bodyweight"], confidence: 0.8 },
          { name: "Unknown Gizmo", equipment: ["NotAnEnum"], confidence: 0.2 },
        ],
      },
      new Set(["Bodyweight Move", "Unknown Gizmo"]),
    );
    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.name, "Bodyweight Move");
  });

  it("clamps confidence when out of range", () => {
    const rows = parseInferredEquipment(
      {
        lifts: [
          { name: "Cable Row", equipment: ["CableStation"], confidence: 5 },
        ],
      },
      new Set(["Cable Row"]),
    );
    assert.equal(rows[0]?.confidence, 1);
  });

  it("rejects non-array payloads gracefully", () => {
    assert.deepEqual(parseInferredEquipment({}, new Set(["X"])), []);
    assert.deepEqual(parseInferredEquipment({ lifts: "nope" }, new Set(["X"])), []);
  });

  it("caps batch size constant matches design", () => {
    assert.equal(IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH, 100);
  });
});

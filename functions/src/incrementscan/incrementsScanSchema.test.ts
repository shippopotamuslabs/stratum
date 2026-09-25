import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  INCREMENT_SCAN_MAX_MICRO_STOPS,
  INCREMENT_SCAN_MAX_TIERS,
  incrementScanJsonSchema,
  parseScannedTiers,
} from "./incrementsScanSchema";

describe("parseScannedTiers", () => {
  it("parses a recorded lb label fixture with an open-ended top band", () => {
    const scan = parseScannedTiers({
      unit: "lb",
      tiers: [
        { lower: 10, upper: 50, step: 10 },
        { lower: 50, upper: 100, step: 10 },
        { lower: 100, upper: null, step: 10 },
      ],
      microStops: [],
    });

    assert.equal(scan.unit, "lb");
    assert.deepEqual(scan.tiers, [
      { lower: 10, upper: 50, step: 10 },
      { lower: 50, upper: 100, step: 10 },
      { lower: 100, upper: null, step: 10 },
    ]);
    assert.deepEqual(scan.microStops, []);
  });

  it("parses a recorded kg fixture with adder micro stops", () => {
    const scan = parseScannedTiers({
      unit: "kg",
      tiers: [
        { lower: 5, upper: 25, step: 5 },
        { lower: 25, upper: null, step: 5 },
      ],
      microStops: [2.5, 1.25],
    });

    assert.equal(scan.unit, "kg");
    assert.equal(scan.tiers.length, 2);
    // Micro stops are normalized ascending regardless of model order.
    assert.deepEqual(scan.microStops, [1.25, 2.5]);
  });

  it("sorts tiers by lower bound and tolerates gaps", () => {
    const scan = parseScannedTiers({
      unit: "lb",
      tiers: [
        { lower: 100, upper: null, step: 10 },
        { lower: 10, upper: 40, step: 5 },
      ],
      microStops: [],
    });

    assert.deepEqual(scan.tiers, [
      { lower: 10, upper: 40, step: 5 },
      { lower: 100, upper: null, step: 10 },
    ]);
  });

  it("drops garbage rows and unknown echo fields", () => {
    const scan = parseScannedTiers({
      unit: "lb",
      tiers: [
        { lower: 10, upper: 50, step: 10, label: "10-50", confidence: 0.9 },
        { lower: "10", upper: 50, step: 10 },
        { lower: 50, upper: 100, step: -5 },
        { lower: 50, upper: 100, step: Number.NaN },
        { lower: 50, upper: Number.POSITIVE_INFINITY, step: 10 },
        { lower: 60, upper: 60, step: 5 },
        { lower: 70, upper: 60, step: 5 },
        { lower: -10, upper: 50, step: 5 },
        "not-a-row",
        null,
      ],
      microStops: [],
      hallucinated: [{ lower: 0, upper: 1000, step: 1 }],
    });

    assert.deepEqual(scan.tiers, [{ lower: 10, upper: 50, step: 10 }]);
  });

  it("drops non-positive micro stops, dedupes, and sorts", () => {
    const scan = parseScannedTiers({
      unit: "lb",
      tiers: [{ lower: 10, upper: null, step: 10 }],
      microStops: [5, 2.5, 5, 0, -1, "2.5", Number.NaN],
    });

    assert.deepEqual(scan.microStops, [2.5, 5]);
  });

  it("returns empty tiers for non-object or missing payloads", () => {
    assert.deepEqual(parseScannedTiers(null), {
      unit: "lb",
      tiers: [],
      microStops: [],
    });
    assert.deepEqual(parseScannedTiers({}), {
      unit: "lb",
      tiers: [],
      microStops: [],
    });
    assert.deepEqual(parseScannedTiers({ unit: "lb", tiers: "nope" }), {
      unit: "lb",
      tiers: [],
      microStops: [],
    });
  });

  it("falls back to lb for a missing or unknown unit", () => {
    assert.equal(
      parseScannedTiers({ unit: "stones", tiers: [], microStops: [] }).unit,
      "lb",
    );
    assert.equal(
      parseScannedTiers({ tiers: [], microStops: [] }).unit,
      "lb",
    );
  });

  it("caps tiers and micro stops at the wire maxima", () => {
    const tiers = Array.from({ length: 12 }, (_, i) => ({
      lower: i * 10,
      upper: i === 11 ? null : (i + 1) * 10,
      step: 10,
    }));
    const scan = parseScannedTiers({
      unit: "lb",
      tiers,
      microStops: [1, 2, 3, 4, 5, 6, 7, 8],
    });

    assert.equal(scan.tiers.length, INCREMENT_SCAN_MAX_TIERS);
    assert.equal(scan.microStops.length, INCREMENT_SCAN_MAX_MICRO_STOPS);
  });

  it("wire caps match the plan contract", () => {
    assert.equal(INCREMENT_SCAN_MAX_TIERS, 8);
    assert.equal(INCREMENT_SCAN_MAX_MICRO_STOPS, 6);
  });
});

describe("incrementScanJsonSchema", () => {
  it("exposes a strict bounded schema", () => {
    const shape = incrementScanJsonSchema();
    assert.equal(shape.name, "equipment_increments_scan");
    assert.equal(shape.strict, true);
    const schema = shape.schema as {
      properties: {
        unit: { enum: string[] };
        tiers: { maxItems: number };
        microStops: { maxItems: number };
      };
      required: string[];
    };
    assert.deepEqual(schema.properties.unit.enum, ["lb", "kg"]);
    assert.equal(schema.properties.tiers.maxItems, 8);
    assert.equal(schema.properties.microStops.maxItems, 6);
    assert.deepEqual(schema.required, ["unit", "tiers", "microStops"]);
  });
});

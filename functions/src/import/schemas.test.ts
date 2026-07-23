import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_INTENT,
  normalizeImportTimestamp,
  normalizeWeightUnit,
  PHASE_BY_TOOL,
  sanitizeClientProgressMessage,
  sanitizeIntent,
  validateNormalizedHistory,
} from "./schemas";
import { VALID_NORMALIZED } from "./testFixtures";

describe("sanitizeIntent", () => {
  it("returns fallback when intent missing", () => {
    assert.equal(
      sanitizeIntent(undefined, "write_conversion_script"),
      DEFAULT_INTENT.write_conversion_script,
    );
  });

  it("returns fallback for empty string after trim", () => {
    assert.equal(sanitizeIntent("   ", "execute_on_full"), DEFAULT_INTENT.execute_on_full);
  });

  it("strips newlines and caps length", () => {
    const long = "a".repeat(200);
    const result = sanitizeIntent(`line1\nline2 ${long}`, "execute_on_sample");
    assert.ok(result.length <= 120);
    assert.ok(!result.includes("\n"));
  });

  it("rejects technical leak text as intent", () => {
    assert.equal(
      sanitizeIntent("Traceback (most recent call last):", "execute_on_sample"),
      DEFAULT_INTENT.execute_on_sample,
    );
    assert.equal(
      sanitizeIntent("Each set requires id, sessionId, exerciseNameRaw", "execute_on_sample"),
      DEFAULT_INTENT.execute_on_sample,
    );
  });
});

describe("sanitizeClientProgressMessage", () => {
  it("passes through user-facing messages", () => {
    assert.equal(
      sanitizeClientProgressMessage("Mapping CSV columns", "creating_converter"),
      "Mapping CSV columns",
    );
  });

  it("replaces technical leaks with phase default", () => {
    assert.equal(
      sanitizeClientProgressMessage(
        "Each set requires id, sessionId, exerciseNameRaw, setIndex",
        "validating_sample",
      ),
      DEFAULT_INTENT.execute_on_sample,
    );
  });
});

describe("PHASE_BY_TOOL", () => {
  it("maps each tool to a progress phase", () => {
    assert.equal(PHASE_BY_TOOL.write_conversion_script, "creating_converter");
    assert.equal(PHASE_BY_TOOL.execute_on_sample, "validating_sample");
    assert.equal(PHASE_BY_TOOL.execute_on_full, "converting_full");
  });
});

describe("normalizeWeightUnit", () => {
  it("maps common variants to Lb or Kg", () => {
    assert.equal(normalizeWeightUnit("lbs"), "Lb");
    assert.equal(normalizeWeightUnit("lb"), "Lb");
    assert.equal(normalizeWeightUnit("KG"), "Kg");
    assert.equal(normalizeWeightUnit("kilograms"), "Kg");
  });

  it("rejects unknown units", () => {
    assert.throws(() => normalizeWeightUnit("stone"), /Unsupported weight unit/);
  });
});

describe("normalizeImportTimestamp", () => {
  it("maps date-only values to UTC midnight", () => {
    assert.equal(normalizeImportTimestamp("2025-12-28"), "2025-12-28T00:00:00Z");
  });

  it("appends Z to local datetimes without offset", () => {
    assert.equal(normalizeImportTimestamp("2025-12-28T10:15:00"), "2025-12-28T10:15:00Z");
  });

  it("keeps UTC datetimes with Z", () => {
    assert.equal(normalizeImportTimestamp("2025-12-28T10:15:00Z"), "2025-12-28T10:15:00Z");
  });

  it("rejects space-separated datetimes", () => {
    assert.throws(
      () => normalizeImportTimestamp("2018-01-31 20:21:08"),
      /Invalid timestamp/,
    );
  });
});

describe("validateNormalizedHistory", () => {
  it("accepts valid payload", () => {
    const result = validateNormalizedHistory(VALID_NORMALIZED);
    assert.equal(result.sets[0]?.exerciseNameRaw, "Squat");
  });

  it("rejects non-object root", () => {
    assert.throws(() => validateNormalizedHistory(null), /JSON object/);
  });

  it("rejects missing set fields", () => {
    assert.throws(
      () =>
        validateNormalizedHistory({
          sessions: [{ id: "s1", startedAt: "2025-01-01T00:00:00Z" }],
          sets: [{ id: "set-1", sessionId: "s1" }],
        }),
      /set requires/,
    );
  });

  it("normalizes weightUnit on sets", () => {
    const result = validateNormalizedHistory({
      sessions: [{ id: "s1", startedAt: "2025-12-28" }],
      sets: [
        {
          id: "set-1",
          sessionId: "s1",
          exerciseNameRaw: "Squat",
          setIndex: 0,
          weightAmount: 100,
          weightUnit: "lbs",
          reps: 5,
          completedAt: "2025-12-28",
        },
      ],
    });
    assert.equal(result.sets[0]?.weightUnit, "Lb");
    assert.equal(result.sessions[0]?.startedAt, "2025-12-28T00:00:00Z");
    assert.equal(result.sets[0]?.completedAt, "2025-12-28T00:00:00Z");
  });

  it("applies fallbackWeightUnit when weightUnit omitted", () => {
    const result = validateNormalizedHistory(
      {
        sessions: [{ id: "s1", startedAt: "2025-12-28" }],
        sets: [
          {
            id: "set-1",
            sessionId: "s1",
            exerciseNameRaw: "Squat",
            setIndex: 0,
            weightAmount: 100,
            reps: 5,
            completedAt: "2025-12-28",
          },
        ],
      },
      "Kg",
    );
    assert.equal(result.sets[0]?.weightUnit, "Kg");
  });
});

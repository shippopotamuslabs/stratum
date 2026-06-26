import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { collectImportViolations, ImportValidationError } from "./importValidation";
import { validateNormalizedHistory } from "./schemas";
import { VALID_NORMALIZED } from "./testFixtures";

describe("collectImportViolations", () => {
  it("returns empty list for valid fixture", () => {
    assert.deepEqual(collectImportViolations(VALID_NORMALIZED), []);
  });

  it("flags non-positive reps with set context", () => {
    const violations = collectImportViolations({
      sessions: [{ id: "session-1", startedAt: "2025-01-01T10:00:00Z" }],
      sets: [
        {
          id: "set-1",
          sessionId: "session-1",
          exerciseNameRaw: "Squat",
          setIndex: 0,
          weightAmount: 100,
          weightUnit: "Lb",
          reps: 0,
          completedAt: "2025-01-01T10:05:00Z",
        },
      ],
    });
    assert.ok(violations.some((v) => v.includes("reps must be a positive integer")));
  });

  it("flags unknown sessionId", () => {
    const violations = collectImportViolations({
      sessions: [{ id: "session-1", startedAt: "2025-01-01T10:00:00Z" }],
      sets: [
        {
          id: "set-1",
          sessionId: "missing",
          exerciseNameRaw: "Squat",
          setIndex: 0,
          weightAmount: 100,
          weightUnit: "Lb",
          reps: 5,
          completedAt: "2025-01-01T10:05:00Z",
        },
      ],
    });
    assert.ok(violations.some((v) => v.includes("does not match any session")));
  });
});

describe("validateNormalizedHistory business rules", () => {
  it("throws ImportValidationError for invalid reps", () => {
    assert.throws(
      () =>
        validateNormalizedHistory({
          sessions: [{ id: "s1", startedAt: "2025-01-01T00:00:00Z" }],
          sets: [
            {
              id: "set-1",
              sessionId: "s1",
              exerciseNameRaw: "Bench",
              setIndex: 0,
              weightAmount: 135,
              weightUnit: "Lb",
              reps: 0,
              completedAt: "2025-01-01T00:00:00Z",
            },
          ],
        }),
      ImportValidationError,
    );
  });
});

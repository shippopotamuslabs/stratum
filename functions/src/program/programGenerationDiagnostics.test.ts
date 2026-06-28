import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  logPipelineTotal,
  logProgramGenerationMetricAttempt,
  logSplitPlannerResult,
  logWorkoutBuilderResult,
} from "./programGenerationDiagnostics";

// These loggers are thin wrappers over firebase-functions logger with no return value or side
// effects beyond logging. The tests assert they don't throw and accept the documented args.

describe("programGenerationDiagnostics", () => {
  it("logProgramGenerationMetricAttempt does not throw on pass/fail", () => {
    assert.doesNotThrow(() =>
      logProgramGenerationMetricAttempt("sid", 0, true, 0, []),
    );
    assert.doesNotThrow(() =>
      logProgramGenerationMetricAttempt("sid", 1, false, 3, ["WEEKLY_VOLUME_BELOW_TARGET", "MUSCLE_FREQUENCY_LOW", "EXERCISE_ORDER_STACKED"]),
    );
  });

  it("logSplitPlannerResult does not throw", () => {
    assert.doesNotThrow(() => logSplitPlannerResult("sid", true, true, false));
    assert.doesNotThrow(() => logSplitPlannerResult("sid", false, false, true));
  });

  it("logWorkoutBuilderResult does not throw", () => {
    assert.doesNotThrow(() => logWorkoutBuilderResult("sid", 0, 3500, false));
    assert.doesNotThrow(() => logWorkoutBuilderResult("sid", 2, 0, true));
  });

  it("logPipelineTotal does not throw", () => {
    assert.doesNotThrow(() =>
      logPipelineTotal(
        "sid",
        { splitPlannerMs: 40, buildersMs: 60, integrateMs: 1, totalMs: 101 },
        0,
      ),
    );
  });
});
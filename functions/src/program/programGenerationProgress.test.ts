import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  sanitizeProgramGenerationProgressMessage,
} from "./programGenerationProgress";

describe("programGenerationProgress", () => {
  it("sanitizeProgramGenerationProgressMessage hides technical leaks and falls back to the phase message", () => {
    assert.equal(
      sanitizeProgramGenerationProgressMessage("validate_program_metrics failed", "validating_program"),
      "Checking your draft program…",
    );
  });

  it("sanitizeProgramGenerationProgressMessage passes through clean user-facing text", () => {
    assert.equal(
      sanitizeProgramGenerationProgressMessage("Planning your weekly split…", "split_planning"),
      "Planning your weekly split…",
    );
  });

  it("sanitizeProgramGenerationProgressMessage falls back for v2 phases with a leaky message", () => {
    assert.equal(
      sanitizeProgramGenerationProgressMessage("workoutLiftSets parse error", "building_workouts"),
      "Building your workouts…",
    );
  });

  it("sanitizeProgramGenerationProgressMessage returns null for a leaky message with no phase fallback", () => {
    assert.equal(
      sanitizeProgramGenerationProgressMessage("MEV computation failed", undefined),
      null,
    );
  });
});
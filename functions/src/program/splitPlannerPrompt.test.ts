import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets, availablePatternsByMuscle } from "./volumeTargetPlanner";
import {
  buildSplitPlannerInput,
  SPLIT_PLANNER_PROMPT_VERSION,
  SPLIT_PLANNER_SYSTEM_PROMPT,
  splitPlannerOutputSchema,
} from "./splitPlannerPrompt";

function ctxFor(days = 5) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return parseGenerationContext(
    JSON.stringify({
      profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, weekdays, trainingDaysPerWeek: days },
      specialization: { Chest: "Specialize" },
      provenLifts: [
        { id: "bench", name: "Bench", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
        { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
        { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
      ],
      availableLifts: [],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedmrv: 20, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
      ],
    }),
  );
}

describe("splitPlannerPrompt", () => {
  it("system prompt is version 1 and mentions partitioning, frequency, and JSON-only", () => {
    assert.equal(SPLIT_PLANNER_PROMPT_VERSION, 1);
    assert.ok(/partition/.test(SPLIT_PLANNER_SYSTEM_PROMPT));
    assert.ok(/at least 2 days/.test(SPLIT_PLANNER_SYSTEM_PROMPT));
    assert.ok(/ONLY a JSON object/.test(SPLIT_PLANNER_SYSTEM_PROMPT));
  });

  it("buildSplitPlannerInput excludes individual lifts and includes weekly targets + available patterns", () => {
    const ctx = ctxFor(5);
    const input = buildSplitPlannerInput(ctx, computeAllWeeklyTargets(ctx), availablePatternsByMuscle(ctx));
    const inputJson = JSON.stringify(input);
    assert.ok(input.weeklyTargets !== undefined);
    assert.ok((input.frequencyConstraints as { minWeekly: number }).minWeekly === 2);
    assert.ok((input.perDayTimeBudget as { hardSetCap: number }).hardSetCap > 0);
    assert.ok(input.availablePatternsByMuscle !== undefined);
    // No lift ids leak into the split-planner input.
    assert.ok(!/bench/.test(inputJson), "lift id 'bench' leaked into split-planner input");
    assert.ok(!/"lifts"/.test(inputJson), "lifts array leaked into split-planner input");
  });

  it("splitPlannerOutputSchema is strict and requires days with muscleBudgets + suggestedPatterns", () => {
    const schema = splitPlannerOutputSchema();
    assert.equal(schema.strict, true);
    assert.equal(schema.schema.properties.days.items.required.includes("muscleBudgets"), true);
    assert.equal(schema.schema.properties.days.items.required.includes("suggestedPatterns"), true);
  });
});
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import { computeAllWeeklyTargets } from "./volumeTargetPlanner";
import { allocateDayPlans } from "./splitAllocator";
import { repBandsForLifts } from "./repBandPlanner";
import { buildGlobalCandidates } from "./liftPriority";
import {
  buildProgramBuilderInput,
  buildProgramBuilderRetryMessage,
  programBuilderOutputSchema,
  PROGRAM_BUILDER_PROMPT_VERSION,
  PROGRAM_BUILDER_SYSTEM_PROMPT,
} from "./programBuilderPrompt";

function ctxFor(days = 4) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return parseGenerationContext(
    JSON.stringify({
      profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, weekdays, trainingDaysPerWeek: days },
      specialization: { Chest: "Specialize" },
      provenLifts: [
        { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8 } },
        { id: "row", name: "Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
      ],
      availableLifts: [],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
      ],
    }),
  );
}

describe("programBuilderPrompt", () => {
  it("system prompt is version 4 and instructs target-driven whole-program building + session caps", () => {
    assert.equal(PROGRAM_BUILDER_PROMPT_VERSION, 4);
    assert.ok(/HIT THE MUSCLE BUDGETS/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/targetExerciseCount/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/suggestedPatterns are COVERAGE HINTS/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/VARY lift selection across sibling days/.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/Never reuse the same liftId more than once within a single workout/.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/interleave antagonists/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/repBand/.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
    assert.ok(/No prose, no tools/.test(PROGRAM_BUILDER_SYSTEM_PROMPT));
  });

  it("system prompt instructs compound-before-accessory ordering with a specialization exception", () => {
    // The nuanced rule: compounds first by default, but pre-exhaust/specialization is allowed.
    assert.ok(/isCompound: true/.test(PROGRAM_BUILDER_SYSTEM_PROMPT), "prompt should reference the isCompound signal");
    assert.ok(/compound.*before|compounds?.*before/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT), "prompt should instruct compound-first ordering");
    assert.ok(/specializ|pre-exhaust|pre-exhaustion/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT), "prompt should allow the specialization exception");
    assert.ok(/deadlift.*last|squat.*last/i.test(PROGRAM_BUILDER_SYSTEM_PROMPT), "prompt should explicitly forbid deadlift/squat last");
  });

  it("buildProgramBuilderInput contains all days + a global candidates list + constraints, no landmarks/responseProfile/profile", () => {
    const ctx = ctxFor(4);
    const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
    const globalCandidates = buildGlobalCandidates(plans, ctx);
    const globalRepBands = repBandsForLifts(globalCandidates.map((c) => c.liftId), ctx);
    const input = buildProgramBuilderInput(plans, globalCandidates, globalRepBands, "Double");
    const inputJson = JSON.stringify(input);

    // Has the essentials for a whole-program call.
    assert.ok(Array.isArray((input as { days: unknown[] }).days));
    assert.equal((input as { days: unknown[] }).days.length, 4, "should include all 4 day plans");
    assert.equal((input as { constraints: { varyAcrossSiblingDays: boolean } }).constraints.varyAcrossSiblingDays, true);
    assert.equal((input as { constraints: { noDuplicateLiftWithinWorkout: boolean } }).constraints.noDuplicateLiftWithinWorkout, true);
    assert.equal((input as { constraints: { compoundBeforeAccessory: boolean } }).constraints.compoundBeforeAccessory, true);
    assert.equal((input as { constraints: { hitMuscleBudgets: boolean } }).constraints.hitMuscleBudgets, true);
    assert.ok(/bench/.test(inputJson), "candidate liftId should appear");

    // Each day carries muscle budgets (the hard constraint) + suggested patterns (advisory).
    const firstDay = (input as { days: Array<{ muscleBudgets: unknown[]; suggestedPatterns: unknown[]; targetExerciseCount: number }> }).days[0];
    assert.ok(firstDay.muscleBudgets.length > 0, "day should carry muscle budgets");
    assert.ok(firstDay.suggestedPatterns.length > 0, "day should carry suggested patterns");
    assert.equal(firstDay.targetExerciseCount, 8, "4-day upper day should cap at 8 exercises");

    // Candidates live at the root (a single global pool), not nested per-day.
    const candidates = (input as { candidates: Array<{ liftId: string; isCompound: boolean }> }).candidates;
    assert.ok(Array.isArray(candidates) && candidates.length > 0, "global candidates should be a non-empty root array");
    assert.ok(candidates.every((c) => typeof c.isCompound === "boolean"), "every candidate should carry an isCompound boolean");
    const bench = candidates.find((c) => c.liftId === "bench");
    assert.ok(bench, "bench should be a candidate");
    assert.equal(bench!.isCompound, true, "bench (HorizontalPress) should be isCompound: true");

    // Does NOT have the bloated v1 fields.
    assert.ok(!/literatureVolumeLandmarks/.test(inputJson), "landmarks leaked into builder input");
    assert.ok(!/responseProfile/.test(inputJson), "responseProfile leaked into builder input");
    assert.ok(!/"profile"/.test(inputJson), "profile object leaked into builder input");
    assert.ok(!/volumeBaselines/.test(inputJson), "volumeBaselines leaked into builder input");
    assert.ok(!/specialization/.test(inputJson), "specialization map leaked into builder input");
  });

  it("candidates carry a denormalized repBand and progressionScheme", () => {
    const ctx = ctxFor(4);
    const plans = allocateDayPlans(ctx, computeAllWeeklyTargets(ctx));
    const globalCandidates = buildGlobalCandidates(plans, ctx);
    const globalRepBands = repBandsForLifts(globalCandidates.map((c) => c.liftId), ctx);
    const input = buildProgramBuilderInput(plans, globalCandidates, globalRepBands, "Double");
    const candidates = (input as { candidates: Array<{ repBand: { repMin: number; repMax: number }; progressionScheme: string }> }).candidates;
    assert.ok(candidates.length > 0);
    const firstCandidate = candidates[0];
    assert.ok(firstCandidate.repBand.repMin > 0);
    assert.ok(firstCandidate.repBand.repMax > 0);
    assert.equal(firstCandidate.progressionScheme, "Double");
  });

  it("programBuilderOutputSchema reuses the strict program_generation schema", () => {
    const schema = programBuilderOutputSchema();
    assert.equal(schema.name, "program_generation");
    assert.equal(schema.strict, true);
    // Whole-program shape: top-level "program" wrapper with a workouts array.
    assert.ok(schema.schema.properties.program !== undefined);
    assert.equal(
      schema.schema.properties.program.required.includes("workouts"),
      true,
    );
  });

  it("buildProgramBuilderRetryMessage asks for in-chat tweaks with listed issues", () => {
    const message = buildProgramBuilderRetryMessage(
      [
        { code: "WEEKLY_VOLUME_BELOW_TARGET", severity: "error", message: "Chest low" },
        { code: "PUSH_PULL_IMBALANCE", severity: "warning", message: "Push heavy" },
      ],
      4,
    );
    assert.match(message, /few issues with the program/i);
    assert.match(message, /targeted tweaks/i);
    assert.match(message, /\[ERROR WEEKLY_VOLUME_BELOW_TARGET\]/);
    assert.match(message, /\[WARN PUSH_PULL_IMBALANCE\]/);
    assert.match(message, /4 workouts/);
  });
});

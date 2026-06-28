import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type OpenAI from "openai";
import { parseGenerationContext, toNormalizationRequest } from "./programPayloadContext";
import { generateProgramPipeline, type ChatCompletionLike, type PipelineProgressCallback } from "./generateProgramPipeline";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8 } },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPush", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "inc-press", name: "Incline Press", movementPattern: "InclinePush", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep", "PosteriorDeltoid"] },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "pullup", name: "Pullup", movementPattern: "VerticalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "LegPush", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "leg-ext", name: "Leg Extension", movementPattern: "QuadIso", primaryMuscles: ["Quad"], secondaryMuscles: [] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute", "LowerBack"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "leg-curl", name: "Leg Curl", movementPattern: "HamstringIso", primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  { id: "hip-thrust", name: "Hip Thrust", movementPattern: "GluteIso", primaryMuscles: ["Glute"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "Calves", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "seated-calf", name: "Seated Calf Raise", movementPattern: "Calves", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "DeltIso", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "rear-delt-fly", name: "Rear Delt Fly", movementPattern: "DeltIso", primaryMuscles: ["PosteriorDeltoid"], secondaryMuscles: [] },
  { id: "ohp", name: "Overhead Press", movementPattern: "VerticalPush", primaryMuscles: ["AnteriorDeltoid"], secondaryMuscles: ["Tricep"] },
  { id: "curl", name: "Curl", movementPattern: "BicepIso", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "TricepIso", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
  { id: "crunch", name: "Crunch", movementPattern: "AbIso", primaryMuscles: ["Ab"], secondaryMuscles: [] },
];

const LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Glute", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedmrv: 14, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "PosteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "AnteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Ab", estimatedMev: 2, estimatedMav: 6, estimatedMrv: 12, estimatedPv: 4 },
];

function payloadFor(days: number) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return JSON.stringify({
    profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, weekdays, trainingDaysPerWeek: days },
    specialization: { Chest: "Specialize" },
    provenLifts: LIFT_CATALOG,
    availableLifts: [],
    literatureVolumeLandmarks: LANDMARKS,
  });
}

function normalizationRequestFor(payload: string) {
  return toNormalizationRequest(parseGenerationContext(payload));
}

/** Standard 3-set spec. */
function setSpec(): unknown[] {
  return [
    { position: 0, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: true },
    { position: 1, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: false },
    { position: 2, setType: "Standard", repMin: 6, repMax: 8, rirGoal: 2, isTopSet: false },
  ];
}

/**
 * Fake client that routes by response_format schema name: "split_planner" → split-planner responses,
 * "program_generation" → whole-program builder responses. Each role has its own response queue.
 */
function fakeMultiPhaseClient(opts: {
  splitPlannerResponses?: Array<string | null>;
  builderResponses?: Array<string | null>;
}): { client: ChatCompletionLike; builderCallCount: () => number; splitPlannerCallCount: () => number } {
  let builderCalls = 0;
  let splitPlannerCalls = 0;
  const splitPlannerQueue = [...(opts.splitPlannerResponses ?? [])];
  const builderQueue = [...(opts.builderResponses ?? [])];

  const client: ChatCompletionLike = {
    chat: {
      completions: {
        create: async (params: OpenAI.Chat.Completions.ChatCompletionCreateParamsNonStreaming) => {
          const schemaName =
            (params.response_format as { json_schema?: { name?: string } } | undefined)?.json_schema?.name;
          if (schemaName === "split_planner") {
            splitPlannerCalls++;
            const content = splitPlannerQueue.shift() ?? null;
            return { choices: [{ message: { content } }] } as OpenAI.Chat.Completions.ChatCompletion;
          }
          // program_generation (or any other) → whole-program builder response.
          builderCalls++;
          const content = builderQueue.shift() ?? null;
          return { choices: [{ message: { content } }] } as OpenAI.Chat.Completions.ChatCompletion;
        },
      },
    },
  };
  return { client, builderCallCount: () => builderCalls, splitPlannerCallCount: () => splitPlannerCalls };
}

/** A valid whole-program builder response where sibling days deliberately vary their lifts. */
function validProgramJson(): string {
  return JSON.stringify({
    program: {
      name: "Upper/Lower",
      workouts: [
        { name: "Upper A", weekday: "Monday", position: 0, workoutLifts: [
          { liftId: "bench", position: 0, workoutLiftSets: setSpec() },
          { liftId: "row", position: 1, workoutLiftSets: setSpec() },
        ] },
        { name: "Lower A", weekday: "Tuesday", position: 1, workoutLifts: [
          { liftId: "squat", position: 0, workoutLiftSets: setSpec() },
          { liftId: "rdl", position: 1, workoutLiftSets: setSpec() },
        ] },
        { name: "Upper B", weekday: "Thursday", position: 2, workoutLifts: [
          { liftId: "db-press", position: 0, workoutLiftSets: setSpec() },
          { liftId: "seated-row", position: 1, workoutLiftSets: setSpec() },
        ] },
        { name: "Lower B", weekday: "Friday", position: 3, workoutLifts: [
          { liftId: "front-squat", position: 0, workoutLiftSets: setSpec() },
          { liftId: "gm", position: 1, workoutLiftSets: setSpec() },
        ] },
      ],
    },
  });
}

function validSplitPlannerJson(days: number): string {
  const dayThemes = ["Upper A", "Lower A", "Arms + Chest touch", "Lower B", "Upper B"].slice(0, days);
  return JSON.stringify({
    days: dayThemes.map((theme, i) => ({
      weekday: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"][i],
      theme,
      muscleBudgets: [{ muscle: "Chest", sets: 8, min: 6, aim: 8 }],
      patternSlots: [{ pattern: "HorizontalPush", muscleFocus: "Chest", minSets: 3, maxSets: 4 }],
    })),
  });
}

/** Collects liftIds per workout name from a normalized program. */
function liftSetsByWorkoutName(program: unknown): Map<string, Set<string>> {
  const workouts = (program as { workouts?: Array<{ name?: string; workoutLifts?: Array<{ liftId?: string }> }> }).workouts ?? [];
  return new Map(workouts.map((w) => [w.name ?? "", new Set((w.workoutLifts ?? []).map((l) => l.liftId ?? ""))]));
}

describe("generateProgramPipeline", () => {
  it("4-day deterministic happy path: no LLM split planner, ONE builder call, progress phases in order", async () => {
    const payload = payloadFor(4);
    const { client, builderCallCount, splitPlannerCallCount } = fakeMultiPhaseClient({
      builderResponses: [validProgramJson()],
    });
    const phases: string[] = [];
    const progress: PipelineProgressCallback = async (phase) => { phases.push(phase); };

    const result = await generateProgramPipeline(
      client as never,
      "deepseek/deepseek-v4-pro",
      payload,
      "stratum-program-generate:test-uid",
      normalizationRequestFor(payload),
      { progress, skipFinalValidation: true },
    ) as { name: string; workouts: unknown[] };

    assert.equal(splitPlannerCallCount(), 0, "4-day should not invoke LLM split planner");
    assert.equal(builderCallCount(), 1, "whole-program path makes a single builder call");
    assert.equal(phases[0], "split_planning");
    assert.equal(phases[1], "building_workouts");
    assert.equal(phases[2], "integrating");
    assert.ok((result.workouts as unknown[]).length === 4);
  });

  it("sibling days differ in their lift sets when the builder varies them", async () => {
    const payload = payloadFor(4);
    const { client } = fakeMultiPhaseClient({ builderResponses: [validProgramJson()] });
    const result = await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    );
    const byName = liftSetsByWorkoutName(result);
    const upperA = byName.get("Upper A");
    const upperB = byName.get("Upper B");
    assert.ok(upperA && upperB);
    // Siblings must not be identical lift sets (A differs from B in at least one lift).
    assert.ok(
      [...upperA].some((id) => !upperB.has(id)) || [...upperB].some((id) => !upperA.has(id)),
      `Upper A ${[...upperA]} must differ from Upper B ${[...upperB]}`,
    );
    const lowerA = byName.get("Lower A");
    const lowerB = byName.get("Lower B");
    assert.ok(lowerA && lowerB);
    assert.ok(
      [...lowerA].some((id) => !lowerB.has(id)) || [...lowerB].some((id) => !lowerA.has(id)),
      `Lower A ${[...lowerA]} must differ from Lower B ${[...lowerB]}`,
    );
  });

  it("no duplicate liftId appears within any workout even if the builder repeats one", async () => {
    const duped = JSON.stringify({
      program: {
        name: "Upper/Lower",
        workouts: [
          { name: "Upper A", weekday: "Monday", position: 0, workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: setSpec() },
            { liftId: "bench", position: 1, workoutLiftSets: setSpec() },
            { liftId: "row", position: 2, workoutLiftSets: setSpec() },
          ] },
          { name: "Lower A", weekday: "Tuesday", position: 1, workoutLifts: [
            { liftId: "squat", position: 0, workoutLiftSets: setSpec() },
            { liftId: "rdl", position: 1, workoutLiftSets: setSpec() },
          ] },
          { name: "Upper B", weekday: "Thursday", position: 2, workoutLifts: [
            { liftId: "row", position: 0, workoutLiftSets: setSpec() },
            { liftId: "lat-raise", position: 1, workoutLiftSets: setSpec() },
          ] },
          { name: "Lower B", weekday: "Friday", position: 3, workoutLifts: [
            { liftId: "rdl", position: 0, workoutLiftSets: setSpec() },
            { liftId: "calf-raise", position: 1, workoutLiftSets: setSpec() },
          ] },
        ],
      },
    });
    const payload = payloadFor(4);
    const { client } = fakeMultiPhaseClient({ builderResponses: [duped] });
    const result = await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    );
    const workouts = (result as { workouts: Array<{ name?: string; workoutLifts?: Array<{ liftId?: string }> }> }).workouts;
    for (const workout of workouts) {
      const ids = (workout.workoutLifts ?? []).map((l) => l.liftId ?? "");
      assert.equal(new Set(ids).size, ids.length, `workout "${workout.name}" has duplicate liftIds: ${JSON.stringify(ids)}`);
    }
  });

  it("5-day invokes the LLM split planner, then one whole-program builder call", async () => {
    const payload = payloadFor(5);
    const { client, splitPlannerCallCount, builderCallCount } = fakeMultiPhaseClient({
      splitPlannerResponses: [validSplitPlannerJson(5)],
      builderResponses: [validProgramJson()],
    });

    await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    );

    assert.ok(splitPlannerCallCount() >= 1, "5-day should call the split planner");
    assert.equal(builderCallCount(), 1, "whole-program path makes a single builder call regardless of day count");
  });

  it("the whole-program builder returns invalid JSON → deterministic fallback fills every day", async () => {
    const payload = payloadFor(4);
    const { client } = fakeMultiPhaseClient({ builderResponses: ["not json"] });

    const result = await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    ) as { workouts: Array<{ workoutLifts?: unknown[] }> };

    assert.ok(result.workouts.length === 4);
    for (const workout of result.workouts) {
      assert.ok((workout.workoutLifts?.length ?? 0) > 0, "failed builder call should yield non-empty fallback workouts");
    }
  });

  it("the whole-program builder returns null → full deterministic fallback produces a valid program", async () => {
    const payload = payloadFor(4);
    const { client } = fakeMultiPhaseClient({ builderResponses: [null] });

    const result = await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    ) as { workouts: Array<{ workoutLifts?: unknown[] }> };

    assert.ok(result.workouts.length === 4);
    for (const workout of result.workouts) {
      assert.ok((workout.workoutLifts?.length ?? 0) > 0, "fallback workout should be non-empty");
    }
  });

  it("split planner returns invalid JSON → allocator fallback kicks in, then builder runs", async () => {
    const payload = payloadFor(5);
    const { client, splitPlannerCallCount } = fakeMultiPhaseClient({
      splitPlannerResponses: ["not json", "still not json"],
      builderResponses: [validProgramJson()],
    });

    const result = await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true },
    ) as { workouts: unknown[] };

    assert.ok(splitPlannerCallCount() >= 1);
    assert.ok((result.workouts as unknown[]).length > 0);
  });

  it("skipSplitPlanner forces the deterministic allocator even for 6 days", async () => {
    const payload = payloadFor(6);
    const { client, splitPlannerCallCount } = fakeMultiPhaseClient({
      builderResponses: [validProgramJson()],
    });

    await generateProgramPipeline(
      client as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipSplitPlanner: true, skipFinalValidation: true },
    );

    assert.equal(splitPlannerCallCount(), 0, "skipSplitPlanner should suppress the LLM planner");
  });
});

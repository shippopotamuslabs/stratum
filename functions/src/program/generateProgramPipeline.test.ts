import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext, toNormalizationRequest } from "./programPayloadContext";
import { generateProgramPipeline, type ChatCompletionLike, type PipelineProgressCallback } from "./generateProgramPipeline";

const LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"], trainingInsights: { sessionCount: 12, progressTrend: "Improving", fatigueDecaySlope: -0.8 } },
  { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
  { id: "inc-press", name: "Incline Press", movementPattern: "InclinePress", primaryMuscles: ["Chest"], secondaryMuscles: ["AnteriorDeltoid"] },
  { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep", "PosteriorDeltoid"] },
  { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "pullup", name: "Pullup", movementPattern: "VerticalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
  { id: "squat", name: "Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "front-squat", name: "Front Squat", movementPattern: "SquatPattern", primaryMuscles: ["Quad"], secondaryMuscles: ["Glute"] },
  { id: "leg-ext", name: "Leg Extension", movementPattern: "KneeExtension", primaryMuscles: ["Quad"], secondaryMuscles: [] },
  { id: "rdl", name: "RDL", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["Glute", "LowerBack"] },
  { id: "gm", name: "Good Morning", movementPattern: "HipHinge", primaryMuscles: ["Hamstring"], secondaryMuscles: ["LowerBack"] },
  { id: "leg-curl", name: "Leg Curl", movementPattern: "KneeFlexion", primaryMuscles: ["Hamstring"], secondaryMuscles: [] },
  { id: "calf-raise", name: "Calf Raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "seated-calf", name: "Seated Calf Raise", movementPattern: "PlantarFlexion", primaryMuscles: ["Calf"], secondaryMuscles: [] },
  { id: "lat-raise", name: "Lateral Raise", movementPattern: "ShoulderAbduction", primaryMuscles: ["LateralDeltoid"], secondaryMuscles: [] },
  { id: "rear-delt-fly", name: "Rear Delt Fly", movementPattern: "ShoulderHorizontalAbduction", primaryMuscles: ["PosteriorDeltoid"], secondaryMuscles: [] },
  { id: "ohp", name: "Overhead Press", movementPattern: "VerticalPress", primaryMuscles: ["AnteriorDeltoid"], secondaryMuscles: ["Tricep"] },
  { id: "curl", name: "Curl", movementPattern: "ElbowFlexion", primaryMuscles: ["Bicep"], secondaryMuscles: [] },
  { id: "tricep-ext", name: "Tricep Extension", movementPattern: "ElbowExtension", primaryMuscles: ["Tricep"], secondaryMuscles: [] },
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
];

function payloadFor(days: number) {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"].slice(0, days).join(",");
  return JSON.stringify({
    profile: { trainingAge: "Intermediate", sessionLengthMinutes: 75, weekdays, trainingDaysPerWeek: days },
    specialization: { Bicep: "Specialize", Tricep: "Specialize" },
    provenLifts: LIFT_CATALOG,
    availableLifts: [],
    literatureVolumeLandmarks: LANDMARKS,
  });
}

function normalizationRequestFor(payload: string) {
  return toNormalizationRequest(parseGenerationContext(payload));
}

function noopClient(): ChatCompletionLike {
  return {
    chat: {
      completions: {
        create: async () => ({ choices: [{ message: { content: '{"swaps":[]}' } }] }) as never,
      },
    },
  };
}

function liftSetsByWorkoutName(program: unknown): Map<string, Set<string>> {
  const workouts = (program as { workouts?: Array<{ name?: string; workoutLifts?: Array<{ liftId?: string }> }> }).workouts ?? [];
  return new Map(workouts.map((w) => [w.name ?? "", new Set((w.workoutLifts ?? []).map((l) => l.liftId ?? ""))]));
}

describe("generateProgramPipeline", () => {
  it("4-day deterministic path: cycleLength matches workouts, no calendar weekdays", async () => {
    const payload = payloadFor(4);
    const phases: string[] = [];
    const progress: PipelineProgressCallback = async (phase) => { phases.push(phase); };

    const result = await generateProgramPipeline(
      noopClient() as never,
      "deepseek/deepseek-v4-pro",
      payload,
      "stratum-program-generate:test-uid",
      normalizationRequestFor(payload),
      { progress, skipFinalValidation: true, skipLiftSwap: true },
    ) as { name: string; cycleLength: number; workouts: Array<{ weekday: unknown; workoutLifts: unknown[] }> };

    assert.equal(phases[0], "split_planning");
    assert.equal(phases[1], "building_workouts");
    assert.equal(phases[2], "integrating");
    assert.equal(result.cycleLength, 4);
    assert.equal(result.workouts.length, 4);
    for (const w of result.workouts) {
      assert.equal(w.weekday, null);
      assert.ok((w.workoutLifts?.length ?? 0) > 0);
    }
  });

  it("sibling upper days rotate lift selection", async () => {
    const payload = payloadFor(4);
    const result = await generateProgramPipeline(
      noopClient() as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true, skipLiftSwap: true },
    );
    const byName = liftSetsByWorkoutName(result);
    const upperA = byName.get("Upper A");
    const upperB = byName.get("Upper B");
    assert.ok(upperA && upperB);
    assert.ok(
      [...upperA].some((id) => !upperB.has(id)) || [...upperB].some((id) => !upperA.has(id)),
    );
  });

  it("no duplicate liftId within any workout", async () => {
    const payload = payloadFor(4);
    const result = await generateProgramPipeline(
      noopClient() as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true, skipLiftSwap: true },
    );
    const workouts = (result as { workouts: Array<{ name?: string; workoutLifts?: Array<{ liftId?: string }> }> }).workouts;
    for (const workout of workouts) {
      const ids = (workout.workoutLifts ?? []).map((l) => l.liftId ?? "");
      assert.equal(new Set(ids).size, ids.length, `workout "${workout.name}" has duplicate liftIds`);
    }
  });

  it("5-day and 6-day splits produce full cycles", async () => {
    for (const days of [5, 6]) {
      const payload = payloadFor(days);
      const result = await generateProgramPipeline(
        noopClient() as never, "model", payload, "sid", normalizationRequestFor(payload),
        { skipFinalValidation: true, skipLiftSwap: true },
      ) as { cycleLength: number; workouts: unknown[] };
      assert.equal(result.cycleLength, days);
      assert.equal(result.workouts.length, days);
    }
  });

  it("lower days have at most one hip hinge", async () => {
    const payload = payloadFor(4);
    const result = await generateProgramPipeline(
      noopClient() as never, "model", payload, "sid", normalizationRequestFor(payload),
      { skipFinalValidation: true, skipLiftSwap: true },
    );
    const workouts = (result as { workouts: Array<{ name?: string; workoutLifts?: Array<{ liftId?: string }> }> }).workouts;
    const hingeIds = new Set(["rdl", "gm"]);
    for (const w of workouts.filter((x) => (x.name ?? "").toLowerCase().includes("lower"))) {
      const hinges = (w.workoutLifts ?? []).filter((l) => hingeIds.has(l.liftId ?? ""));
      assert.ok(hinges.length <= 1, `${w.name} has ${hinges.length} hip hinges`);
    }
  });
});

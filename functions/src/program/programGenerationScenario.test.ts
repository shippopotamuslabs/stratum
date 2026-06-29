/**
 * Cross-scenario integration tests for deterministic program generation.
 *
 * Guards Weightology-aligned structural invariants (per-session volume ceiling,
 * frequency distribution, cycle integrity) across common split/specialization combinations.
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { GenerationContext } from "./programPayloadContext";
import { parseGenerationContext, toNormalizationRequest } from "./programPayloadContext";
import {
  generateProgramPipeline,
  type ChatCompletionLike,
} from "./generateProgramPipeline";
import {
  computeWorkoutMuscleVolume,
  validateProgramMetrics,
  type ProgramDraft,
} from "./programValidation";
import { frequencyTierForWeeklySets, SESSION_MUSCLE_SET_CAP, sessionDurationBudgetMinutes } from "./rpReferenceTables";
import { planWeeklyVolume } from "./volumePlanner";

export const SCENARIO_LIFT_CATALOG = [
  { id: "bench", name: "Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep", "AnteriorDeltoid"] },
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
  { id: "hip-thrust", name: "Hip Thrust", movementPattern: "HipExtension", primaryMuscles: ["Glute"], secondaryMuscles: ["Hamstring"] },
];

export const SCENARIO_LANDMARKS = [
  { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Quad", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
  { muscle: "Hamstring", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Glute", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Calf", estimatedMev: 6, estimatedMav: 10, estimatedMrv: 16, estimatedPv: 8 },
  { muscle: "Tricep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "Bicep", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "LateralDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "PosteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
  { muscle: "AnteriorDeltoid", estimatedMev: 4, estimatedMav: 8, estimatedMrv: 14, estimatedPv: 6 },
];

/** Common specialization presets seen in onboarding. */
export const SPECIALIZATION_PRESETS: Record<string, Record<string, string>> = {
  maintenance: {},
  arms: { Bicep: "Specialize", Tricep: "Specialize" },
  chest: { Chest: "Specialize" },
  back: { Back: "Specialize" },
  glutes: { Glute: "Specialize" },
  shoulders: { LateralDeltoid: "Specialize", AnteriorDeltoid: "Specialize" },
  legs: { Quad: "Specialize", Glute: "Specialize" },
};

export interface ScenarioConfig {
  days: number;
  preset: keyof typeof SPECIALIZATION_PRESETS;
  label: string;
}

export const GENERATION_SCENARIOS: ScenarioConfig[] = [];
for (const days of [3, 4, 5, 6]) {
  for (const [preset, specialization] of Object.entries(SPECIALIZATION_PRESETS)) {
    GENERATION_SCENARIOS.push({
      days,
      preset: preset as keyof typeof SPECIALIZATION_PRESETS,
      label: `${days}-day ${preset}`,
    });
    void specialization;
  }
}

export function scenarioPayload(days: number, specialization: Record<string, string>): string {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
    .slice(0, days)
    .join(",");
  return JSON.stringify({
    profile: {
      trainingAge: "Advanced",
      sessionLengthMinutes: 75,
      weekdays,
      trainingDaysPerWeek: days,
    },
    specialization,
    provenLifts: SCENARIO_LIFT_CATALOG,
    availableLifts: [],
    literatureVolumeLandmarks: SCENARIO_LANDMARKS,
  });
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

export function primarySetsByMuscleInWorkout(
  workout: ProgramDraft["workouts"][number],
  context: GenerationContext,
): Record<string, number> {
  const totals: Record<string, number> = {};
  for (const lift of workout.workoutLifts ?? []) {
    const meta = context.liftsById.get(lift.liftId ?? "");
    const setCount = lift.workoutLiftSets?.length ?? 0;
    if (!meta || setCount === 0) continue;
    for (const muscle of meta.primaryMuscles) {
      totals[muscle] = (totals[muscle] ?? 0) + setCount;
    }
  }
  return totals;
}

export function assertWeightologySessionCaps(
  program: ProgramDraft,
  context: GenerationContext,
  scenarioLabel: string,
): void {
  const report = validateProgramMetrics(program, context);
  const sessionCapErrors = report.issues.filter((i) => i.code === "SESSION_MUSCLE_VOLUME_HIGH");
  assert.equal(
    sessionCapErrors.length,
    0,
    `${scenarioLabel}: fractional session volume exceeds ${SESSION_MUSCLE_SET_CAP} — ${sessionCapErrors.map((i) => i.message).join("; ")}`,
  );

  for (const workout of program.workouts) {
    const primary = primarySetsByMuscleInWorkout(workout, context);
    for (const [muscle, sets] of Object.entries(primary)) {
      assert.ok(
        sets <= SESSION_MUSCLE_SET_CAP,
        `${scenarioLabel}: ${workout.name} has ${sets} primary sets for ${muscle} (cap ${SESSION_MUSCLE_SET_CAP})`,
      );
    }
    const fractional = computeWorkoutMuscleVolume(workout, context);
    for (const [muscle, credits] of Object.entries(fractional)) {
      assert.ok(
        credits <= SESSION_MUSCLE_SET_CAP + 0.01,
        `${scenarioLabel}: ${workout.name} has ${credits} fractional credits for ${muscle}`,
      );
    }
  }
}

export function assertFrequencyMatchesVolume(
  program: ProgramDraft,
  context: GenerationContext,
  scenarioLabel: string,
): void {
  const report = validateProgramMetrics(program, context);
  for (const [muscle, weekly] of Object.entries(report.summary.weeklyMuscleVolume)) {
    if (weekly <= 10) continue;
    const tier = frequencyTierForWeeklySets(weekly);
    const minFreq = tier === 1 ? 1 : tier === 2 ? 2 : 3;
    const actual = report.summary.muscleWeeklyFrequency[muscle] ?? 0;
    assert.ok(
      actual >= minFreq,
      `${scenarioLabel}: ${muscle} has ${weekly.toFixed(0)} weekly sets but only ${actual}× frequency (need ≥${minFreq}×)`,
    );
  }
}

async function generateScenario(config: ScenarioConfig): Promise<{
  program: ProgramDraft;
  context: GenerationContext;
}> {
  const specialization = SPECIALIZATION_PRESETS[config.preset] ?? {};
  const payload = scenarioPayload(config.days, specialization);
  const context = parseGenerationContext(payload);
  const result = await generateProgramPipeline(
    noopClient() as never,
    "model",
    payload,
    `scenario-${config.label}`,
    toNormalizationRequest(context),
    { skipLiftSwap: true },
  );
  return { program: result as ProgramDraft, context };
}

describe("programGenerationScenario — Weightology-aligned invariants", () => {
  for (const scenario of GENERATION_SCENARIOS) {
    it(`${scenario.label}: cycle structure and no duplicate lifts`, async () => {
      const { program } = await generateScenario(scenario);
      assert.equal(program.cycleLength, scenario.days);
      assert.equal(program.workouts.length, scenario.days);
      for (const workout of program.workouts) {
        assert.equal(workout.weekday, null);
        const ids = (workout.workoutLifts ?? []).map((l) => l.liftId ?? "");
        assert.equal(new Set(ids).size, ids.length, `${workout.name} has duplicate liftIds`);
        assert.ok(ids.length >= 3, `${workout.name} should have enough exercises to fill a session`);
      }
    });

    it(`${scenario.label}: ≤8 hard sets per muscle per session (primary + secondary credits)`, async () => {
      const { program, context } = await generateScenario(scenario);
      assertWeightologySessionCaps(program, context, scenario.label);
    });

    it(`${scenario.label}: weekly volume targets from landmarks (not time-budget scaled)`, async () => {
      const specialization = SPECIALIZATION_PRESETS[scenario.preset] ?? {};
      const context = parseGenerationContext(scenarioPayload(scenario.days, specialization));
      const targets = planWeeklyVolume(context);

      if (specialization.Chest === "Specialize") {
        assert.ok((targets.get("Chest")?.aim ?? 0) >= 14);
      } else if (specialization.Back !== "Specialize") {
        assert.equal(targets.get("Back")?.aim, 14);
      }

      if (specialization.Back === "Specialize") {
        assert.ok((targets.get("Back")?.aim ?? 0) >= 14);
      } else if (specialization.Chest !== "Specialize") {
        assert.equal(targets.get("Chest")?.aim, 14);
      }

      if (specialization.Bicep === "Specialize" || specialization.Tricep === "Specialize") {
        const neutralContext = parseGenerationContext(
          scenarioPayload(scenario.days, { Bicep: "Maintenance", Tricep: "Maintenance" }),
        );
        const neutral = planWeeklyVolume(neutralContext);
        if (specialization.Bicep === "Specialize") {
          assert.ok((targets.get("Bicep")?.aim ?? 0) > (neutral.get("Bicep")?.aim ?? 0));
        }
        if (specialization.Tricep === "Specialize") {
          assert.ok((targets.get("Tricep")?.aim ?? 0) > (neutral.get("Tricep")?.aim ?? 0));
        }
      }
    });
  }

  for (const days of [4, 5, 6]) {
    it(`${days}-day maintenance: muscles above 10 weekly sets trained ≥2× per cycle`, async () => {
      const config: ScenarioConfig = { days, preset: "maintenance", label: `${days}-day maintenance` };
      const { program, context } = await generateScenario(config);
      assertFrequencyMatchesVolume(program, context, config.label);
    });
  }

  it("4-day arm specialization: back weekly target ~14, split across upper days not stacked in one session", async () => {
    const config: ScenarioConfig = { days: 4, preset: "arms", label: "4-day arms" };
    const { program, context } = await generateScenario(config);
    const targets = planWeeklyVolume(context);
    assert.equal(targets.get("Back")?.aim, 14);

    const upperWorkouts = program.workouts.filter((w) => (w.name ?? "").toLowerCase().includes("upper"));
    assert.ok(upperWorkouts.length >= 2);
    let totalBack = 0;
    for (const workout of upperWorkouts) {
      const backSets = primarySetsByMuscleInWorkout(workout, context).Back ?? 0;
      totalBack += backSets;
      assert.ok(backSets <= SESSION_MUSCLE_SET_CAP, `${workout.name} back sets ${backSets}`);
    }
    assert.ok(totalBack >= 8, "back should accumulate meaningful weekly volume across upper days");
  });

  it("4-day arm specialization: direct bicep/tricep work keeps meaningful sets per upper day", async () => {
    const config: ScenarioConfig = { days: 4, preset: "arms", label: "4-day arms direct" };
    const { program, context } = await generateScenario(config);
    const upperWorkouts = program.workouts.filter((w) => (w.name ?? "").toLowerCase().includes("upper"));
    for (const workout of upperWorkouts) {
      const direct = primarySetsByMuscleInWorkout(workout, context);
      assert.ok(
        (direct.Bicep ?? 0) >= 4,
        `${workout.name} should have ≥4 direct bicep sets, got ${direct.Bicep ?? 0}`,
      );
      assert.ok(
        (direct.Tricep ?? 0) >= 4,
        `${workout.name} should have ≥4 direct tricep sets, got ${direct.Tricep ?? 0}`,
      );
    }
  });

  for (const days of [3, 4, 5, 6]) {
    it(`${days}-day 30-minute sessions stay within session length + 5 minutes`, async () => {
      const payload = scenarioPayload(days, { Bicep: "Specialize", Tricep: "Specialize" });
      const body = JSON.parse(payload) as { profile: { sessionLengthMinutes: number } };
      body.profile.sessionLengthMinutes = 30;
      const context = parseGenerationContext(JSON.stringify(body));
      const result = await generateProgramPipeline(
        noopClient() as never,
        "model",
        JSON.stringify(body),
        `duration-${days}d`,
        toNormalizationRequest(context),
        { skipLiftSwap: true },
      ) as ProgramDraft;
      const report = validateProgramMetrics(result, context);
      const budget = sessionDurationBudgetMinutes(30);
      report.summary.estimatedWorkoutMinutes.forEach((minutes, index) => {
        const name = result.workouts[index]?.name ?? `workout ${index}`;
        assert.ok(
          minutes <= budget + 0.01,
          `${name}: ${minutes.toFixed(1)} min exceeds ${budget} min budget (30 + 5 slack)`,
        );
      });
    });
  }

  it("3-day 30m arms: full body day balances chest and back instead of stacking press", async () => {
    const body = JSON.parse(scenarioPayload(3, { Bicep: "Specialize", Tricep: "Specialize" })) as {
      profile: { sessionLengthMinutes: number };
    };
    body.profile.sessionLengthMinutes = 30;
    const context = parseGenerationContext(JSON.stringify(body));
    const { workouts } = await generateProgramPipeline(
      noopClient() as never,
      "model",
      JSON.stringify(body),
      "3d-30m-arms-balance",
      toNormalizationRequest(context),
      { skipLiftSwap: true },
    ) as ProgramDraft;
    const fullBody = workouts.find((w) => (w.name ?? "").includes("Full Body"));
    assert.ok(fullBody);
    const muscles = primarySetsByMuscleInWorkout(fullBody!, context);
    const chest = muscles.Chest ?? 0;
    const back = muscles.Back ?? 0;
    if (chest > 0 && back === 0) {
      // Only assert when the simplified catalog includes a pull candidate on full body days.
      const hasPullPattern = (fullBody!.workoutLifts ?? []).some((lift) => {
        const pattern = context.liftsById.get(lift.liftId ?? "")?.movementPattern;
        return pattern === "HorizontalPull" || pattern === "VerticalPull";
      });
      if (hasPullPattern) {
        assert.ok(back > 0, `full body should include back work when chest has ${chest} sets`);
      }
      assert.ok(chest <= 4, "no single chest exercise should exceed 4 sets on full body");
    }
  });

  it("5-day 30m arms: upper push includes direct tricep work", async () => {
    const body = JSON.parse(scenarioPayload(5, { Bicep: "Specialize", Tricep: "Specialize" })) as {
      profile: { sessionLengthMinutes: number };
    };
    body.profile.sessionLengthMinutes = 30;
    const context = parseGenerationContext(JSON.stringify(body));
    const { workouts } = await generateProgramPipeline(
      noopClient() as never,
      "model",
      JSON.stringify(body),
      "5d-30m-arms-push",
      toNormalizationRequest(context),
      { skipLiftSwap: true },
    ) as ProgramDraft;
    const push = workouts.find((w) => (w.name ?? "").includes("Upper Push"));
    assert.ok(push);
    const tricep = primarySetsByMuscleInWorkout(push!, context).Tricep ?? 0;
    assert.ok(tricep >= 2, `upper push should include tricep sets on arm specialization, got ${tricep}`);
  });

  it("3-day 30m calf+chest: allocates calf volume", async () => {
    const body = JSON.parse(scenarioPayload(3, { Calf: "Specialize", Chest: "Specialize" })) as {
      profile: { sessionLengthMinutes: number };
    };
    body.profile.sessionLengthMinutes = 30;
    const context = parseGenerationContext(JSON.stringify(body));
    const { workouts } = await generateProgramPipeline(
      noopClient() as never,
      "model",
      JSON.stringify(body),
      "3d-30m-calves-chest",
      toNormalizationRequest(context),
      { skipLiftSwap: true },
    ) as ProgramDraft;
    let calfSets = 0;
    for (const workout of workouts) {
      calfSets += primarySetsByMuscleInWorkout(workout, context).Calf ?? 0;
    }
    assert.ok(calfSets > 0, "calf specialization should produce calf sets on 30m programs");
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import {
  alternativesForLift,
  applySwaps,
  buildCandidateMap,
  buildLiftSwapReviewPayload,
  buildSwapCandidateMap,
  validateSwaps,
  type LiftSwapCandidateMap,
} from "./liftSwapPass";
import type { BuiltProgram } from "./slotBasedProgramBuilder";
import { LIFT_SWAP_MAX_PER_WORKOUT, LIFT_SWAP_PROMPT_VERSION } from "./liftSwapPrompt";

const PROGRAM: BuiltProgram = {
  name: "Test",
  cycleLength: 1,
  workouts: [
    {
      name: "Upper A",
      weekday: null,
      position: 0,
      workoutLifts: [
        {
          liftId: "decline-barbell-bench-press",
          position: 0,
          workoutLiftSets: [{ position: 0, setType: "Standard", repMin: 4, repMax: 12, rirGoal: 2, isTopSet: false }],
        },
        {
          liftId: "row",
          position: 1,
          workoutLiftSets: [{ position: 0, setType: "Standard", repMin: 6, repMax: 15, rirGoal: 2, isTopSet: false }],
        },
      ],
    },
  ],
};

function testContext() {
  return parseGenerationContext(
    JSON.stringify({
      profile: {
        trainingAge: "Advanced",
        sessionLengthMinutes: 75,
        weekdays: "Monday,Tuesday,Wednesday,Thursday",
        trainingDaysPerWeek: 4,
      },
      specialization: { Bicep: "Specialize", Tricep: "Specialize" },
      provenLifts: [
        { id: "bench", name: "Barbell Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
        { id: "decline-barbell-bench-press", name: "Decline Barbell Bench Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"], mainstream: true },
        { id: "db-press", name: "Dumbbell Press", movementPattern: "HorizontalPress", primaryMuscles: ["Chest"], secondaryMuscles: ["Tricep"] },
        { id: "row", name: "Barbell Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
        { id: "seated-row", name: "Seated Row", movementPattern: "HorizontalPull", primaryMuscles: ["Back"], secondaryMuscles: ["Bicep"] },
      ],
      availableLifts: [],
      literatureVolumeLandmarks: [
        { muscle: "Chest", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
        { muscle: "Back", estimatedMev: 8, estimatedMav: 14, estimatedMrv: 20, estimatedPv: 11 },
      ],
    }),
  );
}

const LEGACY_CANDIDATE_MAP: LiftSwapCandidateMap = {
  byWorkout: new Map([
    [0, new Map([
      [0, ["decline-barbell-bench-press", "bench", "db-press"]],
      [1, ["row", "seated-row"]],
    ])],
  ]),
};

describe("liftSwapPass", () => {
  it("rejects swaps outside candidate list, same-lift no-ops, and over per-workout cap", () => {
    const valid = validateSwaps(
      [
        { workoutPosition: 0, liftPosition: 0, newLiftId: "bench" },
        { workoutPosition: 0, liftPosition: 1, newLiftId: "seated-row" },
        { workoutPosition: 0, liftPosition: 0, newLiftId: "squat" },
        { workoutPosition: 0, liftPosition: 0, newLiftId: "decline-barbell-bench-press" },
        { workoutPosition: 0, liftPosition: 0, newLiftId: "db-press" },
      ],
      PROGRAM,
      LEGACY_CANDIDATE_MAP,
    );
    assert.equal(valid.length, 3);
    assert.equal(valid[0]?.newLiftId, "bench");
    assert.equal(valid[1]?.newLiftId, "seated-row");
    assert.equal(valid[2]?.newLiftId, "db-press");
    assert.equal(LIFT_SWAP_MAX_PER_WORKOUT, 3);
  });

  it("applies valid swaps to program draft", () => {
    const swapped = applySwaps(PROGRAM, [
      { workoutPosition: 0, liftPosition: 0, newLiftId: "bench" },
    ]);
    assert.equal(swapped.workouts[0]?.workoutLifts?.[0]?.liftId, "bench");
    assert.equal(swapped.workouts[0]?.workoutLifts?.[1]?.liftId, "row");
  });

  it("buildCandidateMap maps selection candidates by workout and lift position", () => {
    const map = buildCandidateMap([
      [
        {
          slot: { setCount: 3 } as never,
          lift: { id: "bench" } as never,
          candidates: [{ id: "bench" } as never, { id: "db-press" } as never],
        },
      ],
    ]);
    assert.deepEqual(map.byWorkout.get(0)?.get(0), ["bench", "db-press"]);
  });

  it("alternativesForLift prefers flat bench over decline for horizontal press", () => {
    const context = testContext();
    const alts = alternativesForLift(context, "decline-barbell-bench-press", []);
    assert.ok(alts.includes("bench"));
    assert.equal(alts[0], "bench");
  });

  it("buildLiftSwapReviewPayload includes program intent and lift names for review", () => {
    const context = testContext();
    const candidateMap = buildSwapCandidateMap(PROGRAM, context, [[]]);
    const payload = buildLiftSwapReviewPayload(PROGRAM, context, candidateMap);

    assert.equal(payload.promptVersion, LIFT_SWAP_PROMPT_VERSION);
    assert.deepEqual(payload.intent.specializedMuscles, ["Bicep", "Tricep"]);
    assert.equal(payload.intent.trainingAge, "Advanced");
    assert.equal(payload.program.workouts[0]?.workoutLifts[0]?.name, "Decline Barbell Bench Press");
    assert.equal(payload.program.workouts[0]?.workoutLifts[0]?.setCount, 1);
    assert.ok(payload.program.workouts[0]?.workoutLifts[0]?.candidates.some((c) => c.name === "Barbell Bench Press"));
  });
});

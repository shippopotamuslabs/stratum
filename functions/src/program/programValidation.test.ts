import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import {
  validateExerciseOrdering,
  validateMuscleFrequency,
  validateNoDuplicateLifts,
  validateProgramMetrics,
  validateWeeklyMuscleVolume,
  validateWorkoutDuration,
  weeklyVolumeToleranceBand,
} from "./programValidation";

const CONTEXT = parseGenerationContext(
  JSON.stringify({
    profile: {
      trainingAge: "Intermediate",
      sessionLengthMinutes: 60,
      weekdays: "Monday,Wednesday,Friday,Saturday",
      trainingDaysPerWeek: 4,
    },
    specialization: { Chest: "Specialize" },
    provenLifts: [
      {
        id: "bench",
        movementPattern: "HorizontalPush",
        primaryMuscles: ["Chest"],
        secondaryMuscles: ["Tricep"],
      },
      {
        id: "row",
        movementPattern: "HorizontalPull",
        primaryMuscles: ["Back"],
        secondaryMuscles: ["Bicep"],
      },
    ],
    availableLifts: [],
    literatureVolumeLandmarks: [
      {
        muscle: "Chest",
        estimatedMev: 8,
        estimatedMav: 14,
        estimatedMrv: 20,
        estimatedPv: 11,
      },
      {
        muscle: "Back",
        estimatedMev: 8,
        estimatedMav: 14,
        estimatedMrv: 20,
        estimatedPv: 11,
      },
    ],
  }),
);

describe("programValidation", () => {
  it("flags workouts that exceed session duration budget", () => {
    const program = {
      workouts: [
        {
          name: "Upper",
          workoutLifts: [
            {
              liftId: "bench",
              workoutLiftSets: Array.from({ length: 22 }, (_, i) => ({ position: i })),
            },
          ],
        },
      ],
    };
    const issues = validateWorkoutDuration(program, CONTEXT);
    assert.ok(issues.some((issue) => issue.code === "WORKOUT_DURATION_EXCEEDED"));
  });

  it("flags low muscle frequency on 4-day programs", () => {
    const issues = validateMuscleFrequency({ Chest: 1 }, CONTEXT);
    assert.ok(issues.some((issue) => issue.code === "MUSCLE_FREQUENCY_LOW"));
  });

  it("flags stacked same-primary exercises as a warning", () => {
    const program = {
      workouts: [
        {
          name: "Upper",
          workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: [{}, {}] },
            { liftId: "bench", position: 1, workoutLiftSets: [{}, {}] },
            { liftId: "bench", position: 2, workoutLiftSets: [{}, {}] },
          ],
        },
      ],
    };
    const issues = validateExerciseOrdering(program, CONTEXT);
    const stacked = issues.find((issue) => issue.code === "EXERCISE_ORDER_STACKED");
    assert.ok(stacked);
    assert.equal(stacked!.severity, "warning");
  });

  it("flags a workout with a duplicate liftId (Bug 1 defense-in-depth)", () => {
    const program = {
      workouts: [
        {
          name: "Upper A",
          workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: [{}] },
            { liftId: "bench", position: 1, workoutLiftSets: [{}] },
          ],
        },
      ],
    };
    const issues = validateNoDuplicateLifts(program);
    assert.ok(issues.some((issue) => issue.code === "DUPLICATE_LIFT_IN_WORKOUT"));
  });

  it("passes a workout with all-distinct liftIds", () => {
    const program = {
      workouts: [
        {
          name: "Upper A",
          workoutLifts: [
            { liftId: "bench", position: 0, workoutLiftSets: [{}] },
            { liftId: "row", position: 1, workoutLiftSets: [{}] },
          ],
        },
      ],
    };
    const issues = validateNoDuplicateLifts(program);
    assert.equal(issues.length, 0);
  });

  it("validateProgramMetrics aggregates summary", () => {
    const report = validateProgramMetrics(
      {
        workouts: [
          {
            name: "Upper",
            workoutLifts: [
              { liftId: "bench", workoutLiftSets: [{}, {}, {}] },
              { liftId: "row", workoutLiftSets: [{}, {}, {}] },
            ],
          },
        ],
      },
      CONTEXT,
    );
    assert.ok(report.summary.weeklyMuscleVolume.Chest >= 3);
    assert.ok(typeof report.pass === "boolean");
  });

  it("weekly volume within fuzzy band passes without error", () => {
    const band = weeklyVolumeToleranceBand({ min: 8, aim: 14, max: 20 });
    assert.ok(band.minFloor <= 5.5);
    const issues = validateWeeklyMuscleVolume({ Back: 5.5 }, CONTEXT);
    assert.ok(!issues.some((issue) => issue.code === "WEEKLY_VOLUME_BELOW_TARGET"));
  });

  it("weekly volume materially below fuzzy band still errors", () => {
    const issues = validateWeeklyMuscleVolume({ Back: 2 }, CONTEXT);
    assert.ok(issues.some((issue) => issue.code === "WEEKLY_VOLUME_BELOW_TARGET"));
  });
});

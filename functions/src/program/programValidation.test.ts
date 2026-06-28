import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseGenerationContext } from "./programPayloadContext";
import {
  validateExerciseOrdering,
  validateMuscleFrequency,
  validateNoDuplicateLifts,
  validateProgramMetrics,
  validateWorkoutDuration,
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
              workoutLiftSets: Array.from({ length: 20 }, (_, i) => ({ position: i })),
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

  it("flags stacked same-primary exercises", () => {
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
    assert.ok(issues.some((issue) => issue.code === "EXERCISE_ORDER_STACKED"));
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
});

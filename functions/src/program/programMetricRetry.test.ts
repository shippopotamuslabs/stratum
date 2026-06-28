import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { collectRejectedLiftIds } from "./programSchema";
import { buildMetricRetryFeedback, validateProgramMetrics } from "./programValidation";
import { parseGenerationContext } from "./programPayloadContext";

describe("program metric retry feedback", () => {
  it("collectRejectedLiftIds finds ids outside the enabled catalog", () => {
    const rejected = collectRejectedLiftIds(
      {
        program: {
          workouts: [
            {
              workoutLifts: [{ liftId: "back-squat" }, { liftId: "phantom-press" }],
            },
          ],
        },
      },
      new Set(["back-squat"]),
    );
    assert.deepEqual(rejected, ["phantom-press"]);
  });

  it("buildMetricRetryFeedback mentions rejected lifts and training day count", () => {
    const context = parseGenerationContext(
      JSON.stringify({
        profile: { trainingAge: "Beginner", trainingDaysPerWeek: 4, sessionLengthMinutes: 60 },
        provenLifts: [
          {
            id: "lat-pulldown",
            movementPattern: "VerticalPull",
            primaryMuscles: ["Back"],
            secondaryMuscles: [],
          },
        ],
        availableLifts: [],
        literatureVolumeLandmarks: [],
      }),
    );
    const report = validateProgramMetrics(
      {
        workouts: [
          {
            name: "Upper A",
            workoutLifts: [
              {
                liftId: "lat-pulldown",
                workoutLiftSets: [{}, {}],
              },
            ],
          },
        ],
      },
      context,
    );
    const feedback = buildMetricRetryFeedback(report, context, ["phantom-press"]);
    assert.match(feedback, /4 workouts/);
    assert.match(feedback, /phantom-press/);
    assert.match(feedback, /validate_program_metrics/);
  });
});

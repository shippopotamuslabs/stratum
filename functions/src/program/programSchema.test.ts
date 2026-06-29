import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  parseAndNormalizeProgram,
  type ProgramNormalizationRequest,
} from "./programSchema";

const BEGINNER_REQUEST: ProgramNormalizationRequest = {
  trainingAge: "Beginner",
  enabledLiftIds: new Set(["back-squat", "bicep-curl"]),
  movementPatternByLiftId: new Map([
    ["back-squat", "SquatPattern"],
    ["bicep-curl", "ElbowFlexion"],
  ]),
};

const INTERMEDIATE_REQUEST: ProgramNormalizationRequest = {
  trainingAge: "Intermediate",
  enabledLiftIds: new Set(["deadlift"]),
  movementPatternByLiftId: new Map([["deadlift", "HipHinge"]]),
};

const FIXTURE_A = {
  program: {
    name: "Full Body",
    workouts: [
      {
        name: "Session 1",
        weekday: "Monday",
        position: 0,
        workoutLifts: [
          {
            liftId: "back-squat",
            position: 0,
            workoutLiftSets: [
              { position: 0, setType: "Standard", repMin: 5, repMax: 5, rirGoal: 9, isTopSet: true },
            ],
          },
          {
            liftId: "bicep-curl",
            position: 1,
            workoutLiftSets: [
              { position: 0, setType: "Standard", repMin: 12, repMax: 15, rirGoal: 3, isTopSet: true },
            ],
          },
        ],
      },
    ],
  },
};

const FIXTURE_B = {
  program: {
    name: "PPL",
    workouts: [
      {
        name: "Pull",
        weekday: "Monday",
        position: 0,
        workoutLifts: [
          {
            liftId: "deadlift",
            position: 0,
            workoutLiftSets: [
              { position: 0, setType: "Standard", repMin: 3, repMax: 5, rirGoal: 7, isTopSet: true },
              { position: 1, setType: "Standard", repMin: 3, repMax: 5, rirGoal: 5, isTopSet: false },
            ],
          },
        ],
      },
    ],
  },
};

describe("parseAndNormalizeProgram", () => {
  describe("PROG-GEN-703 progressionScheme is assigned from trainingAge", () => {
    it("assigns Linear to every workoutLift when trainingAge is Beginner", () => {
      const result = parseAndNormalizeProgram(FIXTURE_A, BEGINNER_REQUEST) as NormalizedProgram;
      for (const workout of result.workouts) {
        for (const lift of workout.workoutLifts) {
          assert.equal(lift.progressionScheme, "Linear");
        }
      }
    });

    it("assigns Double to every workoutLift when trainingAge is Intermediate", () => {
      const result = parseAndNormalizeProgram(FIXTURE_A, {
        ...BEGINNER_REQUEST,
        trainingAge: "Intermediate",
      }) as NormalizedProgram;
      for (const workout of result.workouts) {
        for (const lift of workout.workoutLifts) {
          assert.equal(lift.progressionScheme, "Double");
        }
      }
    });
  });

  describe("PROG-GEN-704 back-off RIR clamped; top-set RIR fixed at 2", () => {
    it("clamps the major-compound back-off RIR and fixes the top set to 2", () => {
      const result = parseAndNormalizeProgram(FIXTURE_B, INTERMEDIATE_REQUEST) as NormalizedProgram;
      const lift = result.workouts[0]?.workoutLifts[0];
      const sets = lift?.workoutLiftSets ?? [];
      const topSet = sets.find((s) => s.isTopSet);
      const backOff = sets.find((s) => !s.isTopSet);
      assert.equal(topSet?.rirGoal, 2);
      assert.ok(backOff && backOff.rirGoal >= 1 && backOff.rirGoal <= 2);
    });
  });

  describe("PROG-GEN-706 unknown lift id is dropped", () => {
    it("drops a workoutLift whose liftId is not enabled, keeping the valid one", () => {
      const fixture = {
        program: {
          name: "X",
          workouts: [
            {
              name: "Day",
              weekday: "Monday",
              position: 0,
              workoutLifts: [
                {
                  liftId: "phantom-lift",
                  position: 0,
                  workoutLiftSets: [
                    { position: 0, setType: "Standard", repMin: 5, repMax: 5, rirGoal: 2, isTopSet: true },
                  ],
                },
                {
                  liftId: "bench-press",
                  position: 1,
                  workoutLiftSets: [
                    { position: 0, setType: "Standard", repMin: 5, repMax: 5, rirGoal: 2, isTopSet: true },
                  ],
                },
              ],
            },
          ],
        },
      };
      const result = parseAndNormalizeProgram(fixture, {
        trainingAge: "Beginner",
        enabledLiftIds: new Set(["bench-press"]),
        movementPatternByLiftId: new Map(),
      }) as NormalizedProgram;
      // The phantom lift is dropped; the valid bench-press remains.
      assert.equal(result.workouts.length, 1);
      const lifts = result.workouts[0]?.workoutLifts ?? [];
      assert.equal(lifts.length, 1);
      assert.equal(lifts[0]?.liftId, "bench-press");
    });
  });

  describe("PROG-GEN-707 empty program after validation is rejected", () => {
    it("throws when every workout is dropped", () => {
      const fixture = {
        program: {
          name: "Empty",
          workouts: [
            {
              name: "Day",
              weekday: "Monday",
              position: 0,
              workoutLifts: [
                {
                  liftId: "phantom-lift",
                  position: 0,
                  workoutLiftSets: [
                    { position: 0, setType: "Standard", repMin: 5, repMax: 5, rirGoal: 2, isTopSet: true },
                  ],
                },
              ],
            },
          ],
        },
      };
      assert.throws(() =>
        parseAndNormalizeProgram(fixture, {
          trainingAge: "Beginner",
          enabledLiftIds: new Set(),
          movementPatternByLiftId: new Map(),
        }),
      );
    });
  });
});

interface NormalizedSet {
  position: number;
  setType: string;
  repMin: number;
  repMax: number;
  rirGoal: number;
  isTopSet: boolean;
}

interface NormalizedLift {
  liftId: string;
  position: number;
  progressionScheme: "Linear" | "Double";
  workoutLiftSets: NormalizedSet[];
}

interface NormalizedWorkout {
  name: string;
  weekday: string | null;
  position: number;
  workoutLifts: NormalizedLift[];
}

interface NormalizedProgram {
  name: string;
  workouts: NormalizedWorkout[];
}

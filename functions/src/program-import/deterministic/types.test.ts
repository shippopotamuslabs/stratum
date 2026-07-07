import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { validateProgramTemplate } from "./types";
import type { NormalizedProgramTemplate } from "./types";

function template(workouts: unknown[]): NormalizedProgramTemplate {
  return { programName: "Test", cycleLength: null, workouts: workouts as never } as never;
}

describe("validateProgramTemplate", () => {
  const valid: NormalizedProgramTemplate = {
    programName: "Test",
    cycleLength: 4,
    workouts: [
      {
        name: "Day 1",
        weekday: null,
        position: 0,
        lifts: [
          {
            name: "Squat",
            position: 0,
            sets: [
              { position: 0, setTypeLabel: null, repMin: 5, repMax: 5, rirGoal: 2, restSeconds: 180, isTopSet: true },
            ],
            supersetGroupKey: null,
          },
        ],
      },
    ],
  };

  it("accepts a valid template", () => {
    assert.doesNotThrow(() => validateProgramTemplate(valid));
  });

  it("rejects a template with no workouts", () => {
    assert.throws(() => validateProgramTemplate({ ...valid, workouts: [] }), /no workouts/);
  });

  it("rejects a workout with no lifts", () => {
    const bad = { ...valid, workouts: [{ ...valid.workouts[0], lifts: [] }] };
    assert.throws(() => validateProgramTemplate(bad), /has no lifts/);
  });

  it("rejects a lift with an empty name", () => {
    const bad = {
      ...valid,
      workouts: [{ ...valid.workouts[0], lifts: [{ ...valid.workouts[0].lifts[0], name: "  " }] }],
    };
    assert.throws(() => validateProgramTemplate(bad), /has no name/);
  });

  it("rejects a lift with no sets", () => {
    const bad = {
      ...valid,
      workouts: [{ ...valid.workouts[0], lifts: [{ ...valid.workouts[0].lifts[0], sets: [] }] }],
    };
    assert.throws(() => validateProgramTemplate(bad), /has no sets/);
  });

  it("rejects non-positive reps", () => {
    const bad = {
      ...valid,
      workouts: [
        {
          ...valid.workouts[0],
          lifts: [
            {
              ...valid.workouts[0].lifts[0],
              sets: [{ ...valid.workouts[0].lifts[0].sets[0], repMin: 0 }],
            },
          ],
        },
      ],
    };
    assert.throws(() => validateProgramTemplate(bad), /non-positive repMin/);
  });

  it("rejects repMax < repMin", () => {
    const bad = {
      ...valid,
      workouts: [
        {
          ...valid.workouts[0],
          lifts: [
            {
              ...valid.workouts[0].lifts[0],
              sets: [{ ...valid.workouts[0].lifts[0].sets[0], repMin: 8, repMax: 5 }],
            },
          ],
        },
      ],
    };
    assert.throws(() => validateProgramTemplate(bad), /repMax < repMin/);
  });

  it("accepts nullable reps (null repMin/repMax are allowed)", () => {
    const withNullReps: NormalizedProgramTemplate = {
      ...valid,
      workouts: [
        {
          ...valid.workouts[0],
          lifts: [
            {
              ...valid.workouts[0].lifts[0],
              sets: [{ ...valid.workouts[0].lifts[0].sets[0], repMin: null, repMax: null }],
            },
          ],
        },
      ],
    };
    assert.doesNotThrow(() => validateProgramTemplate(withNullReps));
  });

  it("throws when given non-array workouts (guards the type assertions)", () => {
    assert.throws(() => validateProgramTemplate(template([{}]) as never));
  });
});

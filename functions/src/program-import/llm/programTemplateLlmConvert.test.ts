import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type OpenAI from "openai";
import {
  coerceProgramTemplate,
  convertProgramTemplateWithLlm,
  looksLikeNameOnlyShell,
} from "./programTemplateLlmConvert";

function fakeCompletion(content: string): OpenAI.Chat.Completions.ChatCompletion {
  return {
    id: "t",
    object: "chat.completion",
    created: 0,
    model: "test",
    choices: [
      {
        index: 0,
        message: { role: "assistant", content, refusal: null },
        finish_reason: "stop",
        logprobs: null,
      },
    ],
  } as OpenAI.Chat.Completions.ChatCompletion;
}

const validTemplate = {
  programName: "Test Program",
  cycleLength: 7,
  workouts: [
    {
      name: "Upper A",
      weekday: "Sunday",
      position: 0,
      lifts: [
        {
          name: "Bench Press",
          position: 0,
          supersetGroupKey: null,
          sets: [
            {
              position: 0,
              setTypeLabel: null,
              repMin: 8,
              repMax: 10,
              rirGoal: 2,
              restSeconds: 120,
              isTopSet: true,
            },
          ],
        },
      ],
    },
  ],
};

describe("programTemplateLlmConvert", () => {
  it("coerces loose JSON into template shape", () => {
    const t = coerceProgramTemplate({
      programName: "P",
      workouts: [{ name: "D1", lifts: [{ name: "Squat", sets: [{ repMin: 5, repMax: 5 }] }] }],
    });
    assert.equal(t.programName, "P");
    assert.equal(t.workouts[0]!.lifts[0]!.name, "Squat");
    assert.equal(t.workouts[0]!.lifts[0]!.sets[0]!.isTopSet, true);
  });

  it("expands compact setCount + shared rep range server-side", () => {
    const t = coerceProgramTemplate({
      programName: "5-Day Hypertrophy",
      cycleLength: 5,
      workouts: [
        {
          name: "Upper A",
          weekday: null,
          position: 0,
          lifts: [
            {
              name: "T-Bar Row",
              position: 0,
              setCount: 4,
              repMin: 10,
              repMax: 14,
              rirGoal: null,
              restSeconds: null,
              setTypeLabel: null,
              supersetGroupKey: null,
            },
          ],
        },
      ],
    });
    const lift = t.workouts[0]!.lifts[0]!;
    assert.equal(lift.sets.length, 4);
    assert.equal(lift.sets[0]!.isTopSet, true);
    assert.equal(lift.sets[1]!.isTopSet, false);
    assert.equal(lift.sets[0]!.repMin, 10);
    assert.equal(lift.sets[3]!.repMax, 14);
  });

  it("treats sets-as-number as setCount for compact lifts", () => {
    const t = coerceProgramTemplate({
      workouts: [
        {
          name: "Day 1",
          lifts: [{ name: "Squat", sets: 3, repMin: 5, repMax: 8 }],
        },
      ],
    });
    assert.equal(t.workouts[0]!.lifts[0]!.sets.length, 3);
  });

  it("detects name-only workout shells", () => {
    const t = coerceProgramTemplate({
      programName: "5-Day Hypertrophy",
      workouts: [
        { name: "Upper A", weekday: null },
        { name: "Lower A", weekday: null },
      ],
    });
    assert.equal(t.workouts[0]!.lifts.length, 0);
    assert.equal(looksLikeNameOnlyShell(t), true);
  });

  it("returns validated template on first successful model call", async () => {
    let calls = 0;
    const result = await convertProgramTemplateWithLlm("Day|Lift|Sets\n1|Squat|3", {
      createCompletion: async () => {
        calls += 1;
        return fakeCompletion(JSON.stringify(validTemplate));
      },
    });
    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.template.programName, "Test Program");
      assert.equal(result.template.workouts.length, 1);
    }
    assert.equal(calls, 1);
  });

  it("repairs once when first output fails validation", async () => {
    let calls = 0;
    const bad = { programName: "X", workouts: [] };
    const result = await convertProgramTemplateWithLlm("grid", {
      createCompletion: async () => {
        calls += 1;
        return fakeCompletion(JSON.stringify(calls === 1 ? bad : validTemplate));
      },
    });
    assert.equal(result.ok, true);
    assert.equal(calls, 2);
  });

  it("fails after repair still invalid", async () => {
    const bad = { programName: "X", workouts: [] };
    const result = await convertProgramTemplateWithLlm("grid", {
      createCompletion: async () => fakeCompletion(JSON.stringify(bad)),
    });
    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.kind, "validation");
    }
  });

  it("rejects empty view without calling model", async () => {
    let calls = 0;
    const result = await convertProgramTemplateWithLlm("  ", {
      createCompletion: async () => {
        calls += 1;
        return fakeCompletion("{}");
      },
    });
    assert.equal(result.ok, false);
    assert.equal(calls, 0);
  });
});

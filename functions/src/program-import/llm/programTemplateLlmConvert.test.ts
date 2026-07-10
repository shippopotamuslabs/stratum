import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type OpenAI from "openai";
import {
  coerceProgramTemplate,
  convertProgramTemplateWithLlm,
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

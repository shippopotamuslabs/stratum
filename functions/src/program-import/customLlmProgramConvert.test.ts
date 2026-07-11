import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type OpenAI from "openai";
import { FakeJobRef } from "../import/jobRef";
import { FakeToolStorage } from "../import/toolStorage";
import { customLlmProgramConvert } from "./customLlmProgramConvert";

const UID = "user-1";
const JOB_ID = "job-1";

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

const validTemplateJson = JSON.stringify({
  programName: "Imported",
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
              rirGoal: null,
              restSeconds: null,
              isTopSet: true,
            },
          ],
        },
        {
          name: "Barbell Row",
          position: 1,
          supersetGroupKey: null,
          sets: [
            {
              position: 0,
              setTypeLabel: null,
              repMin: 8,
              repMax: 10,
              rirGoal: null,
              restSeconds: null,
              isTopSet: true,
            },
          ],
        },
        {
          name: "Overhead Press",
          position: 2,
          supersetGroupKey: null,
          sets: [
            {
              position: 0,
              setTypeLabel: null,
              repMin: 6,
              repMax: 8,
              rirGoal: null,
              restSeconds: null,
              isTopSet: true,
            },
          ],
        },
      ],
    },
  ],
});

describe("customLlmProgramConvert", () => {
  it("rejects garbage without calling the LLM", async () => {
    let llmCalls = 0;
    const storage = new FakeToolStorage();
    const rawPath = `programImports/${UID}/${JOB_ID}/raw/junk.csv`;
    storage.files.set(rawPath, Buffer.from("a,b\n1,2\n"));
    const jobRef = new FakeJobRef({ rawStoragePath: rawPath, fileName: "junk.csv" });

    const outcome = await customLlmProgramConvert({
      uid: UID,
      jobId: JOB_ID,
      jobRef,
      storage,
      createCompletion: async () => {
        llmCalls += 1;
        return fakeCompletion(validTemplateJson);
      },
    });

    assert.equal(outcome.status, "rejected");
    assert.equal(llmCalls, 0);
  });

  it("accepts tidy program CSV and marks job ready via LLM", async () => {
    const csv = [
      "Day,Lift Name,Sets,Rep Range",
      "Upper A (Sunday),Bench Press,4,8-10",
      ",Barbell Row,3,8-10",
      ",Overhead Press,3,6-8",
      "Lower A (Monday),Back Squat,3,5",
      ",Romanian Deadlift,3,8",
      ",Leg Curl,3,10-12",
    ].join("\n");
    const storage = new FakeToolStorage();
    const rawPath = `programImports/${UID}/${JOB_ID}/raw/program.csv`;
    storage.files.set(rawPath, Buffer.from(csv, "utf8"));
    const jobRef = new FakeJobRef({ rawStoragePath: rawPath, fileName: "program.csv" });

    let llmCalls = 0;
    const outcome = await customLlmProgramConvert({
      uid: UID,
      jobId: JOB_ID,
      jobRef,
      storage,
      createCompletion: async () => {
        llmCalls += 1;
        return fakeCompletion(validTemplateJson);
      },
    });

    assert.equal(outcome.status, "ready", JSON.stringify(outcome));
    assert.equal(llmCalls, 1);
    assert.equal(jobRef.data.status, "ready");
    assert.equal(
      (jobRef.data.latestConversion as { format: string }).format,
      "llm_custom",
    );
    const saved = [...storage.files.keys()].filter((p) => p.endsWith("template.json"));
    assert.equal(saved.length, 1);
  });
});

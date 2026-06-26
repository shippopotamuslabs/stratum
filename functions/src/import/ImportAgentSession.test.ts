import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type OpenAI from "openai";
import {
  buildBlobGateFailureMessage,
  buildBlobGateNudgeMessage,
  handleBlobGate,
  MAX_BLOB_NUDGES,
  MAX_TURNS,
  runImportAgentSessionWithDeps,
  summarizeToolError,
  truncateToolOutput,
  type ChatMessage,
  type ImportAgentSessionDeps,
} from "./ImportAgentSession";
import { dispatchTool } from "./tools";
import { FakeJobRef } from "./jobRef";
import { FakeToolStorage } from "./toolStorage";
import {
  baseJobData,
  makeCompletion,
  makeToolCall,
  SAMPLE_CONVERTER_SCRIPT,
  sandboxSuccess,
  VALID_NORMALIZED_JSON,
} from "./testFixtures";

const UID = "user-1";
const JOB_ID = "job-1";

function buildDeps(
  completionQueue: OpenAI.Chat.Completions.ChatCompletion[],
  options: {
    sampleText?: string;
    sandboxResults?: Array<ReturnType<typeof sandboxSuccess> | Error>;
  } = {},
): { deps: ImportAgentSessionDeps; jobRef: FakeJobRef; storage: FakeToolStorage } {
  const jobRef = new FakeJobRef(baseJobData());
  const storage = new FakeToolStorage();
  const sandboxQueue = [...(options.sandboxResults ?? [sandboxSuccess()])];

  const deps: ImportAgentSessionDeps = {
    jobRef,
    async getJob() {
      return {
        sampleStoragePath: jobRef.data.sampleStoragePath as string,
        rawStoragePath: jobRef.data.rawStoragePath as string,
        fullSliceStoragePath: jobRef.data.fullSliceStoragePath as string | undefined,
        scriptStoragePath: jobRef.data.scriptStoragePath as string | undefined,
        latestConversion: jobRef.data.latestConversion as { storagePath?: string } | undefined,
      };
    },
    updateJob: (patch) => jobRef.update(patch),
    async readJobConversionPath() {
      const latest = jobRef.data.latestConversion as { storagePath?: string } | undefined;
      return latest?.storagePath;
    },
    downloadSample: async () => options.sampleText ?? "exercise_name,weight,reps\nSquat,100,8",
    createCompletion: async () => {
      const next = completionQueue.shift();
      if (!next) {
        throw new Error("No more mocked completions");
      }
      return next;
    },
    dispatchTool,
    buildToolContext(job, ref) {
      return {
        uid: UID,
        jobId: JOB_ID,
        jobRef: ref,
        sampleStoragePath: job.sampleStoragePath,
        rawStoragePath: job.rawStoragePath,
        fullSliceStoragePath: job.fullSliceStoragePath,
        storage,
        runSandbox: async () => {
          const next = sandboxQueue.shift();
          if (!next) {
            return sandboxSuccess();
          }
          if (next instanceof Error) {
            throw next;
          }
          return next;
        },
      };
    },
  };

  return { deps, jobRef, storage };
}

describe("runImportAgentSessionWithDeps", () => {
  it("returns failed when job is missing", async () => {
    const jobRef = new FakeJobRef();
    const result = await runImportAgentSessionWithDeps({
      jobRef,
      getJob: async () => undefined,
      updateJob: (p) => jobRef.update(p),
      readJobConversionPath: async () => undefined,
      downloadSample: async () => "",
      createCompletion: async () => makeCompletion([]),
      dispatchTool,
      buildToolContext: () => ({
        uid: UID,
        jobId: JOB_ID,
        jobRef,
        sampleStoragePath: "",
        rawStoragePath: "",
      }),
    });
    assert.equal(result.status, "failed");
    assert.equal(result.errorMessage, "Job not found.");
  });

  it("runs write → sample → full and marks job ready", async () => {
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    const completions = [
      makeCompletion([
        makeToolCall("tc-1", "write_conversion_script", {
          intent: "Mapping CSV columns",
          script: SAMPLE_CONVERTER_SCRIPT,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-2", "execute_on_sample", {
          intent: "Testing on sample rows",
          scriptStoragePath: scriptPath,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-3", "execute_on_full", {
          intent: "Converting full export",
          scriptStoragePath: scriptPath,
        }),
      ]),
    ];
    const { deps, jobRef, storage } = buildDeps(completions, {
      sandboxResults: [sandboxSuccess(), sandboxSuccess({ rowCount: 1, sessionCount: 1 })],
    });
    const job = await deps.getJob();
    assert.ok(job);
    storage.files.set(
      job.rawStoragePath,
      Buffer.from("exercise_name,weight,reps,timestamp\nSquat,100,8,2025-01-01T10:00:00Z"),
    );

    const result = await runImportAgentSessionWithDeps(deps);

    assert.equal(result.status, "ready");
    assert.equal(jobRef.data.status, "ready");
    assert.ok(jobRef.data.latestConversion);
    assert.equal(jobRef.progressUpdates()[0]?.phase, "reading_data");
    assert.ok(
      jobRef.progressUpdates().some((p) => p.phase === "creating_converter" && p.message?.includes("CSV")),
    );
    assert.ok(jobRef.progressUpdates().some((p) => p.phase === "validating_sample"));
    assert.ok(jobRef.progressUpdates().some((p) => p.phase === "converting_full"));
    assert.ok(storage.files.has(scriptPath));
  });

  it("retries after sample sandbox failure then succeeds on full execute", async () => {
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    const completions = [
      makeCompletion([
        makeToolCall("tc-1", "write_conversion_script", {
          intent: "Building converter",
          script: SAMPLE_CONVERTER_SCRIPT,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-2", "execute_on_sample", {
          intent: "Quick test",
          scriptStoragePath: scriptPath,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-3", "write_conversion_script", {
          intent: "Fixing column mapping",
          script: SAMPLE_CONVERTER_SCRIPT,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-4", "execute_on_sample", {
          intent: "Re-testing sample",
          scriptStoragePath: scriptPath,
        }),
      ]),
      makeCompletion([
        makeToolCall("tc-5", "execute_on_full", {
          intent: "Full conversion",
          scriptStoragePath: scriptPath,
        }),
      ]),
    ];
    const { deps, jobRef, storage } = buildDeps(completions, {
      sandboxResults: [
        new Error("KeyError: exercise"),
        sandboxSuccess(),
        sandboxSuccess(),
      ],
    });
    storage.files.set(
      (await deps.getJob())!.rawStoragePath,
      Buffer.from("exercise_name,weight,reps\nSquat,100,8"),
    );

    const result = await runImportAgentSessionWithDeps(deps);

    assert.equal(result.status, "ready");
    const toolMessages = jobRef.updates.filter((u) => u.progressMessage);
    assert.ok(toolMessages.every((u) => !String(u.progressMessage).includes("KeyError")));
  });

  it("fails after blob gate exhausts nudges without conversion", async () => {
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    const failingFull = [
      makeCompletion([
        makeToolCall("tc-1", "execute_on_full", {
          intent: "Trying full convert",
          scriptStoragePath: scriptPath,
        }),
      ]),
    ];
    // Queue enough identical failing turns to hit blob gate after each execute_on_full attempt
    const completions: OpenAI.Chat.Completions.ChatCompletion[] = [];
    for (let i = 0; i < MAX_BLOB_NUDGES + 2; i++) {
      completions.push(...failingFull);
    }

    const jobRef = new FakeJobRef({
      ...baseJobData(),
      scriptStoragePath: scriptPath,
    });
    const storage = new FakeToolStorage();
    const sandboxQueue: Array<ReturnType<typeof sandboxSuccess> | Error> = [
      new Error("script not found"),
    ];
    let completionIndex = 0;

    const deps: ImportAgentSessionDeps = {
      jobRef,
      getJob: async () => ({
        sampleStoragePath: jobRef.data.sampleStoragePath as string,
        rawStoragePath: jobRef.data.rawStoragePath as string,
        scriptStoragePath: scriptPath,
      }),
      updateJob: (p) => jobRef.update(p),
      readJobConversionPath: async () =>
        (jobRef.data.latestConversion as { storagePath?: string } | undefined)?.storagePath,
      downloadSample: async () => "sample",
      createCompletion: async () => completions[completionIndex++]!,
      dispatchTool,
      buildToolContext: (job, ref) => ({
        uid: UID,
        jobId: JOB_ID,
        jobRef: ref,
        sampleStoragePath: job.sampleStoragePath,
        rawStoragePath: job.rawStoragePath,
        storage,
        runSandbox: async () => {
          const next = sandboxQueue.shift() ?? new Error("script not found");
          if (next instanceof Error) {
            throw next;
          }
          return next;
        },
      }),
    };

    const result = await runImportAgentSessionWithDeps(deps);

    assert.equal(result.status, "failed");
    assert.match(
      result.errorMessage ?? "",
      /Conversion failed after 5 attempts\. Last error from execute_on_full: script not found/,
    );
    assert.equal(jobRef.data.status, "failed");
    assert.equal(jobRef.data.progressPhase, "failed");
    assert.equal(jobRef.data.lastToolError, "script not found");
    assert.equal(jobRef.data.lastToolName, "execute_on_full");
  });

  it("fails when model returns empty choice", async () => {
    const emptyCompletion: OpenAI.Chat.Completions.ChatCompletion = {
      id: "empty",
      object: "chat.completion",
      created: 0,
      model: "test",
      choices: [],
    };
    const { deps } = buildDeps([emptyCompletion]);
    const result = await runImportAgentSessionWithDeps(deps);
    assert.equal(result.status, "failed");
    assert.equal(result.errorMessage, "Empty model response.");
  });

  it("fails after maximum turns without conversion", async () => {
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    const writeOnly = makeCompletion([
      makeToolCall("tc-write", "write_conversion_script", {
        intent: "Still iterating",
        script: SAMPLE_CONVERTER_SCRIPT,
      }),
    ]);
    const completions = Array.from({ length: MAX_TURNS }, () => writeOnly);
    const { deps, jobRef } = buildDeps(completions);

    const result = await runImportAgentSessionWithDeps(deps);

    assert.equal(result.status, "failed");
    assert.equal(result.errorMessage, "Agent exceeded maximum turns.");
    assert.equal(jobRef.data.agentTurnCount, MAX_TURNS);
  });

  it("sets agent_running and reading_data at session start", async () => {
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    const completions = [
      makeCompletion([
        makeToolCall("tc-full", "execute_on_full", {
          intent: "One-shot full",
          scriptStoragePath: scriptPath,
        }),
      ]),
    ];
    const jobRef = new FakeJobRef({
      ...baseJobData(),
      scriptStoragePath: scriptPath,
    });
    const storage = new FakeToolStorage();
    storage.files.set(jobRef.data.rawStoragePath as string, Buffer.from("a,b\n1,2"));

    const deps: ImportAgentSessionDeps = {
      jobRef,
      getJob: async () => ({
        sampleStoragePath: jobRef.data.sampleStoragePath as string,
        rawStoragePath: jobRef.data.rawStoragePath as string,
        scriptStoragePath: scriptPath,
      }),
      updateJob: (p) => jobRef.update(p),
      readJobConversionPath: async () =>
        (jobRef.data.latestConversion as { storagePath?: string } | undefined)?.storagePath,
      downloadSample: async () => "sample",
      createCompletion: async () => completions[0]!,
      dispatchTool,
      buildToolContext: (job, ref) => ({
        uid: UID,
        jobId: JOB_ID,
        jobRef: ref,
        sampleStoragePath: job.sampleStoragePath,
        rawStoragePath: job.rawStoragePath,
        storage,
        runSandbox: async () => sandboxSuccess(),
      }),
    };

    await runImportAgentSessionWithDeps(deps);

    assert.equal(jobRef.data.status, "ready");
    assert.ok(
      jobRef.updates.some(
        (u) => u.status === "agent_running" && u.progressPhase === "reading_data",
      ),
    );
  });
});

describe("handleBlobGate", () => {
  it("does nothing when conversion already exists", async () => {
    const messages: ChatMessage[] = [];
    const updates: Record<string, unknown>[] = [];
    const result = await handleBlobGate(
      async (patch) => {
        updates.push(patch);
      },
      messages,
      0,
      "imports/user/job/conversions/1/normalized.json",
      true,
    );
    assert.equal(result.done, false);
    assert.equal(result.blobNudges, 0);
    assert.equal(messages.length, 0);
    assert.equal(updates.length, 0);
  });

  it("nudges up to MAX_BLOB_NUDGES then fails", async () => {
    const messages: ChatMessage[] = [];
    const updates: Record<string, unknown>[] = [];
    let nudges = 0;
    const lastToolFailure = {
      toolName: "execute_on_full",
      errorSummary: "validationErrors: reps must be positive",
    };

    for (let i = 0; i < MAX_BLOB_NUDGES; i++) {
      const step = await handleBlobGate(
        async (patch) => {
          updates.push(patch);
        },
        messages,
        nudges,
        undefined,
        true,
        lastToolFailure,
      );
      nudges = step.blobNudges;
      assert.equal(step.done, false);
      assert.match(messages.at(-1)?.content ?? "", /execute_on_full did not produce conversion output/);
      assert.match(messages.at(-1)?.content ?? "", /validationErrors/);
    }

    const final = await handleBlobGate(
      async (patch) => {
        updates.push(patch);
      },
      messages,
      nudges,
      undefined,
      true,
      lastToolFailure,
    );

    assert.equal(final.done, true);
    assert.equal(final.result?.status, "failed");
    assert.match(
      final.result?.errorMessage ?? "",
      /Conversion failed after 5 attempts\. Last error from execute_on_full/,
    );
    assert.equal(messages.filter((m) => m.content?.includes("execute_on_full did not produce")).length, MAX_BLOB_NUDGES);
    assert.ok(updates.some((u) => u.status === "failed" && u.lastToolError === lastToolFailure.errorSummary));
  });
});

describe("blob gate messaging helpers", () => {
  it("summarizeToolError collapses whitespace and strips ERROR prefix", () => {
    assert.equal(
      summarizeToolError("ERROR: KeyError: exercise\nline 2"),
      "KeyError: exercise line 2",
    );
  });

  it("buildBlobGateFailureMessage includes last tool context", () => {
    const message = buildBlobGateFailureMessage({
      toolName: "execute_on_sample",
      errorSummary: "No script saved yet.",
    });
    assert.match(message, /execute_on_sample/);
    assert.match(message, /No script saved yet/);
  });

  it("buildBlobGateNudgeMessage guides the model when no prior tool error exists", () => {
    const message = buildBlobGateNudgeMessage();
    assert.match(message, /execute_on_sample/);
    assert.match(message, /execute_on_full/);
  });
});

describe("truncateToolOutput", () => {
  it("truncates very long tool output", () => {
    const long = "x".repeat(10_000);
    const out = truncateToolOutput(long, 100);
    assert.ok(out.length < long.length);
    assert.ok(out.endsWith("…[truncated]"));
  });
});

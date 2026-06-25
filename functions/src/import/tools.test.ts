import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { dispatchTool } from "./tools";
import { FakeJobRef } from "./jobRef";
import { FakeToolStorage } from "./toolStorage";
import {
  DEFAULT_INTENT,
  validateNormalizedHistory,
} from "./schemas";
import {
  SAMPLE_CONVERTER_SCRIPT,
  sandboxSuccess,
  VALID_NORMALIZED,
} from "./testFixtures";

const UID = "user-1";
const JOB_ID = "job-1";

function toolCtx(jobRef: FakeJobRef, storage: FakeToolStorage, overrides: Record<string, unknown> = {}) {
  return {
    uid: UID,
    jobId: JOB_ID,
    jobRef,
    sampleStoragePath: "imports/user-1/job-1/sample/export.csv",
    rawStoragePath: "imports/user-1/job-1/raw/export.csv",
    storage,
    ...overrides,
  };
}

describe("dispatchTool", () => {
  it("write_conversion_script saves script and updates progress phase", async () => {
    const jobRef = new FakeJobRef({});
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "write_conversion_script",
      { intent: "Mapping CSV export", script: SAMPLE_CONVERTER_SCRIPT },
      toolCtx(jobRef, storage),
    );

    assert.equal(result.isError, false);
    assert.equal(result.conversionUpdated, false);
    const scriptPath = `imports/${UID}/${JOB_ID}/script/converter.py`;
    assert.ok(storage.files.has(scriptPath));
    assert.equal(jobRef.data.scriptStoragePath, scriptPath);
    assert.equal(jobRef.data.progressPhase, "creating_converter");
    assert.equal(jobRef.data.progressMessage, DEFAULT_INTENT.write_conversion_script);
    assert.ok(jobRef.progressUpdates()[0]?.message?.includes("CSV"));
  });

  it("write_conversion_script rejects empty script", async () => {
    const jobRef = new FakeJobRef({});
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "write_conversion_script",
      { intent: "test", script: "  " },
      toolCtx(jobRef, storage),
    );
    assert.equal(result.isError, true);
    assert.match(result.content, /script is required/);
  });

  it("execute_on_sample fails without prior script", async () => {
    const jobRef = new FakeJobRef({});
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "execute_on_sample",
      { intent: "Testing sample" },
      toolCtx(jobRef, storage, {
        runSandbox: async () => sandboxSuccess(),
      }),
    );
    assert.equal(result.isError, true);
    assert.match(result.content, /No script saved yet/);
  });

  it("execute_on_sample returns preview and outcome message on success", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "execute_on_sample",
      {
        intent: "Running quick test",
        scriptStoragePath: jobRef.data.scriptStoragePath,
      },
      toolCtx(jobRef, storage, {
        runSandbox: async () => sandboxSuccess({ rowCount: 42, sessionCount: 3 }),
      }),
    );

    assert.equal(result.isError, false);
    assert.equal(jobRef.data.progressPhase, "validating_sample");
    assert.match(String(jobRef.data.progressMessage), /42 sets found/);
    const payload = JSON.parse(result.content) as { rowCount: number; preview: string };
    assert.equal(payload.rowCount, 42);
    assert.ok(payload.preview.includes("session-1"));
  });

  it("execute_on_sample surfaces sandbox errors to the model", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "execute_on_sample",
      { intent: "Test", scriptStoragePath: jobRef.data.scriptStoragePath },
      toolCtx(jobRef, storage, {
        runSandbox: async () => {
          throw new Error("Traceback: invalid column");
        },
      }),
    );
    assert.equal(result.isError, true);
    assert.match(result.content, /invalid column/);
    assert.equal(jobRef.data.progressMessage, "Test");
  });

  it("execute_on_sample rejects invalid normalized schema from sandbox", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "execute_on_sample",
      { intent: "Test", scriptStoragePath: jobRef.data.scriptStoragePath },
      toolCtx(jobRef, storage, {
        runSandbox: async () => ({
          outputJson: JSON.stringify({ sessions: [], sets: [{ bad: true }] }),
          rowCount: 1,
          sessionCount: 0,
        }),
      }),
    );
    assert.equal(result.isError, true);
    assert.match(result.content, /set requires/i);
  });

  it("execute_on_sample returns validation errors for the model to fix", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    const result = await dispatchTool(
      "execute_on_sample",
      { intent: "Test", scriptStoragePath: jobRef.data.scriptStoragePath },
      toolCtx(jobRef, storage, {
        runSandbox: async () => ({
          outputJson: JSON.stringify({
            sessions: [{ id: "s1", startedAt: "2025-01-01T00:00:00Z" }],
            sets: [
              {
                id: "set-1",
                sessionId: "s1",
                exerciseNameRaw: "Squat",
                setIndex: 0,
                weightAmount: 100,
                weightUnit: "Lb",
                reps: 0,
                completedAt: "2025-01-01T00:00:00Z",
              },
            ],
          }),
          rowCount: 1,
          sessionCount: 1,
        }),
      }),
    );
    assert.equal(result.isError, true);
    const payload = JSON.parse(result.content) as { validationErrors: string[] };
    assert.ok(payload.validationErrors.some((v) => v.includes("reps must be a positive integer")));
  });

  it("execute_on_full builds slice, writes conversion, and sets latestConversion", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    const rawPath = "imports/user-1/job-1/raw/export.csv";
    storage.files.set(
      rawPath,
      Buffer.from("exercise_name,weight,reps,timestamp\nSquat,100,8,2025-06-01T10:00:00Z"),
    );

    const result = await dispatchTool(
      "execute_on_full",
      { intent: "Full history convert", scriptStoragePath: jobRef.data.scriptStoragePath },
      toolCtx(jobRef, storage, {
        rawStoragePath: rawPath,
        runSandbox: async () => sandboxSuccess({ rowCount: 10, sessionCount: 2 }),
      }),
    );

    assert.equal(result.isError, false);
    assert.equal(result.conversionUpdated, true);
    assert.equal(jobRef.data.progressPhase, "converting_full");
    assert.ok(jobRef.data.fullSliceStoragePath);
    assert.ok(jobRef.data.latestConversion);
    const conversionPaths = [...storage.files.keys()].filter((k) => k.includes("/conversions/"));
    assert.equal(conversionPaths.length, 1);
    const saved = JSON.parse(storage.files.get(conversionPaths[0]!)!.toString("utf8"));
    validateNormalizedHistory(saved);
  });

  it("uses default intent when model omits intent", async () => {
    const jobRef = new FakeJobRef({
      scriptStoragePath: `imports/${UID}/${JOB_ID}/script/converter.py`,
    });
    const storage = new FakeToolStorage();
    storage.files.set(
      "imports/user-1/job-1/raw/export.csv",
      Buffer.from("a,b\n1,2"),
    );
    await dispatchTool(
      "execute_on_full",
      { scriptStoragePath: jobRef.data.scriptStoragePath },
      toolCtx(jobRef, storage, {
        runSandbox: async () => sandboxSuccess(),
      }),
    );
    const firstProgress = jobRef.progressUpdates().find((p) => p.phase === "converting_full");
    assert.equal(firstProgress?.message, DEFAULT_INTENT.execute_on_full);
  });

  it("returns error for unknown tool name", async () => {
    const jobRef = new FakeJobRef({});
    const storage = new FakeToolStorage();
    const result = await dispatchTool("explode_database", {}, toolCtx(jobRef, storage));
    assert.equal(result.isError, true);
    assert.match(result.content, /Unknown tool/);
  });
});

describe("validateNormalizedHistory", () => {
  it("accepts valid fixture", () => {
    const parsed = validateNormalizedHistory(VALID_NORMALIZED);
    assert.equal(parsed.sets.length, 1);
  });

  it("rejects missing sessions array", () => {
    assert.throws(
      () => validateNormalizedHistory({ sets: [] }),
      /sessions and sets arrays/,
    );
  });
});

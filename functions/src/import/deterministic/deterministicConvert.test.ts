import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { deterministicConvert } from "./deterministicConvert";
import { FakeJobRef } from "../jobRef";
import { FakeToolStorage } from "../toolStorage";

const UID = "user-1";
const JOB_ID = "job-1";
const RAW_PATH = `imports/${UID}/${JOB_ID}/raw/export.csv`;

const HEVY = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n" +
  'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Crunch (Machine),,,"0","normal",205,18,,,9\n';

function deps(rawBytes: Buffer, jobData: Record<string, unknown> = {}) {
  const storage = new FakeToolStorage();
  storage.files.set(RAW_PATH, rawBytes);
  const jobRef = new FakeJobRef({
    rawStoragePath: RAW_PATH,
    ...jobData,
  });
  return { storage, jobRef };
}

describe("deterministicConvert", () => {
  it("detects a known format, persists normalized.json, and marks the job ready", async () => {
    const { storage, jobRef } = deps(Buffer.from(HEVY, "utf8"));

    const result = await deterministicConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });

    assert.ok(result);
    assert.equal(result!.format, "hevy");
    assert.equal(result!.rowCount, 1);
    assert.equal(result!.sessionCount, 1);

    // normalized.json persisted under the conversions/ path.
    const savedPaths = [...storage.files.keys()].filter((p) => p.includes("/conversions/") && p.endsWith("/normalized.json"));
    assert.equal(savedPaths.length, 1);
    const saved = JSON.parse(storage.files.get(savedPaths[0])!.toString("utf8"));
    assert.equal(saved.sets[0].exerciseNameRaw, "Crunch (Machine)");

    // Job marked ready with latestConversion metadata.
    assert.equal(jobRef.data.status, "ready");
    assert.equal(jobRef.data.progressPhase, "converting_full");
    const latest = jobRef.data.latestConversion as { storagePath: string; rowCount: number; sessionCount: number };
    assert.equal(latest.rowCount, 1);
    assert.equal(latest.sessionCount, 1);
    assert.ok(latest.storagePath.includes("/conversions/"));
  });

  it("returns null for an unrecognised format so the caller falls back to the agent", async () => {
    const { storage, jobRef } = deps(Buffer.from("foo,bar\n1,2\n", "utf8"));

    const result = await deterministicConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });

    assert.equal(result, null);
    // No conversion persisted, no job status change.
    assert.equal(jobRef.data.status, undefined);
    assert.equal(jobRef.data.latestConversion, undefined);
  });

  it("returns null and leaves the job untouched when rawStoragePath is missing", async () => {
    const storage = new FakeToolStorage();
    const jobRef = new FakeJobRef({});

    const result = await deterministicConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });

    assert.equal(result, null);
    assert.equal(jobRef.data.status, undefined);
  });

  it("returns null when the parser produces zero valid sets", async () => {
    // Header-only Hevy export => no sets.
    const headerOnly = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n";
    const { storage, jobRef } = deps(Buffer.from(headerOnly, "utf8"));

    const result = await deterministicConvert({ uid: UID, jobId: JOB_ID, jobRef, storage });

    assert.equal(result, null);
    assert.equal(jobRef.data.status, undefined);
  });
});

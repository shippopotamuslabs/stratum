import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FieldValue } from "firebase-admin/firestore";
import {
  buildJobResetTrace,
  clientStartMsToTimestamp,
  staleTraceDeletions,
  initialTraceMap,
  STALE_TRACE_FIELDS,
  TRACE_CLIENT_START,
  TRACE_DISPATCHER_POST_SENT,
  TRACE_DISPATCHER_TRIGGER,
  TRACE_JOB_CREATED,
  TRACE_SOLVER,
  TRACE_WORKER_CLAIMED,
  TRACE_WORKER_SOLVING,
  TRACE_WORKER_START,
  TRACE_WORKER_UPLOADED,
} from "./synthesisTrace";
import traceFieldSchema from "./traceFields.json";

const DELETE_SENTINEL_NAME = FieldValue.delete().constructor.name;

function isDeleteSentinel(value: unknown): boolean {
  return (value as { constructor?: { name?: string } })?.constructor?.name ===
    DELETE_SENTINEL_NAME;
}

describe("synthesis trace fields (STR-13)", () => {
  it("derives every TRACE_* constant from the canonical traceFields.json list", () => {
    // Single-source check: the checked-in schema is the authority; the TS
    // module must not add, drop, or rename fields behind its back.
    assert.deepEqual([...STALE_TRACE_FIELDS], traceFieldSchema.staleTraceFields);
    for (const constant of [
      TRACE_DISPATCHER_TRIGGER,
      TRACE_DISPATCHER_POST_SENT,
      TRACE_WORKER_START,
      TRACE_WORKER_CLAIMED,
      TRACE_WORKER_SOLVING,
      TRACE_WORKER_UPLOADED,
      TRACE_SOLVER,
    ]) {
      assert.ok(
        STALE_TRACE_FIELDS.includes(constant),
        `expected ${constant} in traceFields.json`,
      );
    }
  });

  it("exposes stable trace field name constants", () => {
    assert.equal(TRACE_CLIENT_START, "trace.clientStart");
    assert.equal(TRACE_JOB_CREATED, "trace.jobCreated");
    assert.equal(TRACE_DISPATCHER_TRIGGER, "trace.dispatcherTrigger");
    assert.equal(TRACE_DISPATCHER_POST_SENT, "trace.dispatcherPostSent");
  });

  it("converts client epoch ms to a Timestamp", () => {
    const ts = clientStartMsToTimestamp(1_700_000_000_123);
    assert.ok(ts);
    assert.equal(ts!.seconds, 1_700_000_000);
    assert.equal(ts!.nanoseconds, 123_000_000);
  });

  it("returns null for missing or invalid clientStartMs", () => {
    assert.equal(clientStartMsToTimestamp(undefined), null);
    assert.equal(clientStartMsToTimestamp(null), null);
    assert.equal(clientStartMsToTimestamp("123"), null);
    assert.equal(clientStartMsToTimestamp(NaN), null);
    assert.equal(clientStartMsToTimestamp(Infinity), null);
    assert.equal(clientStartMsToTimestamp(0), null);
    assert.equal(clientStartMsToTimestamp(-5), null);
  });

  it("returns null for out-of-range epoch ms instead of throwing", () => {
    // Far future: beyond Firestore Timestamp range.
    assert.equal(clientStartMsToTimestamp(999_999_999_999_999_999), null);
    // Far past: negative would have been caught, but huge positive is out of range.
    assert.equal(clientStartMsToTimestamp(Number.MAX_SAFE_INTEGER), null);
  });

  it("initialTraceMap includes clientStart and jobCreated", () => {
    const ts = clientStartMsToTimestamp(1_700_000_000_000)!;
    const map = initialTraceMap(ts);
    assert.equal(map["clientStart"], ts);
    assert.ok(map["jobCreated"]);
  });

  it("initialTraceMap falls back to serverTimestamp for null clientStart", () => {
    const map = initialTraceMap(null);
    assert.ok(map["clientStart"]);
    assert.ok(map["jobCreated"]);
  });

  it("staleTraceDeletions uses short keys for nesting inside trace:{...}", () => {
    const deletions = staleTraceDeletions();
    // Exact key set: one short key per canonical field, no dotted paths.
    // Dotted paths nested under `trace` would become literal
    // `trace.workerStart` keys instead of deletions (the STR-13 retry bug).
    assert.deepEqual(Object.keys(deletions).sort(), [
      "dispatcherPostSent",
      "dispatcherTrigger",
      "solver",
      "workerClaimed",
      "workerSolving",
      "workerStart",
      "workerUploaded",
    ]);
    for (const key of Object.keys(deletions)) {
      assert.ok(!key.includes("."), `deletion key must be short, got ${key}`);
      assert.ok(isDeleteSentinel(deletions[key]), `expected delete sentinel for ${key}`);
    }
  });

  it("staleTraceDeletions clears dispatcher fields on retry", () => {
    const deletions = staleTraceDeletions();
    assert.ok(deletions["dispatcherTrigger"]);
    assert.ok(deletions["dispatcherPostSent"]);
    // Verify they are FieldValue.delete() sentinels (not timestamps).
    assert.ok(isDeleteSentinel(deletions["dispatcherTrigger"]));
  });

  it("staleTraceDeletions clears worker fields so retries show no stale solve", () => {
    const deletions = staleTraceDeletions();
    for (const field of [
      "workerStart",
      "workerClaimed",
      "workerSolving",
      "workerUploaded",
      "solver",
    ]) {
      assert.ok(deletions[field], `expected deletion for ${field}`);
    }
    // Deletions must not wipe the fresh clientStart/jobCreated written alongside.
    assert.equal(deletions["clientStart"], undefined);
    assert.equal(deletions["jobCreated"], undefined);
  });

  it("buildJobResetTrace composes the exact merge:true payload shape", () => {
    // Mirrors the `trace: buildJobResetTrace(...)` update in
    // startProgramSynthesis.ts: assert the full payload a retried jobId
    // receives, so a key-shape regression fails here, not in production.
    const ts = clientStartMsToTimestamp(1_700_000_000_000)!;
    const payload = buildJobResetTrace(ts);
    assert.deepEqual(Object.keys(payload).sort(), [
      "clientStart",
      "dispatcherPostSent",
      "dispatcherTrigger",
      "jobCreated",
      "solver",
      "workerClaimed",
      "workerSolving",
      "workerStart",
      "workerUploaded",
    ]);
    for (const key of Object.keys(payload)) {
      assert.ok(!key.includes("."), `payload key must be short, got ${key}`);
    }
    assert.equal(payload["clientStart"], ts);
    assert.ok(payload["jobCreated"]);
    assert.ok(!isDeleteSentinel(payload["clientStart"]));
    assert.ok(!isDeleteSentinel(payload["jobCreated"]));
    for (const key of [
      "dispatcherTrigger",
      "dispatcherPostSent",
      "workerStart",
      "workerClaimed",
      "workerSolving",
      "workerUploaded",
      "solver",
    ]) {
      assert.ok(isDeleteSentinel(payload[key]), `expected delete sentinel for ${key}`);
    }
  });
});

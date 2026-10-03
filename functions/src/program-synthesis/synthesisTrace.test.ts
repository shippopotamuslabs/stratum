import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { FieldValue } from "firebase-admin/firestore";
import {
  clientStartMsToTimestamp,
  staleTraceDeletions,
  initialTraceMap,
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

describe("synthesis trace fields (STR-13)", () => {
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

  it("staleTraceDeletions clears dispatcher fields on retry", () => {
    const deletions = staleTraceDeletions();
    assert.ok(deletions[TRACE_DISPATCHER_TRIGGER]);
    assert.ok(deletions[TRACE_DISPATCHER_POST_SENT]);
    // Verify they are FieldValue.delete() sentinels (not timestamps).
    assert.equal(
      deletions[TRACE_DISPATCHER_TRIGGER]?.constructor?.name,
      FieldValue.delete().constructor.name,
    );
  });

  it("staleTraceDeletions clears worker fields so retries show no stale solve", () => {
    const deletions = staleTraceDeletions();
    for (const field of [
      TRACE_WORKER_START,
      TRACE_WORKER_CLAIMED,
      TRACE_WORKER_SOLVING,
      TRACE_WORKER_UPLOADED,
      TRACE_SOLVER,
    ]) {
      assert.ok(deletions[field], `expected deletion for ${field}`);
    }
    // Deletions must not wipe the fresh clientStart/jobCreated written alongside.
    assert.equal(deletions["clientStart"], undefined);
    assert.equal(deletions["jobCreated"], undefined);
  });
});

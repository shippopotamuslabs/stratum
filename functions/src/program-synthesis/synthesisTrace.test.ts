import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  clientStartMsToTimestamp,
  TRACE_CLIENT_START,
  TRACE_JOB_CREATED,
} from "./startProgramSynthesis";
import {
  TRACE_DISPATCHER_TRIGGER,
  TRACE_DISPATCHER_POST_SENT,
} from "./processProgramSynthesisJob";

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
});

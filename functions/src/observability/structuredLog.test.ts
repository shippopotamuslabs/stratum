import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  ERROR_REPORT_TYPE,
  buildErrorReport,
  buildLogRecord,
  createLogger,
  formatError,
} from "./structuredLog";

describe("formatError", () => {
  it("serializes Error type, message, and stack", () => {
    const err = new TypeError("bad shape");
    const details = formatError(err);
    assert.equal(details.errorType, "TypeError");
    assert.equal(details.errorMessage, "bad shape");
    assert.match(details.stackTrace, /TypeError: bad shape/);
  });

  it("handles non-Error throws without throwing", () => {
    const details = formatError("plain string failure");
    assert.equal(details.errorType, "Unknown");
    assert.equal(details.errorMessage, "plain string failure");
    assert.equal(details.stackTrace, "plain string failure");
  });
});

describe("buildLogRecord", () => {
  it("keeps severity, message, and correlation fields top-level", () => {
    const record = buildLogRecord("INFO", "dispatch sent", {
      service: "processProgramSynthesisJob",
      uid: "u1",
      jobId: "j1",
      durationMs: 42,
    });
    assert.equal(record["severity"], "INFO");
    assert.equal(record["message"], "dispatch sent");
    assert.equal(record["uid"], "u1");
    assert.equal(record["jobId"], "j1");
    assert.equal(record["durationMs"], 42);
  });

  it("ignores severity/message keys in context instead of clobbering", () => {
    const record = buildLogRecord("INFO", "real message", {
      severity: "ERROR",
      message: "spoofed",
    } as unknown as Record<string, unknown>);
    assert.equal(record["severity"], "INFO");
    assert.equal(record["message"], "real message");
  });
});

describe("buildErrorReport", () => {
  it("adds the Error Reporting marker, serviceContext, and stack", () => {
    const err = new Error("boom");
    const record = buildErrorReport("solve failed", err, {
      service: "solver",
      uid: "u1",
      jobId: "j1",
    });
    assert.equal(record["@type"], ERROR_REPORT_TYPE);
    assert.equal(record["severity"], "ERROR");
    assert.deepEqual(record["serviceContext"], { service: "solver" });
    assert.equal(record["errorType"], "Error");
    assert.equal(record["uid"], "u1");
    assert.equal(record["jobId"], "j1");
    assert.match(String(record["message"]), /solve failed: Error: boom/);
    assert.match(String(record["message"]), /at /);
  });

  it("derives service and serviceContext.service from one source", () => {
    const record = buildErrorReport("solve failed", new Error("boom"), {
      service: "solver",
    });
    assert.equal(record["service"], "solver");
    assert.deepEqual(record["serviceContext"], { service: "solver" });
  });

  it("ignores severity/message keys in context instead of clobbering", () => {
    const record = buildErrorReport("real message", new Error("boom"), {
      severity: "INFO",
      message: "spoofed",
    } as unknown as Record<string, unknown>);
    assert.equal(record["severity"], "ERROR");
    assert.match(String(record["message"]), /real message: Error: boom/);
  });
});

describe("createLogger", () => {
  it("emits single-line JSON with the default service", () => {
    const lines: Array<{ line: string; toStderr: boolean }> = [];
    const log = createLogger({
      service: "processProgramSynthesisJob",
      write: (line, toStderr) => lines.push({ line, toStderr }),
    });
    log.info("trigger received", { uid: "u1", jobId: "j1" });
    assert.equal(lines.length, 1);
    assert.equal(lines[0].toStderr, false);
    const parsed = JSON.parse(lines[0].line) as Record<string, unknown>;
    assert.equal(parsed["severity"], "INFO");
    assert.equal(parsed["service"], "processProgramSynthesisJob");
    assert.equal(parsed["jobId"], "j1");
  });

  it("routes warnings and errors to stderr, info to stdout", () => {    const lines: Array<{ line: string; toStderr: boolean }> = [];
    const log = createLogger({
      service: "s",
      write: (line, toStderr) => lines.push({ line, toStderr }),
    });
    log.warn("degraded", {});
    log.error("failed without exception", {});
    log.reportError("threw", new Error("x"), {});
    assert.deepEqual(
      lines.map((l) => l.toStderr),
      [true, true, true],
    );
    const reported = JSON.parse(lines[2].line) as Record<string, unknown>;
    assert.equal(reported["@type"], ERROR_REPORT_TYPE);
  });

  it("pins the logger service so context cannot override it", () => {
    const lines: Array<{ line: string; toStderr: boolean }> = [];
    const log = createLogger({
      service: "processProgramSynthesisJob",
      write: (line, toStderr) => lines.push({ line, toStderr }),
    });
    log.info("trigger received", { service: "spoofed" });
    const parsed = JSON.parse(lines[0].line) as Record<string, unknown>;
    assert.equal(parsed["service"], "processProgramSynthesisJob");
  });
});

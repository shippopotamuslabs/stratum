import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  LOG_SOURCE,
  MAX_ENTRIES_PER_REQUEST,
  MAX_ENTRIES_PER_USER_PER_DAY,
  applyQuotaUpdate,
  isQuotaExceeded,
  parseIngestClientLogs,
  quotaDocPath,
  utcDateKey,
} from "./ingestClientLogs";

const NOW = Date.parse("2026-10-01T12:00:00.000Z");

function entry(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    severity: "WARNING",
    tag: "SwapLift",
    message: "no active program",
    clientTimestampMs: NOW - 1000,
    sessionId: "run-1",
    durationMs: null,
    ...overrides,
  };
}

function request(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    entries: [entry()],
    appVersion: "1.2.3",
    platform: "android",
    droppedEntryCount: 0,
    ...overrides,
  };
}

function expectInvalid(data: unknown, pattern?: RegExp): void {
  assert.throws(
    () => parseIngestClientLogs(data, NOW),
    (err: { code?: string; message?: string }) => {
      assert.equal(err.code, "invalid-argument");
      if (pattern) {
        assert.match(String(err.message), pattern);
      }
      return true;
    },
  );
}

describe("parseIngestClientLogs", () => {
  it("accepts a well-formed batch with defaults", () => {
    const parsed = parseIngestClientLogs(request(), NOW);
    assert.equal(parsed.entries.length, 1);
    assert.equal(parsed.entries[0].severity, "WARNING");
    assert.equal(parsed.entries[0].sessionId, "run-1");
    assert.equal(parsed.appVersion, "1.2.3");
    assert.equal(parsed.platform, "android");
    assert.equal(parsed.droppedEntryCount, 0);
  });

  it("defaults a missing droppedEntryCount to zero", () => {
    const data = request();
    delete data["droppedEntryCount"];
    assert.equal(parseIngestClientLogs(data, NOW).droppedEntryCount, 0);
  });

  it("accepts ERROR severity and omits optional fields as null", () => {
    const parsed = parseIngestClientLogs(
      request({ entries: [entry({ severity: "ERROR", sessionId: undefined })] }),
      NOW,
    );
    assert.equal(parsed.entries[0].severity, "ERROR");
    assert.equal(parsed.entries[0].sessionId, null);
    assert.equal(parsed.entries[0].durationMs, null);
    assert.equal(parsed.entries[0].outcome, null);
    assert.equal(parsed.entries[0].reasonCode, null);
  });

  it("accepts OUTCOME severity with flat outcome columns", () => {
    const parsed = parseIngestClientLogs(
      request({
        entries: [
          entry({
            severity: "OUTCOME",
            tag: "program_generation",
            message: "outcome=expected_failure reason=synthesis_failed",
            outcome: "expected_failure",
            reasonCode: "synthesis_failed",
          }),
        ],
      }),
      NOW,
    );
    assert.equal(parsed.entries[0].severity, "OUTCOME");
    assert.equal(parsed.entries[0].outcome, "expected_failure");
    assert.equal(parsed.entries[0].reasonCode, "synthesis_failed");
  });

  it("rejects bad outcome and reasonCode values", () => {
    expectInvalid(request({ entries: [entry({ outcome: "failed" })] }), /outcome/);
    expectInvalid(request({ entries: [entry({ outcome: 42 })] }), /outcome/);
    expectInvalid(request({ entries: [entry({ reasonCode: "Quota Exceeded" })] }), /reasonCode/);
    expectInvalid(request({ entries: [entry({ reasonCode: "" })] }), /reasonCode/);
    expectInvalid(
      request({ entries: [entry({ outcome: "x".repeat(33) })] }),
      /outcome/,
    );
    expectInvalid(
      request({ entries: [entry({ reasonCode: "r".repeat(65) })] }),
      /reasonCode/,
    );
  });

  it("rejects outcome columns on WARNING/ERROR and OUTCOME without an outcome", () => {
    expectInvalid(
      request({ entries: [entry({ severity: "WARNING", outcome: "success" })] }),
      /OUTCOME/,
    );
    expectInvalid(
      request({ entries: [entry({ severity: "ERROR", reasonCode: "sync_not_started" })] }),
      /OUTCOME/,
    );
    expectInvalid(request({ entries: [entry({ severity: "OUTCOME" })] }), /outcome/);
  });

  it("accepts OUTCOME success without a reasonCode", () => {
    const parsed = parseIngestClientLogs(
      request({
        entries: [
          entry({
            severity: "OUTCOME",
            tag: "cloud_sync",
            message: "outcome=success",
            outcome: "success",
          }),
        ],
      }),
      NOW,
    );
    assert.equal(parsed.entries[0].severity, "OUTCOME");
    assert.equal(parsed.entries[0].outcome, "success");
    assert.equal(parsed.entries[0].reasonCode, null);
  });

  it("rejects non-objects, empty entries, and oversized batches", () => {
    expectInvalid(undefined);
    expectInvalid({ entries: [] }, /non-empty/);
    expectInvalid(request({ entries: [] }), /non-empty/);
    expectInvalid(
      request({ entries: Array.from({ length: MAX_ENTRIES_PER_REQUEST + 1 }, () => entry()) }),
      /at most/,
    );
  });

  it("rejects bad severity, tag, message, timestamp, and duration", () => {
    expectInvalid(request({ entries: [entry({ severity: "VERBOSE" })] }), /severity/);
    expectInvalid(request({ entries: [entry({ tag: "" })] }), /tag/);
    expectInvalid(request({ entries: [entry({ tag: "x".repeat(65) })] }), /tag/);
    expectInvalid(request({ entries: [entry({ message: "" })] }), /message/);
    expectInvalid(
      request({ entries: [entry({ message: "x".repeat(4097) })] }),
      /message/,
    );
    expectInvalid(request({ entries: [entry({ clientTimestampMs: -1 })] }), /timestamp/);
    expectInvalid(
      request({ entries: [entry({ clientTimestampMs: NOW + 3600000 + 1 })] }),
      /timestamp/,
    );
    expectInvalid(request({ entries: [entry({ durationMs: -2 })] }), /durationMs/);
  });

  it("accepts days-old timestamps from the offline queue", () => {
    const parsed = parseIngestClientLogs(
      request({ entries: [entry({ clientTimestampMs: NOW - 7 * 86400000 })] }),
      NOW,
    );
    assert.equal(parsed.entries[0].clientTimestampMs, NOW - 7 * 86400000);
  });

  it("rejects bad envelope fields", () => {
    expectInvalid(request({ appVersion: "" }), /appVersion/);
    expectInvalid(request({ platform: "ios" }), /platform/);
    expectInvalid(request({ droppedEntryCount: -1 }), /droppedEntryCount/);
    expectInvalid(request({ droppedEntryCount: 1.5 }), /droppedEntryCount/);
  });
});

describe("quota helpers", () => {
  it("builds a per-user quota doc path", () => {
    assert.equal(quotaDocPath("uid-1"), "clientLogQuota/uid-1");
  });

  it("keys quotas by UTC date", () => {
    assert.equal(utcDateKey(NOW), "2026-10-01");
  });

  it("rejects only past the daily cap", () => {
    assert.equal(isQuotaExceeded(MAX_ENTRIES_PER_USER_PER_DAY - 1, 1), false);
    assert.equal(isQuotaExceeded(MAX_ENTRIES_PER_USER_PER_DAY, 1), true);
  });

  it("starts a new day at zero and accumulates within a day", () => {
    assert.deepEqual(applyQuotaUpdate(undefined, "2026-10-01", 50), {
      dateKey: "2026-10-01",
      count: 50,
    });
    assert.deepEqual(
      applyQuotaUpdate({ dateKey: "2026-09-30", count: 1999 }, "2026-10-01", 10),
      { dateKey: "2026-10-01", count: 10 },
    );
    assert.deepEqual(
      applyQuotaUpdate({ dateKey: "2026-10-01", count: 100 }, "2026-10-01", 25),
      { dateKey: "2026-10-01", count: 125 },
    );
  });

  it("throws resource-exhausted past the cap", () => {
    assert.throws(
      () => applyQuotaUpdate({ dateKey: "2026-10-01", count: 2000 }, "2026-10-01", 1),
      (err: { code?: string }) => {
        assert.equal(err.code, "resource-exhausted");
        return true;
      },
    );
  });
});

describe("log source marker", () => {
  it("uses the STR-28 sink filter value", () => {
    assert.equal(LOG_SOURCE, "stratum-client-log");
  });
});

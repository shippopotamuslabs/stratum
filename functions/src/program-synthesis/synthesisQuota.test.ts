import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  MAX_SYNTHESIS_PER_MONTH,
  effectiveStartCount,
  effectiveSuccessCount,
  isAtMonthlyQuota,
  nextQuotaAfterStart,
  nextQuotaAfterSuccess,
  utcMonthKey,
  synthesisQuotaPath,
} from "./synthesisQuota";
import {
  isValidSynthesisRequestStoragePath,
} from "./startProgramSynthesis";
import {
  resolveSolverServiceUrl,
  solverProcessUrl,
  allowUnauthSolverDispatch,
  SOLVER_SERVICE_URL_ENV,
  ALLOW_UNAUTH_SOLVER_DISPATCH_ENV,
} from "./processProgramSynthesisJob";
import {
  SYNTHESIS_STALE_JOB_MS,
  SYNTHESIS_CLIENT_POLL_TIMEOUT_MS,
} from "./synthesisJobGuards";

describe("utcMonthKey", () => {
  it("formats UTC year-month with zero padding", () => {
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 0, 15))), "2026-01");
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 6, 1))), "2026-07");
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 11, 31, 23, 59))), "2026-12");
  });

  it("uses UTC boundary (not local) near month edges", () => {
    assert.equal(utcMonthKey(new Date("2026-07-01T00:30:00.000Z")), "2026-07");
    assert.equal(utcMonthKey(new Date("2026-06-30T23:59:59.999Z")), "2026-06");
  });
});

describe("effectiveStartCount / isAtMonthlyQuota (starts, not only successes)", () => {
  it("missing doc counts as zero", () => {
    assert.equal(effectiveStartCount(undefined, "2026-07"), 0);
    assert.equal(isAtMonthlyQuota(undefined, "2026-07"), false);
  });

  it("rolls over when monthKey differs", () => {
    const prev = { monthKey: "2026-06", startCount: 10, successCount: 10 };
    assert.equal(effectiveStartCount(prev, "2026-07"), 0);
    assert.equal(isAtMonthlyQuota(prev, "2026-07"), false);
    assert.deepEqual(nextQuotaAfterStart(prev, "2026-07"), {
      monthKey: "2026-07",
      startCount: 1,
      successCount: 0,
    });
  });

  it("caps starts at MAX_SYNTHESIS_PER_MONTH (10)", () => {
    assert.equal(MAX_SYNTHESIS_PER_MONTH, 10);
    const atCap = { monthKey: "2026-07", startCount: 10, successCount: 3 };
    assert.equal(isAtMonthlyQuota(atCap, "2026-07"), true);
    const under = { monthKey: "2026-07", startCount: 9, successCount: 2 };
    assert.equal(isAtMonthlyQuota(under, "2026-07"), false);
  });

  it("legacy docs with only successCount still gate starts", () => {
    const legacy = { monthKey: "2026-07", successCount: 10 };
    assert.equal(effectiveStartCount(legacy, "2026-07"), 10);
    assert.equal(isAtMonthlyQuota(legacy, "2026-07"), true);
  });

  it("nextQuotaAfterSuccess preserves startCount", () => {
    const doc = { monthKey: "2026-07", startCount: 4, successCount: 2 };
    assert.deepEqual(nextQuotaAfterSuccess(doc, "2026-07"), {
      monthKey: "2026-07",
      startCount: 4,
      successCount: 3,
    });
    assert.equal(effectiveSuccessCount(doc, "2026-07"), 2);
  });

  it("synthesisQuotaPath is users/{uid}/synthesisQuota/current", () => {
    assert.equal(synthesisQuotaPath("uid-1"), "users/uid-1/synthesisQuota/current");
  });
});

describe("stale vs client poll alignment", () => {
  it("stale window is greater than client poll timeout", () => {
    assert.ok(SYNTHESIS_STALE_JOB_MS > SYNTHESIS_CLIENT_POLL_TIMEOUT_MS);
    assert.equal(SYNTHESIS_CLIENT_POLL_TIMEOUT_MS, 5 * 60 * 1000);
    assert.equal(SYNTHESIS_STALE_JOB_MS, 6 * 60 * 1000);
  });
});

describe("isValidSynthesisRequestStoragePath", () => {
  it("accepts exact request.json path", () => {
    assert.equal(
      isValidSynthesisRequestStoragePath("u1", "j1", "synthesis/u1/j1/request.json"),
      true,
    );
  });

  it("rejects wrong path, result.json, or escape", () => {
    assert.equal(
      isValidSynthesisRequestStoragePath("u1", "j1", "synthesis/u1/j1/result.json"),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath("u1", "j1", "synthesis/u1/other/request.json"),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath("u1", "j1", "synthesis/u1/j1/../x/request.json"),
      false,
    );
  });
});

describe("solver URL helpers", () => {
  it("resolveSolverServiceUrl trims and strips trailing slash", () => {
    assert.equal(
      resolveSolverServiceUrl({ [SOLVER_SERVICE_URL_ENV]: " https://x.run.app/ " }),
      "https://x.run.app",
    );
    assert.equal(resolveSolverServiceUrl({}), null);
  });

  it("solverProcessUrl appends /process", () => {
    assert.equal(solverProcessUrl("https://x.run.app"), "https://x.run.app/process");
  });

  it("allowUnauthSolverDispatch defaults false", () => {
    assert.equal(allowUnauthSolverDispatch({}), false);
    assert.equal(
      allowUnauthSolverDispatch({ [ALLOW_UNAUTH_SOLVER_DISPATCH_ENV]: "true" }),
      true,
    );
  });
});

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  MAX_SYNTHESIS_PER_MONTH,
  effectiveSuccessCount,
  isAtMonthlyQuota,
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
  SOLVER_SERVICE_URL_ENV,
} from "./processProgramSynthesisJob";

describe("utcMonthKey", () => {
  it("formats UTC year-month with zero padding", () => {
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 0, 15))), "2026-01");
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 6, 1))), "2026-07");
    assert.equal(utcMonthKey(new Date(Date.UTC(2026, 11, 31, 23, 59))), "2026-12");
  });

  it("uses UTC boundary (not local) near month edges", () => {
    // 2026-07-01 00:30 UTC is still July regardless of local TZ of the runner.
    assert.equal(utcMonthKey(new Date("2026-07-01T00:30:00.000Z")), "2026-07");
    // Last instant of June UTC.
    assert.equal(utcMonthKey(new Date("2026-06-30T23:59:59.999Z")), "2026-06");
  });
});

describe("effectiveSuccessCount / isAtMonthlyQuota / month rollover", () => {
  it("missing doc counts as zero", () => {
    assert.equal(effectiveSuccessCount(undefined, "2026-07"), 0);
    assert.equal(effectiveSuccessCount(null, "2026-07"), 0);
    assert.equal(isAtMonthlyQuota(undefined, "2026-07"), false);
  });

  it("rolls over when monthKey differs (counter restarts)", () => {
    const prev = { monthKey: "2026-06", successCount: 10 };
    assert.equal(effectiveSuccessCount(prev, "2026-07"), 0);
    assert.equal(isAtMonthlyQuota(prev, "2026-07"), false);
    assert.deepEqual(nextQuotaAfterSuccess(prev, "2026-07"), {
      monthKey: "2026-07",
      successCount: 1,
    });
  });

  it("caps at MAX_SYNTHESIS_PER_MONTH (10)", () => {
    assert.equal(MAX_SYNTHESIS_PER_MONTH, 10);
    const atCap = { monthKey: "2026-07", successCount: 10 };
    assert.equal(isAtMonthlyQuota(atCap, "2026-07"), true);
    const under = { monthKey: "2026-07", successCount: 9 };
    assert.equal(isAtMonthlyQuota(under, "2026-07"), false);
    assert.deepEqual(nextQuotaAfterSuccess(under, "2026-07"), {
      monthKey: "2026-07",
      successCount: 10,
    });
  });

  it("treats non-finite / negative successCount as zero", () => {
    assert.equal(
      effectiveSuccessCount({ monthKey: "2026-07", successCount: Number.NaN }, "2026-07"),
      0,
    );
    assert.equal(
      effectiveSuccessCount({ monthKey: "2026-07", successCount: -3 }, "2026-07"),
      0,
    );
  });

  it("synthesisQuotaPath is users/{uid}/synthesisQuota/current", () => {
    assert.equal(synthesisQuotaPath("uid-1"), "users/uid-1/synthesisQuota/current");
  });
});

describe("isValidSynthesisRequestStoragePath", () => {
  const uid = "user-a";
  const jobId = "job-1";

  it("accepts paths under synthesis/{uid}/{jobId}/", () => {
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `synthesis/${uid}/${jobId}/request.json`),
      true,
    );
  });

  it("rejects wrong uid, jobId, parent escape, or empty object name", () => {
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `synthesis/other/${jobId}/request.json`),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `synthesis/${uid}/other/request.json`),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `synthesis/${uid}/${jobId}/../x.json`),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `synthesis/${uid}/${jobId}/`),
      false,
    );
    assert.equal(
      isValidSynthesisRequestStoragePath(uid, jobId, `programImports/${uid}/${jobId}/x.json`),
      false,
    );
  });
});

describe("solver URL helpers", () => {
  it("resolveSolverServiceUrl trims and strips trailing slash", () => {
    assert.equal(
      resolveSolverServiceUrl({ [SOLVER_SERVICE_URL_ENV]: " https://solver.example.run.app/ " }),
      "https://solver.example.run.app",
    );
    assert.equal(resolveSolverServiceUrl({}), null);
    assert.equal(resolveSolverServiceUrl({ [SOLVER_SERVICE_URL_ENV]: "   " }), null);
  });

  it("solverProcessUrl appends /process", () => {
    assert.equal(
      solverProcessUrl("https://solver.example.run.app"),
      "https://solver.example.run.app/process",
    );
    assert.equal(
      solverProcessUrl("https://solver.example.run.app/"),
      "https://solver.example.run.app/process",
    );
  });
});

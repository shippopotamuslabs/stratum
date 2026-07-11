import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  isHistoricalImportJobStale,
  HISTORICAL_IMPORT_STALE_JOB_MS,
} from "./importJobGuards";

describe("isHistoricalImportJobStale", () => {
  it("treats missing data as stale", () => {
    assert.equal(isHistoricalImportJobStale(undefined), true);
  });

  it("treats missing timestamps as stale", () => {
    assert.equal(isHistoricalImportJobStale({ status: "agent_running" }), true);
  });

  it("is fresh when updatedAt is recent", () => {
    const now = 1_000_000;
    assert.equal(
      isHistoricalImportJobStale(
        {
          updatedAt: { toMillis: () => now - 60_000 },
        },
        now,
      ),
      false,
    );
  });

  it("is stale when updatedAt is older than the lease window", () => {
    const now = 1_000_000;
    assert.equal(
      isHistoricalImportJobStale(
        {
          updatedAt: { toMillis: () => now - HISTORICAL_IMPORT_STALE_JOB_MS - 1 },
        },
        now,
      ),
      true,
    );
  });

  it("falls back to progressUpdatedAt then createdAt", () => {
    const now = 1_000_000;
    assert.equal(
      isHistoricalImportJobStale(
        {
          progressUpdatedAt: { toMillis: () => now - 1_000 },
        },
        now,
      ),
      false,
    );
    assert.equal(
      isHistoricalImportJobStale(
        {
          createdAt: { toMillis: () => now - HISTORICAL_IMPORT_STALE_JOB_MS - 5 },
        },
        now,
      ),
      true,
    );
  });
});

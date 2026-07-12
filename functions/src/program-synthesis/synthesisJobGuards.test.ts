import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  isSynthesisJobStale,
  SYNTHESIS_STALE_JOB_MS,
} from "./synthesisJobGuards";

describe("isSynthesisJobStale", () => {
  it("treats missing data as stale", () => {
    assert.equal(isSynthesisJobStale(undefined), true);
  });

  it("treats missing timestamps as stale", () => {
    assert.equal(isSynthesisJobStale({ status: "running" }), true);
  });

  it("is fresh when updatedAt is recent", () => {
    const now = 1_000_000;
    assert.equal(
      isSynthesisJobStale(
        {
          updatedAt: { toMillis: () => now - 60_000 },
        },
        now,
      ),
      false,
    );
  });

  it("is stale when updatedAt is older than the lease window (6 min)", () => {
    const now = 1_000_000;
    assert.equal(
      isSynthesisJobStale(
        {
          updatedAt: { toMillis: () => now - SYNTHESIS_STALE_JOB_MS - 1 },
        },
        now,
      ),
      true,
    );
  });

  it("is still fresh just under the lease window", () => {
    const now = 1_000_000;
    assert.equal(
      isSynthesisJobStale(
        {
          updatedAt: { toMillis: () => now - SYNTHESIS_STALE_JOB_MS + 1 },
        },
        now,
      ),
      false,
    );
  });

  it("falls back to progressUpdatedAt then createdAt", () => {
    const now = 1_000_000;
    assert.equal(
      isSynthesisJobStale(
        {
          progressUpdatedAt: { toMillis: () => now - 1_000 },
        },
        now,
      ),
      false,
    );
    assert.equal(
      isSynthesisJobStale(
        {
          createdAt: { toMillis: () => now - SYNTHESIS_STALE_JOB_MS - 5 },
        },
        now,
      ),
      true,
    );
  });
});

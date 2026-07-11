import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  isProgramTemplateJobStale,
  PROGRAM_TEMPLATE_STALE_JOB_MS,
} from "./programTemplateJobGuards";

describe("isProgramTemplateJobStale", () => {
  it("treats missing data as stale", () => {
    assert.equal(isProgramTemplateJobStale(undefined), true);
  });

  it("treats missing timestamps as stale", () => {
    assert.equal(isProgramTemplateJobStale({ status: "running" }), true);
  });

  it("is fresh when updatedAt is recent", () => {
    const now = 1_000_000;
    assert.equal(
      isProgramTemplateJobStale(
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
      isProgramTemplateJobStale(
        {
          updatedAt: { toMillis: () => now - PROGRAM_TEMPLATE_STALE_JOB_MS - 1 },
        },
        now,
      ),
      true,
    );
  });

  it("falls back to progressUpdatedAt then createdAt", () => {
    const now = 1_000_000;
    assert.equal(
      isProgramTemplateJobStale(
        {
          progressUpdatedAt: { toMillis: () => now - 1_000 },
        },
        now,
      ),
      false,
    );
    assert.equal(
      isProgramTemplateJobStale(
        {
          createdAt: { toMillis: () => now - PROGRAM_TEMPLATE_STALE_JOB_MS - 5 },
        },
        now,
      ),
      true,
    );
  });
});

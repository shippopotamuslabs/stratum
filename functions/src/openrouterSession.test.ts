import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildOpenRouterChatCompletionExtras,
  buildProgramGenerationChatCompletionExtras,
  customLiftInferenceSessionId,
  importAgentSessionId,
  OPENROUTER_SESSION_ID_MAX,
  sanitizeOpenRouterSessionId,
} from "./openrouterSession";

describe("openrouterSession", () => {
  it("builds stable import agent session ids", () => {
    assert.equal(
      importAgentSessionId("user-1", "job-abc"),
      "stratum-import:user-1:job-abc",
    );
  });

  it("builds stable custom lift session ids", () => {
    assert.equal(customLiftInferenceSessionId("user-1"), "stratum-custom-lifts:user-1");
  });

  it("truncates session ids longer than 256 characters", () => {
    const longUid = "u".repeat(300);
    const sessionId = importAgentSessionId(longUid, "job-1");
    assert.equal(sessionId.length, OPENROUTER_SESSION_ID_MAX);
    assert.ok(sessionId.startsWith("stratum-import:"));
  });

  it("rejects blank session ids", () => {
    assert.throws(() => sanitizeOpenRouterSessionId("   "), /non-empty/);
  });

  it("omits provider sort for sticky session completions", () => {
    const extras = buildOpenRouterChatCompletionExtras("stratum-import:user:job");
    assert.equal(extras.session_id, "stratum-import:user:job");
    assert.equal(extras.provider.sort, undefined);
    assert.equal(extras.provider.require_parameters, false);
  });

  it("program generation uses price sort with throughput floor", () => {
    const extras = buildProgramGenerationChatCompletionExtras("stratum-program-generate:user-1");
    assert.equal(extras.session_id, "stratum-program-generate:user-1");
    assert.equal(extras.provider.sort, "price");
    assert.equal(extras.provider.require_parameters, true);
    assert.deepEqual(extras.provider.preferred_min_throughput, { p90: 50 });
  });
});

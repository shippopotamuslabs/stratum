import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildOpenRouterProviderRouting } from "./openrouterRouting";

describe("buildOpenRouterProviderRouting", () => {
  it("defaults to throughput sort with fallbacks and deny data collection", () => {
    const routing = buildOpenRouterProviderRouting();

    assert.equal(routing.sort, "throughput");
    assert.equal(routing.allow_fallbacks, true);
    assert.equal(routing.require_parameters, false);
    assert.equal(routing.data_collection, "deny");
    assert.equal(routing.zdr, false);
    assert.equal(routing.order, undefined);
  });

  it("pins a preferred provider via order when set", () => {
    const routing = buildOpenRouterProviderRouting({
      preferredProvider: "deepinfra",
      allowFallbacks: false,
    });

    assert.deepEqual(routing.order, ["deepinfra"]);
    assert.equal(routing.sort, undefined);
    assert.equal(routing.allow_fallbacks, false);
  });

  it("requires provider parameters when requested", () => {
    const routing = buildOpenRouterProviderRouting({ requireParameters: true });

    assert.equal(routing.require_parameters, true);
    assert.equal(routing.sort, "throughput");
  });

  it("omits sort when sticky session routing is enabled", () => {
    const routing = buildOpenRouterProviderRouting({ stickySession: true });

    assert.equal(routing.sort, undefined);
    assert.equal(routing.order, undefined);
    assert.equal(routing.allow_fallbacks, true);
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildOpenRouterProviderRouting,
  buildProgramGenerationProviderRouting,
  PROGRAM_GENERATION_PREFERRED_MIN_THROUGHPUT,
} from "./openrouterRouting";

describe("buildOpenRouterProviderRouting", () => {
  it("defaults to throughput sort with fallbacks, deny data collection, and ZDR", () => {
    const routing = buildOpenRouterProviderRouting();

    assert.equal(routing.sort, "throughput");
    assert.equal(routing.allow_fallbacks, true);
    assert.equal(routing.require_parameters, false);
    assert.equal(routing.data_collection, "deny");
    assert.equal(routing.zdr, true);
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
    assert.equal(routing.zdr, true);
    assert.equal(routing.data_collection, "deny");
  });

  it("allows disabling ZDR when explicitly requested", () => {
    const routing = buildOpenRouterProviderRouting({ zeroDataRetention: false });
    assert.equal(routing.zdr, false);
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

  it("program generation sorts by price with throughput floor and ZDR", () => {
    const routing = buildProgramGenerationProviderRouting();

    assert.equal(routing.sort, "price");
    assert.equal(routing.require_parameters, true);
    assert.equal(routing.allow_fallbacks, true);
    assert.equal(routing.data_collection, "deny");
    assert.equal(routing.zdr, true);
    assert.deepEqual(routing.preferred_min_throughput, PROGRAM_GENERATION_PREFERRED_MIN_THROUGHPUT);
  });

  it("forwards preferredMinThroughput when set explicitly", () => {
    const routing = buildOpenRouterProviderRouting({
      sort: "price",
      preferredMinThroughput: { p50: 75 },
      stickySession: false,
    });

    assert.equal(routing.sort, "price");
    assert.deepEqual(routing.preferred_min_throughput, { p50: 75 });
  });
});

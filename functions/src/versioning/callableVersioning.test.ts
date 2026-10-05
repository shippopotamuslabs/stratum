import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  buildResultMeta,
  buildVersionedResult,
  logCallableVersion,
} from "./callableVersioning";
import { createLogger } from "../observability/structuredLog";

describe("buildResultMeta", () => {
  it("builds a version-only block when the major is current", () => {
    assert.deepEqual(buildResultMeta(1), { version: 1 });
  });

  it("builds a deprecation block while the major is deprecated", () => {
    assert.deepEqual(
      buildResultMeta(1, {
        sunsetDate: "2027-03-01",
        message: "Superseded by inferEquipmentIncrements_v2.",
      }),
      {
        version: 1,
        deprecation: {
          sunsetDate: "2027-03-01",
          message: "Superseded by inferEquipmentIncrements_v2.",
        },
      },
    );
  });

  it("copies the deprecation so later mutation cannot reshape the wire block", () => {
    const deprecation = { sunsetDate: "2027-03-01", message: "Superseded." };
    const meta = buildResultMeta(1, deprecation);
    deprecation.message = "mutated";
    assert.equal(meta.deprecation?.message, "Superseded.");
  });

  it("throws on non-positive-integer versions", () => {
    for (const version of [0, -1, 1.5, Number.NaN]) {
      assert.throws(() => buildResultMeta(version), /positive integer/);
    }
  });

  it("throws on malformed sunset dates and empty messages", () => {
    assert.throws(
      () => buildResultMeta(1, { sunsetDate: "March 2027", message: "Superseded." }),
      /YYYY-MM-DD/,
    );
    assert.throws(
      () => buildResultMeta(1, { sunsetDate: "2027-03-01", message: "" }),
      /non-empty/,
    );
  });
});

describe("buildVersionedResult", () => {
  it("spreads the payload with a _meta v1 baseline and no deprecation", () => {
    const payload = { unit: "lb", tiers: [{ lower: 10, upper: 50, step: 10 }] };
    assert.deepEqual(buildVersionedResult(payload, 1), {
      unit: "lb",
      tiers: [{ lower: 10, upper: 50, step: 10 }],
      _meta: { version: 1 },
    });
  });

  it("passes a deprecation through to the _meta block", () => {
    const result = buildVersionedResult({ ok: true }, 1, {
      sunsetDate: "2027-03-01",
      message: "Superseded.",
    });
    assert.deepEqual(result._meta, {
      version: 1,
      deprecation: { sunsetDate: "2027-03-01", message: "Superseded." },
    });
  });

  it("does not mutate the input payload", () => {
    const payload = { ok: true };
    buildVersionedResult(payload, 1);
    assert.deepEqual(payload, { ok: true });
  });
});

describe("logCallableVersion", () => {
  it("emits callable and version as top-level structured fields", () => {
    const lines: string[] = [];
    const logger = createLogger({
      service: "inferEquipmentIncrements",
      write: (line) => lines.push(line),
    });
    logCallableVersion(logger, "inferEquipmentIncrements", 1, { uid: "user-1" });
    assert.equal(lines.length, 1);
    assert.deepEqual(JSON.parse(lines[0] ?? ""), {
      severity: "INFO",
      message: "Callable inferEquipmentIncrements v1 served.",
      uid: "user-1",
      callable: "inferEquipmentIncrements",
      version: 1,
      service: "inferEquipmentIncrements",
    });
  });
});

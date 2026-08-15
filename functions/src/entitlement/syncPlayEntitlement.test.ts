import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  MAX_PURCHASE_TOKEN_LENGTH,
  hashPurchaseToken,
  parsePurchaseToken,
  playEntitlementWriteData,
} from "./syncPlayEntitlement";

const NOW = Date.parse("2026-08-14T20:00:00.000Z");
const FUTURE = Date.parse("2026-12-01T00:00:00.000Z");
const PAST = Date.parse("2026-01-01T00:00:00.000Z");

describe("parsePurchaseToken", () => {
  it("accepts a non-empty token", () => {
    assert.equal(parsePurchaseToken({ purchaseToken: "play-token-1" }), "play-token-1");
  });

  it("rejects missing, empty, or non-string tokens", () => {
    const expectInvalid = (data: unknown) => {
      assert.throws(
        () => parsePurchaseToken(data),
        (err: { code?: string }) => {
          assert.equal(err.code, "invalid-argument");
          return true;
        },
      );
    };
    expectInvalid(undefined);
    expectInvalid({});
    expectInvalid({ purchaseToken: "" });
    expectInvalid({ purchaseToken: "   " });
    expectInvalid({ purchaseToken: 12 });
  });

  it("rejects oversized tokens", () => {
    assert.throws(
      () => parsePurchaseToken({ purchaseToken: "x".repeat(MAX_PURCHASE_TOKEN_LENGTH + 1) }),
      (err: { code?: string; message?: string }) => {
        assert.equal(err.code, "invalid-argument");
        assert.match(String(err.message), /too long/);
        return true;
      },
    );
  });
});

describe("hashPurchaseToken", () => {
  it("stores a sha256 hex fingerprint, not the raw token", () => {
    const token = "raw-purchase-token";
    const hash = hashPurchaseToken(token);
    assert.equal(hash.includes(token), false);
    assert.equal(hash, createHash("sha256").update(token, "utf8").digest("hex"));
    assert.equal(hash.length, 64);
  });
});

describe("playEntitlementWriteData", () => {
  it("omits past expiry when still active (grace) so membership has no expiry gate", () => {
    assert.deepEqual(
      playEntitlementWriteData(
        { active: true, productId: "stratum_membership", expiryTimeMillis: PAST },
        "abc",
        NOW,
      ),
      { active: true, purchaseTokenHash: "abc", productId: "stratum_membership" },
    );
  });

  it("keeps future expiry on an active sub", () => {
    assert.deepEqual(
      playEntitlementWriteData(
        { active: true, productId: "stratum_membership", expiryTimeMillis: FUTURE },
        "abc",
        NOW,
      ),
      {
        active: true,
        purchaseTokenHash: "abc",
        productId: "stratum_membership",
        expiryTimeMillis: FUTURE,
      },
    );
  });

  it("writes inactive play docs (restore with no subscription)", () => {
    assert.deepEqual(
      playEntitlementWriteData({ active: false, expiryTimeMillis: PAST }, "abc", NOW),
      { active: false, purchaseTokenHash: "abc", expiryTimeMillis: PAST },
    );
  });
});

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  PLAY_PACKAGE_NAME,
  PlayPublisherError,
  mapSubscriptionPurchaseV2,
  playSubscriptionsv2Url,
  verifyPlayPurchaseToken,
} from "./playPublisher";

const NOW = Date.parse("2026-08-14T20:00:00.000Z");
const FUTURE = "2026-12-01T00:00:00.000Z";
const PAST = "2026-01-01T00:00:00.000Z";
const FUTURE_MS = Date.parse(FUTURE);
const PAST_MS = Date.parse(PAST);

function purchaseV2(state: string, expiryTime?: string, productId = "stratum_membership") {
  return {
    kind: "androidpublisher#subscriptionPurchaseV2",
    subscriptionState: state,
    lineItems: [
      {
        productId,
        expiryTime,
      },
    ],
  };
}

describe("playSubscriptionsv2Url", () => {
  it("uses Play applicationId com.aether.stratum and encodes the token", () => {
    assert.equal(PLAY_PACKAGE_NAME, "com.aether.stratum");
    assert.equal(
      playSubscriptionsv2Url("abc+def/g="),
      "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
        "com.aether.stratum/purchases/subscriptionsv2/tokens/abc%2Bdef%2Fg%3D",
    );
  });
});

describe("mapSubscriptionPurchaseV2", () => {
  it("maps ACTIVE with future expiry to active + productId + expiryTimeMillis", () => {
    assert.deepEqual(mapSubscriptionPurchaseV2(purchaseV2("SUBSCRIPTION_STATE_ACTIVE", FUTURE), NOW), {
      active: true,
      productId: "stratum_membership",
      expiryTimeMillis: FUTURE_MS,
    });
  });

  it("maps IN_GRACE_PERIOD as active even with past expiry", () => {
    assert.deepEqual(
      mapSubscriptionPurchaseV2(purchaseV2("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", PAST), NOW),
      {
        active: true,
        productId: "stratum_membership",
        expiryTimeMillis: PAST_MS,
      },
    );
  });

  it("maps CANCELED in paid window (future expiry) as active", () => {
    assert.equal(
      mapSubscriptionPurchaseV2(purchaseV2("SUBSCRIPTION_STATE_CANCELED", FUTURE), NOW).active,
      true,
    );
  });

  it("maps CANCELED with past expiry as inactive", () => {
    assert.equal(
      mapSubscriptionPurchaseV2(purchaseV2("SUBSCRIPTION_STATE_CANCELED", PAST), NOW).active,
      false,
    );
  });

  it("maps EXPIRED as inactive", () => {
    assert.equal(
      mapSubscriptionPurchaseV2(purchaseV2("SUBSCRIPTION_STATE_EXPIRED", PAST), NOW).active,
      false,
    );
  });

  it("fail-closes on malformed JSON", () => {
    assert.deepEqual(mapSubscriptionPurchaseV2(null, NOW), { active: false });
    assert.deepEqual(mapSubscriptionPurchaseV2("nope", NOW), { active: false });
    assert.deepEqual(mapSubscriptionPurchaseV2({}, NOW), { active: false });
  });
});

describe("verifyPlayPurchaseToken", () => {
  it("maps 404 to inactive without throwing", async () => {
    const snapshot = await verifyPlayPurchaseToken("missing-token", NOW, async () => ({
      status: 404,
      data: { error: { code: 404 } },
    }));
    assert.deepEqual(snapshot, { active: false });
  });

  it("throws PlayPublisherError on non-404 HTTP failures", async () => {
    await assert.rejects(
      () =>
        verifyPlayPurchaseToken("tok", NOW, async () => ({
          status: 500,
          data: { error: "unavailable" },
        })),
      (err: unknown) => {
        assert.ok(err instanceof PlayPublisherError);
        assert.equal(err.status, 500);
        return true;
      },
    );
  });

  it("maps a successful Publisher payload without hitting live Play", async () => {
    const snapshot = await verifyPlayPurchaseToken("tok", NOW, async () => ({
      status: 200,
      data: purchaseV2("SUBSCRIPTION_STATE_ACTIVE", FUTURE),
    }));
    assert.equal(snapshot.active, true);
    assert.equal(snapshot.productId, "stratum_membership");
    assert.equal(snapshot.expiryTimeMillis, FUTURE_MS);
  });
});

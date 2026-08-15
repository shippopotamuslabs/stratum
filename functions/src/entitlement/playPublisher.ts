/**
 * Android Publisher API v3 (subscriptionsv2) verification.
 *
 * Auth is Application Default Credentials (Cloud Functions runtime service account).
 * Grant that service account Play Console access: View app information, View financial data,
 * and View / manage orders and subscriptions. Package name is the Play applicationId
 * `com.aether.stratum`, not the Kotlin source namespace.
 *
 * RTDN (Real-time developer notifications) is not wired yet; `syncPlayEntitlement` is the
 * client-driven refresh path.
 */
import { GoogleAuth } from "google-auth-library";

export const PLAY_PACKAGE_NAME = "com.aether.stratum";
export const ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

export const SUBSCRIPTION_STATE_ACTIVE = "SUBSCRIPTION_STATE_ACTIVE";
export const SUBSCRIPTION_STATE_IN_GRACE_PERIOD = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
export const SUBSCRIPTION_STATE_CANCELED = "SUBSCRIPTION_STATE_CANCELED";

export type PlaySubscriptionSnapshot = {
  active: boolean;
  productId?: string;
  expiryTimeMillis?: number;
};

export type PublisherRequest = (url: string) => Promise<{ status: number; data: unknown }>;

export class PlayPublisherError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "PlayPublisherError";
  }
}

export function playSubscriptionsv2Url(purchaseToken: string): string {
  return (
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${PLAY_PACKAGE_NAME}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`
  );
}

function parseRfc3339Millis(value: unknown): number | undefined {
  if (typeof value !== "string" || value.length === 0) {
    return undefined;
  }
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) {
    return undefined;
  }
  return ms;
}

function lineItemSnapshot(raw: unknown): { productId?: string; expiryTimeMillis?: number } {
  if (!Array.isArray(raw)) {
    return {};
  }
  let bestExpiry: number | undefined;
  let productId: string | undefined;
  for (const item of raw) {
    if (!item || typeof item !== "object") {
      continue;
    }
    const rec = item as Record<string, unknown>;
    const expiry = parseRfc3339Millis(rec.expiryTime);
    if (typeof rec.productId === "string" && rec.productId.length > 0 && productId == null) {
      productId = rec.productId;
    }
    if (expiry != null && (bestExpiry == null || expiry > bestExpiry)) {
      bestExpiry = expiry;
      if (typeof rec.productId === "string" && rec.productId.length > 0) {
        productId = rec.productId;
      }
    }
  }
  const out: { productId?: string; expiryTimeMillis?: number } = {};
  if (productId) {
    out.productId = productId;
  }
  if (bestExpiry != null) {
    out.expiryTimeMillis = bestExpiry;
  }
  return out;
}

/**
 * Map Android Publisher `SubscriptionPurchaseV2` JSON to a Play entitlement snapshot.
 * Fail closed on malformed payloads. Does not call Play.
 */
export function mapSubscriptionPurchaseV2(
  json: unknown,
  nowMs: number = Date.now(),
): PlaySubscriptionSnapshot {
  if (!json || typeof json !== "object") {
    return { active: false };
  }
  const rec = json as Record<string, unknown>;
  const lines = lineItemSnapshot(rec.lineItems);
  const state = rec.subscriptionState;
  let active = false;
  if (state === SUBSCRIPTION_STATE_ACTIVE || state === SUBSCRIPTION_STATE_IN_GRACE_PERIOD) {
    active = true;
  } else if (state === SUBSCRIPTION_STATE_CANCELED) {
    active = lines.expiryTimeMillis != null && lines.expiryTimeMillis > nowMs;
  }
  const out: PlaySubscriptionSnapshot = { active };
  if (lines.productId) {
    out.productId = lines.productId;
  }
  if (lines.expiryTimeMillis != null) {
    out.expiryTimeMillis = lines.expiryTimeMillis;
  }
  return out;
}

function httpStatus(err: unknown): number | undefined {
  if (!err || typeof err !== "object") {
    return undefined;
  }
  const response = (err as { response?: { status?: unknown } }).response;
  if (response && typeof response.status === "number") {
    return response.status;
  }
  const status = (err as { status?: unknown }).status;
  return typeof status === "number" ? status : undefined;
}

async function defaultPublisherRequest(url: string): Promise<{ status: number; data: unknown }> {
  const auth = new GoogleAuth({ scopes: [ANDROID_PUBLISHER_SCOPE] });
  const client = await auth.getClient();
  const res = await client.request<unknown>({ url, method: "GET" });
  return { status: res.status ?? 200, data: res.data };
}

/**
 * Verify a Play purchase token. 404 → `{ active: false }`. Other HTTP / network errors
 * throw `PlayPublisherError` (fail closed; caller must not grant access).
 */
export async function verifyPlayPurchaseToken(
  purchaseToken: string,
  nowMs: number = Date.now(),
  request: PublisherRequest = defaultPublisherRequest,
): Promise<PlaySubscriptionSnapshot> {
  const url = playSubscriptionsv2Url(purchaseToken);
  try {
    const { status, data } = await request(url);
    if (status === 404) {
      return { active: false };
    }
    if (status < 200 || status >= 300) {
      throw new PlayPublisherError(status, `Play Publisher HTTP ${status}`);
    }
    return mapSubscriptionPurchaseV2(data, nowMs);
  } catch (err) {
    if (err instanceof PlayPublisherError) {
      throw err;
    }
    const status = httpStatus(err);
    if (status === 404) {
      return { active: false };
    }
    throw new PlayPublisherError(status ?? 0, "Play Publisher request failed");
  }
}

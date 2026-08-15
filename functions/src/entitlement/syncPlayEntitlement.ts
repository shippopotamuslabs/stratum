/**
 * Callable: verify a Play purchase token and Admin-write `users/{uid}/entitlement/play`.
 *
 * Does not store the raw purchase token — only a SHA-256 fingerprint.
 * RTDN is future; this callable is the restore / post-purchase sync path.
 */
import * as admin from "firebase-admin";
import { createHash } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  PLAY_ENTITLEMENT_DOC_ID,
  entitlementDocPath,
  isUserEntitled,
} from "./assertEntitled";
import {
  PlayPublisherError,
  type PlaySubscriptionSnapshot,
  verifyPlayPurchaseToken,
} from "./playPublisher";

export const MAX_PURCHASE_TOKEN_LENGTH = 4096;

export function hashPurchaseToken(purchaseToken: string): string {
  return createHash("sha256").update(purchaseToken, "utf8").digest("hex");
}

export function parsePurchaseToken(data: unknown): string {
  if (!data || typeof data !== "object") {
    throw new HttpsError("invalid-argument", "purchaseToken is required.");
  }
  const token = (data as { purchaseToken?: unknown }).purchaseToken;
  if (typeof token !== "string" || token.trim().length === 0) {
    throw new HttpsError("invalid-argument", "purchaseToken is required.");
  }
  if (token.length > MAX_PURCHASE_TOKEN_LENGTH) {
    throw new HttpsError("invalid-argument", "purchaseToken is too long.");
  }
  return token;
}

/**
 * Fields written to the Play entitlement doc (plus `updatedAt` at write time).
 * Omit a past expiry when still active (grace period) so `isUserEntitled` treats it as
 * no-expiry. Inactive docs may keep a past expiry for diagnostics.
 */
export function playEntitlementWriteData(
  snapshot: PlaySubscriptionSnapshot,
  purchaseTokenHash: string,
  nowMs: number,
): {
  active: boolean;
  purchaseTokenHash: string;
  productId?: string;
  expiryTimeMillis?: number;
} {
  const data: {
    active: boolean;
    purchaseTokenHash: string;
    productId?: string;
    expiryTimeMillis?: number;
  } = {
    active: snapshot.active,
    purchaseTokenHash,
  };
  if (snapshot.productId) {
    data.productId = snapshot.productId;
  }
  if (
    snapshot.expiryTimeMillis != null &&
    (!snapshot.active || snapshot.expiryTimeMillis > nowMs)
  ) {
    data.expiryTimeMillis = snapshot.expiryTimeMillis;
  }
  return data;
}

export const syncPlayEntitlement = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const purchaseToken = parsePurchaseToken(request.data);

  let snapshot: PlaySubscriptionSnapshot;
  try {
    snapshot = await verifyPlayPurchaseToken(purchaseToken);
  } catch (err) {
    if (err instanceof PlayPublisherError) {
      throw new HttpsError("failed-precondition", "Could not verify Play subscription.");
    }
    throw err;
  }

  const nowMs = Date.now();
  const fields = playEntitlementWriteData(snapshot, hashPurchaseToken(purchaseToken), nowMs);
  // RTDN is future; this Admin write is the current Play snapshot (no raw purchase token).
  await admin.firestore().doc(entitlementDocPath(uid, PLAY_ENTITLEMENT_DOC_ID)).set({
    ...fields,
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { entitled: await isUserEntitled(uid, nowMs) };
});

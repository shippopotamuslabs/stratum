/**
 * Server-side membership check. Cloud jobs admit only entitled users.
 *
 * Entitled if:
 * - `users/{uid}/entitlement/override` has `{ freeAccess: true }` (Admin / complimentary VIP), or
 * - `users/{uid}/entitlement/play` has `{ active: true }` and no expiry, or expiryTimeMillis > now.
 *
 * Clients cannot write these docs (Firestore rules write:false). Play purchases are verified
 * via Android Publisher API then written by `syncPlayEntitlement`.
 */
import * as admin from "firebase-admin";
import { HttpsError } from "firebase-functions/v2/https";

export const OVERRIDE_ENTITLEMENT_DOC_ID = "override";
export const PLAY_ENTITLEMENT_DOC_ID = "play";
export const MEMBERSHIP_REQUIRED_MESSAGE = "Stratum membership required.";

export type EntitlementDocSnapshot = {
  exists: boolean;
  data(): Record<string, unknown> | undefined;
};

export type EntitlementFirestore = {
  doc(path: string): {
    get(): Promise<EntitlementDocSnapshot>;
  };
};

export function entitlementDocPath(uid: string, docId: string): string {
  return `users/${uid}/entitlement/${docId}`;
}

function defaultEntitlementFirestore(): EntitlementFirestore {
  return admin.firestore();
}

let entitlementFirestoreGetter: () => EntitlementFirestore = defaultEntitlementFirestore;

/** Test-only: replace Admin Firestore used by entitlement reads. */
export function setEntitlementFirestoreGetter(getter: () => EntitlementFirestore): void {
  entitlementFirestoreGetter = getter;
}

export function resetEntitlementFirestoreGetter(): void {
  entitlementFirestoreGetter = defaultEntitlementFirestore;
}

export type EntitlementDocs = {
  override?: Record<string, unknown>;
  play?: Record<string, unknown>;
};

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

/**
 * Pure membership decision from already-loaded entitlement docs.
 */
export function isEntitledFromDocs(docs: EntitlementDocs, nowMs: number): boolean {
  if (docs.override?.freeAccess === true) {
    return true;
  }
  const play = docs.play;
  if (!play || play.active !== true) {
    return false;
  }
  if (!Object.prototype.hasOwnProperty.call(play, "expiryTimeMillis") || play.expiryTimeMillis == null) {
    return true;
  }
  return isFiniteNumber(play.expiryTimeMillis) && play.expiryTimeMillis > nowMs;
}

export async function isUserEntitled(
  uid: string,
  nowMs?: number,
  getFirestore?: () => EntitlementFirestore,
): Promise<boolean> {
  const now = nowMs ?? Date.now();
  const db = (getFirestore ?? entitlementFirestoreGetter)();
  const overrideSnap = await db.doc(entitlementDocPath(uid, OVERRIDE_ENTITLEMENT_DOC_ID)).get();
  const playSnap = await db.doc(entitlementDocPath(uid, PLAY_ENTITLEMENT_DOC_ID)).get();
  return isEntitledFromDocs(
    {
      override: overrideSnap.exists ? overrideSnap.data() : undefined,
      play: playSnap.exists ? playSnap.data() : undefined,
    },
    now,
  );
}

export async function assertUserEntitled(
  uid: string,
  nowMs?: number,
  getFirestore?: () => EntitlementFirestore,
): Promise<void> {
  if (!(await isUserEntitled(uid, nowMs, getFirestore))) {
    throw new HttpsError("permission-denied", MEMBERSHIP_REQUIRED_MESSAGE);
  }
}

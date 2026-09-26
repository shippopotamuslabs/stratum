/**
 * Daily + monthly fair-use quota for increment-scan (photo-to-tiers) calls.
 *
 * Caps are 30 scans per uid per UTC calendar month and 10 scans per uid per
 * UTC calendar day. Every admitted callable invocation consumes budget, even
 * when the model later returns an empty scan (compute abuse).
 *
 * Doc path: `users/{uid}/incrementsScanQuota/current`
 * Fields: `{ monthKey: "YYYY-MM", dayKey: "YYYY-MM-DD", monthCount, dayCount, updatedAt }`
 *
 * Admission (read + cap check + increment) is a single Firestore transaction
 * owned by `admitIncrementScan`, called from `inferEquipmentIncrements`
 * after payload validation and the fail-closed secret check, so retries and
 * misconfigured calls never double-spend or spend budget.
 */
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import type { DocumentReference, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/** Increment scans allowed per uid per UTC calendar month. */
export const MAX_SCANS_PER_MONTH = 30;

/** Increment scans allowed per uid per UTC calendar day. */
export const MAX_SCANS_PER_DAY = 10;

export const SCAN_QUOTA_DOC_ID = "current";

export type ScanQuotaDoc = {
  monthKey: string;
  dayKey: string;
  /** Number of admitted scans in `monthKey`. */
  monthCount: number;
  /** Number of admitted scans in `dayKey`. */
  dayCount: number;
};

/** Minimal Firestore surface used for quota admission (fakes stay small). */
export type ScanQuotaFirestore = {
  doc(path: string): DocumentReference;
  runTransaction<T>(updateFunction: (tx: Transaction) => Promise<T>): Promise<T>;
};

/** UTC calendar month key, e.g. `"2026-07"`. */
export function utcMonthKey(date: Date = new Date()): string {
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth() + 1;
  return `${y.toString().padStart(4, "0")}-${m.toString().padStart(2, "0")}`;
}

/** UTC calendar day key, e.g. `"2026-07-14"`. */
export function utcDayKey(date: Date = new Date()): string {
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth() + 1;
  const d = date.getUTCDate();
  return (
    `${y.toString().padStart(4, "0")}-${m.toString().padStart(2, "0")}-` +
    `${d.toString().padStart(2, "0")}`
  );
}

export function scanQuotaPath(uid: string): string {
  return `users/${uid}/incrementsScanQuota/${SCAN_QUOTA_DOC_ID}`;
}

function nonNegInt(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
    return 0;
  }
  return Math.floor(value);
}

/** Effective scan count for [monthKey]. Missing/rolled month → 0. */
function effectiveMonthCount(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  monthKey: string,
): number {
  if (!doc || doc.monthKey !== monthKey) {
    return 0;
  }
  return nonNegInt(doc.monthCount);
}

/** Effective scan count for [dayKey]. Missing/rolled day → 0. */
function effectiveDayCount(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  dayKey: string,
): number {
  if (!doc || doc.dayKey !== dayKey) {
    return 0;
  }
  return nonNegInt(doc.dayCount);
}

/** True when the user is already at the monthly scan cap for [monthKey]. */
function isAtMonthlyScanQuota(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  monthKey: string,
  cap: number = MAX_SCANS_PER_MONTH,
): boolean {
  return effectiveMonthCount(doc, monthKey) >= cap;
}

/** True when the user is already at the daily scan cap for [dayKey]. */
function isAtDailyScanQuota(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  dayKey: string,
  cap: number = MAX_SCANS_PER_DAY,
): boolean {
  return effectiveDayCount(doc, dayKey) >= cap;
}

/** Next stored shape after one scan admission in [monthKey]/[dayKey]. */
function nextQuotaAfterScan(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  monthKey: string,
  dayKey: string,
): ScanQuotaDoc {
  return {
    monthKey,
    dayKey,
    monthCount: effectiveMonthCount(doc, monthKey) + 1,
    dayCount: effectiveDayCount(doc, dayKey) + 1,
  };
}

/**
 * Throws `resource-exhausted` when the caller is at either the monthly or the
 * daily scan cap. Pure (no Firestore) so cap enforcement is unit-testable.
 */
export function assertScanQuotaAllowed(
  doc: Partial<ScanQuotaDoc> | undefined | null,
  monthKey: string,
  dayKey: string,
): void {
  if (isAtMonthlyScanQuota(doc, monthKey)) {
    throw new HttpsError(
      "resource-exhausted",
      `Monthly scan limit reached (${MAX_SCANS_PER_MONTH} per month). Try again next month.`,
    );
  }
  if (isAtDailyScanQuota(doc, dayKey)) {
    throw new HttpsError(
      "resource-exhausted",
      `Daily scan limit reached (${MAX_SCANS_PER_DAY} per day). Try again tomorrow.`,
    );
  }
}

/**
 * Admits one increment scan for [uid]: runs the read + cap check + counter
 * increment in a single Firestore transaction. Throws `resource-exhausted`
 * at either cap; anything else surfaces as `internal`.
 */
export async function admitIncrementScan(
  uid: string,
  deps: { firestore?: ScanQuotaFirestore; now?: Date } = {},
): Promise<void> {
  const db = deps.firestore ?? admin.firestore();
  const now = deps.now ?? new Date();
  const monthKey = utcMonthKey(now);
  const dayKey = utcDayKey(now);
  const quotaRef = db.doc(scanQuotaPath(uid));

  try {
    await db.runTransaction(async (tx) => {
      const quotaSnap = await tx.get(quotaRef);
      const quotaData = quotaSnap.exists
        ? (quotaSnap.data() as Partial<ScanQuotaDoc>)
        : undefined;
      assertScanQuotaAllowed(quotaData, monthKey, dayKey);
      const next = nextQuotaAfterScan(quotaData, monthKey, dayKey);
      tx.set(
        quotaRef,
        {
          monthKey: next.monthKey,
          dayKey: next.dayKey,
          monthCount: next.monthCount,
          dayCount: next.dayCount,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
    });
  } catch (err) {
    if (err instanceof HttpsError) {
      throw err;
    }
    console.error(`admitIncrementScan transaction failed for ${uid}:`, err);
    throw new HttpsError("internal", "Could not check scan quota. Please try again.");
  }
}

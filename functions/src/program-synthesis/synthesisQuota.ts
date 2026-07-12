/**
 * Monthly fair-use quota for program-synthesis jobs.
 *
 * Cap is 10 **starts** per uid per calendar month (UTC) so failed full-length solves still
 * consume budget (compute abuse). Success count is still tracked for analytics/TOS.
 *
 * Doc path: `users/{uid}/synthesisQuota/current`
 * Fields: `{ monthKey: "YYYY-MM", startCount: number, successCount: number, updatedAt }`
 *
 * Success counting is owned by the Firestore trigger `onSynthesisJobWritten`.
 * Start counting is owned by `startProgramSynthesis` admission transaction.
 */
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/** Program-synthesis **starts** allowed per uid per UTC calendar month (failed or ready). */
export const MAX_SYNTHESIS_PER_MONTH = 10;

/** Document-only request size budget; enforced via Storage rules + start metadata check. */
export const MAX_REQUEST_BYTES = 5 * 1024 * 1024;

export const SYNTHESIS_QUOTA_DOC_ID = "current";

export type SynthesisQuotaDoc = {
  monthKey: string;
  /** Number of start admissions this month (incremented on successful start callable). */
  startCount: number;
  /** Number of ready jobs this month (incremented on ready + quotaCounted). */
  successCount: number;
};

/** UTC calendar month key, e.g. `"2026-07"`. */
export function utcMonthKey(date: Date = new Date()): string {
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth() + 1;
  return `${y.toString().padStart(4, "0")}-${m.toString().padStart(2, "0")}`;
}

export function synthesisQuotaPath(uid: string): string {
  return `users/${uid}/synthesisQuota/${SYNTHESIS_QUOTA_DOC_ID}`;
}

function nonNegInt(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
    return 0;
  }
  return Math.floor(value);
}

/**
 * Effective start count for [monthKey]. Missing/rolled month → 0.
 * Backward compatible: if startCount missing but successCount present for same month,
 * treat startCount as at least successCount (legacy docs).
 */
export function effectiveStartCount(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
): number {
  if (!doc || doc.monthKey !== monthKey) {
    return 0;
  }
  const starts = nonNegInt(doc.startCount);
  const successes = nonNegInt(doc.successCount);
  return Math.max(starts, successes);
}

/**
 * Effective success count for [monthKey] given a stored quota doc (or missing doc).
 * Rolled months restart at 0.
 */
export function effectiveSuccessCount(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
): number {
  if (!doc || doc.monthKey !== monthKey) {
    return 0;
  }
  return nonNegInt(doc.successCount);
}

/** True when the user is already at the monthly **start** cap for [monthKey]. */
export function isAtMonthlyQuota(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
  cap: number = MAX_SYNTHESIS_PER_MONTH,
): boolean {
  return effectiveStartCount(doc, monthKey) >= cap;
}

/**
 * Next stored shape after one **start** admission in [monthKey].
 */
export function nextQuotaAfterStart(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
): SynthesisQuotaDoc {
  return {
    monthKey,
    startCount: effectiveStartCount(doc, monthKey) + 1,
    successCount: effectiveSuccessCount(doc, monthKey),
  };
}

/**
 * Next stored shape after one successful job in [monthKey].
 */
export function nextQuotaAfterSuccess(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
): SynthesisQuotaDoc {
  return {
    monthKey,
    startCount: effectiveStartCount(doc, monthKey),
    successCount: effectiveSuccessCount(doc, monthKey) + 1,
  };
}

/**
 * Throws `resource-exhausted` when the caller has already used [MAX_SYNTHESIS_PER_MONTH]
 * synthesis **starts** in the current UTC month.
 */
export async function assertUnderMonthlyQuota(uid: string): Promise<void> {
  const monthKey = utcMonthKey();
  const snap = await admin.firestore().doc(synthesisQuotaPath(uid)).get();
  const data = snap.exists ? (snap.data() as Partial<SynthesisQuotaDoc>) : undefined;
  if (isAtMonthlyQuota(data, monthKey)) {
    throw new HttpsError(
      "resource-exhausted",
      `Monthly program generation limit reached (${MAX_SYNTHESIS_PER_MONTH} per month). Try again next month.`,
    );
  }
}

/**
 * Idempotent success increment keyed by job: only counts when the job is not yet
 * `quotaCounted`. Sets `quotaCounted: true` on the job in the same transaction as the
 * quota bump so retries never double-count.
 */
export async function incrementSynthesisSuccessIfNeeded(
  uid: string,
  jobId: string,
): Promise<boolean> {
  const db = admin.firestore();
  const jobRef = db.doc(`users/${uid}/synthesisJobs/${jobId}`);
  const quotaRef = db.doc(synthesisQuotaPath(uid));
  const monthKey = utcMonthKey();

  return db.runTransaction(async (tx) => {
    const jobSnap = await tx.get(jobRef);
    if (!jobSnap.exists) {
      return false;
    }
    const job = jobSnap.data()!;
    if (job.status !== "ready" || job.quotaCounted === true) {
      return false;
    }

    const quotaSnap = await tx.get(quotaRef);
    const quotaData = quotaSnap.exists
      ? (quotaSnap.data() as Partial<SynthesisQuotaDoc>)
      : undefined;
    const next = nextQuotaAfterSuccess(quotaData, monthKey);

    tx.set(
      quotaRef,
      {
        monthKey: next.monthKey,
        startCount: next.startCount,
        successCount: next.successCount,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    tx.update(jobRef, {
      quotaCounted: true,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return true;
  });
}

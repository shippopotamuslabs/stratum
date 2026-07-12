/**
 * Monthly fair-use quota for successful program-synthesis jobs.
 *
 * Cap is 10 **successful** (`ready`) jobs per uid per calendar month (UTC).
 * Doc path: `users/{uid}/synthesisQuota/current`
 * Fields: `{ monthKey: "YYYY-MM", successCount: number, updatedAt }`
 *
 * Success counting is owned by the Firestore trigger `onSynthesisJobWritten` so the JVM
 * worker only needs to mark jobs `ready` — quota logic stays in Node.
 */
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

/** Successful program-synthesis jobs allowed per uid per UTC calendar month. */
export const MAX_SYNTHESIS_PER_MONTH = 10;

/** Document-only request size budget (client uploads; start may check metadata later). */
export const MAX_REQUEST_BYTES = 5 * 1024 * 1024;

export const SYNTHESIS_QUOTA_DOC_ID = "current";

export type SynthesisQuotaDoc = {
  monthKey: string;
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
  const n = doc.successCount;
  if (typeof n !== "number" || !Number.isFinite(n) || n < 0) {
    return 0;
  }
  return Math.floor(n);
}

/** True when the user is already at the monthly success cap for [monthKey]. */
export function isAtMonthlyQuota(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
  cap: number = MAX_SYNTHESIS_PER_MONTH,
): boolean {
  return effectiveSuccessCount(doc, monthKey) >= cap;
}

/**
 * Next stored shape after one successful job in [monthKey].
 * Pure helper for tests and shared with [incrementSuccessCount].
 */
export function nextQuotaAfterSuccess(
  doc: Partial<SynthesisQuotaDoc> | undefined | null,
  monthKey: string,
): SynthesisQuotaDoc {
  return {
    monthKey,
    successCount: effectiveSuccessCount(doc, monthKey) + 1,
  };
}

/**
 * Throws `resource-exhausted` when the caller has already used [MAX_SYNTHESIS_PER_MONTH]
 * successful synthesis jobs in the current UTC month.
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
 * Increment the uid's success counter for the current UTC month.
 * Safe to call more than once for different jobs; callers must ensure per-job idempotency
 * (see `onSynthesisJobWritten` + `quotaCounted`).
 */
export async function incrementSuccessCount(uid: string): Promise<SynthesisQuotaDoc> {
  const monthKey = utcMonthKey();
  const ref = admin.firestore().doc(synthesisQuotaPath(uid));
  return admin.firestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.exists ? (snap.data() as Partial<SynthesisQuotaDoc>) : undefined;
    const next = nextQuotaAfterSuccess(data, monthKey);
    tx.set(
      ref,
      {
        monthKey: next.monthKey,
        successCount: next.successCount,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    return next;
  });
}

/**
 * Idempotent success increment keyed by job: only counts when the job is not yet
 * `quotaCounted`. Sets `quotaCounted: true` on the job in the same transaction as the
 * quota bump so retries never double-count.
 *
 * Prefer the Firestore trigger path; this helper is also available if a worker needs to
 * call Admin-side quota counting later.
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

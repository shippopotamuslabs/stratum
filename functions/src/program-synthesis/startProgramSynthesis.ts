import { getStorage } from "firebase-admin/storage";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { isSynthesisJobStale } from "./synthesisJobGuards";
import { assertUserEntitled } from "../entitlement/assertEntitled";
import { clientStartMsToTimestamp, buildJobResetTrace } from "./synthesisTrace";
import {
  MAX_REQUEST_BYTES,
  MAX_SYNTHESIS_PER_MONTH,
  effectiveStartCount,
  nextQuotaAfterStart,
  synthesisQuotaPath,
  utcMonthKey,
  type SynthesisQuotaDoc,
} from "./synthesisQuota";

/**
 * Validate that [requestStoragePath] is under `synthesis/{uid}/{jobId}/request.json`.
 * Client uploads the request JSON first, then calls this callable.
 */
export function isValidSynthesisRequestStoragePath(
  uid: string,
  jobId: string,
  requestStoragePath: string,
): boolean {
  if (typeof requestStoragePath !== "string" || requestStoragePath.length === 0) {
    return false;
  }
  if (requestStoragePath.includes("..") || requestStoragePath.includes("//")) {
    return false;
  }
  if (requestStoragePath.startsWith("/") || requestStoragePath.includes("\\")) {
    return false;
  }
  const expected = `synthesis/${uid}/${jobId}/request.json`;
  return requestStoragePath === expected;
}

class AdmissionError extends Error {
  constructor(
    readonly code: "resource-exhausted" | "failed-precondition" | "invalid-argument",
    message: string,
  ) {
    super(message);
    this.name = "AdmissionError";
  }
}

/**
 * Enqueue a program-synthesis job. The client uploads the CP-SAT request document to Storage
 * first, then calls this with the Storage path; this writes the job doc that
 * `processProgramSynthesisJob` dispatches to the JVM Cloud Run solver.
 *
 * Admission is a single Firestore transaction: monthly start quota + single active job +
 * job create. Stale pending/running jobs are failed inside that transaction so a stuck
 * worker cannot permanently block retries.
 */
export const startProgramSynthesis = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  await assertUserEntitled(uid);
  const data = request.data as {
    jobId?: string;
    requestStoragePath?: string;
    clientStartMs?: number;
  };
  if (!data.jobId || !data.requestStoragePath) {
    throw new HttpsError(
      "invalid-argument",
      "jobId and requestStoragePath are required.",
    );
  }
  const clientStart =
    data.clientStartMs !== undefined ? clientStartMsToTimestamp(data.clientStartMs) : null;
  if (typeof data.jobId !== "string" || data.jobId.length === 0 || data.jobId.includes("/")) {
    throw new HttpsError("invalid-argument", "jobId is invalid.");
  }
  if (!isValidSynthesisRequestStoragePath(uid, data.jobId, data.requestStoragePath)) {
    throw new HttpsError(
      "invalid-argument",
      `requestStoragePath must be synthesis/${uid}/${data.jobId}/request.json.`,
    );
  }

  // Enforce size before spending quota (Storage rules also cap; this is defense in depth).
  try {
    const [meta] = await getStorage().bucket().file(data.requestStoragePath).getMetadata();
    const size = Number(meta.size ?? 0);
    if (!Number.isFinite(size) || size <= 0) {
      throw new HttpsError("invalid-argument", "Request object is missing or empty.");
    }
    if (size > MAX_REQUEST_BYTES) {
      throw new HttpsError(
        "invalid-argument",
        `Request is too large (${size} bytes). Maximum is ${MAX_REQUEST_BYTES} bytes.`,
      );
    }
  } catch (err) {
    if (err instanceof HttpsError) {
      throw err;
    }
    console.warn(`Could not read request metadata for ${data.requestStoragePath}: ${err}`);
    throw new HttpsError(
      "failed-precondition",
      "Upload the generation request before starting synthesis.",
    );
  }

  const db = getFirestore();
  const monthKey = utcMonthKey();
  const quotaRef = db.doc(synthesisQuotaPath(uid));
  const jobRef = db.doc(`users/${uid}/synthesisJobs/${data.jobId}`);
  const activeQuery = db
    .collection(`users/${uid}/synthesisJobs`)
    .where("status", "in", ["pending", "running"])
    .limit(10);

  try {
    await db.runTransaction(async (tx) => {
      const quotaSnap = await tx.get(quotaRef);
      const activeSnap = await tx.get(activeQuery);
      const existing = await tx.get(jobRef);

      const quotaData = quotaSnap.exists
        ? (quotaSnap.data() as Partial<SynthesisQuotaDoc>)
        : undefined;
      if (effectiveStartCount(quotaData, monthKey) >= MAX_SYNTHESIS_PER_MONTH) {
        throw new AdmissionError(
          "resource-exhausted",
          `Monthly program generation limit reached (${MAX_SYNTHESIS_PER_MONTH} per month). Try again next month.`,
        );
      }

      for (const snap of activeSnap.docs) {
        if (snap.id === data.jobId) {
          continue;
        }
        const jobData = snap.data();
        if (isSynthesisJobStale(jobData)) {
          tx.update(snap.ref, {
            status: "failed",
            progressPhase: "failed",
            progressMessage: "Program synthesis timed out",
            errorMessage: "Previous program synthesis timed out. Please try again.",
            updatedAt: FieldValue.serverTimestamp(),
          });
          continue;
        }
        throw new AdmissionError(
          "failed-precondition",
          "Another program synthesis is already in progress.",
        );
      }

      if (existing.exists) {
        const existingData = existing.data()!;
        if (
          (existingData.status === "running" || existingData.status === "pending") &&
          !isSynthesisJobStale(existingData)
        ) {
          throw new AdmissionError(
            "failed-precondition",
            "Program synthesis job is already in progress.",
          );
        }
      }

      const nextQuota = nextQuotaAfterStart(quotaData, monthKey);
      tx.set(
        quotaRef,
        {
          monthKey: nextQuota.monthKey,
          startCount: nextQuota.startCount,
          successCount: nextQuota.successCount,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );

      tx.set(
        jobRef,
        {
          status: "pending",
          requestStoragePath: data.requestStoragePath,
          progressPhase: "queued",
          progressMessage: "Queued for program synthesis…",
          progressUpdatedAt: FieldValue.serverTimestamp(),
          createdAt: existing.exists
            ? existing.data()?.createdAt ?? FieldValue.serverTimestamp()
            : FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
          errorMessage: FieldValue.delete(),
          resultStoragePath: FieldValue.delete(),
          quotaCounted: false,
          // Per-job trace waterfall (STR-13): permanent, low-cost handoff timestamps.
          // clientStart is the app's tap/upload-done epoch ms; falls back to server time.
          // Stale dispatcher/worker/solver fields are explicitly cleared with SHORT
          // keys nested under trace (merge:true deep-merges nested maps, so dotted
          // paths here would become literal keys instead of deletions): a retried
          // jobId must not show the previous attempt's handoff timestamps.
          trace: buildJobResetTrace(clientStart),
        },
        { merge: true },
      );
    });
  } catch (err) {
    if (err instanceof AdmissionError) {
      throw new HttpsError(err.code, err.message);
    }
    if (err instanceof HttpsError) {
      throw err;
    }
    console.error(`startProgramSynthesis transaction failed for ${uid}:`, err);
    throw new HttpsError("internal", "Could not start program synthesis. Please try again.");
  }

  return { jobId: data.jobId, status: "pending" };
});

import { FieldValue, type DocumentReference, getFirestore } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { GoogleAuth } from "google-auth-library";
import { createLogger } from "../observability/structuredLog";
import { isFirestoreNotFound } from "../import/safeJobUpdate";
import {
  TRACE_DISPATCHER_POST_SENT,
  TRACE_DISPATCHER_TRIGGER,
  type TraceField,
} from "./synthesisTrace";

/**
 * Env var: base URL of the JVM Cloud Run solver service
 * (e.g. `https://solver-xxxxx.run.app`). Dispatcher POSTs to `{url}/process`.
 * When unset (local/dev without a solver), the job is marked failed with a clear message.
 */
export const SOLVER_SERVICE_URL_ENV = "SOLVER_SERVICE_URL";

/** STR-28 structured logger: JSON lines with severity + uid/jobId correlation. */
const log = createLogger({ service: "processProgramSynthesisJob" });

/**
 * When `"true"`, allow unauthenticated fetch to the solver after OIDC failure.
 * Production must leave this unset/false — Cloud Run is private.
 */
export const ALLOW_UNAUTH_SOLVER_DISPATCH_ENV = "ALLOW_UNAUTH_SOLVER_DISPATCH";

export type SolverDispatchBody = {
  uid: string;
  jobId: string;
};

/**
 * Resolve the solver base URL from the environment. Empty/whitespace → null.
 */
export function resolveSolverServiceUrl(
  env: NodeJS.ProcessEnv = process.env,
): string | null {
  const raw = env[SOLVER_SERVICE_URL_ENV];
  if (typeof raw !== "string") {
    return null;
  }
  const trimmed = raw.trim().replace(/\/+$/, "");
  return trimmed.length > 0 ? trimmed : null;
}

export function allowUnauthSolverDispatch(
  env: NodeJS.ProcessEnv = process.env,
): boolean {
  return env[ALLOW_UNAUTH_SOLVER_DISPATCH_ENV]?.trim().toLowerCase() === "true";
}

/**
 * Build the Cloud Run process endpoint from a base service URL.
 */
export function solverProcessUrl(baseUrl: string): string {
  return `${baseUrl.replace(/\/+$/, "")}/process`;
}

export type SolverDispatchResult = {
  ok: boolean;
  status: number;
  bodyText: string;
};

/**
 * POST `{uid, jobId}` to the JVM solver with an OIDC ID token (audience = service base URL).
 * Inject [requestImpl] in tests to avoid real GoogleAuth / network.
 *
 * Production fails closed on OIDC errors unless [ALLOW_UNAUTH_SOLVER_DISPATCH_ENV] is `"true"`.
 */
export async function dispatchSynthesisJobToSolver(opts: {
  baseUrl: string;
  body: SolverDispatchBody;
  requestImpl?: (url: string, body: SolverDispatchBody) => Promise<SolverDispatchResult>;
  env?: NodeJS.ProcessEnv;
}): Promise<SolverDispatchResult> {
  const url = solverProcessUrl(opts.baseUrl);
  if (opts.requestImpl) {
    return opts.requestImpl(url, opts.body);
  }

  const env = opts.env ?? process.env;
  try {
    const auth = new GoogleAuth();
    // Audience must be the Cloud Run service URL (base), not the /process path.
    const client = await auth.getIdTokenClient(opts.baseUrl);
    const res = await client.request<unknown>({
      url,
      method: "POST",
      data: opts.body,
      headers: { "Content-Type": "application/json" },
      responseType: "text",
      validateStatus: () => true,
    });
    const status = res.status ?? 0;
    const bodyText =
      typeof res.data === "string"
        ? res.data
        : res.data == null
          ? ""
          : JSON.stringify(res.data);
    return { ok: status >= 200 && status < 300, status, bodyText };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    if (!allowUnauthSolverDispatch(env)) {
      log.error("Cloud Run ID-token request failed; unauth fallback disabled", {
        uid: opts.body.uid,
        jobId: opts.body.jobId,
        detail: message,
      });
      return {
        ok: false,
        status: 0,
        bodyText: `OIDC failed: ${message}`,
      };
    }
    log.warn("Cloud Run ID-token request failed; retrying without auth", {
      uid: opts.body.uid,
      jobId: opts.body.jobId,
      detail: message,
    });
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(opts.body),
    });
    const bodyText = await res.text().catch(() => "");
    return { ok: res.ok, status: res.status, bodyText };
  }
}

/**
 * Record a dispatcher handoff timestamp on the job doc. Uses dot-notation update
 * so only the trace field is touched. Safe from trigger loops: the onDocumentWritten
 * handler ignores writes where status was already "pending".
 *
 * Fire-and-forget: returns void, never blocks the dispatch path. Failures are logged.
 */
export function recordDispatcherTrace(jobRef: DocumentReference, field: TraceField): void {
  jobRef.update({ [field]: FieldValue.serverTimestamp() }).catch((err) => {
    // Trace writes are best-effort; never fail the dispatch over them.
    log.warn("recordDispatcherTrace failed", { field, detail: String(err) });
  });
}

/**
 * Mark job failed only when it is still non-terminal (`pending` or `running`).
 * Never overwrites `ready` / `failed` / `rejected` — worker owns terminal success.
 */
export async function markJobFailedIfStillActive(
  jobRef: DocumentReference,
  fields: {
    progressMessage: string;
    errorMessage: string;
  },
): Promise<boolean> {
  const db = getFirestore();
  try {
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(jobRef);
      if (!snap.exists) {
        return false;
      }
      const status = snap.get("status") as string | undefined;
      if (status !== "pending" && status !== "running") {
        return false;
      }
      tx.update(jobRef, {
        status: "failed",
        progressPhase: "failed",
        progressMessage: fields.progressMessage,
        errorMessage: fields.errorMessage,
        updatedAt: FieldValue.serverTimestamp(),
        progressUpdatedAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
  } catch (error: unknown) {
    if (isFirestoreNotFound(error)) {
      return false;
    }
    throw error;
  }
}

/**
 * Firestore dispatcher for program-synthesis jobs.
 *
 * When a job becomes `pending`, HTTP POSTs to the Cloud Run solver. The JVM worker owns
 * claim (`running`) and terminal `ready`/`failed`. The HTTP call is synchronous for the
 * full solve (Cloud Run request-based CPU requires an open request), so this function's
 * timeout must exceed the worker deadline (120s) + cold start + I/O.
 *
 * On dispatch failure only marks failed when the job is still active — never clobbers `ready`.
 */
export const processProgramSynthesisJob = onDocumentWritten(
  {
    document: "users/{uid}/synthesisJobs/{jobId}",
    // Event-driven max is 540s. Worker: 120s solve + cold start + upload headroom.
    timeoutSeconds: 540,
    memory: "256MiB",
  },
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) {
      return;
    }
    const data = after.data();
    if (!data || data.status !== "pending") {
      return;
    }
    const before = event.data?.before?.data();
    if (before?.status === "pending") {
      // Already pending — ignore heartbeat/field-only writes that re-trigger the path.
      return;
    }

    const uid = event.params.uid as string;
    const jobId = event.params.jobId as string;
    const jobRef = getFirestore().doc(`users/${uid}/synthesisJobs/${jobId}`);

    // STR-13 trace: mark when the dispatcher trigger received the job.
    // Fire-and-forget: does not block the solver POST.
    recordDispatcherTrace(jobRef, TRACE_DISPATCHER_TRIGGER);

    const baseUrl = resolveSolverServiceUrl();
    if (!baseUrl) {
      await markJobFailedIfStillActive(jobRef, {
        progressMessage: "Solver unavailable",
        errorMessage: "Program generation is temporarily unavailable. Please try again later.",
      });
      log.error("SOLVER_SERVICE_URL unset; cannot dispatch synthesis job", {
        uid,
        jobId,
      });
      return;
    }

    try {
      // STR-13 trace: mark immediately before POSTing to the solver.
      // Fire-and-forget: does not add round-trips to dispatch latency.
      recordDispatcherTrace(jobRef, TRACE_DISPATCHER_POST_SENT);
      const dispatchStartMs = Date.now();
      const result = await dispatchSynthesisJobToSolver({
        baseUrl,
        body: { uid, jobId },
      });
      const durationMs = Date.now() - dispatchStartMs;
      if (!result.ok) {
        const detail = result.bodyText.slice(0, 500);
        log.warn("Solver dispatch non-OK", {
          uid,
          jobId,
          durationMs,
          status: result.status,
          body: detail,
        });
        await markJobFailedIfStillActive(jobRef, {
          progressMessage: "Failed to start solver",
          errorMessage: "Program generation failed. Please try again.",
        });
      } else {
        log.info("Solver dispatch succeeded", { uid, jobId, durationMs });
      }
    } catch (err) {
      // Unexpected throw (network/OIDC): report with stack for Error Reporting.
      log.reportError("Solver dispatch threw", err, { uid, jobId });
      await markJobFailedIfStillActive(jobRef, {
        progressMessage: "Failed to start solver",
        errorMessage: "Program generation failed. Please try again.",
      });
    }
  },
);

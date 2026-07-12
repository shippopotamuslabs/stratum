import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { GoogleAuth } from "google-auth-library";
import { updateJobIfPresent } from "../import/safeJobUpdate";

/**
 * Env var: base URL of the Phase 3 JVM Cloud Run solver service
 * (e.g. `https://solver-xxxxx.run.app`). Dispatcher POSTs to `{url}/process`.
 * When unset (local/dev without a solver), the job is marked failed with a clear message.
 */
export const SOLVER_SERVICE_URL_ENV = "SOLVER_SERVICE_URL";

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
 */
export async function dispatchSynthesisJobToSolver(opts: {
  baseUrl: string;
  body: SolverDispatchBody;
  /**
   * Optional HTTP layer. Default uses `GoogleAuth.getIdTokenClient(baseUrl).request(...)`
   * so Cloud Run receives a valid identity token.
   */
  requestImpl?: (url: string, body: SolverDispatchBody) => Promise<SolverDispatchResult>;
}): Promise<SolverDispatchResult> {
  const url = solverProcessUrl(opts.baseUrl);
  if (opts.requestImpl) {
    return opts.requestImpl(url, opts.body);
  }

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
    // Fall back to unauthenticated fetch for local/dev when ADC/ID tokens are unavailable.
    const message = err instanceof Error ? err.message : String(err);
    console.warn(`Cloud Run ID-token request failed (${message}); retrying without auth`);
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
 * Firestore dispatcher for program-synthesis jobs.
 *
 * When a job becomes `pending`, HTTP POSTs to the Cloud Run solver. Does **not** claim the
 * job as `running` — the JVM worker owns claim/progress/ready|failed so multi-instance
 * workers can coordinate without Node racing them.
 *
 * On dispatch failure (missing URL, non-2xx, network error) marks the job `failed`.
 */
export const processProgramSynthesisJob = onDocumentWritten(
  {
    document: "users/{uid}/synthesisJobs/{jobId}",
    timeoutSeconds: 60,
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
    const jobRef = admin.firestore().doc(`users/${uid}/synthesisJobs/${jobId}`);

    const baseUrl = resolveSolverServiceUrl();
    if (!baseUrl) {
      await updateJobIfPresent(jobRef, {
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Solver unavailable",
        errorMessage:
          "Program synthesis solver is not configured (SOLVER_SERVICE_URL unset). Deploy the Cloud Run worker or set the env var.",
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }

    try {
      const result = await dispatchSynthesisJobToSolver({
        baseUrl,
        body: { uid, jobId },
      });
      if (!result.ok) {
        const detail = result.bodyText.slice(0, 500);
        console.warn(
          `Solver dispatch non-OK for ${uid}/${jobId}: status=${result.status} body=${detail}`,
        );
        await updateJobIfPresent(jobRef, {
          status: "failed",
          progressPhase: "failed",
          progressMessage: "Failed to start solver",
          errorMessage: `Solver dispatch failed (HTTP ${result.status}). Please try again.`,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      console.warn(`Solver dispatch threw for ${uid}/${jobId}: ${message}`);
      await updateJobIfPresent(jobRef, {
        status: "failed",
        progressPhase: "failed",
        progressMessage: "Failed to start solver",
        errorMessage: `Solver dispatch error: ${message}`,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  },
);

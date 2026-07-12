/**
 * Callable: permanently delete the caller's cloud account data and/or Firebase Auth user.
 * Clients cannot hard-delete sync docs (Firestore rules deny delete); Admin SDK cascade only.
 *
 * Phases keep Auth alive until the client finishes local Room wipe so Settings can surface a
 * snackbar if local cleanup fails after cloud data is gone.
 */
import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";

/**
 * Storage object prefixes owned by a user (must stay aligned with client import/synthesis paths).
 * Firestore subcollections under `users/{uid}` (importJobs, programImportJobs, synthesisJobs,
 * synthesisQuota, sync entities) are removed via recursiveDelete of the user doc.
 */
export const USER_STORAGE_PREFIXES = ["imports", "programImports", "synthesis"] as const;

export type DeleteAccountPhase = "data" | "auth" | "all";

export type DeleteUserCloudAccountDeps = {
  wipeData: (uid: string) => Promise<void>;
  deleteAuth: (uid: string) => Promise<void>;
};

export function userFirestorePath(uid: string): string {
  return `users/${uid}`;
}

export function userStoragePrefixes(uid: string): string[] {
  return USER_STORAGE_PREFIXES.map((root) => `${root}/${uid}/`);
}

/**
 * Parse callable phase.
 * - Absent/null → `"all"` (one-shot Admin/tooling).
 * - Known phases accepted as-is.
 * - Unknown strings throw (fail closed on a destructive endpoint).
 */
export function parseDeleteAccountPhase(raw: unknown): DeleteAccountPhase {
  if (raw === undefined || raw === null) {
    return "all";
  }
  if (raw === "data" || raw === "auth" || raw === "all") {
    return raw;
  }
  throw new Error(`Invalid deleteAccount phase: ${String(raw)}`);
}

/** Single-pass Firestore tree + Storage prefix wipe. Storage failures propagate. */
export async function wipeUserFirestoreAndStorage(uid: string): Promise<void> {
  const db = admin.firestore();
  await db.recursiveDelete(db.doc(userFirestorePath(uid)));

  const bucket = admin.storage().bucket();
  for (const prefix of userStoragePrefixes(uid)) {
    await bucket.deleteFiles({ prefix, force: true });
  }
}

/**
 * Wipe Firestore + Storage for [uid], then a second pass so late writers (import agents that
 * finished mid-cascade) cannot leave residual docs/objects under the same uid.
 */
export async function deleteUserOwnedCloudData(uid: string): Promise<void> {
  await wipeUserFirestoreAndStorage(uid);
  await wipeUserFirestoreAndStorage(uid);
}

/** Delete Firebase Auth user; not-found is success (idempotent retry). */
export async function deleteFirebaseAuthUser(uid: string): Promise<void> {
  try {
    await admin.auth().deleteUser(uid);
  } catch (error: unknown) {
    const code =
      error && typeof error === "object" && "code" in error
        ? String((error as { code: unknown }).code)
        : "";
    if (code === "auth/user-not-found") {
      return;
    }
    throw error;
  }
}

/**
 * Phase orchestration. Optional [deps] inject wipe/auth for unit tests; production uses Admin.
 *
 * - data: wipe cloud (double-pass)
 * - auth: settle wipe + delete Auth (catches late import rewrites after client data phase)
 * - all: data wipe + settle wipe + Auth
 */
export async function deleteUserCloudAccount(
  uid: string,
  phase: DeleteAccountPhase,
  deps: DeleteUserCloudAccountDeps = {
    wipeData: deleteUserOwnedCloudData,
    deleteAuth: deleteFirebaseAuthUser,
  },
): Promise<void> {
  if (phase === "data" || phase === "all") {
    await deps.wipeData(uid);
  }
  if (phase === "auth" || phase === "all") {
    await deps.wipeData(uid);
    await deps.deleteAuth(uid);
  }
}

export const deleteAccount = onCall(
  {
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const uid = request.auth.uid;
    let phase: DeleteAccountPhase;
    try {
      phase = parseDeleteAccountPhase(
        (request.data as { phase?: unknown } | null | undefined)?.phase,
      );
    } catch (error) {
      throw new HttpsError(
        "invalid-argument",
        error instanceof Error ? error.message : "Invalid phase.",
      );
    }
    try {
      await deleteUserCloudAccount(uid, phase);
      return { ok: true, phase };
    } catch (error) {
      console.error(`deleteAccount failed for uid=${uid} phase=${phase}:`, error);
      throw new HttpsError("internal", "Account deletion failed. Try again later.");
    }
  },
);

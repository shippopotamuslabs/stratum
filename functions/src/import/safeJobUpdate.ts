import type { DocumentReference, UpdateData, DocumentData } from "firebase-admin/firestore";

/** gRPC / Admin SDK codes for a missing document. */
export function isFirestoreNotFound(error: unknown): boolean {
  if (!error || typeof error !== "object") {
    return false;
  }
  const code = "code" in error ? (error as { code: unknown }).code : undefined;
  return code === 5 || code === "not-found" || code === "NOT_FOUND";
}

/**
 * Job status update that no-ops when the doc was cascade-deleted (account deletion).
 * Prevents mid-flight import agents from recreating `users/{uid}/…` after wipe.
 */
export async function updateJobIfPresent(
  jobRef: DocumentReference,
  data: UpdateData<DocumentData>,
): Promise<boolean> {
  try {
    await jobRef.update(data);
    return true;
  } catch (error: unknown) {
    if (isFirestoreNotFound(error)) {
      console.info(
        `Job ${jobRef.path} missing (likely account deletion); skipping status update`,
      );
      return false;
    }
    throw error;
  }
}

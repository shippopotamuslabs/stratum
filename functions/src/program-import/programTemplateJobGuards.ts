/**
 * Shared guards for program-template import jobs — stale running/pending detection so a Cloud
 * Function hard-timeout cannot permanently block retries.
 */
import type { DocumentData, Timestamp } from "firebase-admin/firestore";

/** Slightly above processProgramTemplateJob timeoutSeconds (300) so legitimate jobs finish first. */
export const PROGRAM_TEMPLATE_STALE_JOB_MS = 6 * 60 * 1000;

function timestampToMillis(value: unknown): number | null {
  if (value == null) {
    return null;
  }
  if (typeof value === "object" && value !== null && "toMillis" in value) {
    const ts = value as Timestamp;
    if (typeof ts.toMillis === "function") {
      return ts.toMillis();
    }
  }
  return null;
}

/**
 * True when the job doc has not been updated within [PROGRAM_TEMPLATE_STALE_JOB_MS].
 * Missing timestamps are treated as stale so a stuck doc without timestamps can be superseded.
 */
export function isProgramTemplateJobStale(
  data: DocumentData | undefined,
  nowMs: number = Date.now(),
): boolean {
  if (!data) {
    return true;
  }
  const updated =
    timestampToMillis(data.updatedAt) ??
    timestampToMillis(data.progressUpdatedAt) ??
    timestampToMillis(data.createdAt);
  if (updated == null) {
    return true;
  }
  return nowMs - updated >= PROGRAM_TEMPLATE_STALE_JOB_MS;
}

/**
 * Shared guards for historical import jobs — stale pending_agent/agent_running detection so a
 * Cloud Function hard-timeout cannot permanently block retries.
 */
import type { DocumentData, Timestamp } from "firebase-admin/firestore";

/** Slightly above processImportJob timeoutSeconds (540) so legitimate jobs finish first. */
export const HISTORICAL_IMPORT_STALE_JOB_MS = 10 * 60 * 1000;

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
 * True when the job doc has not been updated within [HISTORICAL_IMPORT_STALE_JOB_MS].
 * Missing timestamps are treated as stale so a stuck doc without timestamps can be superseded.
 */
export function isHistoricalImportJobStale(
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
  return nowMs - updated >= HISTORICAL_IMPORT_STALE_JOB_MS;
}

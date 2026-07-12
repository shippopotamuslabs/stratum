/**
 * Shared guards for program-synthesis jobs — stale pending/running detection so a stuck
 * Cloud Run worker or missed dispatch cannot permanently block retries.
 */
import type { DocumentData, Timestamp } from "firebase-admin/firestore";

/**
 * Must be **greater than** the Android client poll ceiling (5 minutes) so we never
 * reclaim a job and start a second solve while the first client is still polling.
 *
 * Worker Cloud Run timeout is 180s; client poll is 300s; stale at 360s leaves margin.
 */
export const SYNTHESIS_STALE_JOB_MS = 6 * 60 * 1000;

/** Client-side poll ceiling (must stay ≤ SYNTHESIS_STALE_JOB_MS). Documented for alignment. */
export const SYNTHESIS_CLIENT_POLL_TIMEOUT_MS = 5 * 60 * 1000;

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
 * True when the job doc has not been updated within [SYNTHESIS_STALE_JOB_MS].
 * Missing timestamps are treated as stale so a stuck doc without timestamps can be superseded.
 */
export function isSynthesisJobStale(
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
  return nowMs - updated >= SYNTHESIS_STALE_JOB_MS;
}

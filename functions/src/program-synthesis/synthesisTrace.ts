import { FieldValue, Timestamp } from "firebase-admin/firestore";

/**
 * Centralized per-job trace schema for the STR-13 synthesis waterfall.
 * Single owner for field names, Timestamp shape, and update semantics.
 *
 * The `trace` map on the synthesis job doc is permanent and low-cost.
 * All timestamps are Firestore Timestamps (server time unless clientStart
 * was provided by the app).
 */

/** Field names for the per-job `trace` map (dot-notation for updates). */
export const TRACE_CLIENT_START = "trace.clientStart";
export const TRACE_JOB_CREATED = "trace.jobCreated";
export const TRACE_DISPATCHER_TRIGGER = "trace.dispatcherTrigger";
export const TRACE_DISPATCHER_POST_SENT = "trace.dispatcherPostSent";

/** Known trace fields for typed record helpers. */
export type TraceField =
  | typeof TRACE_CLIENT_START
  | typeof TRACE_JOB_CREATED
  | typeof TRACE_DISPATCHER_TRIGGER
  | typeof TRACE_DISPATCHER_POST_SENT;

/**
 * Convert a client epoch-millis timestamp to a Firestore Timestamp.
 * Returns null when the input is invalid or out of Firestore's Timestamp range.
 * Uses the canonical Timestamp.fromMillis (handles nanos normalization).
 */
export function clientStartMsToTimestamp(clientStartMs: unknown): Timestamp | null {
  if (typeof clientStartMs !== "number" || !Number.isFinite(clientStartMs) || clientStartMs <= 0) {
    return null;
  }
  try {
    return Timestamp.fromMillis(clientStartMs);
  } catch {
    // Out of range for Firestore Timestamp — fall back to server time.
    return null;
  }
}

/**
 * Build the initial trace map for a new job doc.
 * Explicitly includes only clientStart and jobCreated; dispatcher fields
 * are written later by the dispatcher trigger.
 */
export function initialTraceMap(clientStart: Timestamp | null): Record<string, unknown> {
  return {
    clientStart: clientStart ?? FieldValue.serverTimestamp(),
    jobCreated: FieldValue.serverTimestamp(),
  };
}

/**
 * Field deletions to clear stale dispatcher trace on job reset.
 * With merge:true, Firestore deep-merges nested maps, so prior
 * dispatcherTrigger/dispatcherPostSent from a reused jobId would survive.
 * Explicitly delete them to keep the waterfall accurate.
 */
export function dispatcherTraceDeletions(): Record<string, unknown> {
  return {
    [TRACE_DISPATCHER_TRIGGER]: FieldValue.delete(),
    [TRACE_DISPATCHER_POST_SENT]: FieldValue.delete(),
  };
}

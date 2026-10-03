import { FieldValue, Timestamp } from "firebase-admin/firestore";
import traceFieldSchema from "./traceFields.json";

/**
 * Centralized per-job trace schema for the STR-13 synthesis waterfall.
 * Single owner for field names, Timestamp shape, and update semantics.
 *
 * The `trace` map on the synthesis job doc is permanent and low-cost.
 * All timestamps are Firestore Timestamps (server time unless clientStart
 * was provided by the app).
 *
 * The resettable field list lives in `traceFields.json` (dotted Firestore
 * paths). The TRACE_* constants, the `TraceField` shape, and
 * `staleTraceDeletions()` below are all derived from that one array so a
 * newly added worker/solver field cannot miss the retry-reset list. The
 * JVM worker's `SynthesisJobWorker.TRACE_*` constants are pinned against
 * the same file by `SynthesisJobTraceFieldsTest.kt` — that test is the
 * cross-language contract, not the keep-in-sync comment it replaced.
 */

/**
 * Canonical stale-trace field list (dotted paths): the single source of
 * truth for retry resets. Imported from `traceFields.json`, frozen so no
 * caller can mutate the shared list.
 */
export const STALE_TRACE_FIELDS: readonly string[] = Object.freeze([
  ...traceFieldSchema.staleTraceFields,
]);

const TRACE_MAP_PREFIX = "trace.";

/**
 * Strip the `trace.` prefix to get the key nested inside the `trace` map.
 * Throws on malformed entries so a bad schema fails fast instead of
 * writing literal dotted keys into the map.
 */
function shortTraceKey(dotted: string): string {
  if (!dotted.startsWith(TRACE_MAP_PREFIX) || dotted.length === TRACE_MAP_PREFIX.length) {
    throw new Error(`traceFields.json entry is not a trace map path: ${dotted}`);
  }
  return dotted.slice(TRACE_MAP_PREFIX.length);
}

/** Known trace fields: dotted paths inside the per-job `trace` map. */
export type TraceField = `trace.${string}`;

/**
 * Resolve a short field name to its dotted update path, validating against
 * the canonical list. A constant whose field is missing from
 * `traceFields.json` throws at module load instead of silently keeping
 * stale timestamps on retry.
 */
function requireTracePath(short: string): TraceField {
  const dotted: TraceField = `${TRACE_MAP_PREFIX}${short}`;
  if (!STALE_TRACE_FIELDS.includes(dotted)) {
    throw new Error(`trace field ${dotted} is missing from traceFields.json`);
  }
  return dotted;
}

/**
 * Dotted trace paths for document-level updates (e.g. `recordDispatcherTrace`
 * uses `{[TRACE_DISPATCHER_TRIGGER]: ...}` at the top level, where Firestore
 * interprets the dots as a nested path). All derived from STALE_TRACE_FIELDS.
 */
export const TRACE_DISPATCHER_TRIGGER = requireTracePath("dispatcherTrigger");
export const TRACE_DISPATCHER_POST_SENT = requireTracePath("dispatcherPostSent");
/**
 * Worker trace fields, written by the JVM Cloud Run worker
 * (`SynthesisJobWorker`). Mirrored here so job resets can clear them —
 * pinned against the worker's `TRACE_*` constants via `traceFields.json`
 * (see `SynthesisJobTraceFieldsTest.kt`).
 */
export const TRACE_WORKER_START = requireTracePath("workerStart");
export const TRACE_WORKER_CLAIMED = requireTracePath("workerClaimed");
export const TRACE_WORKER_SOLVING = requireTracePath("workerSolving");
export const TRACE_WORKER_UPLOADED = requireTracePath("workerUploaded");
export const TRACE_SOLVER = requireTracePath("solver");

/** Fresh-write fields, set on every attempt — intentionally NOT resettable. */
export const TRACE_CLIENT_START: TraceField = "trace.clientStart";
export const TRACE_JOB_CREATED: TraceField = "trace.jobCreated";

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
 *
 * Keys are SHORT (nested inside `trace: {...}` by the caller with
 * merge:true). Never mix dotted paths in here: Firestore would store them
 * as literal keys instead of nested fields.
 */
export function initialTraceMap(clientStart: Timestamp | null): Record<string, unknown> {
  return {
    clientStart: clientStart ?? FieldValue.serverTimestamp(),
    jobCreated: FieldValue.serverTimestamp(),
  };
}

/**
 * Field deletions to clear stale trace on job reset (retry with a reused jobId).
 * With merge:true, Firestore deep-merges nested maps, so prior
 * dispatcher/worker/solver fields from the previous attempt would survive.
 * Explicitly delete them to keep the waterfall accurate. A new pending job that
 * has not solved yet must not show the previous attempt's workerUploaded/solver.
 *
 * Keys are SHORT because the caller spreads this inside `trace: {...}` —
 * dotted paths at that nesting level would become literal
 * `trace.workerStart` keys instead of deletions (the STR-13 retry bug).
 * Derived from STALE_TRACE_FIELDS so new fields are reset automatically.
 */
export function staleTraceDeletions(): Record<string, unknown> {
  return Object.fromEntries(
    STALE_TRACE_FIELDS.map((dotted) => [shortTraceKey(dotted), FieldValue.delete()]),
  );
}

/**
 * Compose the exact `trace` map payload for a job (re)start: fresh
 * clientStart/jobCreated plus short-key deletions of every stale field.
 * The caller writes it as `trace: buildJobResetTrace(...)` with merge:true.
 */
export function buildJobResetTrace(clientStart: Timestamp | null): Record<string, unknown> {
  return {
    ...initialTraceMap(clientStart),
    ...staleTraceDeletions(),
  };
}

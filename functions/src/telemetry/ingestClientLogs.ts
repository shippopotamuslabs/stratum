/**
 * Callable: ingest structured tier-2 client logs (STR-27).
 *
 * App -> `ingestClientLogs` (Auth + App Check) -> Cloud Logging -> BigQuery
 * sink (STR-28). Each accepted entry is written with `firebase-functions/logger`
 * at INFO severity carrying `source: "stratum-client-log"` in the JSON payload:
 *
 * - INFO (never ERROR) so expected failures can never trip Error Reporting or
 *   tier-1 alerts; tier 2 is queryable but never alerted on.
 * - `userId` always comes from `request.auth`, never from the client.
 * - STR-28 sink filter: `jsonPayload.source="stratum-client-log"`.
 *
 * Cost controls: client-side batching/caps (ClientLogBuffer) plus a per-user
 * daily entry quota enforced here in Firestore.
 */
import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { info as logStructured } from "firebase-functions/logger";
import { HttpsError, onCall } from "firebase-functions/v2/https";

export const LOG_SOURCE = "stratum-client-log";
export const MAX_ENTRIES_PER_REQUEST = 100;
export const MAX_MESSAGE_LENGTH = 4096;
export const MAX_TAG_LENGTH = 64;
export const MAX_SESSION_ID_LENGTH = 64;
export const MAX_APP_VERSION_LENGTH = 32;
export const MAX_PAYLOAD_BYTES = 262144;
export const MAX_ENTRIES_PER_USER_PER_DAY = 2000;
export const MAX_FUTURE_SKEW_MS = 3600000;

export type ClientLogSeverity = "WARNING" | "ERROR";

const SEVERITIES: ReadonlySet<string> = new Set(["WARNING", "ERROR"]);

export interface ParsedClientLogEntry {
  severity: ClientLogSeverity;
  tag: string;
  message: string;
  clientTimestampMs: number;
  sessionId: string | null;
  durationMs: number | null;
}

export interface ParsedIngestRequest {
  entries: ParsedClientLogEntry[];
  appVersion: string;
  platform: string;
  droppedEntryCount: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function invalid(message: string): HttpsError {
  return new HttpsError("invalid-argument", message);
}

function parseOptionalString(
  value: unknown,
  field: string,
  maxLength: number,
): string | null {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== "string" || value.length > maxLength) {
    throw invalid(`${field} must be a string of at most ${maxLength} chars.`);
  }
  return value;
}

function parseEntry(raw: unknown, nowMs: number): ParsedClientLogEntry {
  if (!isRecord(raw)) {
    throw invalid("Each entry must be an object.");
  }
  const severity = raw["severity"];
  if (typeof severity !== "string" || !SEVERITIES.has(severity)) {
    throw invalid("Entry severity must be WARNING or ERROR.");
  }
  const tag = raw["tag"];
  if (typeof tag !== "string" || tag.length === 0 || tag.length > MAX_TAG_LENGTH) {
    throw invalid(`Entry tag must be 1-${MAX_TAG_LENGTH} chars.`);
  }
  const message = raw["message"];
  if (
    typeof message !== "string" ||
    message.length === 0 ||
    message.length > MAX_MESSAGE_LENGTH
  ) {
    throw invalid(`Entry message must be 1-${MAX_MESSAGE_LENGTH} chars.`);
  }
  const clientTimestampMs = raw["clientTimestampMs"];
  if (
    typeof clientTimestampMs !== "number" ||
    !Number.isFinite(clientTimestampMs) ||
    clientTimestampMs < 0 ||
    clientTimestampMs > nowMs + MAX_FUTURE_SKEW_MS
  ) {
    throw invalid("Entry clientTimestampMs must be a plausible epoch-millis timestamp.");
  }
  const sessionId = parseOptionalString(raw["sessionId"], "sessionId", MAX_SESSION_ID_LENGTH);
  const durationRaw = raw["durationMs"];
  let durationMs: number | null = null;
  if (durationRaw !== undefined && durationRaw !== null) {
    if (typeof durationRaw !== "number" || !Number.isFinite(durationRaw) || durationRaw < 0) {
      throw invalid("Entry durationMs must be a non-negative number.");
    }
    durationMs = durationRaw;
  }
  return {
    severity: severity as ClientLogSeverity,
    tag,
    message,
    clientTimestampMs,
    sessionId,
    durationMs,
  };
}

export function parseIngestClientLogs(data: unknown, nowMs: number): ParsedIngestRequest {
  if (!isRecord(data)) {
    throw invalid("Request must be an object.");
  }
  if (JSON.stringify(data).length > MAX_PAYLOAD_BYTES) {
    throw invalid("Request payload is too large.");
  }
  const entriesRaw = data["entries"];
  if (!Array.isArray(entriesRaw) || entriesRaw.length === 0) {
    throw invalid("entries must be a non-empty array.");
  }
  if (entriesRaw.length > MAX_ENTRIES_PER_REQUEST) {
    throw invalid(`entries must hold at most ${MAX_ENTRIES_PER_REQUEST} items.`);
  }
  const appVersion = data["appVersion"];
  if (
    typeof appVersion !== "string" ||
    appVersion.length === 0 ||
    appVersion.length > MAX_APP_VERSION_LENGTH
  ) {
    throw invalid(`appVersion must be 1-${MAX_APP_VERSION_LENGTH} chars.`);
  }
  const platform = data["platform"];
  if (platform !== "android") {
    throw invalid('platform must be "android".');
  }
  const droppedRaw = data["droppedEntryCount"];
  const droppedEntryCount = droppedRaw === undefined ? 0 : droppedRaw;
  if (
    typeof droppedEntryCount !== "number" ||
    !Number.isInteger(droppedEntryCount) ||
    droppedEntryCount < 0
  ) {
    throw invalid("droppedEntryCount must be a non-negative integer.");
  }
  return {
    entries: entriesRaw.map((entry) => parseEntry(entry, nowMs)),
    appVersion,
    platform,
    droppedEntryCount,
  };
}

export function quotaDocPath(uid: string): string {
  return `clientLogQuota/${uid}`;
}

export function utcDateKey(nowMs: number): string {
  return new Date(nowMs).toISOString().slice(0, 10);
}

export interface QuotaState {
  dateKey: string;
  count: number;
}

export function isQuotaExceeded(countToday: number, incoming: number): boolean {
  return countToday + incoming > MAX_ENTRIES_PER_USER_PER_DAY;
}

/**
 * Pure quota transition for tests: given the stored state (if any) for today,
 * either rejects the batch or returns the state to persist.
 */
export function applyQuotaUpdate(
  stored: QuotaState | undefined,
  dateKey: string,
  incoming: number,
): QuotaState {
  const countToday = stored !== undefined && stored.dateKey === dateKey ? stored.count : 0;
  if (isQuotaExceeded(countToday, incoming)) {
    throw new HttpsError("resource-exhausted", "Daily client-log quota exceeded.");
  }
  return { dateKey, count: countToday + incoming };
}

async function checkClientLogQuota(uid: string, incoming: number, nowMs: number): Promise<void> {
  const db = getFirestore();
  const dateKey = utcDateKey(nowMs);
  await db.runTransaction(async (tx) => {
    const ref = db.doc(quotaDocPath(uid));
    const snap = await tx.get(ref);
    const data = snap.data() as Partial<QuotaState> | undefined;
    const stored: QuotaState | undefined =
      data !== undefined && typeof data.dateKey === "string" && typeof data.count === "number"
        ? { dateKey: data.dateKey, count: data.count }
        : undefined;
    tx.set(ref, applyQuotaUpdate(stored, dateKey, incoming), { merge: true });
  });
}

export const ingestClientLogs = onCall({ enforceAppCheck: true }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }
  const uid = request.auth.uid;
  const nowMs = Date.now();
  const parsed = parseIngestClientLogs(request.data, nowMs);
  await checkClientLogQuota(uid, parsed.entries.length, nowMs);

  const receivedAt = new Date(nowMs).toISOString();
  // One batchId per request: droppedEntryCount repeats on every row, so SQL
  // must aggregate it as MAX(droppedEntryCount) GROUP BY batchId, never SUM.
  const batchId = randomUUID();
  for (const entry of parsed.entries) {
    logStructured({
      source: LOG_SOURCE,
      batchId,
      severity: entry.severity,
      tag: entry.tag,
      message: entry.message,
      userId: uid,
      sessionId: entry.sessionId,
      clientTimestampMs: entry.clientTimestampMs,
      receivedAt,
      durationMs: entry.durationMs,
      appVersion: parsed.appVersion,
      platform: parsed.platform,
      droppedEntryCount: parsed.droppedEntryCount,
    });
  }
  return { received: parsed.entries.length };
});

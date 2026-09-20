import { Timestamp, FieldValue } from "firebase-admin/firestore";
import { validateNormalizedHistory } from "../schemas";
import { parseFallbackWeightUnit } from "../weightUnit";
import { detectAndParse } from "./formatDetector";
import type { DeterministicResult } from "./types";

/**
 * Storage abstraction for deterministic conversion. Mirrors the `ToolStorage`
 * surface used by the agent tools so the same fake (`FakeToolStorage`) can be
 * injected in tests.
 */
export interface DeterministicStorage {
  download(path: string): Promise<Buffer>;
  save(path: string, content: string | Buffer, contentType?: string): Promise<void>;
}

/**
 * Firestore job-ref surface needed by deterministic conversion. Kept narrow so
 * a plain fake (or the real admin `DocumentReference`) satisfies it.
 */
export interface DeterministicJobRef {
  get(): Promise<{ data(): Record<string, unknown> | undefined }>;
  update(patch: Record<string, unknown>): Promise<unknown>;
}

export interface DeterministicConvertDeps {
  uid: string;
  jobId: string;
  jobRef: DeterministicJobRef;
  storage: DeterministicStorage;
}

/**
 * Try a deterministic conversion before falling back to the LLM agent.
 *
 * Downloads the raw upload, sniffs its format, parses it, validates the result
 * against the same `validateNormalizedHistory` rules the agent's output must
 * satisfy, persists the normalized JSON to Storage exactly where
 * `execute_on_full` would, and marks the job `ready`.
 *
 * Returns `null` for any non-fatal outcome the caller should treat as "try the
 * agent instead": unrecognised format, parse failure, or validation failure.
 * A thrown error from Storage/Firestore is allowed to propagate so the caller's
 * existing error handling (which marks the job `failed`) takes over — those are
 * infrastructure failures, not "unknown format" misses.
 */
export async function deterministicConvert(
  deps: DeterministicConvertDeps,
): Promise<DeterministicResult | null> {
  const jobSnap = await deps.jobRef.get();
  const jobData = jobSnap.data();
  const rawStoragePath = jobData?.rawStoragePath;
  if (typeof rawStoragePath !== "string" || rawStoragePath.length === 0) {
    return null;
  }

  const rawBytes = await deps.storage.download(rawStoragePath);

  const detected = await detectAndParse(rawBytes, (jobData?.fileName as string | undefined) ?? "");
  if (!detected) {
    return null;
  }

  // Validate before persisting. If a parser emits anything the contract
  // rejects, treat it as a miss and let the agent have a go — never persist
  // half-valid output.
  const fallbackWeightUnit = parseFallbackWeightUnit(jobData?.fallbackWeightUnit);
  let history;
  try {
    history = validateNormalizedHistory(detected.history, fallbackWeightUnit);
  } catch {
    return null;
  }

  const rowCount = history.sets.length;
  const sessionCount = history.sessions.length;
  if (rowCount === 0) {
    return null;
  }

  const ts = Date.now();
  const conversionPath = `imports/${deps.uid}/${deps.jobId}/conversions/${ts}/normalized.json`;
  const outputBytes = Buffer.from(JSON.stringify(history), "utf8");
  await deps.storage.save(conversionPath, outputBytes, "application/json");

  await deps.jobRef.update({
    status: "ready",
    progressPhase: "converting_full",
    progressMessage: `Converted ${rowCount} sets across ${sessionCount} sessions`,
    latestConversion: {
      storagePath: conversionPath,
      rowCount,
      sessionCount,
      sizeBytes: outputBytes.length,
      createdAt: Timestamp.now(),
      format: detected.format,
    },
    updatedAt: FieldValue.serverTimestamp(),
  });

  return {
    format: detected.format,
    history,
    rowCount,
    sessionCount,
  };
}

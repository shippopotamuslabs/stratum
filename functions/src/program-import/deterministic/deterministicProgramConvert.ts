import { Timestamp, FieldValue } from "firebase-admin/firestore";
import type { DeterministicStorage, DeterministicJobRef } from "../../import/deterministic/deterministicConvert";
import { detectAndParseProgramTemplate } from "./formatDetector";
import { validateProgramTemplate } from "./types";
import type { ProgramTemplateDeterministicResult } from "./types";
import { assertWithinSizeLimit } from "../programTemplateLimits";

export interface DeterministicProgramConvertDeps {
  uid: string;
  jobId: string;
  jobRef: DeterministicJobRef;
  storage: DeterministicStorage;
}

/**
 * Try a deterministic program-template conversion before falling back to the LLM.
 *
 * Downloads the raw upload, sniffs its format, parses it, validates the result against the
 * program-template contract, persists the normalized JSON to Storage, and marks the job `ready`.
 *
 * Returns `null` for any non-fatal outcome the caller should treat as "try the LLM instead":
 * unrecognised format, parse failure, or validation failure. A thrown error from Storage/Firestore
 * is allowed to propagate so the caller's error handling (which marks the job `failed`) takes over.
 */
export async function deterministicProgramConvert(
  deps: DeterministicProgramConvertDeps,
): Promise<ProgramTemplateDeterministicResult | null> {
  const jobSnap = await deps.jobRef.get();
  const jobData = jobSnap.data();
  const rawStoragePath = jobData?.rawStoragePath;
  if (typeof rawStoragePath !== "string" || rawStoragePath.length === 0) {
    return null;
  }

  const rawBytes = await deps.storage.download(rawStoragePath);

  // Abuse guard: reject oversized files before any parse. Throws ProgramTemplateTooLargeError,
  // which the trigger handler surfaces as a rejection rather than falling back to the LLM.
  assertWithinSizeLimit(rawBytes.length);

  const detected = detectAndParseProgramTemplate(rawBytes);
  if (!detected) {
    return null;
  }

  let template;
  try {
    template = validateProgramTemplate(detected.template);
  } catch {
    return null;
  }

  const ts = Date.now();
  const conversionPath = `programImports/${deps.uid}/${deps.jobId}/conversions/${ts}/template.json`;
  const outputBytes = Buffer.from(JSON.stringify(template), "utf8");
  await deps.storage.save(conversionPath, outputBytes, "application/json");

  const workoutCount = template.workouts.length;
  await deps.jobRef.update({
    status: "ready",
    progressPhase: "parsing",
    progressMessage: `Imported ${workoutCount} workouts`,
    latestConversion: {
      storagePath: conversionPath,
      format: detected.format,
      sizeBytes: outputBytes.length,
      createdAt: Timestamp.now(),
    },
    updatedAt: FieldValue.serverTimestamp(),
  });

  return { format: detected.format, template };
}

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
 * Try a deterministic program-template conversion.
 *
 * Downloads the raw upload, sniffs its format, parses it, validates the result against the
 * program-template contract, persists the normalized JSON to Storage, and marks the job `ready`.
 *
 * Returns `null` for any non-fatal outcome the caller should reject as an unsupported file:
 * unrecognised format, parse failure, or validation failure. `ProgramTemplateTooLargeError` from
 * the size guard propagates so the caller can surface the distinct "too large" rejection; any
 * other thrown error is also treated by the caller as an unsupported-file rejection.
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
  // which the trigger handler surfaces as a distinct "too large" rejection.
  assertWithinSizeLimit(rawBytes.length);

  const detected = await detectAndParseProgramTemplate(rawBytes);
  if (!detected) {
    return null;
  }

  // Validate every template (single or multi-program Stratum).
  let validatedTemplates: Array<{ id: string | null; template: ReturnType<typeof validateProgramTemplate> }>;
  try {
    if (detected.programs && detected.programs.length > 0) {
      validatedTemplates = detected.programs.map((p) => ({
        id: p.id,
        template: validateProgramTemplate(p.template),
      }));
    } else {
      validatedTemplates = [{ id: null, template: validateProgramTemplate(detected.template) }];
    }
  } catch {
    return null;
  }

  const primary = validatedTemplates[0].template;
  const ts = Date.now();
  const conversionPath = `programImports/${deps.uid}/${deps.jobId}/conversions/${ts}/template.json`;
  // Multi-program Stratum: wrap as { programs: [...] }; single stays bare template.
  const outputBody =
    detected.programs && detected.programs.length > 0
      ? {
          programs: validatedTemplates.map((p) => ({
            ...(p.id != null ? { id: p.id } : {}),
            template: p.template,
          })),
        }
      : primary;
  const outputBytes = Buffer.from(JSON.stringify(outputBody), "utf8");
  await deps.storage.save(conversionPath, outputBytes, "application/json");

  const workoutCount = primary.workouts.length;
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

  return { format: detected.format, template: primary };
}

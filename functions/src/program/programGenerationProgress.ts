import { FieldValue, type DocumentReference } from "firebase-admin/firestore";
import { looksLikeTechnicalProgressLeak } from "../import/schemas";

/** Firestore `progressPhase` values on `users/{uid}/programs_generation/latest`.
 *
 *  v2 phases (live): `split_planning`, `building_workouts`, `integrating`.
 *  v1 phases (kept for back-compat with in-flight markers + Android enum exhaustiveness):
 *  `reviewing_profile`, `validating_program`, `drafting_program`, `refining_program`. */
export type ProgramGenerationProgressPhase =
  | "reviewing_profile"
  | "validating_program"
  | "drafting_program"
  | "refining_program"
  | "split_planning"
  | "building_workouts"
  | "integrating"
  | "failed";

const PROGRESS_MESSAGE_BY_PHASE: Partial<Record<ProgramGenerationProgressPhase, string>> = {
  reviewing_profile: "Reviewing your training profile…",
  validating_program: "Checking your draft program…",
  drafting_program: "Building your weekly split…",
  refining_program: "Adjusting volume and exercise order…",
  split_planning: "Planning your weekly split…",
  building_workouts: "Building your workouts…",
  integrating: "Putting it together…",
};

const PROGRAM_TECHNICAL_LEAK_PATTERNS = [
  /workoutLiftSets/i,
  /progressionScheme/i,
  /json_schema/i,
  /MEV|MRV|MAV/,
  /validate_/i,
];

export function looksLikeProgramGenerationTechnicalLeak(message: string): boolean {
  return (
    looksLikeTechnicalProgressLeak(message) ||
    PROGRAM_TECHNICAL_LEAK_PATTERNS.some((pattern) => pattern.test(message))
  );
}

export function sanitizeProgramGenerationProgressMessage(
  message: string | undefined,
  phase: ProgramGenerationProgressPhase | undefined,
): string | null {
  if (!message) {
    return null;
  }
  if (!looksLikeProgramGenerationTechnicalLeak(message)) {
    return message;
  }
  if (phase && PROGRESS_MESSAGE_BY_PHASE[phase]) {
    return PROGRESS_MESSAGE_BY_PHASE[phase] ?? null;
  }
  return null;
}

export type ProgramGenerationMarkerRef = Pick<DocumentReference, "update">;

export async function writeProgramGenerationProgress(
  markerRef: ProgramGenerationMarkerRef,
  phase: ProgramGenerationProgressPhase,
  message: string,
): Promise<void> {
  await markerRef.update({
    progressPhase: phase,
    progressMessage: message,
    progressUpdatedAt: FieldValue.serverTimestamp(),
  });
}

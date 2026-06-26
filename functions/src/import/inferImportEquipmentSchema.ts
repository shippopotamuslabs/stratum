/**
 * Schema + parser for the `inferImportEquipment` Firebase Callable.
 *
 * Maps a user's distinct imported exercise names to the [com.stratum.domain.importing.GymEquipment]
 * enum (string form). The enum here MUST stay in sync with the Kotlin `GymEquipment` enum.
 *
 * Invalid model enum values are filtered, then recovered server-side via BM25 fuzzy match against
 * the closed vocabulary when possible; rows that still have no equipment after recovery are omitted.
 *
 * This callable is **transient**: the returned name -> equipment map lives only for the duration of
 * `ResolveImportLiftsUseCase` and is never written to Room.
 */
import { matchEquipmentEnum } from "./gymEquipmentBm25";
import { GYM_EQUIPMENT, GYM_EQUIPMENT_SET, type GymEquipment } from "./gymEquipmentVocabulary";

export { GYM_EQUIPMENT, type GymEquipment };

export interface InferImportEquipmentExerciseInput {
  name: string;
  loggedSetCount?: number;
}

export interface InferredEquipmentRow {
  name: string;
  equipment: GymEquipment[];
  confidence: number;
}

export const IMPORT_EQUIPMENT_INFERENCE_MAX_BATCH = 100;

export const IMPORT_EQUIPMENT_SYSTEM_PROMPT = `You map strength-training exercise names to the equipment a lifter must have available to perform them.
For each exercise name, return the equipment enum values required (minimal set for THIS exercise, not variants). Examples: "Cable Lateral Raise" -> ["CableStation"]; "Lat Pulldown - Underhand (Cable)" -> ["CableStation","LatPulldownMachine"]; "Bench Press (Dumbbell)" -> ["Dumbbell","FlatBench"]; "Hack Squat (Machine)" -> ["HackSquat"]; "Back Squat" -> ["Barbell","PowerRack"]. Bodyweight-only moves -> ["Bodyweight"]. Only return equipment from the provided enum. confidence is 0..1 of how sure you are the named exercise needs that equipment. Keep the name exactly as provided.`;

export function buildImportEquipmentUserPrompt(
  exercises: InferImportEquipmentExerciseInput[],
): string {
  return JSON.stringify({ exercises }, null, 2);
}

export function importEquipmentJsonSchema() {
  return {
    name: "import_equipment_inference",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        lifts: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              name: { type: "string" },
              equipment: {
                type: "array",
                items: { type: "string", enum: [...GYM_EQUIPMENT] },
              },
              confidence: { type: "number", minimum: 0, maximum: 1 },
            },
            required: ["name", "equipment", "confidence"],
          },
        },
      },
      required: ["lifts"],
    },
  };
}

/**
 * Parse + sanitize the model payload. Only rows whose `name` was in the request are kept.
 * Unknown enum strings are dropped, then BM25-matched to the nearest enum when confidence is high
 * enough; if the row is still empty, the exercise name itself is tried once. Rows with no
 * recoverable equipment are omitted (the caller treats a missing name as "no equipment signal").
 */
export function parseInferredEquipment(
  payload: unknown,
  requestedNames: Set<string>,
): InferredEquipmentRow[] {
  if (!payload || typeof payload !== "object") {
    return [];
  }
  const lifts = (payload as { lifts?: unknown }).lifts;
  if (!Array.isArray(lifts)) {
    return [];
  }
  const seen = new Set<string>();
  const rows: InferredEquipmentRow[] = [];
  for (const entry of lifts) {
    if (!entry || typeof entry !== "object") {
      continue;
    }
    const row = entry as Record<string, unknown>;
    const name = typeof row.name === "string" ? row.name.trim() : "";
    if (!name || !requestedNames.has(name) || seen.has(name)) {
      continue;
    }
    const equipment = resolveEquipmentList(row.equipment, name);
    if (equipment.length === 0) {
      continue;
    }
    const confidence =
      typeof row.confidence === "number" && row.confidence >= 0 && row.confidence <= 1
        ? row.confidence
        : 1;
    rows.push({ name, equipment, confidence });
    seen.add(name);
  }
  return rows;
}

function resolveEquipmentList(raw: unknown, exerciseName: string): GymEquipment[] {
  const valid: GymEquipment[] = [];
  const invalidLabels: string[] = [];

  if (Array.isArray(raw)) {
    for (const item of raw) {
      if (typeof item !== "string") {
        continue;
      }
      const trimmed = item.trim();
      if (!trimmed) {
        continue;
      }
      if (GYM_EQUIPMENT_SET.has(trimmed) && !valid.includes(trimmed as GymEquipment)) {
        valid.push(trimmed as GymEquipment);
      } else {
        invalidLabels.push(trimmed);
      }
    }
  }

  for (const label of invalidLabels) {
    appendUnique(valid, matchEquipmentEnum(label));
  }

  if (valid.length === 0) {
    appendUnique(valid, matchEquipmentEnum(exerciseName));
  }

  return valid;
}

function appendUnique(target: GymEquipment[], value: GymEquipment | null): void {
  if (value !== null && !target.includes(value)) {
    target.push(value);
  }
}

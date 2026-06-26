export const MOVEMENT_PATTERNS = [
  "LegPush",
  "HipHinge",
  "QuadIso",
  "HamstringIso",
  "Calves",
  "HorizontalPush",
  "VerticalPull",
  "HorizontalPull",
  "ChestIso",
  "TricepIso",
  "BicepIso",
  "DeltIso",
  "GluteIso",
  "ForearmIso",
  "TrapIso",
  "AbIso",
  "InclinePush",
  "VerticalPush",
] as const;

export const MUSCLE_GROUPS = [
  "Chest",
  "Back",
  "Quad",
  "Hamstring",
  "Glute",
  "PosteriorDeltoid",
  "LateralDeltoid",
  "AnteriorDeltoid",
  "Tricep",
  "Bicep",
  "Trap",
  "Calf",
  "Forearm",
  "Ab",
  "LowerBack",
  "Abductor",
  "Adductor",
] as const;

export type MovementPattern = (typeof MOVEMENT_PATTERNS)[number];
export type MuscleGroup = (typeof MUSCLE_GROUPS)[number];

export interface InferCustomLiftsExerciseInput {
  name: string;
  loggedSetCount: number;
}

export interface InferredCustomLiftRow {
  name: string;
  movementPattern: MovementPattern;
  primaryMuscles: MuscleGroup[];
  secondaryMuscles: MuscleGroup[];
  equipment?: string | null;
}

export const CUSTOM_LIFT_INFERENCE_MAX_BATCH = 75;

const PULL_PATTERNS = new Set<MovementPattern>(["HorizontalPull", "VerticalPull"]);
const PUSH_PATTERNS = new Set<MovementPattern>([
  "HorizontalPush",
  "VerticalPush",
  "InclinePush",
]);

function applyPatternSecondaryDefaults(row: InferredCustomLiftRow): InferredCustomLiftRow {
  const secondary = [...row.secondaryMuscles];
  if (PULL_PATTERNS.has(row.movementPattern) && !row.primaryMuscles.includes("Bicep")) {
    if (!secondary.includes("Bicep")) {
      secondary.push("Bicep");
    }
  }
  if (PUSH_PATTERNS.has(row.movementPattern) && !row.primaryMuscles.includes("Tricep")) {
    if (!secondary.includes("Tricep")) {
      secondary.push("Tricep");
    }
  }
  return {
    ...row,
    secondaryMuscles: secondary.filter((muscle) => !row.primaryMuscles.includes(muscle)),
  };
}

export const CUSTOM_LIFT_SYSTEM_PROMPT = `You classify strength-training exercise names for a workout app catalog.

For each exercise name, infer:
- movementPattern: one enum value describing how the lift moves
- primaryMuscles: 1-2 muscle groups that receive most training stress
- secondaryMuscles: 0-4 supporting / co-mover muscle groups (e.g. triceps and front delts on a press, biceps on a row)
- equipment: short optional label (e.g. "barbell", "dumbbell", "cable", "machine", "bodyweight")

Use only the provided enum values. Prefer isolation patterns (TricepIso, BicepIso, etc.) for single-joint accessories.
Lateral raises, side raises, and Y-raises: movementPattern DeltIso, primaryMuscles ["LateralDeltoid"].
Rear delt fly, reverse fly, and face pulls: movementPattern DeltIso, primaryMuscles ["PosteriorDeltoid"].
Front raises: movementPattern DeltIso, primaryMuscles ["AnteriorDeltoid"].
If the name mentions a brand or variant (e.g. "Katana Triceps Extension"), classify by the underlying movement.
Return one row per input exercise; keep the name exactly as provided.`;

export function buildCustomLiftUserPrompt(
  exercises: InferCustomLiftsExerciseInput[],
): string {
  return JSON.stringify({ exercises }, null, 2);
}

export function customLiftInferenceJsonSchema() {
  return {
    name: "custom_lift_inference",
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
              movementPattern: { type: "string", enum: [...MOVEMENT_PATTERNS] },
              primaryMuscles: {
                type: "array",
                items: { type: "string", enum: [...MUSCLE_GROUPS] },
                minItems: 1,
                maxItems: 2,
              },
              secondaryMuscles: {
                type: "array",
                items: { type: "string", enum: [...MUSCLE_GROUPS] },
                maxItems: 4,
              },
              equipment: { type: ["string", "null"] },
            },
            required: [
              "name",
              "movementPattern",
              "primaryMuscles",
              "secondaryMuscles",
              "equipment",
            ],
          },
        },
      },
      required: ["lifts"],
    },
  };
}

export function parseInferredCustomLifts(
  payload: unknown,
  requestedNames: Set<string>,
): InferredCustomLiftRow[] {
  if (!payload || typeof payload !== "object") {
    return [];
  }
  const lifts = (payload as { lifts?: unknown }).lifts;
  if (!Array.isArray(lifts)) {
    return [];
  }
  const patternSet = new Set<string>(MOVEMENT_PATTERNS);
  const muscleSet = new Set<string>(MUSCLE_GROUPS);
  const seen = new Set<string>();
  const rows: InferredCustomLiftRow[] = [];
  for (const entry of lifts) {
    if (!entry || typeof entry !== "object") {
      continue;
    }
    const row = entry as Record<string, unknown>;
    const name = typeof row.name === "string" ? row.name.trim() : "";
    if (!name || !requestedNames.has(name) || seen.has(name)) {
      continue;
    }
    const movementPattern = row.movementPattern;
    if (typeof movementPattern !== "string" || !patternSet.has(movementPattern)) {
      continue;
    }
    const primaryMuscles = normalizeMuscleList(row.primaryMuscles, muscleSet, 2);
    if (primaryMuscles.length === 0) {
      continue;
    }
    const secondaryMuscles = normalizeMuscleList(row.secondaryMuscles, muscleSet, 4).filter(
      (muscle) => !primaryMuscles.includes(muscle),
    );
    const equipment =
      typeof row.equipment === "string" && row.equipment.trim().length > 0
        ? row.equipment.trim()
        : null;
    rows.push(
      applyPatternSecondaryDefaults({
        name,
        movementPattern: movementPattern as MovementPattern,
        primaryMuscles: primaryMuscles as MuscleGroup[],
        secondaryMuscles: secondaryMuscles as MuscleGroup[],
        equipment,
      }),
    );
    seen.add(name);
  }
  return rows;
}

function normalizeMuscleList(
  value: unknown,
  allowed: Set<string>,
  maxItems: number,
): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const out: string[] = [];
  for (const item of value) {
    if (typeof item !== "string" || !allowed.has(item) || out.includes(item)) {
      continue;
    }
    out.push(item);
    if (out.length >= maxItems) {
      break;
    }
  }
  return out;
}

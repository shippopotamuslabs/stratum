export const MOVEMENT_PATTERNS = [
  "SquatPattern",
  "HipHinge",
  "KneeExtension",
  "KneeFlexion",
  "PlantarFlexion",
  "HorizontalPress",
  "VerticalPull",
  "HorizontalPull",
  "ShoulderAdduction",
  "ElbowExtension",
  "ElbowFlexion",
  "ShoulderAbduction",
  "ShoulderHorizontalAbduction",
  "ShoulderFlexion",
  "HipExtension",
  "WristFlexionExtension",
  "ScapularElevation",
  "SpinalFlexion",
  "InclinePress",
  "VerticalPress",
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

/**
 * Seed Room Equipment loading-method ids (keep in sync with
 * `core/data/src/main/assets/lifts/seed-equipment.json`). Plus `custom` for brand-specific stacks.
 */
export const SEED_LOADING_KINDS = [
  "dumbbell",
  "double-weight-plates",
  "single-weight-plate",
  "fixed-barbell",
  "pin-loaded-machine",
  "cable",
  "kettlebell",
  "bodyweight",
] as const;

export const CUSTOM_LOADING_KIND = "custom" as const;

export const EQUIPMENT_KINDS = [...SEED_LOADING_KINDS, CUSTOM_LOADING_KIND] as const;

export type MovementPattern = (typeof MOVEMENT_PATTERNS)[number];
export type MuscleGroup = (typeof MUSCLE_GROUPS)[number];
export type SeedLoadingKind = (typeof SEED_LOADING_KINDS)[number];
export type EquipmentKind = (typeof EQUIPMENT_KINDS)[number];

export interface InferCustomLiftsExerciseInput {
  name: string;
  loggedSetCount: number;
}

/** Closed seed loading method — maps 1:1 to a seed EquipmentId. */
export interface SeedEquipmentLoading {
  type: "seed";
  equipmentId: SeedLoadingKind;
}

/** Explicit custom equipment with a single open-ended increment band (lb). */
export interface CustomEquipmentLoading {
  type: "custom";
  name: string;
  incrementLb: number;
}

export type InferredEquipmentLoading = SeedEquipmentLoading | CustomEquipmentLoading;

export interface InferredCustomLiftRow {
  name: string;
  movementPattern: MovementPattern;
  primaryMuscles: MuscleGroup[];
  secondaryMuscles: MuscleGroup[];
  loading: InferredEquipmentLoading;
}

export const CUSTOM_LIFT_INFERENCE_MAX_BATCH = 75;
/** Shared unknown-loading fallback: one reusable custom equipment row (+5 lb open band). */
export const DEFAULT_EQUIPMENT_NAME = "Default";
export const DEFAULT_CUSTOM_INCREMENT_LB = 5;
/** Reject absurd LLM steps; fall back to [DEFAULT_CUSTOM_INCREMENT_LB]. */
export const MAX_CUSTOM_INCREMENT_LB = 100;

const PULL_PATTERNS = new Set<MovementPattern>(["HorizontalPull", "VerticalPull"]);
const PUSH_PATTERNS = new Set<MovementPattern>([
  "HorizontalPress",
  "VerticalPress",
  "InclinePress",
]);
const SEED_LOADING_KIND_SET = new Set<string>(SEED_LOADING_KINDS);

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
- equipmentKind: how the lift is LOADED for weight increments (seed loading method), not the station brand
- customEquipmentName / customIncrementLb: only when equipmentKind is "custom"; otherwise null

equipmentKind is a closed loading lattice (prefer seed over custom):
- double-weight-plates: free bar both sleeves OR plate-loaded machines with two independent horns/sides (Hammer Strength chest press, plate-loaded row, dual-horn press, etc.)
- single-weight-plate: landmine / T-bar / one sleeve only
- pin-loaded-machine: selectorized pin stack only (NOT plate-loaded dual horns)
- cable: cable weight stack
- dumbbell / kettlebell / fixed-barbell / bodyweight: as named
- custom: ONLY when load steps are brand-specific and not modeled by the seeds; then set customEquipmentName (short label) and customIncrementLb (positive lb step, e.g. 5 or 10)

Examples:
- "Seated Neutral Grip Plate-Loaded Machine Press" -> equipmentKind "double-weight-plates"
- "Katana Triceps Extension" (cable) -> equipmentKind "cable"
- "Leg Extension (Machine)" selectorized -> equipmentKind "pin-loaded-machine"
- "Bench Press (Dumbbell)" -> equipmentKind "dumbbell"

Use only the provided enum values. Prefer isolation patterns (ElbowExtension, ElbowFlexion, etc.) for single-joint accessories.
Lateral raises, side raises, and Y-raises: movementPattern ShoulderAbduction, primaryMuscles ["LateralDeltoid"].
Rear delt fly, reverse fly, and face pulls: movementPattern ShoulderHorizontalAbduction, primaryMuscles ["PosteriorDeltoid"].
Front raises: movementPattern ShoulderFlexion, primaryMuscles ["AnteriorDeltoid"].
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
              equipmentKind: { type: "string", enum: [...EQUIPMENT_KINDS] },
              customEquipmentName: { type: ["string", "null"] },
              customIncrementLb: { type: ["number", "null"] },
            },
            required: [
              "name",
              "movementPattern",
              "primaryMuscles",
              "secondaryMuscles",
              "equipmentKind",
              "customEquipmentName",
              "customIncrementLb",
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
    const loading = resolveLoading(row, name);
    rows.push(
      applyPatternSecondaryDefaults({
        name,
        movementPattern: movementPattern as MovementPattern,
        primaryMuscles: primaryMuscles as MuscleGroup[],
        secondaryMuscles: secondaryMuscles as MuscleGroup[],
        loading,
      }),
    );
    seen.add(name);
  }
  return rows;
}

/**
 * Maps wire equipmentKind fields to structured loading. Invalid/missing kinds become the
 * shared "Default" custom equipment (+5 lb) — no fuzzy aliasing onto seed ids and no
 * per-exercise phantom stacks.
 */
export function resolveLoading(
  row: Record<string, unknown>,
  _exerciseName: string,
): InferredEquipmentLoading {
  const kind = typeof row.equipmentKind === "string" ? row.equipmentKind.trim() : "";
  if (SEED_LOADING_KIND_SET.has(kind)) {
    return { type: "seed", equipmentId: kind as SeedLoadingKind };
  }
  if (kind === CUSTOM_LOADING_KIND) {
    return resolveCustomLoading(row);
  }
  return defaultEquipmentLoading();
}

function resolveCustomLoading(row: Record<string, unknown>): CustomEquipmentLoading {
  const rawName =
    typeof row.customEquipmentName === "string" ? row.customEquipmentName.trim() : "";
  // Blank / unknown name → shared Default so progression always has a +5 lattice.
  const name = rawName.length > 0 ? rawName : DEFAULT_EQUIPMENT_NAME;
  return {
    type: "custom",
    name,
    incrementLb: resolveCustomIncrementLb(row.customIncrementLb),
  };
}

export function defaultEquipmentLoading(): CustomEquipmentLoading {
  return {
    type: "custom",
    name: DEFAULT_EQUIPMENT_NAME,
    incrementLb: DEFAULT_CUSTOM_INCREMENT_LB,
  };
}

function resolveCustomIncrementLb(raw: unknown): number {
  if (
    typeof raw === "number" &&
    Number.isFinite(raw) &&
    raw > 0 &&
    raw <= MAX_CUSTOM_INCREMENT_LB
  ) {
    return raw;
  }
  return DEFAULT_CUSTOM_INCREMENT_LB;
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

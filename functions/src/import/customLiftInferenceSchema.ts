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
  "HipAbduction",
  "HipAdduction",
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
  "default",
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
  /** Always derived from [movementPattern] on the server — never free-form LLM tags. */
  primaryMuscles: MuscleGroup[];
  secondaryMuscles: MuscleGroup[];
  loading: InferredEquipmentLoading;
}

export const CUSTOM_LIFT_INFERENCE_MAX_BATCH = 75;
/** Display name of the seed Default implement (unknown-loading fallback, +2.5 lb open band). */
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

/**
 * Keep in sync with `ImportStubLiftGuesser.primaryMusclesFor` in Kotlin domain.
 * Muscles are never asked of the model — only movementPattern is.
 */
export function primaryMusclesForPattern(pattern: MovementPattern): MuscleGroup[] {
  switch (pattern) {
    case "ElbowFlexion":
      return ["Bicep"];
    case "ElbowExtension":
      return ["Tricep"];
    case "ShoulderAdduction":
      return ["Chest"];
    case "ShoulderAbduction":
      return ["LateralDeltoid"];
    case "ShoulderHorizontalAbduction":
      return ["PosteriorDeltoid"];
    case "ShoulderFlexion":
      return ["AnteriorDeltoid"];
    case "HorizontalPull":
    case "VerticalPull":
      return ["Back"];
    case "HorizontalPress":
    case "InclinePress":
      return ["Chest"];
    case "VerticalPress":
      return ["AnteriorDeltoid"];
    case "SquatPattern":
      return ["Quad"];
    case "HipHinge":
      return ["Glute", "Hamstring"];
    case "KneeExtension":
      return ["Quad"];
    case "KneeFlexion":
      return ["Hamstring"];
    case "PlantarFlexion":
      return ["Calf"];
    case "HipExtension":
      return ["Glute"];
    case "ScapularElevation":
      return ["Trap"];
    case "WristFlexionExtension":
      return ["Forearm"];
    case "SpinalFlexion":
      return ["Ab"];
    case "HipAbduction":
      return ["Abductor"];
    case "HipAdduction":
      return ["Adductor"];
    default: {
      const _exhaustive: never = pattern;
      return _exhaustive;
    }
  }
}

function applyPatternMuscleDefaults(
  name: string,
  movementPattern: MovementPattern,
  loading: InferredEquipmentLoading,
): InferredCustomLiftRow {
  const primaryMuscles = primaryMusclesForPattern(movementPattern);
  const secondary: MuscleGroup[] = [];
  if (PULL_PATTERNS.has(movementPattern) && !primaryMuscles.includes("Bicep")) {
    secondary.push("Bicep");
  }
  if (PUSH_PATTERNS.has(movementPattern) && !primaryMuscles.includes("Tricep")) {
    secondary.push("Tricep");
  }
  return {
    name,
    movementPattern,
    primaryMuscles,
    secondaryMuscles: secondary.filter((muscle) => !primaryMuscles.includes(muscle)),
    loading,
  };
}

export const CUSTOM_LIFT_SYSTEM_PROMPT = `You classify strength-training exercise names for a workout app catalog.

For each exercise name, infer ONLY:
- movementPattern: one enum value describing how the lift moves
- equipmentKind: how the lift is LOADED for weight increments (seed loading method), not the station brand
- customEquipmentName / customIncrementLb: only when equipmentKind is "custom"; otherwise null

Do NOT invent muscle groups — the server derives primary/secondary muscles from movementPattern.

equipmentKind is a closed loading lattice (prefer seed over custom):
- double-weight-plates: free bar both sleeves OR plate-loaded machines with two independent horns/sides (Hammer Strength chest press, plate-loaded row, dual-horn press, etc.)
- single-weight-plate: landmine / T-bar / one sleeve only
- pin-loaded-machine: selectorized pin stack only (NOT plate-loaded dual horns)
- cable: cable weight stack
- dumbbell / kettlebell / fixed-barbell / bodyweight: as named
- default: unknown / unspecified loading (shared 2.5 lb open band)
- custom: ONLY when load steps are brand-specific and not modeled by the seeds; then set customEquipmentName (short label) and customIncrementLb (positive lb step, e.g. 5 or 10)

Examples:
- "Seated Neutral Grip Plate-Loaded Machine Press" -> movementPattern HorizontalPress, equipmentKind "double-weight-plates"
- "Katana Triceps Extension" (cable) -> movementPattern ElbowExtension, equipmentKind "cable"
- "Leg Extension (Machine)" selectorized -> movementPattern KneeExtension, equipmentKind "pin-loaded-machine"
- "Bench Press (Dumbbell)" -> movementPattern HorizontalPress, equipmentKind "dumbbell"
- "Machine Crunch" -> movementPattern SpinalFlexion, equipmentKind "pin-loaded-machine"
- "Roman Chair Hip Hinge" -> movementPattern HipHinge, equipmentKind "bodyweight" (or double-weight-plates if loaded)

Use only the provided enum values. Prefer isolation patterns (ElbowExtension, ElbowFlexion, SpinalFlexion, etc.) for single-joint accessories.
Lateral raises, side raises, and Y-raises: movementPattern ShoulderAbduction.
Rear delt fly, reverse fly, and face pulls: movementPattern ShoulderHorizontalAbduction.
Front raises: movementPattern ShoulderFlexion.
Crunches, sit-ups, ab machines: movementPattern SpinalFlexion.
Hip hinges, RDLs, good mornings, back extensions, roman chair hinges: movementPattern HipHinge.
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
              equipmentKind: { type: "string", enum: [...EQUIPMENT_KINDS] },
              customEquipmentName: { type: ["string", "null"] },
              customIncrementLb: { type: ["number", "null"] },
            },
            required: [
              "name",
              "movementPattern",
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
    const loading = resolveLoading(row, name);
    rows.push(
      applyPatternMuscleDefaults(name, movementPattern as MovementPattern, loading),
    );
    seen.add(name);
  }
  return rows;
}

/**
 * Maps wire equipmentKind fields to structured loading. Invalid/missing kinds become the
 * seed Default implement (+2.5 lb) — no fuzzy aliasing onto other seed ids and no
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

function resolveCustomLoading(row: Record<string, unknown>): InferredEquipmentLoading {
  const rawName =
    typeof row.customEquipmentName === "string" ? row.customEquipmentName.trim() : "";
  // Blank / unknown name → seed Default so progression always has a lattice.
  if (rawName.length === 0) {
    return defaultEquipmentLoading();
  }
  return {
    type: "custom",
    name: rawName,
    incrementLb: resolveCustomIncrementLb(row.customIncrementLb),
  };
}

export function defaultEquipmentLoading(): SeedEquipmentLoading {
  return {
    type: "seed",
    equipmentId: "default",
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

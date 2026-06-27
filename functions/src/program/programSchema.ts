/**
 * Strict JSON schema + normalizer for the `generateProgram` callable (OS-7 D2).
 *
 * The model returns structure only under [programJsonSchema] (no `progressionScheme` — the server
 * assigns it). [parseAndNormalizeProgram] then validates (schema, lift membership, ≥1 workout,
 * Double top-set rule) and overwrites the model's derived numerics via [programRules].
 *
 * The normalized document has NO ids — the Android client assigns UUID value-class ids at parse
 * time (D1).
 */
import {
  clampBackOffRir,
  MAJOR_COMPOUND_PATTERNS,
  schemeForTrainingAge,
  TOP_SET_RIR,
} from "./programRules";

export interface ProgramNormalizationRequest {
  trainingAge: string;
  enabledLiftIds: Set<string>;
  /** liftId -> movementPattern, populated from the payload's proven/available lifts. */
  movementPatternByLiftId: Map<string, string>;
}

export function programJsonSchema() {
  return {
    name: "program_generation",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        program: {
          type: "object",
          additionalProperties: false,
          properties: {
            name: { type: "string" },
            workouts: {
              type: "array",
              items: {
                type: "object",
                additionalProperties: false,
                properties: {
                  name: { type: "string" },
                  weekday: {
                    type: ["string", "null"],
                    enum: [
                      "Monday",
                      "Tuesday",
                      "Wednesday",
                      "Thursday",
                      "Friday",
                      "Saturday",
                      "Sunday",
                      null,
                    ],
                  },
                  position: { type: "integer" },
                  workoutLifts: {
                    type: "array",
                    items: {
                      type: "object",
                      additionalProperties: false,
                      properties: {
                        liftId: { type: "string" },
                        position: { type: "integer" },
                        workoutLiftSets: {
                          type: "array",
                          items: {
                            type: "object",
                            additionalProperties: false,
                            properties: {
                              position: { type: "integer" },
                              setType: {
                                type: "string",
                                enum: ["Standard", "MyoRep", "DropSet"],
                              },
                              repMin: { type: "integer" },
                              repMax: { type: "integer" },
                              rirGoal: { type: "integer" },
                              isTopSet: { type: "boolean" },
                            },
                            required: [
                              "position",
                              "setType",
                              "repMin",
                              "repMax",
                              "rirGoal",
                              "isTopSet",
                            ],
                          },
                        },
                      },
                      required: ["liftId", "position", "workoutLiftSets"],
                    },
                  },
                },
                required: ["name", "weekday", "position", "workoutLifts"],
              },
            },
          },
          required: ["name", "workouts"],
        },
      },
      required: ["program"],
    },
  };
}

// Re-exported so the normalizer is the single import surface for the callable.
export { clampBackOffRir, MAJOR_COMPOUND_PATTERNS, schemeForTrainingAge, TOP_SET_RIR };

/**
 * Validate + deterministically normalize the parsed model payload.
 *
 * Validate: schema-conformant; every workoutLift.liftId in [enabledLiftIds]; ≥1 workout after
 * validation; Double lifts have exactly one top set with a rep range.
 * Normalize: progressionScheme from trainingAge; clamp RIR per §2.3; enforce straight sets.
 */
export function parseAndNormalizeProgram(
  payload: unknown,
  request: ProgramNormalizationRequest,
): unknown {
  if (!payload || typeof payload !== "object") {
    throw new Error("Malformed program payload: expected object");
  }
  const program = (payload as { program?: unknown }).program;
  if (!program || typeof program !== "object") {
    throw new Error("Malformed program payload: missing program");
  }
  const prog = program as { name?: unknown; workouts?: unknown };
  const name = typeof prog.name === "string" ? prog.name : "Program";
  const rawWorkouts = Array.isArray(prog.workouts) ? prog.workouts : [];
  const scheme = schemeForTrainingAge(request.trainingAge);

  const workouts: NormalizedWorkout[] = [];
  for (const w of rawWorkouts) {
    if (!w || typeof w !== "object") continue;
    const workout = w as RawWorkout;
    const lifts = normalizeLifts(workout, request, scheme);
    if (lifts.length === 0) continue; // drop workouts emptied by validation
    workouts.push({
      name: typeof workout.name === "string" ? workout.name : "Workout",
      weekday: typeof workout.weekday === "string" ? workout.weekday : null,
      position: typeof workout.position === "number" ? workout.position : workouts.length,
      workoutLifts: lifts,
    });
  }
  if (workouts.length === 0) {
    throw new Error("No valid workouts after normalization");
  }
  return { name, workouts };
}

function normalizeLifts(
  workout: RawWorkout,
  request: ProgramNormalizationRequest,
  scheme: "Linear" | "Double",
): NormalizedLift[] {
  const rawLifts = Array.isArray(workout.workoutLifts) ? workout.workoutLifts : [];
  const out: NormalizedLift[] = [];
  for (const l of rawLifts) {
    if (!l || typeof l !== "object") continue;
    const lift = l as RawLift;
    const liftId = typeof lift.liftId === "string" ? lift.liftId : null;
    if (!liftId || !request.enabledLiftIds.has(liftId)) continue; // drop unknown lifts
    const isMajorCompound = MAJOR_COMPOUND_PATTERNS.has(
      request.movementPatternByLiftId.get(liftId) ?? "",
    );
    const sets = normalizeSets(lift, isMajorCompound, scheme);
    if (sets.length === 0) continue;
    // §2.3 Double top-set rule: a Double lift must have exactly one top set with a rep range.
    if (scheme === "Double") {
      const topSets = sets.filter((s) => s.isTopSet);
      if (topSets.length !== 1 || topSets[0].repMin <= 0 || topSets[0].repMax <= 0) {
        throw new Error(
          `Double progression lift ${liftId} must have exactly one top set with a rep range`,
        );
      }
    }
    out.push({
      liftId,
      position: typeof lift.position === "number" ? lift.position : out.length,
      progressionScheme: scheme,
      workoutLiftSets: sets,
    });
  }
  return out;
}

function normalizeSets(
  lift: RawLift,
  isMajorCompound: boolean,
  scheme: "Linear" | "Double",
): NormalizedSet[] {
  const rawSets = Array.isArray(lift.workoutLiftSets) ? lift.workoutLiftSets : [];
  const out: NormalizedSet[] = [];
  for (const s of rawSets) {
    if (!s || typeof s !== "object") continue;
    const set = s as RawSet;
    const isTopSet = set.isTopSet === true;
    const rirGoal = isTopSet
      ? TOP_SET_RIR
      : clampBackOffRir(isMajorCompound, typeof set.rirGoal === "number" ? set.rirGoal : 0);
    out.push({
      position: typeof set.position === "number" ? set.position : out.length,
      setType: set.setType === "MyoRep" || set.setType === "DropSet" ? set.setType : "Standard",
      repMin: typeof set.repMin === "number" ? set.repMin : 0,
      repMax: typeof set.repMax === "number" ? set.repMax : 0,
      rirGoal,
      isTopSet,
    });
  }
  return out;
}

interface RawWorkout {
  name?: unknown;
  weekday?: unknown;
  position?: unknown;
  workoutLifts?: unknown;
}

interface RawLift {
  liftId?: unknown;
  position?: unknown;
  workoutLiftSets?: unknown;
}

interface RawSet {
  position?: unknown;
  setType?: unknown;
  repMin?: unknown;
  repMax?: unknown;
  rirGoal?: unknown;
  isTopSet?: unknown;
}

interface NormalizedSet {
  position: number;
  setType: string;
  repMin: number;
  repMax: number;
  rirGoal: number;
  isTopSet: boolean;
}

interface NormalizedLift {
  liftId: string;
  position: number;
  progressionScheme: "Linear" | "Double";
  workoutLiftSets: NormalizedSet[];
}

interface NormalizedWorkout {
  name: string;
  weekday: string | null;
  position: number;
  workoutLifts: NormalizedLift[];
}

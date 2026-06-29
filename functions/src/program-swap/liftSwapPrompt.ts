/**
 * Bounded LLM prompt for lift-ID swaps on the finished program (structure fixed).
 */

export const LIFT_SWAP_PROMPT_VERSION = 2;

/** Max swaps the model may suggest in one workout. */
export const LIFT_SWAP_MAX_PER_WORKOUT = 3;

export const LIFT_SWAP_SYSTEM_PROMPT = `You are an experienced hypertrophy coach reviewing a finished training program before it is delivered to the lifter.

Your ONLY job is to suggest lift swaps where a different exercise from that slot's candidate list would clearly be better for the program's goals. The split, set counts, rep ranges, exercise order, and number of exercises are already final — do not change them.

Program intent (specialization, session length, training days) is included in the payload. Use it:
- On arm-specialization programs, favor direct arm isolation (curls, pushdowns, extensions, skull crushers) over odd pressing variants that steal arm volume.
- On full-body or upper days, favor balanced bread-and-butter compounds (flat/incline bench, rows, chin-ups, front/back squat, RDL) over obscure variants (decline bench, guillotine bench, behind-neck press, one-leg barbell squat, dip machine when better tricep isolation exists).
- Prefer dumbbell/machine shoulder press over behind-neck barbell press.
- Prefer flat or incline pressing over decline pressing unless the candidate list offers no reasonable alternative.

Rules:
- Return ONLY swaps — never change sets, reps, RIR, order, or add/remove exercises.
- Each swap must use a liftId from that exercise's candidates list, verbatim.
- Max ${LIFT_SWAP_MAX_PER_WORKOUT} swaps per workout.
- Do not swap a lift that is already an excellent choice for its role.
- Return an empty swaps array if the program already looks like something you would assign.`;

export interface LiftSwapCandidatePayload {
  liftId: string;
  name: string;
  movementPattern: string;
  primaryMuscles: string[];
}

export interface LiftSwapExercisePayload {
  position: number;
  liftId: string;
  name: string;
  movementPattern: string;
  primaryMuscles: string[];
  secondaryMuscles: string[];
  setCount: number;
  repMin: number | null;
  repMax: number | null;
  candidates: LiftSwapCandidatePayload[];
}

export interface LiftSwapWorkoutPayload {
  position: number;
  name: string;
  workoutLifts: LiftSwapExercisePayload[];
}

export interface LiftSwapProgramIntentPayload {
  trainingAge: string;
  sessionLengthMinutes: number | null;
  trainingDaysPerWeek: number;
  cycleLength: number;
  specialization: Record<string, string>;
  specializedMuscles: string[];
}

export interface LiftSwapReviewPayload {
  promptVersion: number;
  intent: LiftSwapProgramIntentPayload;
  program: {
    cycleLength: number;
    workouts: LiftSwapWorkoutPayload[];
  };
}

export function buildLiftSwapInput(payload: LiftSwapReviewPayload): Record<string, unknown> {
  return payload as unknown as Record<string, unknown>;
}

export function liftSwapOutputSchema() {
  return {
    name: "lift_swap",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        swaps: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              workoutPosition: { type: "integer" },
              liftPosition: { type: "integer" },
              newLiftId: { type: "string" },
            },
            required: ["workoutPosition", "liftPosition", "newLiftId"],
          },
        },
      },
      required: ["swaps"],
    },
  };
}

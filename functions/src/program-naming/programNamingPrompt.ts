/**
 * Bounded LLM prompt for program and workout naming on a finished generated program.
 */

export const PROGRAM_NAMING_PROMPT_VERSION = 1;

export const PROGRAM_NAMING_SYSTEM_PROMPT = `You are an experienced hypertrophy coach naming a finished training program before it is delivered to the lifter.

Your ONLY job is to suggest:
1. A short program name (3–8 words) reflecting training frequency, split shape, and specialization emphasis.
2. A short workout name (2–5 words) for each session.

Rules:
- Use the specialization intent, training frequency (days per week / cycle length), and exercise list provided.
- Names should sound like what a coach would label a program in a training app — not robotic internal codes.
- Do NOT include set counts, rep ranges, or RIR in names.
- Do NOT invent exercises not present in the payload.
- Workout names may reference muscle groups or session role (e.g. "Upper — Push & Arms", "Lower — Quads & Calves").
- Program name examples: "5-Day Arm & Calf Focus", "4-Day Upper/Lower Hypertrophy", "3-Day Full Body".
- Return exactly one name per workout position provided.`;

export interface NameProgramIntentPayload {
  trainingAge: string;
  sessionLengthMinutes: number | null;
  trainingDaysPerWeek: number;
  cycleLength: number;
  specialization: Record<string, string>;
  specializedMuscles: string[];
}

export interface NameProgramLiftPayload {
  name: string;
  movementPattern: string;
  primaryMuscles: string[];
  setCount: number;
}

export interface NameProgramWorkoutPayload {
  position: number;
  weekday: string | null;
  workoutLifts: NameProgramLiftPayload[];
}

export interface NameProgramRequestPayload {
  promptVersion: number;
  intent: NameProgramIntentPayload;
  program: {
    workouts: NameProgramWorkoutPayload[];
  };
}

export function buildProgramNamingInput(
  payload: NameProgramRequestPayload,
): Record<string, unknown> {
  return payload as unknown as Record<string, unknown>;
}

export function programNamingOutputSchema() {
  return {
    name: "program_naming",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        programName: { type: "string" },
        workouts: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              position: { type: "integer" },
              name: { type: "string" },
            },
            required: ["position", "name"],
          },
        },
      },
      required: ["programName", "workouts"],
    },
  };
}

export interface NameProgramWorkoutNameRow {
  position: number;
  name: string;
}

export interface NameProgramResponsePayload {
  programName: string;
  workouts: NameProgramWorkoutNameRow[];
}

export function parseNameProgramResponse(content: string): NameProgramResponsePayload | null {
  try {
    const parsed = JSON.parse(content) as {
      programName?: unknown;
      workouts?: unknown;
    };
    if (typeof parsed.programName !== "string" || !Array.isArray(parsed.workouts)) {
      return null;
    }
    const workouts: NameProgramWorkoutNameRow[] = [];
    for (const row of parsed.workouts) {
      if (!row || typeof row !== "object") continue;
      const r = row as Record<string, unknown>;
      if (typeof r.position === "number" && typeof r.name === "string") {
        workouts.push({ position: r.position, name: r.name.trim() });
      }
    }
    if (workouts.length === 0) return null;
    return {
      programName: parsed.programName.trim(),
      workouts,
    };
  } catch {
    return null;
  }
}

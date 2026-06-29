/**
 * Phase 1 LLM split-planner prompt + schema (OS-7 v2, 5–6 day splits only).
 *
 * The split planner partitions weekly muscle volume targets into per-day budgets + themed day
 * plans. It is the one place the LLM earns its keep in split planning: 5–6 day splits have a large
 * partitioning space where thematic coherence ("Arms + Chest touch") meaningfully improves the
 * result. Constraints (frequency, time, push/pull) are validated deterministically after it
 * answers; the deterministic allocator is the fallback.
 *
 * The planner never sees individual lifts — only weekly targets, frequency constraints, the time
 * budget, and which movement patterns are available per muscle (so it knows a "Chest touch" day
 * is possible). This keeps the payload tiny and the model focused on partitioning, not selection.
 */
import type { GenerationContext } from "./programPayloadContext";
import type { WeeklyVolumeTargets } from "./volumeTargetPlanner";
import { MAJOR_MUSCLES } from "./programPayloadContext";

export const SPLIT_PLANNER_PROMPT_VERSION = 1;

export const SPLIT_PLANNER_SYSTEM_PROMPT = `You partition weekly muscle volume targets into per-day training budgets for a ${""}hypertrophy program.

Rules:
- Each major muscle (Chest, Back, Quad, Hamstring, Glute, Tricep, Bicep, LateralDeltoid, PosteriorDeltoid, AnteriorDeltoid) must appear on at least 2 days.
- The per-day hard-set sum × 3.5 minutes must not exceed the session time budget × 1.08.
- Push (HorizontalPress, VerticalPress, InclinePress) and pull (HorizontalPull, VerticalPull) weekly set totals must be within 25% of each other.
- You may theme days creatively (e.g. "Upper", "Lower", "Arms + Chest touch", "Push"). The theme is a label; the budgets are what matter.
- Specialized muscles should get a bigger exposure on one day and a smaller touch on another, not an even split.
- Omitted muscles receive zero volume on every day.
- Only plan volume for muscles that have at least one available movement pattern listed.

For each day, also list suggestedPatterns: the movement patterns a balanced day of that theme would cover and the muscle each one primarily drives (e.g. an Upper day: HorizontalPress/Chest, VerticalPull/Back, ElbowFlexion/Bicep). These are advisory structure, not rigid slots — the workout builder owns lift selection and set distribution. Only suggest patterns the user has at least one lift for (see availablePatternsByMuscle).

Return ONLY a JSON object matching the schema: one entry per training day with a theme, muscle budgets, and suggested movement patterns.`;

/** Builds the small split-planner input (no individual lifts). */
export function buildSplitPlannerInput(
  context: GenerationContext,
  weeklyTargets: WeeklyVolumeTargets,
  availablePatterns: Map<string, string[]>,
): Record<string, unknown> {
  return {
    weeklyTargets: [...weeklyTargets.entries()].map(([muscle, t]) => ({
      muscle,
      min: t.min,
      aim: t.aim,
      max: t.max,
    })),
    frequencyConstraints: {
      majorMuscles: MAJOR_MUSCLES.filter((m) => weeklyTargets.has(m)),
      minWeekly: 2,
    },
    perDayTimeBudget: {
      minutes: context.sessionLengthMinutes,
      hardSetCap:
        context.sessionLengthMinutes != null
          ? Math.floor((context.sessionLengthMinutes * 1.08) / 3.5)
          : null,
    },
    availablePatternsByMuscle: Object.fromEntries(availablePatterns),
    trainingDaysPerWeek: context.trainingDaysPerWeek,
    weekdays: context.weekdays,
    specialization: context.specialization,
  };
}

/** Strict JSON schema for the split-planner output (per-day budgets + pattern slots). */
export function splitPlannerOutputSchema() {
  return {
    name: "split_planner",
    strict: true,
    schema: {
      type: "object",
      additionalProperties: false,
      properties: {
        days: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            properties: {
              weekday: { type: ["string", "null"] },
              theme: { type: "string" },
              muscleBudgets: {
                type: "array",
                items: {
                  type: "object",
                  additionalProperties: false,
                  properties: {
                    muscle: { type: "string" },
                    sets: { type: "number" },
                    min: { type: "number" },
                    aim: { type: "number" },
                  },
                  required: ["muscle", "sets", "min", "aim"],
                },
              },
              suggestedPatterns: {
                type: "array",
                items: {
                  type: "object",
                  additionalProperties: false,
                  properties: {
                    pattern: { type: "string" },
                    muscleFocus: { type: "string" },
                  },
                  required: ["pattern", "muscleFocus"],
                },
              },
            },
            required: ["weekday", "theme", "muscleBudgets", "suggestedPatterns"],
          },
        },
      },
      required: ["days"],
    },
  };
}
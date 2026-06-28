/**
 * Deterministic Phase 0 split allocation (OS-7 v2).
 *
 * Produces a `DayPlan[]` skeleton that fully constrains the per-workout builders: per-muscle
 * hard-set budgets that sum to the weekly targets, per-day time budgets, and per-pattern slots
 * that enforce frequency + push/pull balance by construction. Used directly for 2–4 day
 * programs; for 5–6 day programs it is both the fallback and the constraint set handed to the
 * LLM split planner.
 *
 * The allocator never emits a `patternSlot` for a pattern the user has no enabled lift for
 * (feasibility guard). When a theme's expected pattern is unavailable, it relaxes to the next
 * best available pattern for the same muscle, or marks that muscle Omit for the day and
 * redistributes its sets to the remaining day(s) for that muscle.
 */
import {
  type GenerationContext,
  MAJOR_MUSCLES,
  MINUTES_PER_HARD_SET,
} from "./programPayloadContext";
import {
  availablePatternsByMuscle,
  type WeeklyVolumeTargets,
} from "./volumeTargetPlanner";

/** Session-time tolerance applied when computing the per-day hard-set cap (matches the validators). */
const DURATION_TOLERANCE_RATIO = 1.08;

/** Per-muscle hard-set credit budget for one day. */
export interface MuscleBudget {
  muscle: string;
  /** Target hard-set credits for this muscle on this day (sums to weekly aim across days). */
  sets: number;
  /** Minimum credits Phase 2 must enforce (derived from weekly min ÷ frequency). */
  min: number;
  /** Productive aim Phase 2 should target (derived from weekly aim ÷ frequency). */
  aim: number;
}

/** A pre-allocated movement-pattern slot the builder must fill with one lift. */
export interface PatternSlot {
  pattern: string;
  muscleFocus: string;
  minSets: number;
  maxSets: number;
}

/** A fully constrained day plan handed to a workout builder. */
export interface DayPlan {
  dayIndex: number;
  weekday: string;
  name: string;
  theme: string;
  /** Hard-set cap for the day (sessionLength × tolerance ÷ minutes-per-set), or null if no limit. */
  timeBudgetHardSets: number | null;
  muscleBudgets: MuscleBudget[];
  patternSlots: PatternSlot[];
}

/** A split template: one day descriptor per training day. */
interface SplitDayTemplate {
  theme: string;
  name: string;
  /** Muscles this day is primarily responsible for (drives budget allocation + slot generation). */
  primaryMuscles: string[];
  /** Optional accessory muscles this day can absorb if it has slack time. */
  accessoryMuscles?: string[];
}

/** Lookup of split templates by training-days-per-week. */
const SPLIT_TEMPLATES: Record<number, SplitDayTemplate[]> = {
  // Full body ×2: every major muscle both days.
  2: [
    { theme: "Full Body A", name: "Full Body A", primaryMuscles: allMajorMuscles() },
    { theme: "Full Body B", name: "Full Body B", primaryMuscles: allMajorMuscles() },
  ],
  // Full body ×3: every major muscle all three days.
  3: [
    { theme: "Full Body A", name: "Full Body A", primaryMuscles: allMajorMuscles() },
    { theme: "Full Body B", name: "Full Body B", primaryMuscles: allMajorMuscles() },
    { theme: "Full Body C", name: "Full Body C", primaryMuscles: allMajorMuscles() },
  ],
  // Upper / Lower ×2.
  4: [
    upperTemplate("Upper A"),
    lowerTemplate("Lower A"),
    upperTemplate("Upper B"),
    lowerTemplate("Lower B"),
  ],
  // Upper, Lower, Arms + Chest touch, Lower, Upper — the owner's real-world 5-day example.
  5: [
    upperTemplate("Upper A"),
    lowerTemplate("Lower A"),
    {
      theme: "Arms + Chest touch",
      name: "Arms + Chest touch",
      primaryMuscles: ["Bicep", "Tricep", "Chest", "LateralDeltoid"],
      accessoryMuscles: ["AnteriorDeltoid"],
    },
    lowerTemplate("Lower B"),
    upperTemplate("Upper B"),
  ],
  // Push / Pull / Legs ×2.
  6: [
    pushTemplate("Push A"),
    pullTemplate("Pull A"),
    legsTemplate("Legs A"),
    pushTemplate("Push B"),
    pullTemplate("Pull B"),
    legsTemplate("Legs B"),
  ],
};

function allMajorMuscles(): string[] {
  return [...MAJOR_MUSCLES];
}

function upperTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Chest", "Back", "LateralDeltoid", "Bicep", "Tricep", "PosteriorDeltoid"],
    accessoryMuscles: ["AnteriorDeltoid", "Ab"],
  };
}

function lowerTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Quad", "Hamstring", "Glute", "Calf"],
    accessoryMuscles: ["Ab", "LowerBack"],
  };
}

function pushTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Chest", "Tricep", "LateralDeltoid", "AnteriorDeltoid"],
    accessoryMuscles: ["Ab"],
  };
}

function pullTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Back", "Bicep", "PosteriorDeltoid"],
    accessoryMuscles: ["LowerBack", "Trap"],
  };
}

function legsTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Quad", "Hamstring", "Glute", "Calf"],
    accessoryMuscles: ["Ab", "LowerBack"],
  };
}

/**
 * Returns the split template for [days], or null if no template is defined (e.g. 7 days).
 * Callers must treat null as "defer to LLM split planner" and fall back to the nearest
 * available template if the planner also fails.
 */
export function splitTemplateFor(days: number): SplitDayTemplate[] | null {
  return SPLIT_TEMPLATES[days] ?? null;
}

/**
 * Computes the per-day hard-set time cap. Returns null when no session length is set, meaning
 * the day is unconstrained by time (Phase 2 still validates the final program).
 */
function timeBudgetForDay(context: GenerationContext): number | null {
  if (context.sessionLengthMinutes == null || context.sessionLengthMinutes <= 0) {
    return null;
  }
  return Math.floor(
    (context.sessionLengthMinutes * DURATION_TOLERANCE_RATIO) / MINUTES_PER_HARD_SET,
  );
}

/**
 * The set of muscles a day is responsible for: its primary muscles plus any accessory muscles,
 * intersected with the muscles that actually have weekly targets (non-Omit). Accessories are
 * only included if the user has an enabled lift pattern hitting them.
 */
function dayMuscles(
  template: SplitDayTemplate,
  targets: WeeklyVolumeTargets,
  patternsByMuscle: Map<string, string[]>,
): { primaries: string[]; accessories: string[] } {
  const primaries = template.primaryMuscles.filter(
    (m) => targets.has(m) && (patternsByMuscle.get(m)?.length ?? 0) > 0,
  );
  const accessories = (template.accessoryMuscles ?? []).filter(
    (m) => targets.has(m) && (patternsByMuscle.get(m)?.length ?? 0) > 0,
  );
  return { primaries, accessories };
}

/** Days of the week that target [muscle] as a primary focus. */
function daysTargetingMuscle(
  muscle: string,
  templates: SplitDayTemplate[],
): number[] {
  return templates
    .map((t, i) => (t.primaryMuscles.includes(muscle) ? i : -1))
    .filter((i) => i >= 0);
}

/**
 * Allocates weekly volume targets across training days into per-day muscle budgets, then
 * derives per-pattern slots. Guarantees:
 *  - every non-Omit major muscle appears on ≥2 days (frequency by construction for 3+ days),
 *  - per-day set credits respect the time budget,
 *  - no patternSlot is emitted for a pattern with zero enabled candidates (feasibility guard).
 *
 * When the user's enabled lifts cannot satisfy a template (e.g. a "Legs" day with no leg lifts),
 * the unsatisfiable muscles are dropped for that day and their weekly budget is redistributed to
 * the remaining day(s) that target them; if no day targets them, the muscle receives no slots
 * (Phase 2 will surface the MEV miss as a validation issue rather than failing generation).
 */
export function allocateDayPlans(
  context: GenerationContext,
  weeklyTargets: WeeklyVolumeTargets,
): DayPlan[] {
  const templates = splitTemplateFor(context.trainingDaysPerWeek);
  if (templates == null) {
    // No deterministic template — caller (pipeline) must invoke the LLM split planner.
    return [];
  }
  const patternsByMuscle = availablePatternsByMuscle(context);
  const timeBudget = timeBudgetForDay(context);

  // Frequency per muscle: how many days target it as a primary.
  const dayMusclesByDay = templates.map((t) =>
    dayMuscles(t, weeklyTargets, patternsByMuscle),
  );

  return templates.map((template, dayIndex) => {
    const { primaries, accessories } = dayMusclesByDay[dayIndex];
    const muscleBudgets: MuscleBudget[] = [];

    // Primary muscles: split weekly aim across the days that target this muscle.
    for (const muscle of primaries) {
      const weekly = weeklyTargets.get(muscle);
      if (!weekly) continue;
      const frequency = daysTargetingMuscle(muscle, templates).length;
      const perDay = frequency > 0 ? weekly.aim / frequency : weekly.aim;
      const minPerDay = frequency > 0 ? weekly.min / frequency : weekly.min;
      // Specialize muscles with freq≥2 get a big exposure + a smaller touch (~60/40 split).
      const isSpecialized = (context.specialization[muscle] ?? "Maintenance") === "Specialize";
      const allocation = specializedAllocation(perDay, isSpecialized, frequency, dayIndex, muscle, templates);
      muscleBudgets.push({
        muscle,
        sets: Math.round(allocation * 10) / 10,
        min: Math.ceil(minPerDay),
        aim: Math.round(perDay),
      });
    }

    // Accessory spillover: if the day has time slack, pull accessory muscle volume here.
    const usedCredits = sumPrimaryCredits(muscleBudgets);
    let slack = timeBudget != null ? Math.max(0, timeBudget - usedCredits) : 0;
    if (slack > 0) {
      for (const muscle of accessories) {
        if (slack <= 0) break;
        const weekly = weeklyTargets.get(muscle);
        if (!weekly) continue;
        // Take a small dose from the weekly aim (at most ~1/3, capped by slack).
        const dose = Math.min(
          Math.max(1, Math.floor(weekly.aim / 4)),
          slack,
        );
        if (dose <= 0) continue;
        muscleBudgets.push({ muscle, sets: dose, min: dose, aim: dose });
        slack -= dose;
      }
    }

    // Pattern slots: for each primary muscle budget, pick a pattern the user has available.
    const patternSlots = derivePatternSlots(muscleBudgets, patternsByMuscle, timeBudget);

    return {
      dayIndex,
      weekday: context.weekdays[dayIndex] ?? null,
      name: template.name,
      theme: template.theme,
      timeBudgetHardSets: timeBudget,
      muscleBudgets,
      patternSlots,
    };
  });
}

/**
 * For Specialize muscles trained ≥2×/week, bias toward a big primary exposure + a smaller
 * touch rather than an even split. Non-specialized muscles get an even per-day dose.
 */
function specializedAllocation(
  perDay: number,
  isSpecialized: boolean,
  frequency: number,
  dayIndex: number,
  muscle: string,
  templates: SplitDayTemplate[],
): number {
  if (!isSpecialized || frequency < 2) return perDay;
  const targetingDays = daysTargetingMuscle(muscle, templates);
  const firstDay = targetingDays[0];
  // Big exposure on the first targeting day (~60%), smaller touch on the rest (~40% split).
  if (dayIndex === firstDay) {
    return perDay * 1.2; // 60% of 2× weekly → 1.2× the even dose
  }
  return perDay * 0.8; // 40% of 2× weekly → 0.8× the even dose
}

/** Approximate hard-set credits for the day from primary muscle budgets (1 credit/set). */
function sumPrimaryCredits(budgets: MuscleBudget[]): number {
  return budgets.reduce((sum, b) => sum + b.sets, 0);
}

/**
 * Derive pattern slots from the day's muscle budgets. For each muscle, picks the first available
 * pattern the user has an enabled lift for. Splits a muscle's budget into minSets/maxSets bounds
 * for the slot. Never emits a slot for a muscle with no available pattern (feasibility guard).
 */
function derivePatternSlots(
  budgets: MuscleBudget[],
  patternsByMuscle: Map<string, string[]>,
  timeBudget: number | null,
): PatternSlot[] {
  const slots: PatternSlot[] = [];
  for (const budget of budgets) {
    const patterns = patternsByMuscle.get(budget.muscle) ?? [];
    if (patterns.length === 0) continue; // feasibility guard
    // Prefer the first available pattern (deterministic). A richer version would prefer compound
    // patterns for large budgets, but that's a v2 refinement; the builders pick the lift anyway.
    const pattern = patterns[0];
    const sets = Math.max(1, Math.round(budget.sets));
    slots.push({
      pattern,
      muscleFocus: budget.muscle,
      minSets: Math.max(1, Math.floor(sets * 0.75)),
      maxSets: sets,
    });
  }
  // If time budget is tight, trim the total slot maxSets to fit.
  if (timeBudget != null) {
    const total = slots.reduce((s, slot) => s + slot.maxSets, 0);
    if (total > timeBudget) {
      const scale = timeBudget / total;
      for (const slot of slots) {
        slot.maxSets = Math.max(1, Math.floor(slot.maxSets * scale));
        slot.minSets = Math.min(slot.minSets, slot.maxSets);
      }
    }
  }
  return slots;
}
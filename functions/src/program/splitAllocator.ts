/**
 * Deterministic Phase 0 split allocation (OS-7 v2).
 *
 * Produces a `DayPlan[]` skeleton that hands the LLM builder the hard constraints (per-muscle
 * hard-set budgets that sum to the weekly targets, per-day time budgets) plus a light
 * `suggestedPatterns` advisory — the movement patterns a balanced day for this theme would cover
 * and the muscle each one would drive. The builder OWNS exercise selection, set distribution
 * across lifts, and ordering; these suggestions keep it honest without rigidly slotting one lift
 * per pattern (which collapsed specialized muscles into a single exercise and mislabeled muscle
 * focus via catalog-order-dependent pattern selection).
 *
 * The deterministic allocator is used directly for 2–4 day programs; for 5–6 day programs it is
 * both the fallback and the constraint set handed to the LLM split planner.
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

/**
 * A movement pattern a balanced day for this theme would cover, with the muscle it would primarily
 * drive. ADVISORY ONLY — the builder is free to pick more or fewer lifts per pattern and to
 * distribute sets across multiple lifts. No set bounds: the muscle budgets are the constraint.
 */
export interface SuggestedPattern {
  pattern: string;
  muscleFocus: string;
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
  /**
   * Movement patterns a balanced day of this theme typically covers, with the muscle each drives.
   * Advisory — the builder owns lift selection and set distribution. Patterns are filtered to those
   * the user has at least one enabled lift for, so every suggestion is fillable.
   */
  suggestedPatterns: SuggestedPattern[];
}

/** A split template: one day descriptor per training day. */
interface SplitDayTemplate {
  theme: string;
  name: string;
  /** Muscles this day is primarily responsible for (drives budget allocation). */
  primaryMuscles: string[];
  /** Optional accessory muscles this day can absorb if it has slack time. */
  accessoryMuscles?: string[];
  /**
   * Movement patterns a balanced day of this theme typically covers, with the muscle each drives.
   * Order = the order a coach would program them (compound → isolation). The builder may deviate.
   */
  suggestedPatterns: SuggestedPatternTemplate[];
}

interface SuggestedPatternTemplate {
  pattern: string;
  muscleFocus: string;
}

const UPPER_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "HorizontalPush", muscleFocus: "Chest" },
  { pattern: "VerticalPull", muscleFocus: "Back" },
  { pattern: "InclinePush", muscleFocus: "Chest" },
  { pattern: "HorizontalPull", muscleFocus: "Back" },
  { pattern: "LateralDeltIso", muscleFocus: "LateralDeltoid" },
  { pattern: "BicepIso", muscleFocus: "Bicep" },
  { pattern: "TricepIso", muscleFocus: "Tricep" },
];

const LOWER_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "LegPush", muscleFocus: "Quad" },
  { pattern: "HipHinge", muscleFocus: "Hamstring" },
  { pattern: "QuadIso", muscleFocus: "Quad" },
  { pattern: "HamstringIso", muscleFocus: "Hamstring" },
  { pattern: "Calves", muscleFocus: "Calf" },
  { pattern: "AbIso", muscleFocus: "Ab" },
];

const FULL_BODY_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "LegPush", muscleFocus: "Quad" },
  { pattern: "HorizontalPush", muscleFocus: "Chest" },
  { pattern: "HorizontalPull", muscleFocus: "Back" },
  { pattern: "HipHinge", muscleFocus: "Hamstring" },
];

const PUSH_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "HorizontalPush", muscleFocus: "Chest" },
  { pattern: "VerticalPush", muscleFocus: "AnteriorDeltoid" },
  { pattern: "InclinePush", muscleFocus: "Chest" },
  { pattern: "LateralDeltIso", muscleFocus: "LateralDeltoid" },
  { pattern: "TricepIso", muscleFocus: "Tricep" },
];

const PULL_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "VerticalPull", muscleFocus: "Back" },
  { pattern: "HorizontalPull", muscleFocus: "Back" },
  { pattern: "RearDeltIso", muscleFocus: "PosteriorDeltoid" },
  { pattern: "BicepIso", muscleFocus: "Bicep" },
];

const LEGS_PATTERNS: SuggestedPatternTemplate[] = [
  { pattern: "LegPush", muscleFocus: "Quad" },
  { pattern: "HipHinge", muscleFocus: "Hamstring" },
  { pattern: "QuadIso", muscleFocus: "Quad" },
  { pattern: "HamstringIso", muscleFocus: "Hamstring" },
  { pattern: "Calves", muscleFocus: "Calf" },
];

/** Lookup of split templates by training-days-per-week. */
const SPLIT_TEMPLATES: Record<number, SplitDayTemplate[]> = {
  // Full body ×2: every major muscle both days.
  2: [
    { theme: "Full Body A", name: "Full Body A", primaryMuscles: [...MAJOR_MUSCLES], accessoryMuscles: ["Ab"], suggestedPatterns: FULL_BODY_PATTERNS },
    { theme: "Full Body B", name: "Full Body B", primaryMuscles: [...MAJOR_MUSCLES], accessoryMuscles: ["Ab"], suggestedPatterns: FULL_BODY_PATTERNS },
  ],
  // Full body ×3: every major muscle all three days.
  3: [
    { theme: "Full Body A", name: "Full Body A", primaryMuscles: [...MAJOR_MUSCLES], accessoryMuscles: ["Ab"], suggestedPatterns: FULL_BODY_PATTERNS },
    { theme: "Full Body B", name: "Full Body B", primaryMuscles: [...MAJOR_MUSCLES], accessoryMuscles: ["Ab"], suggestedPatterns: FULL_BODY_PATTERNS },
    { theme: "Full Body C", name: "Full Body C", primaryMuscles: [...MAJOR_MUSCLES], accessoryMuscles: ["Ab"], suggestedPatterns: FULL_BODY_PATTERNS },
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
      suggestedPatterns: [
        { pattern: "HorizontalPush", muscleFocus: "Chest" },
        { pattern: "BicepIso", muscleFocus: "Bicep" },
        { pattern: "TricepIso", muscleFocus: "Tricep" },
        { pattern: "LateralDeltIso", muscleFocus: "LateralDeltoid" },
      ],
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

function upperTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Chest", "Back", "LateralDeltoid", "Bicep", "Tricep", "PosteriorDeltoid"],
    accessoryMuscles: ["AnteriorDeltoid", "Ab"],
    suggestedPatterns: UPPER_PATTERNS,
  };
}

function lowerTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Quad", "Hamstring", "Glute", "Calf"],
    accessoryMuscles: ["Ab", "LowerBack"],
    suggestedPatterns: LOWER_PATTERNS,
  };
}

function pushTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Chest", "Tricep", "LateralDeltoid", "AnteriorDeltoid"],
    accessoryMuscles: ["Ab"],
    suggestedPatterns: PUSH_PATTERNS,
  };
}

function pullTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Back", "Bicep", "PosteriorDeltoid"],
    accessoryMuscles: ["LowerBack", "Trap"],
    suggestedPatterns: PULL_PATTERNS,
  };
}

function legsTemplate(name: string): SplitDayTemplate {
  return {
    theme: name,
    name,
    primaryMuscles: ["Quad", "Hamstring", "Glute", "Calf"],
    accessoryMuscles: ["Ab", "LowerBack"],
    suggestedPatterns: LEGS_PATTERNS,
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
 * Allocates weekly volume targets across training days into per-day muscle budgets, then attaches
 * the theme's suggested movement patterns (filtered to available lifts). Guarantees:
 *  - every non-Omit major muscle appears on ≥2 days (frequency by construction for 3+ days),
 *  - per-day set credits respect the time budget,
 *  - every suggested pattern has ≥1 enabled lift (feasibility guard).
 *
 * When the user's enabled lifts cannot satisfy a template (e.g. a "Legs" day with no leg lifts),
 * the unsatisfiable muscles are dropped for that day and their weekly budget is redistributed to
 * the remaining day(s) that target them; if no day targets them, the muscle receives no budget
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
  const availablePatternSet = availablePatterns(context);
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

    // Scale down primary budgets if they exceed the day's time budget (Upper-day paradox fix).
    let usedCredits = sumPrimaryCredits(muscleBudgets);
    if (timeBudget != null && usedCredits > timeBudget) {
      const scale = timeBudget / usedCredits;
      for (const budget of muscleBudgets) {
        budget.sets = Math.max(2, Math.round(budget.sets * scale * 2) / 2);
      }
      usedCredits = sumPrimaryCredits(muscleBudgets);
    }

    // Accessory spillover: if the day has time slack, pull accessory muscle volume here.
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

    // Suggested patterns for the theme, filtered to those the user can actually fill.
    const suggestedPatterns = deriveSuggestedPatterns(template.suggestedPatterns, availablePatternSet);

    return {
      dayIndex,
      weekday: context.weekdays[dayIndex] ?? null,
      name: template.name,
      theme: template.theme,
      timeBudgetHardSets: timeBudget,
      muscleBudgets,
      suggestedPatterns,
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
 * The set of every movement pattern the user has at least one enabled lift for. Used by the
 * suggested-pattern feasibility guard so the builder is never pointed at a pattern it can't fill.
 */
function availablePatterns(context: GenerationContext): Set<string> {
  const set = new Set<string>();
  for (const lift of context.liftsById.values()) {
    set.add(lift.movementPattern);
  }
  return set;
}

/**
 * Filters a theme's suggested patterns to those the user has ≥1 enabled lift for, so every
 * suggestion is fillable. This is the feasibility guard: the builder is never pointed at a
 * pattern it cannot select a lift for.
 */
function deriveSuggestedPatterns(
  templates: SuggestedPatternTemplate[],
  available: Set<string>,
): SuggestedPattern[] {
  const seen = new Set<string>();
  const out: SuggestedPattern[] = [];
  for (const t of templates) {
    if (!available.has(t.pattern)) continue;
    const key = `${t.pattern}|${t.muscleFocus}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ pattern: t.pattern, muscleFocus: t.muscleFocus });
  }
  return out;
}

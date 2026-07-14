/**
 * Normalized program-template shape produced by the deterministic program-import parsers.
 *
 * This is the server-side mirror of the Kotlin `NormalizedProgramTemplate` in `:core:domain`. Lifts
 * are **name-keyed** — the client resolves them to catalog lift ids via the `LiftNameResolverPort`,
 * creating custom lifts for unmatched names. Distinct from `NormalizedHistory` (training logs) and
 * from the generation-pipeline `NormalizedProgram` (which is already lift-id-resolved).
 */
export interface NormalizedProgramTemplate {
  programName: string | null;
  /**
   * Ordered day-row count for one cycle (training + restDay rows). NOT mesocycle / week-block
   * duration. Null when unknown; client derives train+rest slot space from workouts.
   */
  cycleLength: number | null;
  workouts: NormalizedProgramTemplateWorkout[];
}

export interface NormalizedProgramTemplateWorkout {
  name: string;
  /** Weekday label from the source if present; null when the program is day-positioned. */
  weekday: string | null;
  position: number;
  lifts: NormalizedProgramTemplateLift[];
  /**
   * True for explicit rest days in the cycle (e.g. MacroFactor interleaves Rest rows between
   * training days). A rest day carries no lifts; the client uses it to consume a weekday slot
   * when assigning the schedule but does not create a domain Workout for it.
   */
  restDay?: boolean;
}

export interface NormalizedProgramTemplateLift {
  name: string;
  position: number;
  sets: NormalizedProgramTemplateSet[];
  /**
   * Opaque superset group key shared by lifts in the same superset (e.g. MacroFactor `SS1`).
   * Null for straight lifts. Equal keys within a workout are grouped by the client.
   */
  supersetGroupKey: string | null;
}

export interface NormalizedProgramTemplateSet {
  position: number;
  /** Source set-type label (e.g. "Standard Set", "Failure Set", "Myo Set"); null when unspecified. */
  setTypeLabel: string | null;
  repMin: number | null;
  repMax: number | null;
  rirGoal: number | null;
  restSeconds: number | null;
  isTopSet: boolean;
}

/**
 * Which known program-template export format a file was identified as.
 *
 * Only Alpha Progression and MacroFactor ship program-template exports today. Hevy and Strong do
 * not export programs, so their files never match and are rejected as unsupported. The list is
 * intentionally closed.
 */
export type ProgramTemplateFormat = "alpha_progression" | "macrofactor" | "llm_custom";

export interface ProgramTemplateDeterministicResult {
  format: ProgramTemplateFormat;
  template: NormalizedProgramTemplate;
}

/**
 * Validate a parsed template against the program-template contract.
 *
 * Rules: workouts non-empty; every non-rest workout has at least one lift; every lift has a
 * non-empty name and at least one set; every set has positive reps (repMin > 0, repMax >= repMin
 * when both present). Rest days (restDay=true) are allowed zero lifts. Requires at least one
 * non-rest workout. Throws on violation so the caller can treat a miss as an unsupported file.
 */
export function validateProgramTemplate(template: NormalizedProgramTemplate): NormalizedProgramTemplate {
  if (!Array.isArray(template.workouts) || template.workouts.length === 0) {
    throw new Error("Program template has no workouts");
  }
  if (!template.workouts.some((w) => !w.restDay)) {
    throw new Error("Program template has only rest days");
  }
  template.workouts.forEach((workout, wi) => {
    if (workout.restDay) {
      return;
    }
    if (!Array.isArray(workout.lifts) || workout.lifts.length === 0) {
      throw new Error(`Workout ${wi} (${workout.name}) has no lifts`);
    }
    workout.lifts.forEach((lift, li) => {
      if (typeof lift.name !== "string" || lift.name.trim() === "") {
        throw new Error(`Workout ${wi} lift ${li} has no name`);
      }
      // Reject names that look like garbled binary (control chars or mostly non-printable) — this
      // guards against a parser mis-decoding cell contents. Normal exercise names never contain
      // control characters.
      if (looksCorrupt(lift.name)) {
        throw new Error(`Workout ${wi} lift ${li} has a corrupt name`);
      }
      if (!Array.isArray(lift.sets) || lift.sets.length === 0) {
        throw new Error(`Workout ${wi} lift ${li} (${lift.name}) has no sets`);
      }
      lift.sets.forEach((set, si) => {
        if (typeof set.repMin === "number" && set.repMin <= 0) {
          throw new Error(`Workout ${wi} lift ${li} set ${si} has non-positive repMin`);
        }
        if (typeof set.repMax === "number" && set.repMax <= 0) {
          throw new Error(`Workout ${wi} lift ${li} set ${si} has non-positive repMax`);
        }
        if (typeof set.repMin === "number" && typeof set.repMax === "number" && set.repMax < set.repMin) {
          throw new Error(`Workout ${wi} lift ${li} set ${si} repMax < repMin`);
        }
      });
    });
  });
  return template;
}

/**
 * Detect names that look like garbled binary — control characters or mostly non-printable content.
 * Normal exercise names are ASCII/Unicode letters, digits, spaces, and punctuation — never control
 * chars. A corrupt name signals a parser mis-decode rather than a real lift, so the template is
 * rejected as unsupported.
 */
function looksCorrupt(name: string): boolean {
  let printable = 0;
  for (const ch of name) {
    const code = ch.charCodeAt(0);
    if (code < 0x20 && ch !== "\t") {
      return true; // control character
    }
    if (code >= 0x20 && code !== 0x7f) {
      printable++;
    }
  }
  // If fewer than 50% printable chars, it's garbled binary.
  return printable < name.length / 2;
}

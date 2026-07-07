import type {
  NormalizedProgramTemplate,
  NormalizedProgramTemplateWorkout,
  NormalizedProgramTemplateLift,
  NormalizedProgramTemplateSet,
} from "./types";
import type { ProgramTemplateFormat } from "./types";
import { parseCsvLine } from "../../import/deterministic/csvReader";
import { parseInteger } from "../../import/deterministic/values";

/**
 * Alpha Progression *plan* (program template) export parser.
 *
 * Distinct from the Alpha Progression *history* export parsed by `alphaProgressionParser.ts`.
 * The history export is a sectional document with per-set `#;LB;REPS` weight tables. The plan
 * export carries no weights — it describes the intended program structure only:
 *
 *   "New plan · 4 weeks";2026-07-07
 *   "Day 1 · Day 1"
 *   "1. Leg Extensions · Machine";"3 sets";"10 reps"
 *   "2. Triceps Pushdowns · Cable";"3 sets";"10 reps"
 *   "Day 2 · Day 2"
 *   "1. Bench Press · Barbell";"3 sets";"10 reps"
 *
 * Structure:
 *   - Line 1: plan metadata `"<Plan name> · <N> weeks";<ISO date>`. The first segment before the
 *     first ` · ` is the program name; the week count seeds `cycleLength`.
 *   - Day header: `"Day <n> · <label>"`. The segment after the last ` · ` is the day's display name.
 *   - Lift row: `"<pos>. <name> · <equipment>";"<N> sets";"<M> reps"`. The first ` · `-segment after
 *     the position prefix is the lift name; `N` drives set count; `M` is the rep target (repMin =
 *     repMax = M).
 *
 * Semicolon-delimited (Alpha Progression is a European app). Rep ranges are not exported, so each
 * set inherits the single rep target. RIR/rest are unknown and left null.
 */

const PLAN_HEADER_RE = /^"([^"]+)"\s*;\s*(\d{4}-\d{2}-\d{2})/i;
const DAY_HEADER_RE = /^"Day\s+\d+\s*·\s*([^"]+)"$/i;
const LIFT_ROW_RE = /^"(\d+)\.\s*(.+)"\s*;\s*"(\d+)\s*sets?"\s*;\s*"(\d+)\s*reps?"$/i;
const WEEKS_RE = /(\d+)\s*weeks?/i;
const DELIMITER = ";";

/** Sniff whether a document looks like an Alpha Progression plan export. */
export function matchesAlphaProgressionPlan(text: string): boolean {
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n");
  const hasPlanHeader = lines.some((line) => PLAN_HEADER_RE.test(line.trim()));
  const hasDayHeader = lines.some((line) => DAY_HEADER_RE.test(line.trim()));
  const hasLiftRow = lines.some((line) => LIFT_ROW_RE.test(line.trim()));
  return hasPlanHeader && hasDayHeader && hasLiftRow;
}

/**
 * Take the first ` · `-segment of a name as the display name, dropping trailing equipment /
 * rep-target segments. Alpha Progression joins segments with ` · ` (U+00B7).
 */
function firstSegment(name: string): string {
  return name.split("·")[0]?.trim() ?? name.trim();
}

/** Parse an Alpha Progression plan export. Returns null when the document does not match. */
export function parseAlphaProgressionPlan(text: string): NormalizedProgramTemplate | null {
  if (!matchesAlphaProgressionPlan(text)) {
    return null;
  }
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n");

  let programName: string | null = null;
  let cycleLength: number | null = null;
  const workouts: NormalizedProgramTemplateWorkout[] = [];
  let currentWorkout: NormalizedProgramTemplateWorkout | null = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line === "") {
      continue;
    }

    // Plan header carries the program name + week count.
    const planMatch = line.match(PLAN_HEADER_RE);
    if (planMatch) {
      const planLabel = firstSegment(planMatch[1]);
      programName = planLabel.length > 0 ? planLabel : null;
      const weeksMatch = planMatch[1].match(WEEKS_RE);
      if (weeksMatch) {
        const weeks = parseInteger(weeksMatch[1]);
        cycleLength = weeks !== null && weeks > 0 ? weeks : null;
      }
      continue;
    }

    // Day header opens a new workout.
    const dayMatch = line.match(DAY_HEADER_RE);
    if (dayMatch) {
      const dayName = dayMatch[1].trim();
      currentWorkout = {
        name: dayName,
        weekday: null,
        position: workouts.length,
        lifts: [],
      };
      workouts.push(currentWorkout);
      continue;
    }

    // Lift row: needs an open workout to attach to.
    if (!currentWorkout) {
      continue;
    }
    const liftMatch = line.match(LIFT_ROW_RE);
    if (!liftMatch) {
      continue;
    }
    const position = parseInteger(liftMatch[1]) ?? currentWorkout.lifts.length + 1;
    const name = firstSegment(liftMatch[2]);
    const setCount = parseInteger(liftMatch[3]) ?? 1;
    const reps = parseInteger(liftMatch[4]) ?? 10;
    if (name === "" || setCount <= 0 || reps <= 0) {
      continue;
    }

    const sets: NormalizedProgramTemplateSet[] = [];
    for (let i = 0; i < setCount; i++) {
      sets.push({
        position: i,
        setTypeLabel: null,
        repMin: reps,
        repMax: reps,
        rirGoal: null,
        restSeconds: null,
        isTopSet: i === 0,
      });
    }
    const lift: NormalizedProgramTemplateLift = {
      name,
      position,
      sets,
      supersetGroupKey: null,
    };
    currentWorkout.lifts.push(lift);
  }

  return { programName, cycleLength, workouts };
}

export const alphaProgressionProgramFormat: ProgramTemplateFormat = "alpha_progression";

/** Convenience for tests: parse a single semicolon-delimited line. */
export function parsePlanLine(line: string): string[] {
  return parseCsvLine(line, DELIMITER);
}

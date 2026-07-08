import type { NormalizedHistory, NormalizedSession, NormalizedSet } from "../schemas";
import { parseCsvLine } from "./csvReader";
import { parseInteger, parseNumber, normalizeTimestamp } from "./values";
import type { ImportFormat } from "./types";

/**
 * Alpha Progression spells timestamps as `YYYY-MM-DD HH:MM am/pm` (12-hour,
 * space-separated, lowercase meridian). The shared normaliser only accepts
 * `T`-separated ISO, so convert the 12-hour form to 24-hour ISO first.
 *
 * Returns the original string when it does not match the Alpha Progression
 * shape, so the shared normaliser can reject truly bad input.
 */
const AP_TIMESTAMP_RE =
  /^(\d{4}-\d{2}-\d{2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(am|pm)$/i;

function alphaDateToIso(raw: string): string {
  const match = raw.trim().match(AP_TIMESTAMP_RE);
  if (!match) {
    return raw;
  }
  const [, date, hourRaw, minute, second, meridian] = match;
  let hour = Number(hourRaw) % 12;
  if (/pm/i.test(meridian)) {
    hour += 12;
  }
  const paddedHour = String(hour).padStart(2, "0");
  const sec = second ?? "00";
  return `${date}T${paddedHour}:${minute}:${sec}Z`;
}

/**
 * Alpha Progression export parser.
 *
 * Unlike the other three formats, Alpha Progression exports a *sectional*
 * document rather than a flat table. The structure repeats per workout:
 *
 *   "Day 1 · Day 1 · Week 1 · New plan";"2026-07-07 12:58 pm";"38 sec"
 *   "1. Leg Extensions · Machine · 10 reps"
 *   #;LB;REPS
 *   1;185;10
 *   2;185;10
 *   "2. Triceps Pushdowns · Cable · 10 reps"
 *   #;LB;REPS
 *   1;100;10
 *   <blank line>
 *   "Day 2 · ..."
 *
 * So the parser is a small state machine that walks lines and tracks the
 * current workout (for its timestamp + session id) and current exercise (for
 * its name). Set rows are the `#;LB;REPS` lines that follow each exercise
 * header. Weights are pounds. RIR is not exported by Alpha Progression, so no
 * effort field is emitted.
 *
 * Multiple exercises appear inside one workout, and the document may contain
 * many workouts — each becomes its own session.
 */

const WORKOUT_HEADER_RE = /^"([^"]+)"\s*;\s*"(\d{4}-\d{2}-\d{2}[ T]\d{1,2}:\d{2}(?::\d{2})?\s*(?:am|pm)?)"\s*;/i;
const EXERCISE_HEADER_RE = /^"(\d+)\.\s*(.+)"$/;
const SET_TABLE_HEADER = ["#", "LB", "REPS"];
const SET_TABLE_DELIMITER = ";";

/**
 * Sniff whether a document looks like an Alpha Progression export. The signal
 * is a workout header line followed (eventually) by the `#;LB;REPS` set-table
 * marker, which is distinctive enough that no other known format matches.
 */
export function matchesAlphaProgression(text: string): boolean {
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const hasWorkoutHeader = normalized
    .split("\n")
    .some((line) => WORKOUT_HEADER_RE.test(line.trim()));
  const hasSetTableHeader = normalized.includes(`${SET_TABLE_HEADER.join(SET_TABLE_DELIMITER)}`);
  return hasWorkoutHeader && hasSetTableHeader;
}

interface CurrentWorkout {
  sessionId: string;
  startedAt: string;
}

/**
 * Parse an Alpha Progression export. Returns null when the document does not
 * look like an Alpha Progression export, so the caller can fall back.
 */
export function parseAlphaProgression(text: string): NormalizedHistory | null {
  if (!matchesAlphaProgression(text)) {
    return null;
  }
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n");

  const sessions: NormalizedSession[] = [];
  const sets: NormalizedSet[] = [];
  let workout: CurrentWorkout | null = null;
  let exerciseName: string | null = null;
  let perExerciseIndex = 0;
  let expectSetRows = false;

  function flushExercise(): void {
    exerciseName = null;
    perExerciseIndex = 0;
    expectSetRows = false;
  }

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line === "") {
      // Blank line ends the current exercise's set table.
      flushExercise();
      continue;
    }

    const workoutMatch = line.match(WORKOUT_HEADER_RE);
    if (workoutMatch) {
      // A new workout header starts a fresh session and closes any open exercise.
      flushExercise();
      const sessionId = `alpha-session-${sessions.length + 1}`;
      workout = { sessionId, startedAt: normalizeTimestamp(alphaDateToIso(workoutMatch[2])) };
      sessions.push({
        id: sessionId,
        startedAt: workout.startedAt,
        sourceApp: "Alpha Progression",
      });
      continue;
    }

    if (!workout) {
      // Lines before the first workout header are metadata; ignore.
      continue;
    }

    const exerciseMatch = line.match(EXERCISE_HEADER_RE);
    if (exerciseMatch) {
      // Exercise header: `1. Leg Extensions · Machine · 10 reps`. Take the
      // first `·`-segment as the exercise name (the rest is equipment + rep
      // target, which we drop). Strip the leading `N. ` index.
      const fullBody = exerciseMatch[2].trim();
      const firstSegment = fullBody.split("·")[0]?.trim() ?? fullBody;
      exerciseName = firstSegment;
      perExerciseIndex = 0;
      expectSetRows = false;
      continue;
    }

    // Set-table column header: once seen, the following `#;LB;REPS`-delimited
    // numeric lines are sets for the current exercise.
    if (line === SET_TABLE_HEADER.join(SET_TABLE_DELIMITER)) {
      expectSetRows = true;
      continue;
    }

    if (!expectSetRows || exerciseName === null) {
      continue;
    }

    const fields = parseCsvLine(line, SET_TABLE_DELIMITER);
    if (fields.length < 3) {
      continue;
    }
    const reps = parseInteger(fields[2]);
    const weight = parseNumber(fields[1]);
    if (reps === null || reps <= 0 || weight === null) {
      continue;
    }

    sets.push({
      id: `alpha-set-${sets.length + 1}`,
      sessionId: workout.sessionId,
      exerciseNameRaw: exerciseName,
      setIndex: perExerciseIndex,
      weightAmount: weight,
      weightUnit: "Lb",
      reps,
      completedAt: workout.startedAt,
    });
    perExerciseIndex += 1;
  }

  return { sessions, sets };
}

export const alphaProgressionFormat: ImportFormat = "alpha_progression";

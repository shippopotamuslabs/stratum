import type { NormalizedHistory, NormalizedSession, NormalizedSet } from "../schemas";
import { field, headerIndex, parseCsv } from "./csvReader";
import { parseInteger, parseNumber, rpeToRir, stripSupersetSuffix, normalizeTimestamp } from "./values";
import type { ImportFormat } from "./types";

/**
 * Strong export parser.
 *
 * Strong exports one row per set, semicolon-delimited, with the workout
 * identity (`Workout #`, `Date`, `Workout Name`) repeated on every row. The
 * distinctive header is `Workout #` + `Weight (kg)` + `Set Order`. Weights are
 * always kilograms. RPE is present as a decimal and converted to integer RIR.
 *
 * One workout = one `Workout #` value. Sets are grouped by `Workout #` +
 * `Exercise Name` and reindexed 0-based per exercise (Strong's `Set Order`
 * resets per exercise and would otherwise collide across exercises).
 */

/** Strong date format is `YYYY-MM-DD HH:MM:SS` — space-separated, needs `T`. */
function strongDateToIso(raw: string): string {
  const trimmed = raw.trim();
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(trimmed)) {
    return `${trimmed.replace(" ", "T")}Z`;
  }
  return trimmed;
}

/** Header sniff: returns true when the header row carries Strong's signature columns. */
export function matchesStrong(headerRow: string[] | undefined): boolean {
  if (!headerRow) {
    return false;
  }
  const headers = headerIndex(headerRow);
  return (
    "Workout #" in headers &&
    "Weight (kg)" in headers &&
    "Set Order" in headers &&
    "Exercise Name" in headers &&
    "Date" in headers
  );
}

export function parseStrong(csvText: string): NormalizedHistory {
  const rows = parseCsv(csvText, ";");
  if (rows.length < 2) {
    return { sessions: [], sets: [] };
  }
  const headers = headerIndex(rows[0]);

  const sessions: NormalizedSession[] = [];
  const sessionIndex = new Map<string, number>();
  const sets: NormalizedSet[] = [];
  const perExerciseIndex = new Map<string, number>();

  for (let r = 1; r < rows.length; r++) {
    const row = rows[r];
    const workoutNumber = field(row, headers, "Workout #");
    const dateRaw = field(row, headers, "Date");
    const workoutName = field(row, headers, "Workout Name");
    const exerciseRaw = field(row, headers, "Exercise Name");
    const weightRaw = field(row, headers, "Weight (kg)");
    const repsRaw = field(row, headers, "Reps");
    const rpeRaw = field(row, headers, "RPE");

    const reps = parseInteger(repsRaw);
    const weight = parseNumber(weightRaw);
    if (reps === null || reps <= 0 || weight === null || exerciseRaw.trim() === "" || workoutNumber.trim() === "") {
      continue;
    }

    let sessionPosition = sessionIndex.get(workoutNumber);
    if (sessionPosition === undefined) {
      sessionPosition = sessions.length;
      sessionIndex.set(workoutNumber, sessionPosition);
      const sessionId = `strong-session-${workoutNumber}`;
      const startedAt = normalizeTimestamp(strongDateToIso(dateRaw));
      sessions.push({
        id: sessionId,
        startedAt,
        ...(workoutName.trim() ? {} : {}),
        sourceApp: "Strong",
      });
    }
    const sessionId = sessions[sessionPosition].id;

    const exerciseKey = `${sessionId}|${exerciseRaw}`;
    const setIndex = perExerciseIndex.get(exerciseKey) ?? 0;
    perExerciseIndex.set(exerciseKey, setIndex + 1);

    const rir = rpeToRir(rpeRaw);
    sets.push({
      id: `strong-set-${sets.length + 1}`,
      sessionId,
      exerciseNameRaw: stripSupersetSuffix(exerciseRaw),
      setIndex,
      weightAmount: weight,
      weightUnit: "Kg",
      reps,
      ...(rir !== null ? { rir } : {}),
      completedAt: normalizeTimestamp(strongDateToIso(dateRaw)),
    });
  }

  return { sessions, sets };
}

export const strongFormat: ImportFormat = "strong";

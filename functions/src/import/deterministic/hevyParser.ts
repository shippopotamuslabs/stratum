import type { NormalizedHistory, NormalizedSession, NormalizedSet } from "../schemas";
import { field, headerIndex, parseCsv } from "./csvReader";
import { parseInteger, parseNumber, rpeToRir, stripSupersetSuffix, normalizeTimestamp } from "./values";
import type { ImportFormat } from "./types";

/**
 * Hevy export parser.
 *
 * Hevy exports one row per set, comma-delimited, with the workout identity
 * (title + start/end time) repeated on every row. The distinctive header is
 * `weight_lbs` + `set_type` + `set_index`. Weights are always pounds. RPE is
 * present as a decimal (e.g. 8.5) and is converted to integer RIR.
 *
 * Each unique `(title, start_time)` pair becomes one session; sets are grouped
 * by that key and reindexed 0-based per session (Hevy's `set_index` resets per
 * exercise, which would collide across exercises in one workout).
 */

const HEVY_DATE_FORMAT =
  /^(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4}),\s+(\d{1,2}):(\d{2})$/;

const MONTHS: Record<string, string> = {
  jan: "01", feb: "02", mar: "03", apr: "04", may: "05", jun: "06",
  jul: "07", aug: "08", sep: "09", oct: "10", nov: "11", dec: "12",
};

/** Hevy spells dates as `17 Dec 2025, 12:37` — convert to ISO before delegating. */
function hevyDateToIso(raw: string): string {
  const match = raw.trim().match(HEVY_DATE_FORMAT);
  if (!match) {
    // Fall back to the standard normaliser, which rejects truly bad input.
    return raw;
  }
  const [, day, monthName, year, hour, minute] = match;
  const month = MONTHS[monthName.toLowerCase().slice(0, 3)];
  if (!month) {
    return raw;
  }
  const paddedDay = day.padStart(2, "0");
  const paddedHour = hour.padStart(2, "0");
  return `${year}-${month}-${paddedDay}T${paddedHour}:${minute}:00Z`;
}

/** Header sniff: returns true when the header row carries Hevy's signature columns. */
export function matchesHevy(headerRow: string[] | undefined): boolean {
  if (!headerRow) {
    return false;
  }
  const headers = headerIndex(headerRow);
  return (
    "weight_lbs" in headers &&
    "set_type" in headers &&
    "set_index" in headers &&
    "exercise_title" in headers &&
    "start_time" in headers
  );
}

export function parseHevy(csvText: string): NormalizedHistory {
  const rows = parseCsv(csvText, ",");
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
    const title = field(row, headers, "title");
    const startTimeRaw = field(row, headers, "start_time");
    const endTimeRaw = field(row, headers, "end_time");
    const exerciseRaw = field(row, headers, "exercise_title");
    const weightRaw = field(row, headers, "weight_lbs");
    const repsRaw = field(row, headers, "reps");
    const rpeRaw = field(row, headers, "rpe");

    // Skip rows without the essentials; the validator would reject them anyway.
    const reps = parseInteger(repsRaw);
    const weight = parseNumber(weightRaw);
    if (reps === null || reps <= 0 || weight === null || exerciseRaw.trim() === "") {
      continue;
    }

    const sessionKey = `${title}|${startTimeRaw}`;
    let sessionPosition = sessionIndex.get(sessionKey);
    if (sessionPosition === undefined) {
      sessionPosition = sessions.length;
      sessionIndex.set(sessionKey, sessionPosition);
      const sessionId = `hevy-session-${sessionPosition + 1}`;
      sessions.push({
        id: sessionId,
        startedAt: normalizeTimestamp(hevyDateToIso(startTimeRaw)),
        endedAt: endTimeRaw.trim() ? normalizeTimestamp(hevyDateToIso(endTimeRaw)) : undefined,
        sourceApp: "Hevy",
      });
    }
    const sessionId = sessions[sessionPosition].id;

    // Reindex sets per exercise within the session so set indices are unique
    // and monotonically increasing across the whole workout, not just per exercise.
    const exerciseKey = `${sessionId}|${exerciseRaw}`;
    const setIndex = perExerciseIndex.get(exerciseKey) ?? 0;
    perExerciseIndex.set(exerciseKey, setIndex + 1);

    const rir = rpeToRir(rpeRaw);
    sets.push({
      id: `hevy-set-${sets.length + 1}`,
      sessionId,
      exerciseNameRaw: stripSupersetSuffix(exerciseRaw),
      setIndex,
      weightAmount: weight,
      weightUnit: "Lb",
      reps,
      ...(rir !== null ? { rir } : {}),
      completedAt: normalizeTimestamp(hevyDateToIso(startTimeRaw)),
    });
  }

  return { sessions, sets };
}

export const hevyFormat: ImportFormat = "hevy";

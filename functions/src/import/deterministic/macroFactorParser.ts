import { isMergedFollower, loadWorkbook, scalarCellValue } from "./excelCells";
import type { NormalizedHistory, NormalizedSession, NormalizedSet } from "../schemas";
import { field, headerIndex, parseCsv } from "./csvReader";
import { parseInteger, parseNumber, stripSupersetSuffix, normalizeTimestamp } from "./values";
import type { ImportFormat } from "./types";

/**
 * MacroFactor workout export parser.
 *
 * MacroFactor exports the same column shape in two containers: `.xlsx` (the
 * default in-app export) and `.csv` (an alternate). The columns are identical
 * either way — `Workout Duration`, `Workout`, `Exercise`, `Weight (lbs)`,
 * `Reps`, `RIR`, `Set Type` — so this parser consumes a normalised row matrix
 * and the caller picks the right decoder (exceljs-community for XLSX, the CSV reader for
 * CSV). Weights are always pounds. RIR is already integer-valued, so it passes
 * through. Set-type and the `∈ SS1` superset suffix have no field in the
 * normalized contract: the suffix is stripped from the exercise name (so it
 * does not pollute BM25 lift matching) and set type is dropped.
 *
 * One session per `(Date, Workout)` pair. Sets reindexed 0-based per exercise
 * within the session.
 */

/** Distinctive header columns present in both the XLSX and CSV variants. */
const MACROFACTOR_SIGNATURE = [
  "Workout Duration",
  "Exercise",
  "Weight (lbs)",
  "Reps",
  "RIR",
];

/** Returns the header row when the matrix looks like a MacroFactor export. */
export function matchesMacroFactorRows(rows: string[][] | undefined): string[] | null {
  if (!rows || rows.length === 0) {
    return null;
  }
  const header = rows[0].map((c) => c.trim());
  const hasAll = MACROFACTOR_SIGNATURE.every((col) => header.includes(col));
  return hasAll ? header : null;
}

/** Convenience header sniff for raw CSV text. */
export function matchesMacroFactorCsv(csvText: string): boolean {
  const rows = parseCsv(csvText, ",");
  return matchesMacroFactorRows(rows) !== null;
}

/**
 * XLSX magic-byte sniff. MacroFactor XLSX files start with the ZIP local-file
 * header (`PK\x03\x04`); CSVs do not. Used by the detector to decide whether to
 * decode with exceljs-community or the CSV reader.
 */
export function isXlsxBytes(bytes: Buffer): boolean {
  return bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04;
}

/**
 * Decode an XLSX file into a row matrix of trimmed string cells.
 *
 * Mirrors the old SheetJS decode (`sheet_to_json` with `header: 1`,
 * `raw: true`, `blankrows: false`, `defval: ""`, `cellDates: false`):
 * only the first worksheet is read, Excel serials stay serials (Dates are
 * converted back), and fully empty rows are dropped.
 */
export async function readXlsxRows(bytes: Buffer): Promise<string[][]> {
  const workbook = await loadWorkbook(bytes);
  const sheet = workbook.worksheets[0];
  if (!sheet) {
    return [];
  }
  const date1904 = workbook.properties.date1904 === true;
  const columnCount = sheet.columnCount;
  const matrix: string[][] = [];
  sheet.eachRow({ includeEmpty: true }, (row) => {
    const cells: string[] = [];
    let hasContent = false;
    for (let c = 1; c <= columnCount; c++) {
      const cell = row.getCell(c);
      // Merged followers repeat the master value in exceljs; SheetJS surfaced
      // them as missing. A follower in a required column (e.g. Date) must not
      // resurrect a row — blank it like the other parsers do.
      if (isMergedFollower(cell)) {
        cells.push("");
        continue;
      }
      const value = scalarCellValue(cell, date1904);
      if (value === null || value === "") {
        cells.push("");
        continue;
      }
      cells.push(String(value).trim());
      hasContent = true;
    }
    if (hasContent) {
      matrix.push(cells);
    }
  });
  return matrix;
}

/**
 * Parse a row matrix (from XLSX or CSV) into normalized history when the header
 * matches MacroFactor's signature. Returns null on header mismatch so the
 * caller can treat it as "not MacroFactor" and try the next format.
 */
export function parseMacroFactorRows(rows: string[][]): NormalizedHistory | null {
  const headerRow = matchesMacroFactorRows(rows);
  if (!headerRow) {
    return null;
  }
  const headers = headerIndex(headerRow);

  const sessions: NormalizedSession[] = [];
  const sessionIndex = new Map<string, number>();
  const sets: NormalizedSet[] = [];
  const perExerciseIndex = new Map<string, number>();

  for (let r = 1; r < rows.length; r++) {
    const row = rows[r].map((c) => (c ?? "").trim());
    const dateRaw = field(row, headers, "Date");
    const workoutRaw = field(row, headers, "Workout");
    const exerciseRaw = field(row, headers, "Exercise");
    const weightRaw = field(row, headers, "Weight (lbs)");
    const repsRaw = field(row, headers, "Reps");
    const rirRaw = field(row, headers, "RIR");

    const reps = parseInteger(repsRaw);
    const weight = parseNumber(weightRaw);
    if (reps === null || reps <= 0 || weight === null || exerciseRaw.trim() === "" || dateRaw.trim() === "") {
      continue;
    }

    const sessionKey = `${dateRaw}|${workoutRaw}`;
    let sessionPosition = sessionIndex.get(sessionKey);
    if (sessionPosition === undefined) {
      sessionPosition = sessions.length;
      sessionIndex.set(sessionKey, sessionPosition);
      const sessionId = `macrofactor-session-${sessionPosition + 1}`;
      sessions.push({
        id: sessionId,
        startedAt: normalizeTimestamp(dateRaw),
        sourceApp: "MacroFactor",
      });
    }
    const sessionId = sessions[sessionPosition].id;

    const exerciseKey = `${sessionId}|${exerciseRaw}`;
    const setIndex = perExerciseIndex.get(exerciseKey) ?? 0;
    perExerciseIndex.set(exerciseKey, setIndex + 1);

    const rir = parseInteger(rirRaw);
    sets.push({
      id: `macrofactor-set-${sets.length + 1}`,
      sessionId,
      exerciseNameRaw: stripSupersetSuffix(exerciseRaw),
      setIndex,
      weightAmount: weight,
      weightUnit: "Lb",
      reps,
      ...(rir !== null && rir >= 0 ? { rir } : {}),
      completedAt: normalizeTimestamp(dateRaw),
    });
  }

  return { sessions, sets };
}

/** Parse a MacroFactor CSV export. Returns null on header mismatch. */
export function parseMacroFactorCsv(csvText: string): NormalizedHistory | null {
  return parseMacroFactorRows(parseCsv(csvText, ","));
}

/** Parse a MacroFactor XLSX export. Returns null on header mismatch. */
export async function parseMacroFactorXlsx(bytes: Buffer): Promise<NormalizedHistory | null> {
  return parseMacroFactorRows(await readXlsxRows(bytes));
}

export const macroFactorFormat: ImportFormat = "macrofactor";

import * as xlsx from "xlsx";
import type {
  NormalizedProgramTemplate,
  NormalizedProgramTemplateWorkout,
  NormalizedProgramTemplateLift,
  NormalizedProgramTemplateSet,
} from "./types";
import type { ProgramTemplateFormat } from "./types";
import { parseInteger } from "../../import/deterministic/values";

/**
 * MacroFactor *program* (template) export parser.
 *
 * Distinct from the MacroFactor *history* export parsed by `macroFactorParser.ts`. The history
 * export is a flat per-set table; the program export is a structured sheet describing one or more
 * programs stacked vertically:
 *
 *   Row 1:  A=`Program: <name>`  B=`Cycles: N`  C=`Deload: …`  D=`Color: …`  E=`Icon: …`
 *   Row 2:  A=`Block 1`
 *   Row 3:  header — A=`Cycle 1`  B=`Exercise`  C=`Skipped`  D=`Notes`  then repeating 4-column
 *           set groups `Set N Type | Set N Rep Range | Set N RIR | Set N Rest` starting at column E.
 *           The header row defines the set-group count for that program (variable: 4 or 5 seen).
 *   Rows:   day blocks — each training day is a vertically-merged column-A cell (the day name, e.g.
 *           `Upper A @ FitZone`) spanning its exercise rows; `Rest` days are standalone. Each
 *           exercise row: B=name (with optional ` ∈ SSn` superset suffix), C=Skipped, D=Notes, then
 *           populated set groups (empty cells = slot unused → infer set count from non-empty groups).
 *   blank row separates programs.
 *
 * Set data is real: Type (`Standard Set` / `Failure Set` / `Myo Set`), Rep Range (`6 - 8`, `1+`,
 * myo `15 - 15 (6 - 6, …)`), RIR (numeric; `0` = to failure; Myo sets carry a compound string),
 * Rest (numeric seconds). Supersets are encoded inline in the exercise name as ` ∈ SSn`.
 *
 * Only the first program in the sheet is imported (Stratum holds a single active program); the rest
 * are ignored. `Rest` days are preserved as restDay markers. `cycleLength` is the day-row count
 * (train + rest), not meta `Cycles: N` mesocycles.
 */

const PROGRAM_PREFIX = "Program:";
const SET_GROUP_HEADER_RE = /^Set\s+(\d+)\s+Type$/i;
const SUPERSET_SUFFIX_RE = /\s*∈\s*(?:SS|GS|TS|CS)(\d+)\s*$/;

/** Column index of the first set group (column E = index 4). */
const FIRST_SET_COLUMN = 4;
/** Each set group spans 4 columns: Type, Rep Range, RIR, Rest. */
const SET_GROUP_WIDTH = 4;

/** A cell value: a string, a number, or null when blank. */
type CellValue = string | number | null;

/** Sniff whether an XLSX row matrix looks like a MacroFactor program export. */
export function matchesMacroFactorProgramRows(rows: CellValue[][]): boolean {
  // The metadata row A-column starts with "Program:" and the header row carries "Set 1 Type".
  const hasProgramRow = rows.some(
    (row) => typeof row[0] === "string" && row[0].trim().startsWith(PROGRAM_PREFIX),
  );
  const headerRow = rows.find(
    (row) => typeof row[4] === "string" && SET_GROUP_HEADER_RE.test(String(row[4]).trim()),
  );
  return hasProgramRow && headerRow !== undefined;
}

/**
 * Decode an XLSX file into a dense matrix of cell values, dropping fully-empty rows. The MacroFactor
 * export declares a range extending past real content (empty placeholder rows), so empty rows are
 * skipped to keep the matrix tight.
 */
function readCellMatrix(bytes: Buffer): CellValue[][] {
  let workbook: xlsx.WorkBook;
  try {
    workbook = xlsx.read(bytes, { type: "buffer", cellDates: false });
  } catch {
    // Not a valid XLSX (corrupt, encrypted, or just magic bytes without real ZIP structure).
    return [];
  }
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  if (!sheet || !sheet["!ref"]) {
    return [];
  }
  const range = xlsx.utils.decode_range(sheet["!ref"]);
  const matrix: CellValue[][] = [];
  for (let r = range.s.r; r <= range.e.r; r++) {
    const row: CellValue[] = [];
    let rowHasContent = false;
    for (let c = range.s.c; c <= range.e.c; c++) {
      const addr = xlsx.utils.encode_cell({ r, c });
      const cell = sheet[addr];
      if (!cell || cell.v === undefined || cell.v === null || cell.v === "") {
        row.push(null);
      } else {
        row.push(cell.v);
        rowHasContent = true;
      }
    }
    if (rowHasContent) {
      matrix.push(row);
    }
  }
  return matrix;
}

/** Count how many 4-column set groups a header row declares. */
function countSetGroups(headerRow: CellValue[]): number {
  let count = 0;
  for (let i = FIRST_SET_COLUMN; i + SET_GROUP_WIDTH - 1 < headerRow.length; i += SET_GROUP_WIDTH) {
    const typeHeader = headerRow[i];
    if (typeof typeHeader === "string" && SET_GROUP_HEADER_RE.test(typeHeader.trim())) {
      count++;
    } else {
      break;
    }
  }
  return count;
}

/** Parse a rep-range string like `6 - 8`, `1+`, or myo `15 - 15 (6 - 6, …)` into repMin/repMax. */
function parseRepRange(raw: CellValue): { repMin: number | null; repMax: number | null } {
  if (typeof raw !== "string" || raw.trim() === "") {
    return { repMin: null, repMax: null };
  }
  const trimmed = raw.trim();
  // Plain range: `6 - 8` (ASCII hyphen-space).
  const rangeMatch = trimmed.match(/^(\d+)\s*-\s*(\d+)$/);
  if (rangeMatch) {
    const lo = parseInteger(rangeMatch[1]);
    const hi = parseInteger(rangeMatch[2]);
    return { repMin: lo, repMax: hi };
  }
  // Single value: `1+` or `10`.
  const singleMatch = trimmed.match(/^(\d+)\+?$/);
  if (singleMatch) {
    const v = parseInteger(singleMatch[1]);
    return { repMin: v, repMax: v };
  }
  // Myo-rep compound form: `15 - 15 (6 - 6, …)` — take the first `X - Y` pair.
  const myoMatch = trimmed.match(/^(\d+)\s*-\s*(\d+)/);
  if (myoMatch) {
    const lo = parseInteger(myoMatch[1]);
    const hi = parseInteger(myoMatch[2]);
    return { repMin: lo, repMax: hi };
  }
  return { repMin: null, repMax: null };
}

/** Parse a RIR cell (numeric, or a Myo compound string) into a single integer goal. */
function parseRir(cell: CellValue): number | null {
  if (typeof cell === "number") {
    return Number.isFinite(cell) ? Math.round(cell) : null;
  }
  if (typeof cell === "string") {
    // Leading integer of a compound form like `0 (0, 0, 0, 0)`.
    const match = cell.trim().match(/^(-?\d+)/);
    if (match) {
      return parseInteger(match[1]);
    }
  }
  return null;
}

function parseRest(cell: CellValue): number | null {
  if (typeof cell === "number" && Number.isFinite(cell)) {
    return Math.round(cell);
  }
  return null;
}

/** Split a superset-suffixed exercise name into the clean name and its group key. */
function splitSuperset(rawName: string): { name: string; supersetGroupKey: string | null } {
  const match = rawName.match(SUPERSET_SUFFIX_RE);
  if (!match) {
    return { name: rawName.trim(), supersetGroupKey: null };
  }
  const name = rawName.replace(SUPERSET_SUFFIX_RE, "").trim();
  return { name, supersetGroupKey: match[1] };
}

/**
 * Parse a MacroFactor program XLSX export. Returns null when the sheet does not look like a
 * MacroFactor program export.
 *
 * Only the first program in the sheet is imported. `Rest` day rows are dropped.
 */
export function parseMacroFactorProgramXlsx(bytes: Buffer): NormalizedProgramTemplate | null {
  const matrix = readCellMatrix(bytes);
  if (!matchesMacroFactorProgramRows(matrix)) {
    return null;
  }

  // Locate the first program's metadata row (A starts with "Program:").
  const metaRowIndex = matrix.findIndex(
    (row) => typeof row[0] === "string" && row[0].trim().startsWith(PROGRAM_PREFIX),
  );
  if (metaRowIndex < 0) {
    return null;
  }
  const metaRow = matrix[metaRowIndex];

  // Program name from the A-column prefix.
  const programNameRaw = typeof metaRow[0] === "string" ? metaRow[0].trim() : "";
  const programName = programNameRaw.slice(PROGRAM_PREFIX.length).trim() || null;

  // Note: B-column `Cycles: N` is a mesocycle count (e.g. 9), NOT cycle slot space.
  // Domain Program.cycleLength is train+rest slot count — derived from day rows below.

  // Header row is the first row after meta that carries "Set N Type" in column E.
  let headerRowIndex = -1;
  for (let r = metaRowIndex + 1; r < matrix.length; r++) {
    const eCell = matrix[r][4];
    if (typeof eCell === "string" && SET_GROUP_HEADER_RE.test(eCell.trim())) {
      headerRowIndex = r;
      break;
    }
  }
  if (headerRowIndex < 0) {
    return null;
  }
  const headerRow = matrix[headerRowIndex];
  const setGroupCount = countSetGroups(headerRow);

  // Exercise rows run from headerRowIndex+1 until the next program's metadata row. MacroFactor
  // merges the day name across its exercise rows in column A, but the merge's top cell sits on the
  // SAME row as the day's first exercise — so a day-name row also carries that first lift in column
  // B. We detect a new day whenever column A is a non-empty string that is not a program/block
  // marker, and treat the row's column B as the day's first exercise regardless.
  const workouts: NormalizedProgramTemplateWorkout[] = [];
  let currentWorkout: NormalizedProgramTemplateWorkout | null = null;

  for (let r = headerRowIndex + 1; r < matrix.length; r++) {
    const row = matrix[r];
    const aCell = row[0];
    const bCell = row[1];

    // Another program metadata row → stop importing (only first program is imported).
    if (typeof aCell === "string" && aCell.trim().startsWith(PROGRAM_PREFIX)) {
      break;
    }
    // A non-empty column A that is not a block marker opens a new day (or is a Rest day).
    if (typeof aCell === "string" && aCell.trim() !== "" && aCell.trim() !== "Block 1") {
      const dayLabel = aCell.trim();
      if (dayLabel.toLowerCase() === "rest") {
        // Rest day — preserve as a cycle-position marker so the client can consume a weekday slot
        // when assigning the schedule. Carries no lifts.
        currentWorkout = {
          name: "Rest",
          weekday: null,
          position: workouts.length,
          lifts: [],
          restDay: true,
        };
        workouts.push(currentWorkout);
        continue;
      }
      currentWorkout = {
        // Strip a trailing ` @ <Venue>` from the day name.
        name: dayLabel.split(/\s+@/)[0].trim() || dayLabel,
        weekday: null,
        position: workouts.length,
        lifts: [],
      };
      workouts.push(currentWorkout);
    }

    // Exercise row: needs an open workout to attach to.
    if (!currentWorkout) {
      continue;
    }
    if (typeof bCell !== "string" || bCell.trim() === "") {
      continue;
    }
    const { name, supersetGroupKey } = splitSuperset(bCell);
    if (name === "") {
      continue;
    }

    // Collect populated set groups for this exercise.
    const sets: NormalizedProgramTemplateSet[] = [];
    for (let g = 0; g < setGroupCount; g++) {
      const base = FIRST_SET_COLUMN + g * SET_GROUP_WIDTH;
      const typeCell = row[base];
      const repRangeCell = row[base + 1];
      const rirCell = row[base + 2];
      const restCell = row[base + 3];
      const groupPopulated =
        typeCell !== null || repRangeCell !== null || rirCell !== null || restCell !== null;
      if (!groupPopulated) {
        continue;
      }
      const repRange = parseRepRange(repRangeCell);
      const setTypeLabel =
        typeof typeCell === "string" && typeCell.trim() !== "" ? typeCell.trim() : null;
      const rirGoal = parseRir(rirCell);
      const restSeconds = parseRest(restCell);
      sets.push({
        position: sets.length,
        setTypeLabel,
        repMin: repRange.repMin,
        repMax: repRange.repMax,
        rirGoal,
        restSeconds,
        isTopSet: sets.length === 0,
      });
    }
    if (sets.length === 0) {
      continue;
    }
    const lift: NormalizedProgramTemplateLift = {
      name,
      position: currentWorkout.lifts.length,
      sets,
      supersetGroupKey,
    };
    currentWorkout.lifts.push(lift);
  }

  if (workouts.length === 0) {
    return null;
  }
  // Slot space = ordered day rows (training + rest markers). Do not emit meso "Cycles: N".
  return { programName, cycleLength: workouts.length, workouts };
}

export const macroFactorProgramFormat: ProgramTemplateFormat = "macrofactor";

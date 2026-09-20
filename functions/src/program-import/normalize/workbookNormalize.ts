/**
 * Deterministic workbook → matrix normalization for custom program import.
 *
 * Produces a human-readable cell grid (with Excel date-as-rep-range recovery) so an LLM can see
 * the file the way a coach would, without receiving raw XLSX bytes or formula soup.
 */
import type { Cell, Workbook } from "exceljs-community";
import {
  isMergedFollower,
  loadWorkbook,
  rawCellValue,
  usedRangeBounds,
} from "../../import/deterministic/excelCells";
import { parseCsv } from "../../import/deterministic/csvReader";
import {
  excelSerialToUtcDate,
  isMonthDayOnlyFormat,
  recoverMonthDayRepRange,
} from "./dateRecovery";

export type CellDisplay = string;

export interface NormalizedSheet {
  name: string;
  /** 1-based Excel row numbers aligned with `rows`. */
  excelRowNumbers: number[];
  /** Dense rows of display strings; empty cells are "". Fully empty source rows are dropped. */
  rows: CellDisplay[][];
  /** Count of cells recovered from Excel month-day dates (e.g. 8-10). */
  recoveredDateCells: number;
  /** Rough count of cells that look like formulas (with or without cached value). */
  formulaCells: number;
  /** Literal (non-formula) non-empty cell count. */
  literalCells: number;
}

export interface NormalizedWorkbook {
  kind: "xlsx" | "csv" | "unknown";
  sheets: NormalizedSheet[];
  /** True when bytes were neither valid XLSX nor decodable text. */
  unreadable: boolean;
}

/** XLSX ZIP local-file magic. */
export function isXlsxBytes(bytes: Buffer): boolean {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0x50 &&
    bytes[1] === 0x4b &&
    bytes[2] === 0x03 &&
    bytes[3] === 0x04
  );
}

function sniffDelimiter(text: string): string {
  const sample = text.slice(0, 4000);
  const counts: Record<string, number> = { ",": 0, ";": 0, "\t": 0 };
  for (const ch of sample) {
    if (ch in counts) {
      counts[ch]! += 1;
    }
  }
  let best = ",";
  let bestN = -1;
  for (const [d, n] of Object.entries(counts)) {
    if (n > bestN) {
      best = d;
      bestN = n;
    }
  }
  return best;
}

function formatPlainValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      return "";
    }
    if (Number.isInteger(value) || Math.abs(value - Math.round(value)) < 1e-9) {
      return String(Math.round(value));
    }
    return String(value);
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (value instanceof Date) {
    return value.toISOString().slice(0, 10);
  }
  return String(value).trim();
}

/**
 * Convert one exceljs cell to a display string, applying month-day rep-range recovery.
 *
 * exceljs materializes date-formatted serials as UTC-midnight Dates (its own `excelToDate`
 * uses UTC), so calendar fields are extracted in UTC — the old SheetJS path produced
 * local-midnight Dates and read local fields, which is the same calendar day in
 * production's UTC runtime.
 */
export function cellToDisplay(cell: Cell | undefined): {
  text: string;
  recoveredDate: boolean;
  formula: boolean;
  literal: boolean;
} {
  if (!cell || isMergedFollower(cell)) {
    return { text: "", recoveredDate: false, formula: false, literal: false };
  }

  const z = typeof cell.numFmt === "string" ? cell.numFmt : undefined;
  const hasFormula = typeof cell.formula === "string" && cell.formula.length > 0;
  const v = rawCellValue(cell);

  if (v === null || v === "") {
    return {
      text: "",
      recoveredDate: false,
      formula: hasFormula,
      literal: false,
    };
  }

  if (v instanceof Date) {
    // Date-formatted serial: extract calendar fields in UTC so a non-UTC runtime
    // cannot shift the day.
    const recovered = recoverMonthDayRepRange(v, z, "utc");
    if (recovered) {
      return { text: recovered, recoveredDate: true, formula: hasFormula, literal: !hasFormula };
    }
    return {
      text: v.toISOString().slice(0, 10),
      recoveredDate: false,
      formula: hasFormula,
      literal: !hasFormula,
    };
  }

  if (typeof v === "number" && isMonthDayOnlyFormat(z)) {
    const asDate = excelSerialToUtcDate(v);
    if (asDate) {
      // Serial → UTC Date; extract with UTC fields so timezone offsets cannot shift the day.
      const recovered = recoverMonthDayRepRange(asDate, z, "utc");
      if (recovered) {
        return { text: recovered, recoveredDate: true, formula: hasFormula, literal: !hasFormula };
      }
    }
  }

  const text = formatPlainValue(v);
  if (text.startsWith("=")) {
    return { text: "", recoveredDate: false, formula: true, literal: false };
  }

  return {
    text,
    recoveredDate: false,
    formula: hasFormula,
    literal: !hasFormula && text.length > 0,
  };
}

function sheetFromMatrix(
  name: string,
  matrix: string[][],
  excelRowNumbers: number[],
  recoveredDateCells: number,
  formulaCells: number,
  literalCells: number,
): NormalizedSheet {
  return {
    name,
    excelRowNumbers,
    rows: matrix,
    recoveredDateCells,
    formulaCells,
    literalCells,
  };
}

async function normalizeXlsx(bytes: Buffer): Promise<NormalizedWorkbook> {
  let workbook: Workbook;
  try {
    workbook = await loadWorkbook(bytes);
  } catch {
    return { kind: "unknown", sheets: [], unreadable: true };
  }
  if (!workbook.worksheets.length) {
    return { kind: "xlsx", sheets: [], unreadable: true };
  }

  const sheets: NormalizedSheet[] = [];
  for (const sheet of workbook.worksheets) {
    const bounds = usedRangeBounds(sheet);
    if (!bounds) {
      sheets.push(sheetFromMatrix(sheet.name, [], [], 0, 0, 0));
      continue;
    }
    // Cap extreme sheets (abuse / calculator sprawl) — feasibility rejects; keep parse bounded.
    const maxR = Math.min(bounds.bottom, bounds.top + 800);
    const maxC = Math.min(bounds.right, bounds.left + 40);

    const rows: string[][] = [];
    const excelRowNumbers: number[] = [];
    let recoveredDateCells = 0;
    let formulaCells = 0;
    let literalCells = 0;

    for (let r = bounds.top; r <= maxR; r++) {
      const row: string[] = [];
      let rowHasContent = false;
      for (let c = bounds.left; c <= maxC; c++) {
        const { text, recoveredDate, formula, literal } = cellToDisplay(sheet.getCell(r, c));
        row.push(text);
        if (text) {
          rowHasContent = true;
        }
        if (recoveredDate) {
          recoveredDateCells += 1;
        }
        if (formula) {
          formulaCells += 1;
        }
        if (literal) {
          literalCells += 1;
        }
      }
      if (rowHasContent) {
        rows.push(row);
        excelRowNumbers.push(r); // 1-based Excel row
      }
    }
    sheets.push(
      sheetFromMatrix(sheet.name, rows, excelRowNumbers, recoveredDateCells, formulaCells, literalCells),
    );
  }

  return { kind: "xlsx", sheets, unreadable: false };
}

function normalizeCsv(bytes: Buffer): NormalizedWorkbook {
  let text: string;
  try {
    text = bytes.toString("utf8");
    if (text.includes("\u0000")) {
      return { kind: "unknown", sheets: [], unreadable: true };
    }
  } catch {
    return { kind: "unknown", sheets: [], unreadable: true };
  }

  const delimiter = sniffDelimiter(text);
  const matrix = parseCsv(text, delimiter);
  const rows = matrix.map((row) => row.map((c) => c.trim()));
  const nonEmpty = rows.filter((row) => row.some((c) => c.length > 0));
  let literalCells = 0;
  for (const row of nonEmpty) {
    for (const c of row) {
      if (c) {
        literalCells += 1;
      }
    }
  }
  return {
    kind: "csv",
    sheets: [
      sheetFromMatrix(
        "Sheet1",
        nonEmpty,
        nonEmpty.map((_, i) => i + 1),
        0,
        0,
        literalCells,
      ),
    ],
    unreadable: false,
  };
}

/** Decode raw upload bytes into a normalized multi-sheet workbook view. */
export async function normalizeWorkbook(rawBytes: Buffer): Promise<NormalizedWorkbook> {
  if (!rawBytes || rawBytes.length === 0) {
    return { kind: "unknown", sheets: [], unreadable: true };
  }
  if (isXlsxBytes(rawBytes)) {
    return normalizeXlsx(rawBytes);
  }
  return normalizeCsv(rawBytes);
}

/**
 * Render a sliced sheet region as a compact text grid for the LLM.
 * `rowIndices` are indices into `sheet.rows` (not Excel row numbers).
 */
export function renderSheetView(
  sheet: NormalizedSheet,
  rowIndices: number[],
  options: { regionLabel?: string; maxChars?: number } = {},
): string {
  const maxChars = options.maxChars ?? 50_000;
  const lines: string[] = [];
  lines.push(`## Sheet "${sheet.name}"`);
  if (options.regionLabel) {
    lines.push(`## Region: ${options.regionLabel}`);
  }
  lines.push(
    `(recoveredDateCells=${sheet.recoveredDateCells}, formulaCells=${sheet.formulaCells}, literalCells=${sheet.literalCells})`,
  );
  lines.push("");

  for (const idx of rowIndices) {
    const row = sheet.rows[idx];
    if (!row) {
      continue;
    }
    const excelRow = sheet.excelRowNumbers[idx] ?? idx + 1;
    const cells = row.map((c) => (c.includes("|") ? c.replace(/\|/g, "/") : c));
    let end = cells.length;
    while (end > 0 && cells[end - 1] === "") {
      end -= 1;
    }
    lines.push(`${excelRow}| ${cells.slice(0, end).join(" | ")}`);
  }

  let text = lines.join("\n");
  if (text.length > maxChars) {
    text = `${text.slice(0, maxChars)}\n… [truncated for size]\n`;
  }
  return text;
}

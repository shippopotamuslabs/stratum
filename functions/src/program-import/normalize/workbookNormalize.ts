/**
 * Deterministic workbook → matrix normalization for custom program import.
 *
 * Produces a human-readable cell grid (with Excel date-as-rep-range recovery) so an LLM can see
 * the file the way a coach would, without receiving raw XLSX bytes or formula soup.
 */
import * as xlsx from "xlsx";
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
 * Convert one SheetJS cell to a display string, applying month-day rep-range recovery.
 */
export function cellToDisplay(cell: xlsx.CellObject | undefined): {
  text: string;
  recoveredDate: boolean;
  formula: boolean;
  literal: boolean;
} {
  if (!cell) {
    return { text: "", recoveredDate: false, formula: false, literal: false };
  }

  const z = typeof cell.z === "string" ? cell.z : undefined;
  const hasFormula = typeof cell.f === "string" && cell.f.length > 0;

  if (cell.v === undefined || cell.v === null || cell.v === "") {
    return {
      text: "",
      recoveredDate: false,
      formula: hasFormula,
      literal: false,
    };
  }

  if (cell.v instanceof Date) {
    // SheetJS cellDates yields local-midnight Date objects; use local calendar fields.
    const recovered = recoverMonthDayRepRange(cell.v, z, "local");
    if (recovered) {
      return { text: recovered, recoveredDate: true, formula: hasFormula, literal: !hasFormula };
    }
    return {
      text: cell.v.toISOString().slice(0, 10),
      recoveredDate: false,
      formula: hasFormula,
      literal: !hasFormula,
    };
  }

  if (typeof cell.v === "number" && isMonthDayOnlyFormat(z)) {
    const asDate = excelSerialToUtcDate(cell.v);
    if (asDate) {
      // Serial → UTC Date; extract with UTC fields so timezone offsets cannot shift the day.
      const recovered = recoverMonthDayRepRange(asDate, z, "utc");
      if (recovered) {
        return { text: recovered, recoveredDate: true, formula: hasFormula, literal: !hasFormula };
      }
    }
  }

  const text = formatPlainValue(cell.v);
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

function normalizeXlsx(bytes: Buffer): NormalizedWorkbook {
  let workbook: xlsx.WorkBook;
  try {
    workbook = xlsx.read(bytes, {
      type: "buffer",
      cellDates: true,
      cellNF: true,
      cellText: false,
    });
  } catch {
    return { kind: "unknown", sheets: [], unreadable: true };
  }
  if (!workbook.SheetNames.length) {
    return { kind: "xlsx", sheets: [], unreadable: true };
  }

  const sheets: NormalizedSheet[] = [];
  for (const name of workbook.SheetNames) {
    const sheet = workbook.Sheets[name];
    if (!sheet || !sheet["!ref"]) {
      sheets.push(sheetFromMatrix(name, [], [], 0, 0, 0));
      continue;
    }
    const range = xlsx.utils.decode_range(sheet["!ref"]);
    // Cap extreme sheets (abuse / calculator sprawl) — feasibility rejects; keep parse bounded.
    const maxR = Math.min(range.e.r, range.s.r + 800);
    const maxC = Math.min(range.e.c, range.s.c + 40);

    const rows: string[][] = [];
    const excelRowNumbers: number[] = [];
    let recoveredDateCells = 0;
    let formulaCells = 0;
    let literalCells = 0;

    for (let r = range.s.r; r <= maxR; r++) {
      const row: string[] = [];
      let rowHasContent = false;
      for (let c = range.s.c; c <= maxC; c++) {
        const addr = xlsx.utils.encode_cell({ r, c });
        const cell = sheet[addr] as xlsx.CellObject | undefined;
        const { text, recoveredDate, formula, literal } = cellToDisplay(cell);
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
        excelRowNumbers.push(r + 1); // 1-based Excel row
      }
    }
    sheets.push(
      sheetFromMatrix(name, rows, excelRowNumbers, recoveredDateCells, formulaCells, literalCells),
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
export function normalizeWorkbook(rawBytes: Buffer): NormalizedWorkbook {
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

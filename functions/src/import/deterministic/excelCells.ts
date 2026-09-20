/**
 * Shared exceljs-community cell model for the XLSX import parsers.
 *
 * SheetJS decoded the whole workbook synchronously and surfaced plain values;
 * exceljs-community parses asynchronously and surfaces rich cell objects. This
 * module is the single place that knows about those objects, so the parsers
 * keep working with plain values (strings, numbers, booleans, Dates, null).
 *
 * Deliberate compatibility notes:
 * - exceljs converts numeric cells carrying a date number-format into JS Dates
 *   on read (like SheetJS `cellDates: true`). Call sites that historically ran
 *   with `cellDates: false` use {@link scalarCellValue}, which converts Dates
 *   back to 1900-system Excel serials — exactly the values SheetJS surfaced.
 * - Merged-cell followers repeat the master value in exceljs; SheetJS surfaced
 *   them as missing. {@link isMergedFollower} restores the SheetJS behavior.
 * - Formula cells expose the cached result via {@link rawCellValue}; only the
 *   normalizer distinguishes formula metadata (via `cell.formula`), because the
 *   old code tracked formula cells separately from their cached results.
 */
import { ValueType, Workbook } from "exceljs-community";
import type { Cell, Worksheet } from "exceljs-community";

/** Load a workbook from raw bytes. Rejects on corrupt/encrypted input. */
export async function loadWorkbook(bytes: Buffer): Promise<Workbook> {
  const workbook = new Workbook();
  // exceljs's isomorphic Buffer type is ArrayBuffer-based; Node's Buffer is
  // accepted at runtime (read as a byte stream).
  await workbook.xlsx.load(bytes as unknown as ArrayBuffer);
  return workbook;
}

/**
 * True for merged-follower cells (ValueType.Merge). exceljs repeats the merge
 * master's value on these cells; guard them to null/blank as SheetJS did.
 */
export function isMergedFollower(cell: Cell): boolean {
  return cell.type === ValueType.Merge;
}

/**
 * Convert a JS Date to a 1900-system Excel serial, mirroring exceljs's own
 * `dateToExcel` (UTC day fraction, epoch 25569, optional 1904 adjustment).
 */
export function dateToSerial(value: Date, date1904: boolean): number {
  return 25569 + value.getTime() / 86400000 - (date1904 ? 1462 : 0);
}

/** Plain value an exceljs cell carries: Date is preserved, never a serial. */
export type RawCellValue = string | number | boolean | Date | null;

function unwrapFormulaResult(
  result: number | string | boolean | Date | { error: string } | undefined,
): RawCellValue {
  if (result === undefined) return null;
  if (result instanceof Date) return result;
  if (typeof result !== "object") return result;
  return result.error;
}

/**
 * Unwrap an exceljs cell to a plain value, preserving Dates.
 * - Formula -> cached result (which may itself be a Date or an error).
 * - Error -> the "#DIV/0!" style token.
 * - Rich text -> concatenated text; hyperlink -> display text.
 */
export function rawCellValue(cell: Cell): RawCellValue {
  const value = cell.value;
  if (value === null || value === undefined) return null;
  if (value instanceof Date) return value;
  if (typeof value !== "object") return value; // string | number | boolean
  if ("formula" in value) return unwrapFormulaResult(value.result);
  if ("error" in value) return value.error;
  if ("richText" in value) return value.richText.map((part) => part.text).join("");
  if ("text" in value) return value.text; // hyperlink display text
  return null;
}

/**
 * Scalar variant for the call sites that historically ran SheetJS with
 * `cellDates: false`: Dates become Excel serials, everything else passes
 * through unchanged.
 */
export function scalarCellValue(
  cell: Cell,
  date1904: boolean,
): string | number | boolean | null {
  const raw = rawCellValue(cell);
  if (raw instanceof Date) return dateToSerial(raw, date1904);
  return raw;
}

/** 1-based bounding box of a worksheet's used range; null when empty. */
export interface UsedRange {
  top: number;
  left: number;
  bottom: number;
  right: number;
}

export function usedRangeBounds(sheet: Worksheet): UsedRange | null {
  const dimensions = sheet.dimensions as unknown as
    | { model?: { top: number; left: number; bottom: number; right: number } }
    | undefined;
  const model = dimensions?.model;
  if (!model || model.bottom < model.top || model.right < model.left) return null;
  return { top: model.top, left: model.left, bottom: model.bottom, right: model.right };
}

/**
 * Recover human-intended display text for spreadsheet cells, especially Excel's habit of
 * turning typed rep ranges like `8-10` into dates (Aug 10) with an `m-d` number format.
 */

/** Calendar source for extracting month/day from a JS Date. */
export type DateCalendar = "local" | "utc";

/** True when the number format is month-day without year/time (typical rep-range corruption). */
export function isMonthDayOnlyFormat(numberFormat: string | undefined | null): boolean {
  if (!numberFormat) {
    return false;
  }
  // Strip Excel escapes and brackets noise: m\-d, [$-…]m-d, etc.
  const nf = numberFormat.replace(/\\/g, "").replace(/\[[^\]]*]/g, "").toLowerCase();
  if (!nf.includes("m") || !nf.includes("d")) {
    return false;
  }
  // Year or time components mean a real calendar/time value — leave alone.
  if (/y|h|s|am\/pm|a\/p/.test(nf)) {
    return false;
  }
  return true;
}

/**
 * Format a JS Date as `M-D` when the cell format is month-day only.
 *
 * Use `calendar: "local"` for SheetJS `cellDates` values (typically local midnight).
 * Use `calendar: "utc"` for Dates built from Excel serials via [excelSerialToUtcDate].
 */
export function recoverMonthDayRepRange(
  value: Date,
  numberFormat: string | undefined | null,
  calendar: DateCalendar = "local",
): string | null {
  if (!isMonthDayOnlyFormat(numberFormat)) {
    return null;
  }
  const month = (calendar === "utc" ? value.getUTCMonth() : value.getMonth()) + 1;
  const day = calendar === "utc" ? value.getUTCDate() : value.getDate();
  // Guard against nonsense (Excel sometimes stores 0-serial weirdness).
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }
  return `${month}-${day}`;
}

/**
 * Excel serial date → UTC Date (1900 date system, SheetJS-compatible enough for display recovery).
 * Only used when we have a numeric cell with a month-day format and no JS Date object.
 */
export function excelSerialToUtcDate(serial: number): Date | null {
  if (!Number.isFinite(serial) || serial < 1 || serial > 100_000) {
    return null;
  }
  // SheetJS / Excel: serial 1 = 1899-12-31 in the 1900 system with the leap-year bug.
  const utcDays = Math.floor(serial - 25569);
  const ms = utcDays * 86_400_000;
  const d = new Date(ms);
  return Number.isNaN(d.getTime()) ? null : d;
}

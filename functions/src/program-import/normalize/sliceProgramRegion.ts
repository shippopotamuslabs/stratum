/**
 * Extract the primary program region (prefer Week 1) from a normalized workbook for LLM input.
 */
import type { NormalizedSheet, NormalizedWorkbook } from "./workbookNormalize";
import { renderSheetView } from "./workbookNormalize";

export interface SlicedProgramRegion {
  viewText: string;
  sheetName: string;
  weekSliced: boolean;
  regionLabel: string;
  liftRowEstimate: number;
  rowCount: number;
}

const WEEK_HEADER = /\bweek\s*(\d+)[a-z]?\b/i;
const NEXT_WEEK_OR_PHASE =
  /\b(week\s*\d+[a-z]?|semi-?deload|full\s*deload|block\s*\d+|max\s*testing|phase\s*\d+)\b/i;
const PROSE_NOISE =
  /\b(copyright|important:|suggested rest day|if you have a \d|read before|warm up protocol|weak point)\b/i;
const DAY_OR_EXERCISE_ROW =
  /\b(day\s*\d+|full\s*body|upper|lower|push|pull|squat|bench|deadlift|press|row|curl|exercise)\b/i;

function estimateLiftRows(rows: string[][]): number {
  let n = 0;
  for (const row of rows) {
    const t = row.filter(Boolean).join(" ");
    if (!t) {
      continue;
    }
    if (PROSE_NOISE.test(t) && !DAY_OR_EXERCISE_ROW.test(t)) {
      continue;
    }
    // Heuristic: row has a non-empty cell that looks like an exercise name (letters) and a number-ish sets/reps
    const hasLetters = /[A-Za-z]{3,}/.test(t);
    const hasNumber = /\d/.test(t);
    if (hasLetters && hasNumber) {
      n += 1;
    }
  }
  return n;
}

/**
 * Find matrix indices covering the first week block, or all useful rows if no week headers.
 */
export function selectRegionRowIndices(sheet: NormalizedSheet): {
  indices: number[];
  weekSliced: boolean;
  regionLabel: string;
} {
  const weekStarts: { index: number; weekNum: number }[] = [];
  for (let i = 0; i < sheet.rows.length; i++) {
    const t = sheet.rows[i]!.filter(Boolean).join(" ");
    const m = t.match(WEEK_HEADER);
    if (m) {
      weekStarts.push({ index: i, weekNum: parseInt(m[1]!, 10) });
    }
  }

  if (weekStarts.length === 0) {
    // No multi-week structure — take all non-pure-noise rows, but drop trailing volume-only tables
    // when we see a clear blank gap + "Muscle Group" / "Volume" header.
    const indices: number[] = [];
    let seenProgram = false;
    for (let i = 0; i < sheet.rows.length; i++) {
      const t = sheet.rows[i]!.filter(Boolean).join(" ");
      if (/^muscle\s*group\b/i.test(t) && seenProgram) {
        break; // volume summary table (personal tidy sheets)
      }
      if (PROSE_NOISE.test(t) && !DAY_OR_EXERCISE_ROW.test(t) && t.length > 80) {
        continue;
      }
      if (DAY_OR_EXERCISE_ROW.test(t) || /\b(sets?|reps?|lift|day)\b/i.test(t)) {
        seenProgram = true;
      }
      indices.push(i);
    }
    return {
      indices: indices.length > 0 ? indices : sheet.rows.map((_, i) => i),
      weekSliced: false,
      regionLabel: "full sheet (no week markers)",
    };
  }

  // Prefer Week 1; else earliest week number.
  let start = weekStarts.find((w) => w.weekNum === 1) ?? weekStarts[0]!;
  let end = sheet.rows.length;
  for (const w of weekStarts) {
    if (w.index > start.index) {
      end = w.index;
      break;
    }
  }
  // Also stop at deload / phase banners after start
  for (let i = start.index + 1; i < end; i++) {
    const t = sheet.rows[i]!.filter(Boolean).join(" ");
    if (NEXT_WEEK_OR_PHASE.test(t) && !WEEK_HEADER.test(t.replace(/week\s*1\b/i, ""))) {
      // If this row is another week header we already handle via weekStarts; for deload essays stop.
      if (/\b(deload|max\s*testing|block\s*\d+)\b/i.test(t) && !WEEK_HEADER.test(t)) {
        end = i;
        break;
      }
    }
  }

  const indices: number[] = [];
  for (let i = start.index; i < end; i++) {
    const t = sheet.rows[i]!.filter(Boolean).join(" ");
    // Keep suggested rest day markers (useful as restDay) but drop long essay-only rows.
    if (t.length > 120 && PROSE_NOISE.test(t) && !DAY_OR_EXERCISE_ROW.test(t)) {
      continue;
    }
    indices.push(i);
  }

  return {
    indices,
    // Only true when later weeks existed and were truncated — not for a sole "Week 1" label.
    weekSliced: weekStarts.length > 1,
    regionLabel: `Week ${start.weekNum} only`,
  };
}

/**
 * Slice the best sheet into a compact LLM-facing workbook view.
 */
export function sliceProgramRegion(
  workbook: NormalizedWorkbook,
  bestSheetIndex: number,
  options: { maxChars?: number } = {},
): SlicedProgramRegion {
  const sheet = workbook.sheets[bestSheetIndex];
  if (!sheet) {
    return {
      viewText: "",
      sheetName: "",
      weekSliced: false,
      regionLabel: "empty",
      liftRowEstimate: 0,
      rowCount: 0,
    };
  }

  const { indices, weekSliced, regionLabel } = selectRegionRowIndices(sheet);
  const slicedRows = indices.map((i) => sheet.rows[i]!);
  const liftRowEstimate = estimateLiftRows(slicedRows);

  const otherSheets = workbook.sheets
    .map((s, i) => (i === bestSheetIndex ? null : s.name))
    .filter((n): n is string => n != null);

  const header = [
    `Workbook kind: ${workbook.kind}`,
    `Sheets: ${workbook.sheets.map((s) => s.name).join(", ")}`,
    otherSheets.length ? `Other sheets (ignored for conversion): ${otherSheets.join(", ")}` : "",
    weekSliced
      ? "NOTE: Multi-week source — only the first training week is included. Import as a repeating weekly template."
      : "",
    "NOTE: Day labels may be sparse or mid-block. Group lifts into workouts using day names and structure.",
    "NOTE: Warm-up set columns are not working sets. Prefer Working Sets when both exist.",
  ]
    .filter(Boolean)
    .join("\n");

  const grid = renderSheetView(sheet, indices, {
    regionLabel,
    maxChars: options.maxChars ?? 50_000,
  });

  let viewText = `${header}\n\n${grid}`;
  const maxChars = options.maxChars ?? 55_000;
  if (viewText.length > maxChars) {
    viewText = `${viewText.slice(0, maxChars)}\n… [truncated for size]\n`;
  }

  return {
    viewText,
    sheetName: sheet.name,
    weekSliced,
    regionLabel,
    liftRowEstimate,
    rowCount: indices.length,
  };
}

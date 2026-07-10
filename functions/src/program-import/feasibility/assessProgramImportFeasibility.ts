/**
 * Cheap pre-LLM gate: accept personal tidy / Nippard-style coaching sheets; reject calculators
 * (SBS-like), garbage, and non-programs so we never spend OpenRouter budget on them.
 */
import type { NormalizedSheet, NormalizedWorkbook } from "../normalize/workbookNormalize";

export type FeasibilityReasonCode =
  | "ok"
  | "unreadable"
  | "empty"
  | "not_a_program"
  | "calculator"
  | "too_thin";

export interface FeasibilityResult {
  decision: "accept" | "reject";
  reasonCode: FeasibilityReasonCode;
  /** User-facing rejection message (or short note on accept). */
  message: string;
  /** Index of the strongest candidate sheet when accepted. */
  bestSheetIndex: number | null;
  /** Debug scores for tests/logging. */
  scores?: { sheetIndex: number; name: string; score: number; formulaRatio: number }[];
}

// English + Spanish headers/day labels cover nearly all user program sheets we care about.
const HEADER_VOCAB =
  /\b(day|exercise|lift|movement|sets?|reps?|rep\s*range|working\s*sets?|warm-?up|rpe|rir|rest|load|%1rm|d[ií]a|ejercicio|levantamiento|movimiento|series?|repeticiones?|rango\s*de\s*reps?|calentamiento|descanso|carga)\b/i;

const DAY_SECTION =
  /\b(day\s*\d+|d[ií]a\s*\d+|upper\s*[a-c#\d]*|lower\s*[a-c#\d]*|superior\s*[a-c#\d]*|inferior\s*[a-c#\d]*|full\s*body\s*\d*|cuerpo\s*completo\s*\d*|push|pull|legs?|piernas?|empuje|jal[oó]n|rest\s*day|d[ií]a\s*de\s*descanso)\b/i;

const WEEK_HEADER = /\b(week|semana)\s*\d+[a-z]?\b/i;

const EXERCISE_LIKE =
  /\b(squat|bench|deadlift|press|row|curl|raise|pulldown|pull-?up|extension|fly|rdl|ohp|dip|shrug|hinge|lunge|calf|hamstring|quad|sentadilla|banco|peso\s*muerto|press|remo|curl|elevaci[oó]n|jal[oó]n|dominada|extensi[oó]n|apertura|zancada|gemelo|isquio|cu[aá]driceps?|hombro|pecho|espalda|tr[ií]ceps?|b[ií]ceps?)\b/i;

const CROSS_SHEET_FORMULA = /(?:'[^']+'\s*!|[A-Za-z][A-Za-z0-9_ ]+!)/;

const FREQUENCY_SHEET = /^(2x|3x|4x|5x|6x|untouched|quick\s*setup|setup)$/i;

const SETUP_SHEET = /^(quick\s*setup|setup|untouched)$/i;

const VOLUME_SHEET = /volume|lookup|maxes?/i;

function rowText(row: string[]): string {
  return row.filter(Boolean).join(" ");
}

function sheetSignals(sheet: NormalizedSheet): {
  score: number;
  formulaRatio: number;
  headerHits: number;
  dayHits: number;
  weekHits: number;
  exerciseHits: number;
  crossSheetHits: number;
} {
  let headerHits = 0;
  let dayHits = 0;
  let weekHits = 0;
  let exerciseHits = 0;
  let crossSheetHits = 0;

  const sampleRows = sheet.rows.slice(0, 200);
  for (const row of sampleRows) {
    const t = rowText(row);
    if (!t) {
      continue;
    }
    if (HEADER_VOCAB.test(t)) {
      headerHits += 1;
    }
    if (DAY_SECTION.test(t)) {
      dayHits += 1;
    }
    if (WEEK_HEADER.test(t)) {
      weekHits += 1;
    }
    if (EXERCISE_LIKE.test(t)) {
      exerciseHits += 1;
    }
    // Cross-sheet refs may appear in normalized text if a formula leaked; also count high formula ratio.
    if (CROSS_SHEET_FORMULA.test(t)) {
      crossSheetHits += 1;
    }
  }

  const totalMarked = sheet.formulaCells + sheet.literalCells;
  const formulaRatio = totalMarked > 0 ? sheet.formulaCells / totalMarked : 0;

  // Score favors readable program tables.
  let score = 0;
  score += Math.min(headerHits, 8) * 3;
  score += Math.min(dayHits, 12) * 4;
  score += Math.min(weekHits, 6) * 2;
  score += Math.min(exerciseHits, 20) * 2;
  score += Math.min(sheet.recoveredDateCells, 15); // personal sheets with mangled rep ranges
  score += Math.min(sheet.literalCells, 80) * 0.05;

  if (VOLUME_SHEET.test(sheet.name)) {
    score -= 20;
  }
  if (SETUP_SHEET.test(sheet.name)) {
    score -= 15;
  }
  if (FREQUENCY_SHEET.test(sheet.name) && formulaRatio > 0.4) {
    score -= 25;
  }

  // Penalize formula-heavy calculator sheets.
  if (formulaRatio > 0.45) {
    score -= 30;
  } else if (formulaRatio > 0.25) {
    score -= 10;
  }
  score -= Math.min(crossSheetHits, 20) * 2;

  return { score, formulaRatio, headerHits, dayHits, weekHits, exerciseHits, crossSheetHits };
}

function workbookLooksLikeCalculator(workbook: NormalizedWorkbook, scores: ReturnType<typeof sheetSignals>[]): boolean {
  const names = workbook.sheets.map((s) => s.name);
  const frequencyTabs = names.filter((n) => /^(2x|3x|4x|5x|6x)$/i.test(n)).length;
  const hasQuickSetup = names.some((n) => /quick\s*setup/i.test(n));
  const hasSetup = names.some((n) => /^setup$/i.test(n));

  if (frequencyTabs >= 2 && (hasQuickSetup || hasSetup)) {
    return true;
  }

  // High formula ratio across best sheets + few exercise literals.
  const totalFormula = workbook.sheets.reduce((a, s) => a + s.formulaCells, 0);
  const totalLiteral = workbook.sheets.reduce((a, s) => a + s.literalCells, 0);
  const ratio = totalFormula + totalLiteral > 0 ? totalFormula / (totalFormula + totalLiteral) : 0;
  const bestExercise = Math.max(0, ...scores.map((s) => s.exerciseHits));
  if (ratio > 0.5 && bestExercise < 5 && workbook.sheets.length >= 3) {
    return true;
  }

  return false;
}

/**
 * Decide whether this workbook is worth an LLM conversion attempt.
 */
export function assessProgramImportFeasibility(workbook: NormalizedWorkbook): FeasibilityResult {
  if (workbook.unreadable || workbook.kind === "unknown") {
    return {
      decision: "reject",
      reasonCode: "unreadable",
      message: "This file could not be read as a spreadsheet or CSV program.",
      bestSheetIndex: null,
    };
  }

  if (workbook.sheets.length === 0 || workbook.sheets.every((s) => s.rows.length === 0)) {
    return {
      decision: "reject",
      reasonCode: "empty",
      message: "This spreadsheet is empty.",
      bestSheetIndex: null,
    };
  }

  const perSheet = workbook.sheets.map((sheet, sheetIndex) => {
    const sig = sheetSignals(sheet);
    return {
      sheetIndex,
      name: sheet.name,
      score: sig.score,
      formulaRatio: sig.formulaRatio,
      headerHits: sig.headerHits,
      dayHits: sig.dayHits,
      weekHits: sig.weekHits,
      exerciseHits: sig.exerciseHits,
      crossSheetHits: sig.crossSheetHits,
    };
  });

  if (workbookLooksLikeCalculator(workbook, perSheet)) {
    return {
      decision: "reject",
      reasonCode: "calculator",
      message:
        "This looks like a progressive calculator or multi-sheet training spreadsheet with live formulas. " +
        "Stratum can import app exports (Alpha Progression, MacroFactor) and simpler program tables, " +
        "but not load-calculator workbooks.",
      bestSheetIndex: null,
      scores: perSheet.map((s) => ({
        sheetIndex: s.sheetIndex,
        name: s.name,
        score: s.score,
        formulaRatio: s.formulaRatio,
      })),
    };
  }

  // Pick best non-volume sheet
  let best = perSheet[0]!;
  for (const s of perSheet) {
    if (s.score > best.score) {
      best = s;
    }
  }

  const hasProgramShape =
    best.headerHits >= 1 ||
    best.dayHits >= 1 ||
    best.weekHits >= 1 ||
    best.exerciseHits >= 3;

  if (!hasProgramShape || best.score < 6) {
    return {
      decision: "reject",
      reasonCode: "not_a_program",
      message:
        "This file does not look like a workout program. " +
        "Try an Alpha Progression plan export, a MacroFactor program export, " +
        "or a spreadsheet with days, lifts, sets, and rep ranges.",
      bestSheetIndex: null,
      scores: perSheet.map((s) => ({
        sheetIndex: s.sheetIndex,
        name: s.name,
        score: s.score,
        formulaRatio: s.formulaRatio,
      })),
    };
  }

  // Need enough lift-like content to convert.
  if (best.exerciseHits < 3 && best.dayHits < 2 && best.headerHits < 2) {
    return {
      decision: "reject",
      reasonCode: "too_thin",
      message: "This spreadsheet does not contain enough program structure to import.",
      bestSheetIndex: null,
      scores: perSheet.map((s) => ({
        sheetIndex: s.sheetIndex,
        name: s.name,
        score: s.score,
        formulaRatio: s.formulaRatio,
      })),
    };
  }

  // Thin but has headers — still reject if almost no content rows
  const bestSheet = workbook.sheets[best.sheetIndex]!;
  if (bestSheet.rows.length < 4) {
    return {
      decision: "reject",
      reasonCode: "too_thin",
      message: "This spreadsheet does not contain enough program structure to import.",
      bestSheetIndex: null,
    };
  }

  return {
    decision: "accept",
    reasonCode: "ok",
    message: "Candidate program spreadsheet",
    bestSheetIndex: best.sheetIndex,
    scores: perSheet.map((s) => ({
      sheetIndex: s.sheetIndex,
      name: s.name,
      score: s.score,
      formulaRatio: s.formulaRatio,
    })),
  };
}

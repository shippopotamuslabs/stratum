/**
 * Cheap pre-agent gate for historical training-log imports.
 * Reject empty / unreadable / binary garbage / structureless text so we never spend OpenRouter
 * turns on files that cannot become NormalizedHistory. Prefer accepting borderline cases —
 * the agent is the escape hatch for unknown but legitimate exports.
 */
import { isXlsxBytes } from "../deterministic/macroFactorParser";

export type HistoryFeasibilityReasonCode =
  | "ok"
  | "empty"
  | "unreadable"
  | "binary_noise"
  | "no_structure";

export interface HistoryFeasibilityResult {
  decision: "accept" | "reject";
  reasonCode: HistoryFeasibilityReasonCode;
  /** Server-side diagnostic; client shows generic unsupported copy. */
  message: string;
}

/** Tokens common in training-log headers / app exports (EN + a few ES). */
const SET_LIKE_TOKEN =
  /\b(exercise|lift|workout|weight|reps?|sets?|rir|rpe|e1rm|date|duration|kg|lbs?|pounds?|kilograms?|ejercicio|peso|repeticiones?|series?|fecha)\b/i;

const SAMPLE_BYTES = 64 * 1024;
const MIN_NONEMPTY_LINES_FOR_STRUCTURE = 2;
const MIN_NUMERIC_FIELDS_PER_LINE = 2;
const MIN_LINES_WITH_NUMERICS = 2;
/** Ratio of non-printable bytes above which we treat content as binary noise. */
const BINARY_NOISE_RATIO = 0.3;

export function assessHistoryImportFeasibility(
  rawBytes: Buffer,
  _fileName: string = "",
): HistoryFeasibilityResult {
  void _fileName;

  if (!rawBytes || rawBytes.length === 0) {
    return reject("empty", "Upload is empty.");
  }

  // Known XLSX container — even unknown sheets may be convertible; never reject here.
  if (isXlsxBytes(rawBytes)) {
    return accept();
  }

  const sample = rawBytes.subarray(0, Math.min(rawBytes.length, SAMPLE_BYTES));
  if (isBinaryNoise(sample)) {
    // PDF / PNG / ZIP-that-is-not-xlsx / etc.
    return reject("binary_noise", "File does not look like a training log export.");
  }

  const text = sample.toString("utf8");
  if (!text || !text.trim()) {
    return reject("empty", "Upload has no readable text.");
  }

  // High replacement-character rate after UTF-8 decode → not useful text.
  if (isMostlyInvalidUtf8(sample, text)) {
    return reject("unreadable", "File could not be read as text or a spreadsheet.");
  }

  if (hasSetLikeSignal(text)) {
    return accept();
  }

  return reject(
    "no_structure",
    "File does not look like a training log (no exercise/set structure found).",
  );
}

function accept(): HistoryFeasibilityResult {
  return {
    decision: "accept",
    reasonCode: "ok",
    message: "Candidate for conversion.",
  };
}

function reject(
  reasonCode: Exclude<HistoryFeasibilityReasonCode, "ok">,
  message: string,
): HistoryFeasibilityResult {
  return { decision: "reject", reasonCode, message };
}

function isBinaryNoise(sample: Buffer): boolean {
  if (sample.length === 0) {
    return false;
  }
  // Common non-log magic numbers (PDF, PNG, JPEG, GIF, ZIP/PK that is not xlsx — xlsx already passed).
  if (
    sample[0] === 0x25 && sample[1] === 0x50 && sample[2] === 0x44 && sample[3] === 0x46 // %PDF
  ) {
    return true;
  }
  if (sample[0] === 0x89 && sample[1] === 0x50 && sample[2] === 0x4e && sample[3] === 0x47) {
    return true;
  }
  if (sample[0] === 0xff && sample[1] === 0xd8 && sample[2] === 0xff) {
    return true;
  }
  if (sample[0] === 0x47 && sample[1] === 0x49 && sample[2] === 0x46) {
    return true;
  }
  // ZIP local header — xlsx is ZIP but already accepted via isXlsxBytes; other zips are noise.
  if (sample[0] === 0x50 && sample[1] === 0x4b) {
    return true;
  }

  let nonPrintable = 0;
  for (let i = 0; i < sample.length; i += 1) {
    const b = sample[i]!;
    // Allow common whitespace; count NULs and other controls (except \t \n \r).
    if (b === 0) {
      nonPrintable += 1;
      continue;
    }
    if (b < 9 || (b > 13 && b < 32) || b === 0x7f) {
      nonPrintable += 1;
    }
  }
  return nonPrintable / sample.length >= BINARY_NOISE_RATIO;
}

function isMostlyInvalidUtf8(sample: Buffer, decoded: string): boolean {
  // Replacement char U+FFFD appears when Buffer.toString("utf8") hits invalid sequences.
  const replacements = (decoded.match(/\uFFFD/g) ?? []).length;
  if (replacements === 0) {
    return false;
  }
  // More than ~5% replacement characters relative to decoded length → unreadable.
  return replacements / Math.max(decoded.length, 1) > 0.05 || replacements > 50;
}

function hasSetLikeSignal(text: string): boolean {
  if (SET_LIKE_TOKEN.test(text)) {
    return true;
  }

  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  if (lines.length < MIN_NONEMPTY_LINES_FOR_STRUCTURE) {
    return false;
  }

  let numericLines = 0;
  for (const line of lines.slice(0, 200)) {
    // Split on common CSV / TSV / semicolon delimiters.
    const fields = line.split(/[,;\t|]/).map((f) => f.trim()).filter(Boolean);
    const numericFields = fields.filter((f) => /^-?\d+(\.\d+)?$/.test(f)).length;
    if (numericFields >= MIN_NUMERIC_FIELDS_PER_LINE) {
      numericLines += 1;
    }
  }
  return numericLines >= MIN_LINES_WITH_NUMERICS;
}

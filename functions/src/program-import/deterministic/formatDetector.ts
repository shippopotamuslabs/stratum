import type { NormalizedProgramTemplate } from "./types";
import type { ProgramTemplateFormat } from "./types";
import { matchesAlphaProgressionPlan, parseAlphaProgressionPlan } from "./alphaProgressionProgramParser";
import { parseMacroFactorProgramXlsx } from "./macroFactorProgramParser";

/** XLSX magic-byte sniff (shared with the history detector). */
function isXlsxBytes(bytes: Buffer): boolean {
  return (
    bytes.length >= 4 &&
    bytes[0] === 0x50 &&
    bytes[1] === 0x4b &&
    bytes[2] === 0x03 &&
    bytes[3] === 0x04
  );
}

/**
 * Detect which known program-template export format a raw file is, and parse it if recognised.
 *
 * Returns the parsed template and detected format on success, or `null` when the file does not
 * match any known format — in which case the caller rejects the job as an unsupported file.
 *
 * Order: XLSX first (binary, not UTF-8 decodable), then Alpha Progression plan CSV (sectional).
 * Hevy and Strong do not export programs, so their files never match here and are rejected.
 */
export function detectAndParseProgramTemplate(
  rawBytes: Buffer,
): { format: ProgramTemplateFormat; template: NormalizedProgramTemplate } | null {
  // 1. XLSX: only MacroFactor ships a program XLSX today.
  if (isXlsxBytes(rawBytes)) {
    const template = parseMacroFactorProgramXlsx(rawBytes);
    if (template) {
      return { format: "macrofactor", template };
    }
    return null;
  }

  // 2. Decode as UTF-8 text for the CSV plan format.
  const text = rawBytes.toString("utf8");
  if (matchesAlphaProgressionPlan(text)) {
    const template = parseAlphaProgressionPlan(text);
    if (template) {
      return { format: "alpha_progression", template };
    }
  }

  return null;
}

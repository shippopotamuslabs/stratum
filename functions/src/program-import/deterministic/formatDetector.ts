import type { NormalizedProgramTemplate } from "./types";
import type { ProgramTemplateFormat } from "./types";
import { matchesAlphaProgressionPlan, parseAlphaProgressionPlan } from "./alphaProgressionProgramParser";
import { parseMacroFactorProgramXlsx } from "./macroFactorProgramParser";
import { matchesStratumProgramExport, parseStratumProgramExport } from "./stratumProgramParser";

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

export type ProgramTemplateDetectResult = {
  format: ProgramTemplateFormat;
  /** First (or only) template — used for validation and single-template conversion. */
  template: NormalizedProgramTemplate;
  /**
   * When Stratum multi-program export is detected, all programs for conversion JSON.
   * Absent for third-party single-template formats.
   */
  programs?: Array<{ id: string | null; template: NormalizedProgramTemplate }>;
};

/**
 * Detect which known program-template export format a raw file is, and parse it if recognised.
 *
 * Returns the parsed template and detected format on success, or `null` when the file does not
 * match any known format — in which case the caller rejects the job as an unsupported file.
 *
 * Order: (1) XLSX MacroFactor, (2) Stratum program JSON, (3) Alpha Progression plan CSV.
 * Hevy and Strong do not export programs, so their files never match here and are rejected.
 */
export async function detectAndParseProgramTemplate(
  rawBytes: Buffer,
): Promise<ProgramTemplateDetectResult | null> {
  // 1. XLSX: only MacroFactor ships a program XLSX today.
  if (isXlsxBytes(rawBytes)) {
    const template = await parseMacroFactorProgramXlsx(rawBytes);
    if (template) {
      return { format: "macrofactor", template };
    }
    return null;
  }

  // 2. Decode as UTF-8 text.
  const text = rawBytes.toString("utf8");

  // 2a. Stratum first-class multi-program envelope.
  if (matchesStratumProgramExport(text)) {
    try {
      const parsed = parseStratumProgramExport(text);
      if (parsed.programs.length === 0) {
        return null;
      }
      return {
        format: "stratum",
        template: parsed.programs[0].template,
        programs: parsed.programs,
      };
    } catch {
      return null;
    }
  }

  // 2b. Alpha Progression plan CSV.
  if (matchesAlphaProgressionPlan(text)) {
    const template = parseAlphaProgressionPlan(text);
    if (template) {
      return { format: "alpha_progression", template };
    }
  }

  return null;
}

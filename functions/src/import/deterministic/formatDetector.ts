import type { NormalizedHistory } from "../schemas";
import { parseCsv } from "./csvReader";
import { matchesHevy, parseHevy } from "./hevyParser";
import { matchesStrong, parseStrong } from "./strongParser";
import {
  isXlsxBytes,
  matchesMacroFactorRows,
  parseMacroFactorCsv,
  parseMacroFactorXlsx,
  readXlsxRows,
} from "./macroFactorParser";
import { matchesAlphaProgression, parseAlphaProgression } from "./alphaProgressionParser";
import type { ImportFormat } from "./types";

/**
 * Detect which known export format a raw file is, and parse it if recognised.
 *
 * Returns the parsed history and detected format on success, or `null` when
 * the file does not match any known format — in which case the caller falls
 * back to the LLM agent converter. Detection is content-based rather than
 * extension-based: the raw bytes (XLSX magic + sheet header) and decoded text
 * (CSV header sniff) are the source of truth, since Android's document picker
 * hands us file names that can be wrong or missing.
 *
 * Order matters. The CSV-table formats (Hevy, Strong, MacroFactor-CSV) are
 * disambiguated by their distinctive headers, so they cannot be confused with
 * each other. The XLSX branch is checked first because XLSX bytes are not
 * valid UTF-8, so attempting to decode them as CSV would be meaningless. Alpha
 * Progression's sectional layout has no header row at all, so it is checked
 * last — only after every flat-table format has declined.
 */
export function detectAndParse(rawBytes: Buffer, fileName: string): { format: ImportFormat; history: NormalizedHistory } | null {
  // 1. XLSX (binary): only MacroFactor ships XLSX today.
  if (isXlsxBytes(rawBytes)) {
    const rows = readXlsxRows(rawBytes);
    if (matchesMacroFactorRows(rows)) {
      const history = parseMacroFactorXlsx(rawBytes);
      if (history) {
        return { format: "macrofactor", history };
      }
    }
    // Unknown XLSX — fall through to agent.
    return null;
  }

  // 2. Decode as UTF-8 text for the CSV-based formats.
  const text = rawBytes.toString("utf8");

  // 2a. Alpha Progression (sectional, no header row) — check via its own sniff.
  if (matchesAlphaProgression(text)) {
    const history = parseAlphaProgression(text);
    if (history) {
      return { format: "alpha_progression", history };
    }
  }

  // 2b. Flat CSV tables. Try each format's header sniff; the signatures are
  //     mutually exclusive so the first match wins.
  const commaRows = parseCsv(text, ",");
  const commaHeader = commaRows[0];
  if (matchesHevy(commaHeader)) {
    return { format: "hevy", history: parseHevy(text) };
  }
  if (matchesMacroFactorRows(commaRows)) {
    const history = parseMacroFactorCsv(text);
    if (history) {
      return { format: "macrofactor", history };
    }
  }

  const semicolonRows = parseCsv(text, ";");
  const semicolonHeader = semicolonRows[0];
  if (matchesStrong(semicolonHeader)) {
    return { format: "strong", history: parseStrong(text) };
  }

  // fileName is available as a last-resort hint if a future format needs it;
  // content sniffing above is authoritative for every format we ship today.
  void fileName;
  return null;
}

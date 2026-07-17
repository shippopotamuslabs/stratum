import type { NormalizedHistory } from "../schemas";

/**
 * Which known export format a file was identified as.
 *
 * Deterministic parsers cover the four most common lifting-log apps. Anything
 * that does not match one of these falls back to the LLM agent converter, so
 * the list is intentionally closed: add a new variant only when shipping a new
 * deterministic parser alongside it.
 */
export type ImportFormat =
  | "hevy"
  | "strong"
  | "macrofactor"
  | "alpha_progression"
  | "stratum";

/**
 * Result of a successful deterministic conversion: the normalized history plus
 * counts reported back to the caller for progress messaging and the
 * `latestConversion` job field.
 */
export interface DeterministicResult {
  format: ImportFormat;
  history: NormalizedHistory;
  rowCount: number;
  sessionCount: number;
}

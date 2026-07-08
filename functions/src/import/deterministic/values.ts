import { normalizeImportTimestamp } from "../schemas";

/**
 * Shared value-coercion helpers used by the deterministic parsers.
 *
 * Each export format spells numbers, units, and effort a little differently;
 * these helpers keep the per-format parsers focused on column mapping rather
 * than reinventing the same parsing rules.
 */

/**
 * Parse a numeric field, returning null for blank/unparseable input. The
 * deterministic contract only treats a row as valid when its weight/reps are
 * parseable, so null is the natural "skip this row" signal.
 */
export function parseNumber(raw: string): number | null {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return null;
  }
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}

/**
 * Parse an integer field, returning null for blank/unparseable input. Used for
 * reps, RIR, and set indices — all must be integers per the normalized contract.
 */
export function parseInteger(raw: string): number | null {
  const value = parseNumber(raw);
  if (value === null) {
    return null;
  }
  return Number.isInteger(value) ? value : null;
}

/**
 * Convert an RPE value (6-10, scale of perceived exertion) to an integer RIR
 * (reps in reserve, 0-4) via the standard `round(10 - rpe)` mapping, clamped to
 * be non-negative. The normalized contract only carries `rir`, so RPE-bearing
 * formats (Hevy, Strong) run their effort field through this before emitting.
 *
 * Returns null when RPE is blank or unparseable, so the caller can simply omit
 * `rir` rather than fabricating a value.
 */
export function rpeToRir(raw: string): number | null {
  const value = parseNumber(raw);
  if (value === null) {
    return null;
  }
  const rir = Math.round(10 - value);
  return rir < 0 ? 0 : rir;
}

/**
 * Normalise a raw timestamp into the ISO-8601 UTC form the normalized contract
 * requires. Delegates to the shared server-side normaliser so deterministic and
 * agent-produced output accept exactly the same timestamp spellings.
 */
export function normalizeTimestamp(raw: string): string {
  return normalizeImportTimestamp(raw);
}

/**
 * Strip a trailing superset/group marker like `∈ SS1` from an exercise name.
 * MacroFactor embeds superset membership in the exercise column (e.g.
 * `Dumbbell Skull Crusher ∈ SS3`); the normalized contract has no field for it,
 * and leaving it on would pollute the downstream BM25 lift-name matching. Other
 * formats never carry this suffix so running every name through here is safe.
 */
const SUPERSET_SUFFIX = /\s*∈\s*(?:SS|GS|TS|CS)\d+\s*$/;

export function stripSupersetSuffix(name: string): string {
  return name.replace(SUPERSET_SUFFIX, "").trim();
}

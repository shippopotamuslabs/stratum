/**
 * Callable versioning helpers (STR-48).
 *
 * Breaking changes ship as a new `_v<N>` function; the old export keeps
 * serving through the deprecation window. Every versioned callable spreads an
 * `_meta` block into its `result` (see `docs/callable-function-versioning.md`
 * §4) so served versions stay observable and deprecated versions can point
 * clients at their sunset date. Clients ignore `_meta` — no client behavior
 * may depend on it.
 */
import type { LogContext, StructuredLogger } from "../observability/structuredLog";

export interface CallableDeprecation {
  /** ISO-8601 calendar date (`YYYY-MM-DD`) after which this major is deleted. */
  sunsetDate: string;
  /** Human-readable pointer to the superseding function. */
  message: string;
}

export interface CallableResultMeta {
  /** Major version of the callable that produced this result. */
  version: number;
  /** Present only while this major is deprecated. */
  deprecation?: CallableDeprecation;
}

const SUNSET_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Build the `_meta` block for a callable `result`. Pure — safe to unit test.
 * Throws on programmer error (non-positive-integer version, malformed
 * deprecation) so misuse fails loudly in tests, never as a silent wire shape.
 */
export function buildResultMeta(
  version: number,
  deprecation?: CallableDeprecation,
): CallableResultMeta {
  if (!Number.isInteger(version) || version < 1) {
    throw new Error(`version must be a positive integer, got ${version}.`);
  }
  if (deprecation === undefined) {
    return { version };
  }
  if (!SUNSET_DATE_PATTERN.test(deprecation.sunsetDate)) {
    throw new Error(
      `sunsetDate must be YYYY-MM-DD, got ${JSON.stringify(deprecation.sunsetDate)}.`,
    );
  }
  if (deprecation.message.length === 0) {
    throw new Error("deprecation.message must be non-empty.");
  }
  return { version, deprecation: { ...deprecation } };
}

/**
 * Log which major served a call. Query `jsonPayload.version` in Cloud Logging
 * to confirm a deprecated major's traffic has drained before sunset deletion.
 */
export function logCallableVersion(
  logger: Pick<StructuredLogger, "info">,
  callable: string,
  version: number,
  context: LogContext = {},
): void {
  logger.info(`Callable ${callable} v${version} served.`, {
    ...context,
    callable,
    version,
  });
}

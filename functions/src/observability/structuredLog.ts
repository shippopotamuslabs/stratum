/**
 * Structured logging conventions for Stratum Cloud Functions (STR-28).
 *
 * Every log line is a single JSON object on one line with, at minimum:
 * - `severity`: Cloud Logging severity (DEBUG/INFO/NOTICE/WARNING/ERROR/CRITICAL)
 * - `message`: human-readable summary (no PII beyond uid/jobId correlation)
 * - `service`: Cloud Function entry point emitting the line
 *
 * Correlation fields (`uid`, `jobId`) must be top-level structured fields — never
 * interpolated only into `message` — so BigQuery / Logs Explorer can filter on
 * `jsonPayload.jobId` without regex. Timing fields (`durationMs`) use the same rule.
 *
 * Error Reporting: reports an error via `reportError`, which adds the
 * `type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent`
 * marker plus `serviceContext`, and always writes to stderr with the exception
 * type and stack trace in `message`. Plain `console.error(err)` without a stack
 * does NOT group in Error Reporting — use `reportError`.
 *
 * Severity guidance:
 * - INFO: lifecycle milestones (trigger received, dispatch sent, quota counted)
 * - WARNING: retried/degraded but recovered, or failed without an exception
 *   (e.g. non-OK solver dispatch — no stack exists to report)
 * - ERROR: unexpected failure. With an exception → `reportError`; without one
 *   (config missing, transaction failed with only a string) → `error` so it
 *   stays queryable but does not open an Error Reporting issue with no stack.
 */
export type LogSeverity = "DEBUG" | "INFO" | "NOTICE" | "WARNING" | "ERROR" | "CRITICAL";

/** Structured context attached to every record. Extra keys pass through verbatim. */
export interface LogContext {
  service?: string;
  uid?: string;
  jobId?: string;
  durationMs?: number;
  [key: string]: unknown;
}

/** Serialized form of a caught exception for structured fields + Error Reporting. */
export interface ErrorDetails {
  errorType: string;
  errorMessage: string;
  stackTrace: string;
}

/**
 * Error Reporting event marker. Presence of this `@type` field is what makes
 * Error Reporting ingest the log entry as an issue (grouped by stack fingerprint).
 */
export const ERROR_REPORT_TYPE =
  "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

/** Serialize any caught value into stable error fields. Never throws. */
export function formatError(error: unknown): ErrorDetails {
  if (error instanceof Error) {
    return {
      errorType: error.constructor?.name || "Error",
      errorMessage: error.message,
      stackTrace: typeof error.stack === "string" ? error.stack : String(error),
    };
  }
  const text = String(error);
  return { errorType: "Unknown", errorMessage: text, stackTrace: text };
}

/** Build a log record without emitting it. Pure — safe to unit test. */
export function buildLogRecord(
  severity: LogSeverity,
  message: string,
  context: LogContext = {},
): Record<string, unknown> {
  // severity/message are explicit arguments: strip them from the spread so a
  // context key can never silently clobber them. `service` still flows from
  // context (createLogger's withService pins it to opts.service last).
  const { severity: _ignoredSeverity, message: _ignoredMessage, ...rest } = context;
  return { severity, message, ...rest };
}

/**
 * Build an Error Reporting record: ERROR severity plus the `@type` marker and
 * `serviceContext`, with the exception type/stack in both structured fields
 * and `message` (grouping fingerprints the stack in `message`).
 */
export function buildErrorReport(
  message: string,
  error: unknown,
  context: LogContext = {},
): Record<string, unknown> {
  const details = formatError(error);
  // severity/message are fixed by this builder; service has one source below.
  const { severity: _ignoredSeverity, message: _ignoredMessage, service, ...rest } = context;
  const serviceName = service ?? "unknown";
  return {
    "@type": ERROR_REPORT_TYPE,
    severity: "ERROR" satisfies LogSeverity,
    message: `${message}: ${details.errorType}: ${details.errorMessage}\n${details.stackTrace}`,
    serviceContext: { service: serviceName },
    service: serviceName,
    ...rest,
    errorType: details.errorType,
    errorMessage: details.errorMessage,
    stackTrace: details.stackTrace,
  };
}

export type LogWriter = (line: string, toStderr: boolean) => void;

const defaultWriter: LogWriter = (line, toStderr) => {
  if (toStderr) {
    process.stderr.write(`${line}\n`);
  } else {
    process.stdout.write(`${line}\n`);
  }
};

/**
 * Minimal structured logger. Inject `write` in tests to capture lines.
 * `service` defaults every record emitted by this logger.
 */
export function createLogger(opts: { service: string; write?: LogWriter }) {
  const write = opts.write ?? defaultWriter;
  const emit = (record: Record<string, unknown>, toStderr: boolean): void => {
    write(JSON.stringify(record), toStderr);
  };
  const withService = (context: LogContext = {}): LogContext => ({
    ...context,
    // The logger's service is the contract: it always wins over a
    // caller-supplied context.service, which can no longer clobber it.
    service: opts.service,
  });
  return {
    debug(message: string, context: LogContext = {}): void {
      emit(buildLogRecord("DEBUG", message, withService(context)), false);
    },
    info(message: string, context: LogContext = {}): void {
      emit(buildLogRecord("INFO", message, withService(context)), false);
    },
    warn(message: string, context: LogContext = {}): void {
      emit(buildLogRecord("WARNING", message, withService(context)), true);
    },
    error(message: string, context: LogContext = {}): void {
      emit(buildLogRecord("ERROR", message, withService(context)), true);
    },
    /** Unexpected failure WITH an exception → Error Reporting issue. */
    reportError(message: string, error: unknown, context: LogContext = {}): void {
      emit(buildErrorReport(message, error, withService(context)), true);
    },
  };
}

export type StructuredLogger = ReturnType<typeof createLogger>;

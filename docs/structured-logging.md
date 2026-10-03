# Structured Logging Conventions (STR-28)

Server-side log conventions for the Cloud Run solver (`stratum-solver`) and
Cloud Functions. Companion code:

- `functions/src/observability/structuredLog.ts` (`createLogger`)
- `solver-service/.../StructuredLog.kt` (`StructuredLogger`)

Both emit the same shape so one BigQuery sink and one set of queries cover
both surfaces. Out of scope: client-side logging (STR-27), alert policies
(STR-24).

## 1. Required fields

Every line is a single JSON object with, at minimum:

| Field | Type | Notes |
|-------|------|-------|
| `severity` | `DEBUG`/`INFO`/`NOTICE`/`WARNING`/`ERROR`/`CRITICAL` | Cloud Logging severity; the BQ sink routes on this |
| `message` | string | Human summary. No PII beyond uid/jobId |
| `service` | string | Function entry point (`processProgramSynthesisJob`) or `stratum-solver` |

Correlation and timing are **top-level structured fields**, never interpolated
only into `message`:

| Field | Type | Notes |
|-------|------|-------|
| `uid` | string | Firebase uid (already logged pre-STR-28; no new PII) |
| `jobId` | string | Synthesis / import / scan job id |
| `durationMs` | number | Step timing (solver `POST` round-trip, CP-SAT solve) |

Extra detail goes in named fields (`status`, `body` truncated to 500 chars,
`bytes`, `sessions`), not in `message`, so BQ can `SELECT`/`WHERE` on them.

## 2. Severity guidance

- `INFO` — lifecycle milestones: trigger received, job claimed, dispatch
  succeeded (with `durationMs`), result uploaded.
- `WARNING` — degraded but recovered, or failed with no exception to report:
  non-OK solver dispatch (`status`, `body`), bad request bodies, missing
  optional config with fallback.
- `ERROR` without exception — unexpected failure with no stack (e.g.
  `SOLVER_SERVICE_URL` unset). Queryable, but opens **no** Error Reporting
  issue. Use `log.error` (TS) / `logger.error` (Kotlin).
- `ERROR` with exception — unexpected throw. Use `reportError`
  (TS) / `logger.reportError` (Kotlin): adds the `@type`
  `.../ReportedErrorEvent` marker + `serviceContext`, writes to stderr, and
  embeds `errorType` + `stackTrace` in both fields and `message`.

## 3. Error Reporting grouping

Error Reporting groups by stack fingerprint in `message`, so:

1. Always pass the caught `Error`/`Throwable` — never `String(err)` it away.
   `console.error(err)` without a stack does **not** group; `reportError` does.
2. Keep `serviceContext.service` equal to the deployed service
   (`stratum-solver` for Cloud Run; the function name for Functions).
3. One issue per throw site: log at the layer that handles the failure
   (dispatcher catch, worker catch, HTTP handler catch) — not at every layer
   (see `CODE_STYLE_RULES.md` §1: no duplicate logs).
4. Verify grouping in console: Error Reporting → issues show
   `serviceContext.service` + exception type; a new deploy that only rewords
   `message` prefixes must not fork the issue (stack unchanged).

## 4. Querying via the BigQuery sink

The Cloud Logging sink routes application logs to BigQuery (console-side;
retention + cost controls per ticket). Query the structured fields directly:

```sql
-- Solver dispatch latency p99 by day
SELECT DATE(timestamp) AS day,
       APPROX_QUANTILES(CAST(jsonPayload.durationMs AS INT64), 101)[OFFSET(99)] AS p99_ms
FROM `PROJECT.DATASET.cloud_run_logs`
WHERE jsonPayload.service = 'stratum-solver'
  AND jsonPayload.message = 'uploaded result'
GROUP BY day ORDER BY day;

-- All lines for one job across Functions + solver
SELECT timestamp, jsonPayload.service, jsonPayload.severity, jsonPayload.message
FROM `PROJECT.DATASET.all_app_logs`
WHERE jsonPayload.jobId = 'JOB_ID'
ORDER BY timestamp;

-- Error Reporting-backed failures and their types
SELECT jsonPayload.errorType, COUNT(*) AS n
FROM `PROJECT.DATASET.all_app_logs`
WHERE jsonPayload.severity = 'ERROR'
GROUP BY 1 ORDER BY n DESC;
```

Replace `PROJECT.DATASET.*` with the sink destination table names.

## 5. Cost controls

- One line per lifecycle milestone, not per retry/poll. Success paths log
  once (with `durationMs`); heartbeats and trace writes stay in Firestore,
  not in logs.
- `body`/`detail` fields are truncated (500 chars) before logging.
- `DEBUG` stays off in production paths; promote to `INFO` only what the
  queries in §4 need.

// STR-242 slice 3: canonical SQL source for the Grafana as-code bundle.
//
// Single source of truth: every BigQuery string rendered into
// gcp/grafana/dashboards/stratum-telemetry.json (panels 1-5),
// gcp/grafana/alerting/stratum-telemetry-alerts.yaml (burn, collector, spike
// rules), the outcome-series queries in docs/structured-logging.md, and the
// verify query in gcp/grafana/README.md is defined exactly once below.
// build-grafana-bundle.mjs renders those artifacts from these definitions;
// grafana.test.mjs fails if the committed artifacts drift from a fresh
// generation. To change a query, edit it here, run
// `node gcp/grafana/build-grafana-bundle.mjs`, and commit the result.

// Shared table references (three-part project.dataset.table placeholders).
export const CLIENT_LOG_TABLE = "`PROJECT.DATASET.client_log_sink`";
export const APP_LOG_TABLE = "`PROJECT.DATASET.all_app_logs`";
export const ANALYTICS_EVENTS_TABLE = "`PROJECT.analytics_PROPERTY.events_*`";
export const CRASHLYTICS_TABLE =
  "`PROJECT.CRASHLYTICS_DATASET.CRASHLYTICS_TABLE`";

// Shared filter fragments.
export const OUTCOME_SOURCE_FILTER = "jsonPayload.source = 'stratum-client-log'";

export const OUTCOME_SEVERITY_FILTER = "jsonPayload.severity = 'OUTCOME'";

export const COLLECTOR_MESSAGE_FILTER =
  "CONTAINS_SUBSTR(jsonPayload.message, 'ingestClientLogs')";

export const EXCEPTIONAL_EVENT_FILTER = "event_name = 'exceptional_occurred'";

// Dashboard panel queries (single-line rawSql values).
export const PANEL_1_SQL =
  `SELECT TIMESTAMP_TRUNC(timestamp, MINUTE) AS time, jsonPayload.tag AS use_case, jsonPayload.outcome AS outcome, COUNT(*) AS n FROM ${CLIENT_LOG_TABLE} WHERE $__timeFilter(timestamp) AND ${OUTCOME_SOURCE_FILTER} AND ${OUTCOME_SEVERITY_FILTER} GROUP BY time, use_case, outcome ORDER BY time`;

export const PANEL_2_SQL =
  `SELECT TIMESTAMP_TRUNC(timestamp, MINUTE) AS time, jsonPayload.tag AS use_case, SUM(IF(jsonPayload.outcome = 'expected_failure', 1, 0)) / COUNT(*) AS failure_rate FROM ${CLIENT_LOG_TABLE} WHERE $__timeFilter(timestamp) AND ${OUTCOME_SOURCE_FILTER} AND ${OUTCOME_SEVERITY_FILTER} GROUP BY time, use_case ORDER BY time`;

export const PANEL_3_SQL =
  `SELECT TIMESTAMP_TRUNC(TIMESTAMP_MICROS(event_timestamp), MINUTE) AS time, (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'boundary') AS boundary, COUNT(*) AS n FROM ${ANALYTICS_EVENTS_TABLE} WHERE _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)) AND FORMAT_DATE('%Y%m%d', CURRENT_DATE()) AND ${EXCEPTIONAL_EVENT_FILTER} GROUP BY time, boundary ORDER BY time`;

export const PANEL_4_SQL =
  `SELECT TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), MAX(timestamp), MINUTE) AS minutes_since_last_outcome FROM ${CLIENT_LOG_TABLE} WHERE ${OUTCOME_SOURCE_FILTER} AND ${OUTCOME_SEVERITY_FILTER}`;

export const PANEL_5_SQL =
  `SELECT issue_id, issue_title, event_count, user_count FROM ${CRASHLYTICS_TABLE} WHERE $__timeFilter(event_timestamp) ORDER BY event_count DESC LIMIT 50`;

// Alert rule queries (multi-line; the generator indents them under rawSql).
export const ALERT_BURN_SQL =
  `SELECT TIMESTAMP_SECONDS(5 * 60 * DIV(UNIX_SECONDS(timestamp), 5 * 60)) AS time,
jsonPayload.tag AS use_case,
SUM(IF(jsonPayload.outcome = 'expected_failure', 1, 0)) AS failures,
COUNT(*) AS total
FROM ${CLIENT_LOG_TABLE}
WHERE $__timeFilter(timestamp)
AND ${OUTCOME_SOURCE_FILTER}
AND ${OUTCOME_SEVERITY_FILTER}
GROUP BY time, use_case HAVING COUNT(*) >= 20 ORDER BY time`;

export const ALERT_COLLECTOR_SQL =
  `SELECT TIMESTAMP_TRUNC(timestamp, MINUTE) AS time, COUNT(*) AS n
FROM ${APP_LOG_TABLE}
WHERE $__timeFilter(timestamp)
AND ${COLLECTOR_MESSAGE_FILTER}
AND severity = 'ERROR'
GROUP BY time ORDER BY time`;

export const ALERT_SPIKE_SQL =
  `SELECT TIMESTAMP_TRUNC(TIMESTAMP_MICROS(event_timestamp), MINUTE) AS time, COUNT(*) AS n
FROM ${ANALYTICS_EVENTS_TABLE}
WHERE _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)) AND FORMAT_DATE('%Y%m%d', CURRENT_DATE())
AND ${EXCEPTIONAL_EVENT_FILTER}
GROUP BY time ORDER BY time`;

// docs/structured-logging.md outcome-series queries (multi-line reference
// variants of the panel/burn queries, sharing the same table and filters).
export const DOC_OUTCOME_VOLUME_SQL =
  `SELECT TIMESTAMP_TRUNC(timestamp, MINUTE) AS time,
       jsonPayload.tag AS use_case, jsonPayload.outcome AS outcome,
       COUNT(*) AS n
FROM ${CLIENT_LOG_TABLE}
WHERE ${OUTCOME_SOURCE_FILTER}
  AND ${OUTCOME_SEVERITY_FILTER}
GROUP BY time, use_case, outcome ORDER BY time;`;

export const DOC_BURN_INPUT_SQL =
  `SELECT jsonPayload.tag AS use_case, jsonPayload.reasonCode AS reason,
       SUM(IF(jsonPayload.outcome = 'expected_failure', 1, 0)) AS failures,
       COUNT(*) AS total
FROM ${CLIENT_LOG_TABLE}
WHERE ${OUTCOME_SOURCE_FILTER}
  AND ${OUTCOME_SEVERITY_FILTER}
GROUP BY use_case, reason ORDER BY failures DESC;`;

export const DOC_FRESHNESS_SQL =
  `SELECT TIMESTAMP_DIFF(CURRENT_TIMESTAMP(), MAX(timestamp), MINUTE)
       AS minutes_since_last_outcome
FROM ${CLIENT_LOG_TABLE}
WHERE ${OUTCOME_SOURCE_FILTER}
  AND ${OUTCOME_SEVERITY_FILTER};`;

// The generated fenced-block bodies (without the ```sql fences themselves).
export const DOCS_OUTCOME_BLOCK =
  `-- Outcome volume by use case and outcome (Grafana panel 1)
${DOC_OUTCOME_VOLUME_SQL}

-- Expected-failure share by use case with reason split (burn-alert input)
${DOC_BURN_INPUT_SQL}

-- Pipeline freshness (Grafana panel 4; alert input is the collector-error rule)
${DOC_FRESHNESS_SQL}`;

export const README_VERIFY_SQL =
  `SELECT event_name, COUNT(*) FROM ${ANALYTICS_EVENTS_TABLE}
WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
AND ${EXCEPTIONAL_EVENT_FILTER} GROUP BY 1;`;

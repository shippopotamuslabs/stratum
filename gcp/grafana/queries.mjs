// STR-242 slice 3: shared SQL fragments for the Grafana as-code bundle.
//
// Single source of truth for the filter/table strings hand-copied into
// gcp/grafana/dashboards/stratum-telemetry.json (panels 1/2/4, 3),
// gcp/grafana/alerting/stratum-telemetry-alerts.yaml (burn, collector,
// spike rules), and the outcome queries in docs/structured-logging.md.
// grafana.test.mjs asserts these fragments appear verbatim (modulo
// whitespace) in every artifact, so editing one copy without the others
// fails the suite instead of drifting silently.
export const CLIENT_LOG_TABLE = "`PROJECT.DATASET.client_log_sink`";

export const OUTCOME_SOURCE_FILTER = "jsonPayload.source = 'stratum-client-log'";

export const OUTCOME_SEVERITY_FILTER = "jsonPayload.severity = 'OUTCOME'";

export const COLLECTOR_MESSAGE_FILTER =
  "CONTAINS_SUBSTR(jsonPayload.message, 'ingestClientLogs')";

export const EXCEPTIONAL_EVENT_FILTER = "event_name = 'exceptional_occurred'";

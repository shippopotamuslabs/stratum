#!/usr/bin/env node
// Generate the Grafana as-code bundle from one canonical SQL source (STR-242).
//
// All BigQuery strings live exactly once in ./queries.mjs; this script renders
// them into gcp/grafana/dashboards/stratum-telemetry.json (panels 1-5),
// gcp/grafana/alerting/stratum-telemetry-alerts.yaml (burn, collector, spike
// rules), the outcome-series fenced block in docs/structured-logging.md, and
// the verify-query fenced block in gcp/grafana/README.md. Panel/alert/docs
// prose (titles, thresholds, runbook links) lives here next to the SQL it
// describes; the SQL itself is only interpolated, never retyped.
//
// Workflow: edit queries.mjs, run `node gcp/grafana/build-grafana-bundle.mjs`,
// commit the regenerated files. grafana.test.mjs fails if the committed files
// drift from what this script emits (mirroring
// gcp/monitoring/build-alert-policies.py).
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  ALERT_BURN_SQL,
  ALERT_COLLECTOR_SQL,
  ALERT_SPIKE_SQL,
  DOCS_OUTCOME_BLOCK,
  PANEL_1_SQL,
  PANEL_2_SQL,
  PANEL_3_SQL,
  PANEL_4_SQL,
  PANEL_5_SQL,
  README_VERIFY_SQL,
} from "./queries.mjs";

const DIR = dirname(fileURLToPath(import.meta.url));
const REPO = join(DIR, "..", "..");
const OUT = process.argv[2] ?? REPO;

const DS_BIGQUERY = "${DS_BIGQUERY}";

// Provenance stamp emitted into every generated artifact so a reader (or
// reviewer) can tell builder output from hand-written text without guessing.
const GENERATION_NOTICE =
  "GENERATED from gcp/grafana/queries.mjs by gcp/grafana/build-grafana-bundle.mjs — do not hand-edit.";

const dashboard = {
  uid: "stratum-telemetry",
  title: "Stratum telemetry (STR-242)",
  tags: ["stratum", "telemetry", "str-242"],
  timezone: "utc",
  schemaVersion: 39,
  version: 1,
  description:
    `${GENERATION_NOTICE} Outcome series (OTel-shaped, via the authenticated collector into the client-log BigQuery sink), exceptional-occurrence counter (Analytics BigQuery export), and Crashlytics issue velocity (Crashlytics BigQuery export). Import: Grafana Dashboards > Import, paste this file. Replace the PROJECT./DATASET/PROPERTY placeholders per gcp/grafana/README.md (single vocabulary: PROJECT.DATASET.client_log_sink, PROJECT.analytics_PROPERTY).`,
  panels: [
    {
      id: 1,
      title: "Outcome volume by use case and outcome",
      type: "timeseries",
      datasource: DS_BIGQUERY,
      targets: [{ rawSql: PANEL_1_SQL, format: "time_series" }],
    },
    {
      id: 2,
      title: "Expected-failure rate by use case",
      type: "timeseries",
      datasource: DS_BIGQUERY,
      targets: [{ rawSql: PANEL_2_SQL, format: "time_series" }],
    },
    {
      id: 3,
      title: "Exceptional-occurrence counter rate (Analytics export)",
      description:
        "Bare exceptional_occurred counts for real-time exceptional-rate alerting; issue detail lives in Crashlytics (panel 5).",
      type: "timeseries",
      datasource: DS_BIGQUERY,
      targets: [{ rawSql: PANEL_3_SQL, format: "time_series" }],
    },
    {
      id: 4,
      title: "Outcome pipeline freshness",
      description:
        "Minutes since the newest OUTCOME row. A rising value means the collector path (not necessarily the app) needs attention; compare with the CollectorErrors alert before paging.",
      type: "stat",
      datasource: DS_BIGQUERY,
      targets: [{ rawSql: PANEL_4_SQL, format: "table" }],
    },
    {
      id: 5,
      title: "Crashlytics issue velocity (fill in after export enablement)",
      description:
        "TEMPLATE: enable the Crashlytics BigQuery export, list the dataset tables, and replace CRASHLYTICS_DATASET plus the table/columns below. Grafana sees issue-level rows (grouping happens in Crashlytics before export).",
      type: "table",
      datasource: DS_BIGQUERY,
      targets: [{ rawSql: PANEL_5_SQL, format: "table" }],
    },
  ],
};

const indent16 = (sql) =>
  sql
    .split("\n")
    .map((line) => `                ${line}`)
    .join("\n");

const alertsYaml = `# ${GENERATION_NOTICE}
# Regenerate: node gcp/grafana/build-grafana-bundle.mjs
# Grafana unified alerting, phase 1 (STR-242 slice 3).
# Provisioning format: Grafana file provisioning, apiVersion 1. Drop this file
# into the Grafana provisioning alerting directory, or recreate the rules by
# hand from the SQL/thresholds below. Contact point and BigQuery datasource
# UID are filled at import (see ../README.md). Thresholds are phase-1 starting
# points for a low-volume beta — tighten them once the dashboard shows baselines.
apiVersion: 1
groups:
  - orgId: 1
    name: stratum-telemetry-phase1
    folder: Stratum
    interval: 1m
    rules:
      - uid: stratum-expected-failure-burn
        title: "Stratum: expected-failure burn over baseline"
        condition: volume
        data:
          - refId: volume
            datasourceUid: ${DS_BIGQUERY}
            model:
              rawSql: >-
${indent16(ALERT_BURN_SQL)}
        annotations:
          summary: "Expected-failure volume for a use case exceeds 3x its trailing-hour baseline (min 20 outcomes/5m)."
          runbook: "gcp/grafana/README.md#alerts"
        labels:
          severity: warning
          ticket: str-242

      - uid: stratum-collector-errors
        title: "Stratum: ingestClientLogs collector errors"
        condition: errors
        data:
          - refId: errors
            datasourceUid: ${DS_BIGQUERY}
            model:
              rawSql: >-
${indent16(ALERT_COLLECTOR_SQL)}
        annotations:
          summary: "The authenticated collector callable logged errors in the last 15 minutes — outcomes may be dropping."
          runbook: "gcp/grafana/README.md#alerts"
        labels:
          severity: critical
          ticket: str-242

      - uid: stratum-exceptional-rate-spike
        title: "Stratum: exceptional-occurrence rate spike"
        condition: spike
        data:
          - refId: spike
            datasourceUid: ${DS_BIGQUERY}
            model:
              rawSql: >-
${indent16(ALERT_SPIKE_SQL)}
        annotations:
          summary: "Bare exceptional counter 15-minute rate exceeds 5x its 24-hour baseline (min 5 events/15m)."
          runbook: "gcp/grafana/README.md#alerts"
        labels:
          severity: critical
          ticket: str-242
`;

const OUTCOME_BEGIN =
  "<!-- grafana-generated:begin outcome-queries (do not hand-edit; edit gcp/grafana/queries.mjs and re-run build-grafana-bundle.mjs) -->";
const OUTCOME_END = "<!-- grafana-generated:end outcome-queries -->";
const outcomeBlock =
  `${OUTCOME_BEGIN}\n\`\`\`sql\n${DOCS_OUTCOME_BLOCK}\n\`\`\`\n${OUTCOME_END}`;

const VERIFY_BEGIN =
  "<!-- grafana-generated:begin verify-query (do not hand-edit; edit gcp/grafana/queries.mjs and re-run build-grafana-bundle.mjs) -->";
const VERIFY_END = "<!-- grafana-generated:end verify-query -->";
const verifyBlock =
  `${VERIFY_BEGIN}\n\`\`\`sql\n${README_VERIFY_SQL}\n\`\`\`\n${VERIFY_END}`;

function spliceBlock(text, block) {
  const begin = block.split("\n")[0];
  const end = block.split("\n").at(-1);
  const start = text.indexOf(begin);
  const endIdx = text.indexOf(end);
  if (start === -1) throw new Error(`begin marker missing: ${begin}`);
  if (endIdx === -1) throw new Error(`end marker missing: ${end}`);
  if (text.indexOf(begin, start + 1) !== -1)
    throw new Error(`begin marker not unique: ${begin}`);
  const after = text.indexOf("\n", endIdx);
  const head = text.slice(0, start);
  const tail = after === -1 ? "" : text.slice(after + 1);
  return `${head}${block}\n${tail}`;
}

// Deterministic JSON encoding matching the checked-in file: objects and
// object arrays expand one key/element per line (like JSON.stringify with
// indent 2), while scalar arrays (e.g. tags) stay on one line.
function encodeJson(value, indent) {
  if (Array.isArray(value)) {
    if (value.every((v) => v === null || typeof v !== "object"))
      return `[${value.map((v) => JSON.stringify(v)).join(", ")}]`;
    const pad = " ".repeat(indent + 2);
    const close = " ".repeat(indent);
    return `[\n${value.map((v) => `${pad}${encodeJson(v, indent + 2)}`).join(",\n")}\n${close}]`;
  }
  if (value !== null && typeof value === "object") {
    const pad = " ".repeat(indent + 2);
    const close = " ".repeat(indent);
    const entries = Object.entries(value).map(
      ([k, v]) => `${pad}${JSON.stringify(k)}: ${encodeJson(v, indent + 2)}`,
    );
    return `{\n${entries.join(",\n")}\n${close}}`;
  }
  return JSON.stringify(value);
}

const dashboardJson = `${encodeJson(dashboard, 0)}\n`;

if (process.argv[2] === undefined) {
  // In-place regeneration of the committed artifacts.
  writeFileSync(
    join(REPO, "gcp/grafana/dashboards/stratum-telemetry.json"),
    dashboardJson,
  );
  writeFileSync(
    join(REPO, "gcp/grafana/alerting/stratum-telemetry-alerts.yaml"),
    alertsYaml,
  );
  const docsPath = join(REPO, "docs/structured-logging.md");
  writeFileSync(docsPath, spliceBlock(readFileSync(docsPath, "utf8"), outcomeBlock));
  const readmePath = join(REPO, "gcp/grafana/README.md");
  writeFileSync(
    readmePath,
    spliceBlock(readFileSync(readmePath, "utf8"), verifyBlock),
  );
  console.log("Regenerated Grafana bundle from gcp/grafana/queries.mjs.");
} else {
  // Test mode (mirrors build-alert-policies.py): emit to outDir for comparison.
  mkdirSync(join(OUT, "dashboards"), { recursive: true });
  mkdirSync(join(OUT, "alerting"), { recursive: true });
  writeFileSync(join(OUT, "dashboards/stratum-telemetry.json"), dashboardJson);
  writeFileSync(join(OUT, "alerting/stratum-telemetry-alerts.yaml"), alertsYaml);
  writeFileSync(join(OUT, "outcome-queries-block.md"), `${outcomeBlock}\n`);
  writeFileSync(join(OUT, "readme-verify-block.md"), `${verifyBlock}\n`);
}

// STR-242 slice 3: structural validation for the Grafana as-code bundle.
// Run: node --test gcp/grafana/grafana.test.mjs (no dependencies).
// Config-shape validation, mirroring gcp/monitoring/monitoring.test.mjs:
// dashboard JSON must query the right series, alerts must name real rules
// with thresholds, and the README must carry the manual console steps plus
// the phase-2 entry criteria (ticket items 5-8).
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const dashboard = JSON.parse(readFileSync(join(DIR, "dashboards", "stratum-telemetry.json"), "utf8"));
const alerts = readFileSync(join(DIR, "alerting", "stratum-telemetry-alerts.yaml"), "utf8");
const readme = readFileSync(join(DIR, "README.md"), "utf8");

describe("dashboard", () => {
  it("has the stable uid, title, and ticket tag", () => {
    assert.equal(dashboard.uid, "stratum-telemetry");
    assert.match(dashboard.title, /Stratum telemetry/);
    assert.ok(dashboard.tags.includes("str-242"));
  });

  it("defines five panels with unique ids", () => {
    assert.equal(dashboard.panels.length, 5);
    const ids = dashboard.panels.map((p) => p.id).sort();
    assert.deepEqual(ids, [1, 2, 3, 4, 5]);
  });

  it("every panel queries the BigQuery datasource placeholder", () => {
    for (const panel of dashboard.panels) {
      assert.equal(panel.datasource, "${DS_BIGQUERY}", `panel ${panel.id} datasource`);
      assert.ok(panel.targets.length > 0, `panel ${panel.id} has a target`);
      assert.match(panel.targets[0].rawSql, /FROM `PROJECT\./, `panel ${panel.id} table ref`);
    }
  });

  it("outcome panels filter the collector sink and the OUTCOME severity", () => {
    for (const id of [1, 2, 4]) {
      const sql = dashboard.panels.find((p) => p.id === id).targets[0].rawSql;
      assert.match(sql, /jsonPayload\.source = 'stratum-client-log'/, `panel ${id} source filter`);
      assert.match(sql, /jsonPayload\.severity = 'OUTCOME'/, `panel ${id} severity filter`);
    }
  });

  it("counter panel reads the Analytics export exceptional event", () => {
    const sql = dashboard.panels.find((p) => p.id === 3).targets[0].rawSql;
    assert.match(sql, /event_name = 'exceptional_occurred'/);
    assert.match(sql, /UNNEST\(event_params\)/, "param unnest for boundary split");
  });

  it("Crashlytics panel is an explicit fill-in template", () => {
    const panel = dashboard.panels.find((p) => p.id === 5);
    assert.match(panel.title, /fill in/i);
    assert.match(panel.description, /enable the Crashlytics BigQuery export/i);
    assert.match(panel.targets[0].rawSql, /CRASHLYTICS_DATASET/);
  });

  it("contains no hardcoded project id", () => {
    const text = JSON.stringify(dashboard);
    assert.doesNotMatch(text, /stratum-a1a5b/);
  });
});

describe("phase-1 alerts", () => {
  it("is Grafana provisioning YAML with the three phase-1 rules", () => {
    assert.match(alerts, /apiVersion: 1/);
    for (const uid of [
      "stratum-expected-failure-burn",
      "stratum-collector-errors",
      "stratum-exceptional-rate-spike",
    ]) {
      assert.ok(alerts.includes(uid), `missing rule ${uid}`);
    }
  });

  it("states thresholds and the runbook for each rule", () => {
    assert.match(alerts, /3x.*baseline|min/i);
    assert.match(alerts, /5x.*baseline|min/i);
    assert.match(alerts, /gcp\/grafana\/README\.md#alerts/);
  });

  it("counter and collector rules query the documented exports", () => {
    assert.match(alerts, /event_name = 'exceptional_occurred'/);
    assert.match(alerts, /ingestClientLogs/);
  });
});

describe("runbook", () => {
  it("documents the manual console steps", () => {
    assert.match(readme, /BigQuery data source/);
    assert.match(readme, /Crashlytics BigQuery export/);
    assert.match(readme, /Analytics.*export/i);
  });

  it("states the phase-2 entry criteria", () => {
    assert.match(readme, /14-day parallel run/);
    assert.match(readme, /reconciliation/i);
    assert.match(readme, /retire Crashlytics alert rules/);
  });
});

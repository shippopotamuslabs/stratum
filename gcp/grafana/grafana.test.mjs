// STR-242 slice 3: structural validation for the Grafana as-code bundle.
// Run: node --test gcp/grafana/grafana.test.mjs (no dependencies).
// Config-shape validation, mirroring gcp/monitoring/monitoring.test.mjs:
// dashboard JSON must query the right series, alerts must name real rules
// with thresholds, and the README must carry the manual console steps plus
// the phase-2 entry criteria (ticket items 5-8). All BigQuery strings live
// exactly once in queries.mjs; build-grafana-bundle.mjs renders the artifacts
// below, and the generated-bundle suite fails if the checked-in files drift
// from a fresh generation.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
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

describe("provisioning correctness (slice-3 review H1-H3)", () => {
  it("every rule's condition names an existing data refId", () => {
    const blocks = alerts
      .split(/(?=-\s*uid:\s*stratum-)/g)
      .filter((b) => b.includes("uid:"));
    assert.equal(blocks.length, 3);
    for (const block of blocks) {
      const condition = block.match(/condition:\s*(\S+)/)[1];
      const refIds = [...block.matchAll(/refId:\s*(\S+)/g)].map((m) => m[1]);
      assert.ok(
        refIds.includes(condition),
        `condition '${condition}' matches a data refId (${refIds})`,
      );
    }
  });

  it("burn rule aggregates 5-minute buckets with the min-20 gate", () => {
    const burn = alerts.slice(alerts.indexOf("stratum-expected-failure-burn"));
    assert.match(burn, /DIV\(UNIX_SECONDS\(timestamp\), 5 \* 60\)/);
    assert.match(burn, /HAVING COUNT\(\*\) >= 20/);
  });

  it("every BigQuery table ref is three-part project.dataset.table", () => {
    for (const [name, text] of [
      ["dashboard", JSON.stringify(dashboard)],
      ["alerts", alerts],
    ]) {
      for (const m of text.matchAll(/FROM `([^`]+)`/g)) {
        assert.equal(
          m[1].split(".").length,
          3,
          `${name}: '${m[1]}' is not project.dataset.table`,
        );
      }
    }
  });

  it("collector rule uses CONTAINS_SUBSTR, not infix CONTAINS", () => {
    assert.match(
      alerts,
      /CONTAINS_SUBSTR\(jsonPayload\.message, 'ingestClientLogs'\)/,
    );
    assert.doesNotMatch(alerts, /message CONTAINS '/);
  });
});

describe("single placeholder vocabulary (slice-3 review M5)", () => {
  it("one datasource placeholder across dashboard and alerts", () => {
    assert.match(JSON.stringify(dashboard), /\$\{DS_BIGQUERY\}/);
    assert.match(alerts, /\$\{DS_BIGQUERY\}/);
    assert.doesNotMatch(alerts, /__BIGQUERY_DS_UID__/);
    assert.doesNotMatch(JSON.stringify(dashboard), /__BIGQUERY_DS_UID__/);
  });

  it("no retired dataset placeholders remain", () => {
    for (const text of [JSON.stringify(dashboard), alerts]) {
      assert.doesNotMatch(text, /CLIENT_LOG_DATASET/);
      assert.doesNotMatch(text, /FUNCTIONS_LOG_DATASET/);
    }
  });
});

describe("generated bundle (slice-3 review M4)", () => {
  it("checked-in artifacts match the builder output", () => {
    const tmp = mkdtempSync(join(tmpdir(), "grafana-bundle-"));
    execFileSync(process.execPath, [join(DIR, "build-grafana-bundle.mjs"), tmp], {
      encoding: "utf8",
    });
    for (const f of [
      "dashboards/stratum-telemetry.json",
      "alerting/stratum-telemetry-alerts.yaml",
    ]) {
      const expected = readFileSync(join(tmp, f), "utf8");
      const actual = readFileSync(join(DIR, f), "utf8");
      assert.equal(actual, expected, `${f} drifted from build-grafana-bundle.mjs output`);
    }
    const docs = readFileSync(
      join(DIR, "..", "..", "docs", "structured-logging.md"),
      "utf8",
    );
    assert.ok(
      docs.includes(readFileSync(join(tmp, "outcome-queries-block.md"), "utf8")),
      "docs outcome block drifted from build-grafana-bundle.mjs output",
    );
    assert.ok(
      readme.includes(readFileSync(join(tmp, "readme-verify-block.md"), "utf8")),
      "README verify block drifted from build-grafana-bundle.mjs output",
    );
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

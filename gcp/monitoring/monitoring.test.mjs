// STR-24: structural validation for the Phase-1 monitoring as-code config.
// Run: node --test gcp/monitoring/monitoring.test.mjs (no dependencies).
// This is config-shape validation, not app behavior: the Kotlin
// JUnit5/Turbine/fake stack in contracts/UNIT_TESTING.md does not apply to
// GCP JSON/gcloud config, so node:test is the fitting analog.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join, basename, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const metricFiles = readdirSync(join(DIR, "log-metrics")).filter((f) => f.endsWith(".json"));
const policyFiles = readdirSync(join(DIR, "alert-policies")).filter((f) => f.endsWith(".json"));
const load = (p) => JSON.parse(readFileSync(p, "utf8"));

const EXPECTED_METRICS = [
  "firestore_permission_denied_count",
  "solver_5xx_count",
  "synthesis_error_count",
];

describe("log-based metrics", () => {
  it("defines exactly the expected metric files", () => {
    assert.deepEqual(metricFiles.map((f) => basename(f, ".json")).sort(), [...EXPECTED_METRICS].sort());
  });

  for (const name of EXPECTED_METRICS) {
    it(`${name} is a counter with a service-scoped filter`, () => {
      const m = load(join(DIR, "log-metrics", `${name}.json`));
      assert.ok(m.description?.length > 20, "needs a meaningful description");
      assert.equal(m.metricDescriptor?.metricKind, "DELTA");
      assert.equal(m.metricDescriptor?.valueType, "INT64");
      assert.match(m.filter, /resource\.type=|protoPayload\.serviceName=|logName:/);
    });
  }

  it("firestore metric only matches denied data-access audit entries", () => {
    const m = load(join(DIR, "log-metrics", "firestore_permission_denied_count.json"));
    assert.match(m.filter, /cloudaudit/);
    assert.match(m.filter, /firestore\.googleapis\.com/);
    assert.match(m.filter, /protoPayload\.status\.code=7/); // 7 = PERMISSION_DENIED
  });

  it("synthesis metric covers both callables named in STR-24 plus the dispatcher", () => {
    const m = load(join(DIR, "log-metrics", "synthesis_error_count.json"));
    assert.match(m.filter, /startprogramsynthesis/);
    assert.match(m.filter, /getprogramsynthesisstatus/);
    assert.match(m.filter, /processprogramsynthesisjob/);
  });
});

describe("alert policies", () => {
  it("defines four policies", () => {
    assert.equal(policyFiles.length, 4);
  });

  for (const f of policyFiles) {
    it(`${f} has combiner, threshold condition, and channel placeholder`, () => {
      const p = load(join(DIR, "alert-policies", f));
      assert.ok(p.displayName?.startsWith("Stratum: "), "displayName namespaced for the console list");
      assert.equal(p.combiner, "OR");
      assert.equal(p.conditions?.length, 1);
      const c = p.conditions[0].conditionThreshold;
      assert.ok(c?.filter?.length > 0);
      assert.ok(["COMPARISON_GT", "COMPARISON_LT"].includes(c.comparison));
      assert.equal(typeof c.thresholdValue, "number");
      assert.match(c.duration, /^\d+s$/);
      assert.deepEqual(p.notificationChannels, ["{{CHANNEL}}"]);
      assert.ok(p.documentation?.content?.includes("STR-24"), "triage doc references the ticket");
    });
  }

  it("every user-metric alert references a metric defined in log-metrics/", () => {
    const defined = new Set(EXPECTED_METRICS);
    for (const f of policyFiles) {
      const p = load(join(DIR, "alert-policies", f));
      for (const cond of p.conditions) {
        const m = cond.conditionThreshold.filter.match(/logging\.googleapis\.com\/user\/([a-z_0-9]+)/);
        if (m) assert.ok(defined.has(m[1]), `${f} references unknown metric ${m[1]}`);
      }
    }
  });

  it("latency policy uses the built-in run metric with a p99 aligner", () => {
    const p = load(join(DIR, "alert-policies", "solver-latency-p99.json"));
    const c = p.conditions[0].conditionThreshold;
    assert.match(c.filter, /run\.googleapis\.com\/request_latencies/);
    assert.match(c.filter, /stratum-solver/);
    assert.equal(c.aggregations[0].perSeriesAligner, "ALIGN_PERCENTILE_99");
  });
});

describe("notification channel template", () => {
  it("is an email channel with an unsubstituted address placeholder", () => {
    const c = load(join(DIR, "notification-channel.json"));
    assert.equal(c.type, "email");
    assert.equal(c.labels?.email_address, "{{ALERT_EMAIL}}");
  });
});

describe("apply.sh drift guard", () => {
  const script = readFileSync(join(DIR, "apply.sh"), "utf8");

  it("iterates all config files instead of hardcoding names", () => {
    assert.match(script, /log-metrics\/\*\.json/);
    assert.match(script, /alert-policies\/\*\.json/);
  });

  it("is idempotent (describe-before-create, reuse channel, update policies)", () => {
    assert.match(script, /metrics describe/);
    assert.match(script, /metrics update/);
    assert.match(script, /policies update/);
  });

  it("wires the created channel into policies without hardcoding it", () => {
    assert.match(script, /notificationChannels/);
    assert.ok(!script.includes("{{CHANNEL}}"), "placeholder lives in policy files, not the script");
  });
});

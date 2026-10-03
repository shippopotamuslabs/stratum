// STR-24: structural validation for the Phase-1 monitoring as-code config.
// Run: node --test gcp/monitoring/monitoring.test.mjs (no dependencies).
// This is config-shape validation, not app behavior: the Kotlin
// JUnit5/Turbine/fake stack in contracts/UNIT_TESTING.md does not apply to
// GCP JSON/gcloud config, so node:test is the fitting analog.
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
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

  it("latency policy threshold is in milliseconds, not seconds", () => {
    const p = load(join(DIR, "alert-policies", "solver-latency-p99.json"));
    const c = p.conditions[0].conditionThreshold;
    // run.googleapis.com/request_latencies is a DISTRIBUTION measured in ms;
    // a seconds-scale value would fire on normal traffic (alert fatigue).
    assert.ok(
      c.thresholdValue >= 10000,
      `thresholdValue ${c.thresholdValue} looks like seconds, not milliseconds`,
    );
    assert.match(p.documentation.content, /millisecond/i, "doc notes the ms unit");
  });
});

describe("notification channel template", () => {
  it("is an email channel with an unsubstituted address placeholder", () => {
    const c = load(join(DIR, "notification-channel.json"));
    assert.equal(c.type, "email");
    assert.equal(c.labels?.email_address, "{{ALERT_EMAIL}}");
  });
});

describe("render pipeline contract", () => {
  // Behavior tests for json_render.py, the single JSON helper apply.sh uses.
  // These assert inputs/outputs, not script source text, so behavior-preserving
  // refactors keep passing.
  const render = (...args) =>
    execFileSync("python3", [join(DIR, "json_render.py"), ...args], {
      encoding: "utf8",
    }).trim();

  it("substitute replaces placeholders without shell escaping", () => {
    const tmp = mkdtempSync(join(tmpdir(), "render-"));
    const template = join(tmp, "template.json");
    writeFileSync(template, JSON.stringify({ labels: { email_address: "{{ALERT_EMAIL}}" } }));
    const out = join(tmp, "out.json");
    // '&', '|', '\' are sed-special; the helper must pass them through verbatim.
    render("substitute", template, out, "ALERT_EMAIL=a&b|c\\d@example.com");
    assert.deepEqual(load(out), { labels: { email_address: "a&b|c\\d@example.com" } });
  });

  it("set-channel injects the resolved channel into a policy", () => {
    const tmp = mkdtempSync(join(tmpdir(), "render-"));
    const out = join(tmp, "policy.json");
    const src = join(DIR, "alert-policies", "solver-5xx.json");
    render("set-channel", src, out, "projects/p/notificationChannels/123");
    const policy = load(out);
    assert.deepEqual(policy.notificationChannels, ["projects/p/notificationChannels/123"]);
    assert.equal(policy.displayName, "Stratum: solver 5xx spike");
  });

  it("display-name and find-policy round-trip for idempotent updates", () => {
    const src = join(DIR, "alert-policies", "solver-5xx.json");
    const display = render("display-name", src);
    assert.equal(display, "Stratum: solver 5xx spike");
    const tmp = mkdtempSync(join(tmpdir(), "render-"));
    const listed = join(tmp, "listed.json");
    writeFileSync(
      listed,
      JSON.stringify([
        { name: "projects/p/alertPolicies/1", displayName: "Stratum: solver 5xx spike" },
        { name: "projects/p/alertPolicies/2", displayName: "Something else" },
      ]),
    );
    assert.equal(render("find-policy", listed, display), "projects/p/alertPolicies/1");
    assert.equal(render("find-policy", listed, "Stratum: missing"), "");
  });

  it("find-channel locates the email channel by address", () => {
    const tmp = mkdtempSync(join(tmpdir(), "render-"));
    const listed = join(tmp, "channels.json");
    writeFileSync(
      listed,
      JSON.stringify([
        {
          name: "projects/p/notificationChannels/9",
          type: "email",
          labels: { email_address: "alerts@example.com" },
        },
        { name: "projects/p/notificationChannels/7", type: "webhook", labels: {} },
      ]),
    );
    assert.equal(
      render("find-channel", listed, "alerts@example.com"),
      "projects/p/notificationChannels/9",
    );
    assert.equal(render("find-channel", listed, "nobody@example.com"), "");
  });

  it("every checked-in policy renders through the pipeline", () => {
    const tmp = mkdtempSync(join(tmpdir(), "render-"));
    for (const f of policyFiles) {
      const out = join(tmp, f);
      render("set-channel", join(DIR, "alert-policies", f), out, "projects/p/notificationChannels/1");
      const policy = load(out); // throws on invalid JSON
      assert.deepEqual(policy.notificationChannels, ["projects/p/notificationChannels/1"]);
      assert.ok(
        render("display-name", join(DIR, "alert-policies", f)).startsWith("Stratum: "),
      );
    }
  });
});

describe("alert-policy builder", () => {
  it("checked-in policies match the builder output", () => {
    const tmp = mkdtempSync(join(tmpdir(), "builder-"));
    execFileSync("python3", [join(DIR, "build-alert-policies.py"), tmp], { encoding: "utf8" });
    for (const f of policyFiles) {
      const expected = readFileSync(join(tmp, f), "utf8");
      const actual = readFileSync(join(DIR, "alert-policies", f), "utf8");
      assert.equal(actual, expected, `${f} drifted from build-alert-policies.py output`);
    }
  });
});

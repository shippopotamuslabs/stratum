# Stratum Grafana telemetry (STR-242 slice 3)

Operational Grafana dashboards over BigQuery (vision answer 1). The OTel-shaped
outcome series, the bare `exceptional_occurred` counter, and the Crashlytics
issue feed converge here; Crashlytics stays the capture mechanism.

## Files

| File | Purpose |
|------|---------|
| `dashboards/stratum-telemetry.json` | Dashboard import: outcome volume/rate, counter rate, pipeline freshness, Crashlytics velocity (template) |
| `alerting/stratum-telemetry-alerts.yaml` | Phase-1 alert rules (file-provisioning format): burn, collector errors, exceptional spike |
| `grafana.test.mjs` | Structural validation (`node --test gcp/grafana/grafana.test.mjs`, no dependencies) |
| `queries.mjs` | Canonical SQL source: every BigQuery string in the dashboard, alerts, and docs query blocks is defined exactly once here |
| `build-grafana-bundle.mjs` | Generator: renders `queries.mjs` into the dashboard JSON, alerts YAML, and docs query blocks (`node gcp/grafana/build-grafana-bundle.mjs`, no dependencies) |

## Placeholders (single vocabulary)

One datasource placeholder and one dataset scheme across the dashboard JSON,
the alerts YAML, and the verify queries below — no per-file dialects.
Substitution is mechanical: replace each placeholder with the real value at
import time.

| Placeholder | Replace with |
|-------------|--------------|
| `${DS_BIGQUERY}` | Grafana BigQuery datasource UID |
| `PROJECT.DATASET.client_log_sink` | Outcome sink destination (STR-28 setup) |
| `PROJECT.DATASET.all_app_logs` | Functions/app log sink (collector-error alert) |
| `PROJECT.analytics_PROPERTY` | `PROJECT.analytics_<propertyId>` (Analytics export) |
| `PROJECT.CRASHLYTICS_DATASET.CRASHLYTICS_TABLE` | Crashlytics export dataset/table (panel 5) |

## Setup (manual console steps — not automatable by CI)

### 1. BigQuery data source in Grafana

Grafana Connections → Add data source → Google BigQuery. Authenticate with a
GCP service account that has `roles/bigquery.dataViewer` on the project
datasets (least privilege; no data editor). Note the datasource UID, then
replace every `${DS_BIGQUERY}` in the dashboard JSON and the alerts YAML
with it.

Verify: run panel 1's query in the BigQuery console first with the
`PROJECT.DATASET.client_log_sink` placeholder replaced (sink destination from
the STR-28 setup). Expected: rows with `outcome` in
(`success`, `expected_failure`) once slice 1+2 builds ship.

### 2. Firebase Analytics BigQuery export (counter rate)

Firebase console → Project settings → Integrations → BigQuery → enable the
Analytics export if not already on. Tables appear as
`analytics_<property>.events_YYYYMMDD` within a day. Replace
`PROJECT.analytics_PROPERTY` in panel 3 and the spike alert with the real
`PROJECT.analytics_<property>` prefix.

Verify:

<!-- grafana-generated:begin verify-query (do not hand-edit; edit gcp/grafana/queries.mjs and re-run build-grafana-bundle.mjs) -->
```sql
SELECT event_name, COUNT(*) FROM `PROJECT.analytics_PROPERTY.events_*`
WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
AND event_name = 'exceptional_occurred' GROUP BY 1;
```
<!-- grafana-generated:end verify-query -->

### 3. Crashlytics BigQuery export (exception stream)

Firebase console → Project settings → Integrations → BigQuery → enable the
Crashlytics export (Blaze plan, already in use). Export latency is minutes, not
seconds — fine for crash alerting; the OTel stream covers the fast path.

After enabling, list the dataset tables, then fill in panel 5's
`CRASHLYTICS_DATASET`/`CRASHLYTICS_TABLE`/columns (issue id, title, event and
user counts) and delete the TEMPLATE marker from the panel description.

## Alerts

Phase-1 rules (only Grafana can express volume/burn; Crashlytics rules stay
live in parallel per the phase plan):

- **Expected-failure burn** (warning): 5-minute failure ratio per use case over
  3x its trailing-hour baseline, minimum 20 outcomes/5m so tiny denominators
  never page. A spike means something systemic in that flow — triage the flow,
  not a single user.
- **Collector errors** (critical): any `ingestClientLogs` callable ERROR in
  15 minutes. The collector is the single export path; its errors mean outcomes
  are dropping, not that the app is broken.
- **Exceptional spike** (critical): `exceptional_occurred` 15-minute rate over
  5x its 24-hour baseline, minimum 5 events/15m. Pivot to the Crashlytics
  issue feed (panel 5) for the grouped stack.

Thresholds are starting points for current beta volume; tighten them once the
dashboard shows real baselines. Keep alert queries narrow and aggregated so
evaluation cost stays trivial.

## Phase-2 entry criteria (ticket item 8)

Grafana alerts run parallel to the Crashlytics rules for a defined window
before any Crashlytics rule retires:

1. Minimum 14-day parallel run with both surfaces live.
2. Daily reconciliation: Crashlytics issue event counts vs the
   `exceptional_occurred` counter and outcome series, joined on app version +
   time window (+ class/boundary). Unexplained divergence under 1% of events.
3. The burn and spike alerts each fire-or-would-fire at least once on real
   traffic (no dry-run-only proof).
4. Only then: retire Crashlytics alert rules (capture stays — the SDK keeps
   recording; it stops paging). Document the retirement in
   `docs/crash-alerts-runbook.md`.

## Cost notes

BigQuery alert queries stay narrow and aggregated; dashboard default window is
24h. The log sink, Analytics export, and Crashlytics export all ride the Blaze
plan the projects already run on.

# STR-24 — Backend failure alerts, Phase 1 (email via Cloud Monitoring)

As-code config for GCP alerting on backend failures. Motivating incident:
STR-23 (Firestore sync `PERMISSION_DENIED`, 115 Crashlytics events over 7 days,
nothing paged because data-access audit logs were off and no alerts existed).

## What this provisions

| # | Signal | Mechanism | Threshold |
|---|--------|-----------|-----------|
| 1 | Firestore `PERMISSION_DENIED` | Log-based metric `firestore_permission_denied_count` (from data-access audit logs) + alert | Any denied request in 5 min |
| 2 | `stratum-solver` HTTP 5xx | Log-based metric `solver_5xx_count` + alert | Any 5xx in 5 min |
| 3 | Synthesis function errors (`startProgramSynthesis`, `getProgramSynthesisStatus`, `processProgramSynthesisJob`) | Log-based metric `synthesis_error_count` + alert | >= 2 ERRORs in 5 min (1 is noise: quota/invalid-arg rejections log at ERROR) |
| 4 | `stratum-solver` p99 latency | Built-in metric `run.googleapis.com/request_latencies`, no custom metric needed + alert | p99 > 120s over 10 min (120s = CP-SAT deadline) |
| 5 | Client crash/ANR spikes | Crashlytics velocity + new-issue emails | Console toggles (manual step 3) |

All four Cloud Monitoring policies notify one email channel
(`notification-channel.json` + `ALERT_EMAIL` at apply time).

## Apply (as-code, safe to re-run)

```bash
gcloud auth login
gcloud config set project stratum-a1a5b

PROJECT=stratum-a1a5b ALERT_EMAIL=you@example.com ./gcp/monitoring/apply.sh
```

`apply.sh` is idempotent: metrics are created-or-updated, the email channel is
reused when one already exists for the address, and policies are matched by
display name (`Stratum: ...`) and updated in place (IDs preserved).

Validate without touching GCP:

```bash
node --test gcp/monitoring/monitoring.test.mjs
bash -n gcp/monitoring/apply.sh
```

## Manual steps (need IAM admin / console clicks — NOT attempted by automation)

### 1. Enable Firestore data-access audit logs (project IAM admin)

Data-access audit logs are off by default, which is why the STR-23 denials were
invisible in Cloud Logging. Requires `resourcemanager.projects.setIamPolicy`
(e.g. Project IAM Admin or Owner). Read the current policy, add the
`auditConfigs` block, write it back:

```bash
PROJECT=stratum-a1a5b
gcloud projects get-iam-policy "$PROJECT" --format=json > /tmp/iam.json
python3 - "$PROJECT" <<'PY'
import json, sys
project = sys.argv[1]
p = json.load(open("/tmp/iam.json"))
audits = p.setdefault("auditConfigs", [])
firestore = next((a for a in audits if a.get("service") == "firestore.googleapis.com"), None)
if firestore is None:
    audits.append({"service": "firestore.googleapis.com", "auditLogConfigs": []})
    firestore = audits[-1]
have = {(c.get("logType"), c.get("exemptedMembers", None) and tuple(c["exemptedMembers"]) or ()) for c in firestore.get("auditLogConfigs", [])}
for log_type in ("DATA_READ", "DATA_WRITE"):
    if not any(h[0] == log_type for h in have):
        firestore.setdefault("auditLogConfigs", []).append({"logType": log_type})
json.dump(p, open("/tmp/iam-patched.json", "w"), indent=2)
print("wrote /tmp/iam-patched.json")
PY
gcloud projects set-iam-policy "$PROJECT" /tmp/iam-patched.json
```

Notes:

- Scoped to `firestore.googleapis.com` only (not `allServices`), to bound log
  volume and cost. Admin reads/writes (`ADMIN_READ`/`ADMIN_WRITE`) stay on.
- Writes to user Firestore paths are low-volume (sync uploads, synthesis jobs),
  so `DATA_WRITE` cost is negligible. `DATA_READ` covers `Listen` streams.
- Verify: after the next denied write,
  `protoPayload.serviceName="firestore.googleapis.com" protoPayload.status.code=7`
  must return rows in Cloud Logging; the `firestore_permission_denied_count`
  metric starts counting only from enablement (no backfill).

### 2. Verify the email notification channel

Google sends a verification email to `ALERT_EMAIL` when the channel is created.
**Click the verification link** — unverified channels silently drop alerts.
Check: Monitoring → Alerting → Edit notification channels → verified checkmark.

### 3. Crashlytics velocity + new-issue emails (Firebase console)

Console-only, already documented with screenshots path in
`docs/crash-alerts-runbook.md` §2 — follow that checklist
(new fatal, new non-fatal, new ANR, velocity alerts for `com.aether.stratum`).

## Verify after apply

```bash
PROJECT=stratum-a1a5b
gcloud logging metrics list --project="$PROJECT" --filter='name:firestore_permission_denied_count OR name:solver_5xx_count OR name:synthesis_error_count' --format='value(name)'
gcloud monitoring policies list --project="$PROJECT" --filter='displayName:"Stratum:"' --format='table(displayName, enabled)'
```

Expected: 3 metrics, 4 enabled policies. Trigger a test incident by writing to
a Firestore path the rules deny, then confirm an email arrives within ~10 min.

## Out of scope (per STR-24)

PagerDuty/phone escalation (Phase 2, when paying users exist), Grafana/OTel,
custom dashboards. Cost: log-based metrics, 4 alert policies, and one email
channel are all within the free tier at this volume.

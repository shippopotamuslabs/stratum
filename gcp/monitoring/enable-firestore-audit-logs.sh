#!/usr/bin/env bash
# STR-24 manual step 1: enable Firestore data-access audit logs.
#
# Data-access audit logs are off by default, which is why the STR-23 denials
# were invisible in Cloud Logging. Requires `resourcemanager.projects.setIamPolicy`
# (e.g. Project IAM Admin or Owner).
#
# Usage: PROJECT=stratum-a1a5b ./gcp/monitoring/enable-firestore-audit-logs.sh
#
# Idempotent: merges DATA_READ/DATA_WRITE into the existing auditConfigs for
# firestore.googleapis.com without duplicating entries. Scoped to
# firestore.googleapis.com only (not allServices) to bound log volume and
# cost; admin reads/writes (ADMIN_READ/ADMIN_WRITE) stay on.
#
# Verify after running: after the next denied write,
#   protoPayload.serviceName="firestore.googleapis.com" protoPayload.status.code=7
# must return rows in Cloud Logging.

set -euo pipefail

PROJECT="${PROJECT:?set PROJECT, e.g. PROJECT=stratum-a1a5b}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

gcloud projects get-iam-policy "$PROJECT" --format=json > "$TMP/iam.json"
python3 - "$TMP/iam.json" "$TMP/iam-patched.json" <<'PY'
import json, sys
src, dst = sys.argv[1], sys.argv[2]
p = json.load(open(src))
audits = p.setdefault("auditConfigs", [])
firestore = next((a for a in audits if a.get("service") == "firestore.googleapis.com"), None)
if firestore is None:
    audits.append({"service": "firestore.googleapis.com", "auditLogConfigs": []})
    firestore = audits[-1]
have = {(c.get("logType"), c.get("exemptedMembers", None) and tuple(c["exemptedMembers"]) or ()) for c in firestore.get("auditLogConfigs", [])}
for log_type in ("DATA_READ", "DATA_WRITE"):
    if not any(h[0] == log_type for h in have):
        firestore.setdefault("auditLogConfigs", []).append({"logType": log_type})
json.dump(p, open(dst, "w"), indent=2)
print("wrote", dst)
PY
gcloud projects set-iam-policy "$PROJECT" "$TMP/iam-patched.json"

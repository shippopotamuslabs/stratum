#!/usr/bin/env bash
# STR-24 Phase 1: create/update log-based metrics, the email notification
# channel, and alert policies in a GCP project.
#
# Usage:
#   PROJECT=stratum-a1a5b ALERT_EMAIL=you@example.com ./gcp/monitoring/apply.sh
#
# Idempotent: re-running updates in place (policies keep their IDs).
# Requires: gcloud (with monitoring/logging permissions), python3.
# Does NOT touch IAM audit config (admin step, see README.md step 1) and does
# NOT click email-channel verification or Crashlytics toggles (manual steps).

set -euo pipefail

PROJECT="${PROJECT:?set PROJECT, e.g. PROJECT=stratum-a1a5b}"
ALERT_EMAIL="${ALERT_EMAIL:?set ALERT_EMAIL to the alert recipient address}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== log-based metrics =="
for f in "$DIR"/log-metrics/*.json; do
  name="$(basename "$f" .json)"
  if gcloud logging metrics describe "$name" --project="$PROJECT" >/dev/null 2>&1; then
    gcloud logging metrics update "$name" --project="$PROJECT" --config-from-file="$f"
    echo "updated metric $name"
  else
    gcloud logging metrics create "$name" --project="$PROJECT" --config-from-file="$f"
    echo "created metric $name"
  fi
done

echo "== notification channel =="
CHANNEL_JSON="$TMP/channel.json"
ESCAPED_EMAIL="$(printf "%s" "$ALERT_EMAIL" | sed "s|[&|\\]|\\&|g")"
sed "s|{{ALERT_EMAIL}}|$ESCAPED_EMAIL|" "$DIR/notification-channel.json" > "$CHANNEL_JSON"
CHANNEL="$(gcloud beta monitoring channels list --project="$PROJECT" --format=json \
  | python3 -c 'import json,sys; print(next((c["name"] for c in json.load(sys.stdin) if c.get("type") == "email" and c.get("labels", {}).get("email_address") == sys.argv[1]), ""))' "$ALERT_EMAIL")"
if [ -z "$CHANNEL" ]; then
  CHANNEL="$(gcloud beta monitoring channels create --project="$PROJECT" --channel-content-from-file="$CHANNEL_JSON" --format='value(name)')"
  echo "created channel $CHANNEL"
  echo "NOTE: Gmail/Workspace addresses must click the verification email before alerts flow (README.md step 2)."
else
  echo "reusing channel $CHANNEL"
fi

echo "== alert policies =="
POLICIES_JSON="$TMP/policies.json"
gcloud monitoring policies list --project="$PROJECT" --format=json > "$POLICIES_JSON"
for f in "$DIR"/alert-policies/*.json; do
  display="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["displayName"]' "$f")"
  POLICY_JSON="$TMP/policy.json"
  python3 - "$CHANNEL" "$f" "$POLICY_JSON" <<'PY'
import json, sys
channel, src, dst = sys.argv[1], sys.argv[2], sys.argv[3]
policy = json.load(open(src))
policy["notificationChannels"] = [channel]
json.dump(policy, open(dst, "w"), indent=2)
PY
  existing="$(python3 -c 'import json,sys; print(next((p["name"] for p in json.load(open(sys.argv[1])) if p.get("displayName") == sys.argv[2]), ""))' "$POLICIES_JSON" "$display")"
  if [ -z "$existing" ]; then
    gcloud monitoring policies create --project="$PROJECT" --policy-from-file="$POLICY_JSON" --format='value(name)'
    echo "created policy: $display"
  else
    gcloud monitoring policies update "$existing" --project="$PROJECT" --policy-from-file="$POLICY_JSON" >/dev/null
    echo "updated policy: $display ($existing)"
  fi
done

echo "done."

#!/usr/bin/env bash
# STR-24 Phase 1: create/update log-based metrics, the email notification
# channel, and alert policies in a GCP project.
#
# Usage:
#   PROJECT=stratum-a1a5b ALERT_EMAIL=you@example.com ./gcp/monitoring/apply.sh
#
# Idempotent: re-running updates in place (policies keep their IDs).
# Requires: gcloud (with monitoring/logging permissions), python3.
# Does NOT touch IAM audit config (admin step, see README.md step 1 and
# enable-firestore-audit-logs.sh) and does NOT click email-channel
# verification or Crashlytics toggles (manual steps).
#
# All JSON templating/lookup goes through json_render.py (single helper) so
# no sed escaping or inline python one-liners live in this script.

set -euo pipefail

PROJECT="${PROJECT:?set PROJECT, e.g. PROJECT=stratum-a1a5b}"
ALERT_EMAIL="${ALERT_EMAIL:?set ALERT_EMAIL to the alert recipient address}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RENDER=(python3 "$DIR/json_render.py")
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
"${RENDER[@]}" substitute "$DIR/notification-channel.json" "$CHANNEL_JSON" "ALERT_EMAIL=$ALERT_EMAIL"
CHANNELS_JSON="$TMP/channels.json"
gcloud beta monitoring channels list --project="$PROJECT" --format=json > "$CHANNELS_JSON"
CHANNEL="$("${RENDER[@]}" find-channel "$CHANNELS_JSON" "$ALERT_EMAIL")"
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
  display="$("${RENDER[@]}" display-name "$f")"
  POLICY_JSON="$TMP/policy.json"
  "${RENDER[@]}" set-channel "$f" "$POLICY_JSON" "$CHANNEL"
  existing="$("${RENDER[@]}" find-policy "$POLICIES_JSON" "$display")"
  if [ -z "$existing" ]; then
    gcloud monitoring policies create --project="$PROJECT" --policy-from-file="$POLICY_JSON" --format='value(name)'
    echo "created policy: $display"
  else
    gcloud monitoring policies update "$existing" --project="$PROJECT" --policy-from-file="$POLICY_JSON" >/dev/null
    echo "updated policy: $display ($existing)"
  fi
done

echo "done."

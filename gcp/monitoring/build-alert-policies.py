#!/usr/bin/env python3
"""Generate gcp/monitoring/alert-policies/*.json from one shared envelope (STR-24).

The four alert policies share an identical envelope -- combiner, a single
threshold condition shape, the notificationChannels placeholder, and
userLabels -- and differ only in display names, docs, filters, thresholds,
durations, and aligners. Editing the envelope in four files by hand invites
skew, so the envelope lives here exactly once.

Workflow: edit POLICIES below, run `./gcp/monitoring/build-alert-policies.py`,
commit the regenerated JSON. The checked-in files are build output;
monitoring.test.mjs fails if they drift from what this script emits.

Note on units: `run.googleapis.com/request_latencies` is a DISTRIBUTION
measured in **milliseconds**, so the solver p99 policy's thresholdValue is
120000 (= 120s CP-SAT deadline), not 120.
"""

import json
import re
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
OUT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else DIR / "alert-policies"

POLICIES = [
    {
        "file": "firestore-permission-denied.json",
        "displayName": "Stratum: Firestore PERMISSION_DENIED spike",
        "doc": (
            "Firestore denied N>=1 requests in 5 min (audit-log metric "
            "`firestore_permission_denied_count`). Triage: Cloud Logging filter "
            '`protoPayload.serviceName="firestore.googleapis.com" protoPayload.status.code=7`, '
            "check the failing document path against `firestore.rules`. "
            "Context: STR-23/STR-24, deploy runbook `docs/cloud-program-synthesis-deploy.md`."
        ),
        "conditionName": "Any Firestore PERMISSION_DENIED in 5 min",
        "filter": 'metric.type="logging.googleapis.com/user/firestore_permission_denied_count" AND resource.type="audited_resource"',
        "thresholdValue": 0,
        "durationSecs": 60,
        "alignmentSecs": 300,
        "aligner": "ALIGN_SUM",
    },
    {
        "file": "solver-5xx.json",
        "displayName": "Stratum: solver 5xx spike",
        "doc": (
            "Cloud Run `stratum-solver` returned N>=1 HTTP 5xx in 5 min. Triage: "
            "`gcloud run services logs read stratum-solver --region=us-central1 "
            "--project=stratum-a1a5b --limit=50`, check OOM/timeout (CP-SAT deadline "
            "120s, timeout 180s). Context: STR-24, "
            "`docs/cloud-program-synthesis-deploy.md` section 7."
        ),
        "conditionName": "Any solver 5xx in 5 min",
        "filter": 'metric.type="logging.googleapis.com/user/solver_5xx_count" AND resource.type="cloud_run_revision"',
        "thresholdValue": 0,
        "durationSecs": 60,
        "alignmentSecs": 300,
        "aligner": "ALIGN_SUM",
    },
    {
        "file": "solver-latency-p99.json",
        "displayName": "Stratum: solver p99 latency high",
        "doc": (
            "Cloud Run `stratum-solver` p99 request latency exceeded 120s over 10 min "
            "(built-in metric `run.googleapis.com/request_latencies`, a DISTRIBUTION "
            "measured in **milliseconds** -- `thresholdValue` below is `120000`, i.e. "
            "120s). 120s is the CP-SAT deadline; sustained p99 above it means solves "
            "are timing out against the 180s request timeout. Triage: Cloud Run logs "
            "for the service, check CPU throttling and cold starts (min-instances=0). "
            "Context: STR-24, `docs/cloud-program-synthesis-deploy.md` operational constants."
        ),
        "conditionName": "Solver p99 latency > 120s over 10 min",
        "filter": (
            'metric.type="run.googleapis.com/request_latencies" '
            'AND resource.type="cloud_run_revision" '
            'AND resource.labels.service_name="stratum-solver"'
        ),
        # Milliseconds: run.googleapis.com/request_latencies reports ms.
        "thresholdValue": 120000,
        "durationSecs": 600,
        "alignmentSecs": 600,
        "aligner": "ALIGN_PERCENTILE_99",
    },
    {
        "file": "synthesis-errors.json",
        "displayName": "Stratum: synthesis function errors",
        "doc": (
            "Program-synthesis Functions logged N>=2 ERRORs in 5 min "
            "(`synthesis_error_count`: startProgramSynthesis, getProgramSynthesisStatus, "
            "processProgramSynthesisJob). Threshold is 2, not 1, because expected client "
            "errors (monthly quota, invalid args) also log at ERROR. If the errors are "
            "all quota/invalid-argument rejections, treat as noise and check abuse instead "
            "of paging. Otherwise triage the dispatcher/solver path per "
            "`docs/cloud-program-synthesis-deploy.md` section 7. Context: STR-24."
        ),
        "conditionName": "Synthesis errors >= 2 in 5 min",
        "filter": 'metric.type="logging.googleapis.com/user/synthesis_error_count" AND resource.type="cloud_run_revision"',
        "thresholdValue": 1,
        "durationSecs": 60,
        "alignmentSecs": 300,
        "aligner": "ALIGN_SUM",
    },
]


def build(spec):
    """Render one policy: shared envelope + per-policy condition."""
    return {
        "displayName": spec["displayName"],
        "documentation": {"mimeType": "text/markdown", "content": spec["doc"]},
        "combiner": "OR",
        "conditions": [
            {
                "displayName": spec["conditionName"],
                "conditionThreshold": {
                    "filter": spec["filter"],
                    "comparison": "COMPARISON_GT",
                    "thresholdValue": spec["thresholdValue"],
                    "duration": f'{spec["durationSecs"]}s',
                    "aggregations": [
                        {
                            "alignmentPeriod": f'{spec["alignmentSecs"]}s',
                            "perSeriesAligner": spec["aligner"],
                        }
                    ],
                    "trigger": {"count": 1},
                },
            }
        ],
        "notificationChannels": ["{{CHANNEL}}"],
        "userLabels": {"stratum": "phase1", "ticket": "str-24"},
    }


def _compact(rendered):
    """Keep tiny containers on one line, preserving the hand-written layout
    the checked-in files had before the builder existed (byte-for-byte for
    unchanged policies). The builder owns the input shape, so these patterns
    are deterministic."""
    rendered = re.sub(r'\{\n\s*"count": 1\n\s*\}', '{ "count": 1 }', rendered)
    rendered = re.sub(r'\[\n\s*"\{\{CHANNEL\}\}"\n\s*\]', '["{{CHANNEL}}"]', rendered)
    rendered = re.sub(
        r'\{\n\s*"stratum": "phase1",\n\s*"ticket": "str-24"\n\s*\}',
        '{ "stratum": "phase1", "ticket": "str-24" }',
        rendered,
    )
    return rendered


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for spec in POLICIES:
        path = OUT_DIR / spec["file"]
        rendered = _compact(json.dumps(build(spec), indent=2) + "\n")
        path.write_text(rendered, encoding="utf-8")
        print(f"wrote {path}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Export a program-generation payload JSON from a pulled Stratum Room database."""

from __future__ import annotations

import argparse
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

MUSCLES = [
    "Chest",
    "Back",
    "Quad",
    "Hamstring",
    "Glute",
    "PosteriorDeltoid",
    "LateralDeltoid",
    "AnteriorDeltoid",
    "Tricep",
    "Bicep",
    "Trap",
    "Calf",
    "Forearm",
    "Ab",
    "LowerBack",
    "Abductor",
    "Adductor",
]

LANDMARKS = [
    {"muscle": "Chest", "estimatedMev": 8, "estimatedMav": 14, "estimatedMrv": 20, "estimatedPv": 11},
    {"muscle": "Back", "estimatedMev": 8, "estimatedMav": 14, "estimatedMrv": 20, "estimatedPv": 11},
    {"muscle": "Quad", "estimatedMev": 8, "estimatedMav": 14, "estimatedMrv": 20, "estimatedPv": 11},
    {"muscle": "Hamstring", "estimatedMev": 6, "estimatedMav": 10, "estimatedMrv": 16, "estimatedPv": 8},
    {"muscle": "Glute", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "Tricep", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "Bicep", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "LateralDeltoid", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "PosteriorDeltoid", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "AnteriorDeltoid", "estimatedMev": 4, "estimatedMav": 8, "estimatedMrv": 14, "estimatedPv": 6},
    {"muscle": "Calf", "estimatedMev": 6, "estimatedMav": 10, "estimatedMrv": 16, "estimatedPv": 8},
    {"muscle": "Ab", "estimatedMev": 2, "estimatedMav": 6, "estimatedMrv": 12, "estimatedPv": 4},
]


def decode_muscles(bitmask: int) -> list[str]:
    out: list[str] = []
    for index, muscle in enumerate(MUSCLES):
        if bitmask & (1 << index):
            out.append(muscle)
    return out


def pull_db(device_db: Path) -> None:
    device_db.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.check_output(
        ["adb", "exec-out", "run-as", "com.stratum", "cat", "databases/stratum.db"],
    )
    device_db.write_bytes(data)


def load_proven_ids(cur: sqlite3.Cursor) -> set[str]:
    row = cur.execute("SELECT provenLiftIdsCsv FROM import_analysis_metadata LIMIT 1").fetchone()
    if not row or not row[0]:
        return set()
    return {part.strip() for part in row[0].split(",") if part.strip()}


def load_insights(cur: sqlite3.Cursor) -> dict[str, dict]:
    rows = cur.execute(
        """
        SELECT liftId, sessionCount, estimatedOneRm, fatigueDecaySlope, fatigueDecayIntercept, progressTrend
        FROM lift_training_insights
        """,
    ).fetchall()
    out: dict[str, dict] = {}
    for lift_id, session_count, e1rm, slope, intercept, trend in rows:
        insights: dict[str, object] = {"sessionCount": session_count}
        if e1rm is not None:
            insights["estimatedOneRm"] = e1rm
        if slope is not None:
            insights["fatigueDecaySlope"] = slope
        if intercept is not None:
            insights["fatigueDecayIntercept"] = intercept
        if trend is not None:
            insights["progressTrend"] = trend
        out[lift_id] = insights
    return out


def build_payload(db_path: Path) -> dict:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    profile = cur.execute(
        "SELECT trainingAge, lastTrained, sessionLengthMinutes, weekdays FROM user_training_profile LIMIT 1",
    ).fetchone()
    if not profile:
        raise SystemExit("user_training_profile row missing")
    training_age, last_trained, session_minutes, weekdays_csv = profile
    weekdays = [part.strip() for part in (weekdays_csv or "").split(",") if part.strip()]
    specialization = {
        muscle: level for muscle, level in cur.execute("SELECT muscle, level FROM specialization_preference")
    }
    proven_ids = load_proven_ids(cur)
    insights_by_lift = load_insights(cur)
    lifts = cur.execute(
        """
        SELECT id, name, movementPattern, primaryMusclesBitmask, secondaryMusclesBitmask
        FROM lifts
        WHERE enabled = 1
        ORDER BY name
        """,
    ).fetchall()

    proven: list[dict] = []
    available: list[dict] = []
    for lift_id, name, pattern, primary_mask, secondary_mask in lifts:
        entry = {
            "id": lift_id,
            "name": name,
            "movementPattern": pattern,
            "primaryMuscles": decode_muscles(primary_mask),
            "secondaryMuscles": decode_muscles(secondary_mask),
        }
        insight = insights_by_lift.get(lift_id)
        if insight:
            entry["trainingInsights"] = insight
        if lift_id in proven_ids and insight:
            entry["proven"] = True
            proven.append(entry)
        else:
            available.append(entry)

    return {
        "profile": {
            "trainingAge": training_age,
            "lastTrained": last_trained,
            "sessionLengthMinutes": session_minutes,
            "weekdays": ",".join(weekdays),
            "trainingDaysPerWeek": len(weekdays),
        },
        "specialization": specialization,
        "provenLifts": proven,
        "availableLifts": available,
        "literatureVolumeLandmarks": LANDMARKS,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, help="Path to stratum.db (default: pull from emulator)")
    parser.add_argument("--out", type=Path, required=True, help="Output payload JSON path")
    parser.add_argument("--pull", action="store_true", help="Pull stratum.db from connected emulator/device")
    args = parser.parse_args()

    db_path = args.db or Path("/tmp/stratum-smoke/stratum.db")
    if args.pull or args.db is None:
        print(f"Pulling database to {db_path}", file=sys.stderr)
        pull_db(db_path)

    payload = build_payload(db_path)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "enabledLifts": len(payload["provenLifts"]) + len(payload["availableLifts"]),
                "provenLifts": len(payload["provenLifts"]),
                "availableLifts": len(payload["availableLifts"]),
                "trainingDaysPerWeek": payload["profile"]["trainingDaysPerWeek"],
                "specialization": payload["specialization"],
                "out": str(args.out),
            },
            indent=2,
        ),
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()

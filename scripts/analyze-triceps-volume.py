#!/usr/bin/env python3
"""Approximate Stratum fractional triceps weekly volume from export files."""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

PRIMARY = 1.0
SECONDARY = 0.5


def normalize_name(name: str) -> str:
    name = re.sub(r"\s*∈\s*SS\d+", "", name, flags=re.I)
    name = re.sub(r"\s+", " ", name.strip().lower())
    return name


def guess_pattern(name: str) -> str | None:
    n = normalize_name(name)
    if any(x in n for x in ("curl", "preacher")):
        return "ElbowFlexion"
    if any(x in n for x in ("tricep", "skull crusher", "skullcrusher", "pushdown")):
        return "ElbowExtension"
    if any(x in n for x in ("pulldown", "pullup", "pull up", "chinup", "chin up")):
        return "VerticalPull"
    if any(x in n for x in ("bench press", "benchpress", "pushup", "push up", "dip", "fly")):
        return "HorizontalPress"
    if "incline press" in n or "incline bench" in n:
        return "InclinePress"
    if any(x in n for x in ("overhead press", "ohp", "shoulder press", "military press")):
        return "VerticalPress"
    return "Other"


def primary_muscles(pattern: str) -> set[str]:
    return {
        "ElbowExtension": {"Tricep"},
        "HorizontalPress": {"Chest"},
        "InclinePress": {"Chest"},
        "VerticalPress": {"AnteriorDeltoid"},
        "ElbowFlexion": {"Bicep"},
        "VerticalPull": {"Back"},
        "HorizontalPull": {"Back"},
    }.get(pattern, set())


def secondary_muscles(pattern: str, primary: set[str]) -> set[str]:
    sec: set[str] = set()
    if pattern in ("HorizontalPull", "VerticalPull") and "Bicep" not in primary:
        sec.add("Bicep")
    if pattern in ("HorizontalPress", "VerticalPress", "InclinePress") and "Tricep" not in primary:
        sec.add("Tricep")
    return sec - primary


def week_start(dt: datetime) -> datetime:
    # Monday-start week (ISO), matching typical JVM WeekFields
    start = dt.date() - timedelta(days=dt.weekday())
    return datetime.combine(start, datetime.min.time())


def credit_triceps(pattern: str) -> float:
    primary = primary_muscles(pattern)
    secondary = secondary_muscles(pattern, primary)
    if "Tricep" in primary:
        return PRIMARY
    if "Tricep" in secondary:
        return SECONDARY
    return 0.0


def parse_hevy(path: Path) -> list[tuple[datetime, str, float]]:
    rows: list[tuple[datetime, str, float]] = []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            title = row.get("exercise_title", "")
            if not title:
                continue
            set_type = (row.get("set_type") or "normal").lower()
            if set_type in ("warmup", "warm up", "failure"):
                continue
            dt = datetime.strptime(row["start_time"].split(",")[0].strip(), "%d %b %Y")
            pattern = guess_pattern(title)
            credit = credit_triceps(pattern)
            if credit > 0:
                rows.append((dt, title, credit))
    return rows


def parse_strong(path: Path) -> list[tuple[datetime, str, float]]:
    rows: list[tuple[datetime, str, float]] = []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            name = row.get("Exercise Name", "")
            if not name:
                continue
            reps = (row.get("Reps") or "").strip()
            if not reps or reps == "0":
                continue
            dt = datetime.strptime(row["Date"], "%Y-%m-%d %H:%M:%S")
            pattern = guess_pattern(name)
            credit = credit_triceps(pattern)
            if credit > 0:
                rows.append((dt, name, credit))
    return rows


def parse_macrofactor(path: Path) -> list[tuple[datetime, str, float]]:
    rows: list[tuple[datetime, str, float]] = []
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            name = row.get("Exercise", "")
            if not name:
                continue
            set_type = (row.get("Set Type") or "").lower()
            if "warm" in set_type:
                continue
            reps = (row.get("Reps") or "").strip()
            if not reps:
                continue
            dt = datetime.strptime(row["Date"], "%Y-%m-%d")
            pattern = guess_pattern(name)
            credit = credit_triceps(pattern)
            if credit > 0:
                rows.append((dt, name, credit))
    return rows


def summarize(label: str, rows: list[tuple[datetime, str, float]]) -> None:
    weekly: dict[datetime, float] = defaultdict(float)
    weekly_direct: dict[datetime, float] = defaultdict(float)
    weekly_secondary: dict[datetime, float] = defaultdict(float)
    for dt, name, credit in rows:
        wk = week_start(dt)
        weekly[wk] += credit
        pattern = guess_pattern(name)
        if "Tricep" in primary_muscles(pattern):
            weekly_direct[wk] += credit
        else:
            weekly_secondary[wk] += credit

    if not weekly:
        print(f"\n{label}: no triceps-credited sets")
        return

    max_week = max(weekly, key=weekly.get)
    max_total = weekly[max_week]
    print(f"\n{label}")
    print(f"  weeks with triceps credit: {len(weekly)}")
    print(f"  max fractional weekly total: {max_total:.1f} (week of {max_week.date()})")
    print(f"    direct primary credit: {weekly_direct[max_week]:.1f}")
    print(f"    secondary press credit: {weekly_secondary[max_week]:.1f}")
    top = sorted(weekly.items(), key=lambda x: x[1], reverse=True)[:5]
    print("  top 5 weeks:")
    for wk, total in top:
        print(
            f"    {wk.date()}: total={total:.1f} "
            f"(direct={weekly_direct[wk]:.1f}, secondary={weekly_secondary[wk]:.1f})"
        )


def main() -> None:
    paths = sys.argv[1:] or [
        "/home/sean/Downloads/hevy_data.csv",
        "/home/sean/Downloads/strong_data.csv",
        "/home/sean/Downloads/macro_factor_data.xlsx",
    ]
    all_rows: list[tuple[datetime, str, float]] = []
    for p in paths:
        path = Path(p)
        if not path.exists():
            print(f"skip missing {path}")
            continue
        if path.suffix.lower() == ".csv":
            if "hevy" in path.name.lower():
                rows = parse_hevy(path)
            else:
                rows = parse_strong(path)
        elif path.suffix.lower() == ".xlsx":
            try:
                import openpyxl
            except ImportError:
                print("openpyxl not installed; convert macro_factor to csv or pip install openpyxl")
                continue
            wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
            sheet = wb.active
            headers = [c.value for c in next(sheet.iter_rows(max_row=1))]
            idx = {h: i for i, h in enumerate(headers)}
            rows = []
            for r in sheet.iter_rows(min_row=2, values_only=True):
                name = str(r[idx.get("Exercise", 0)] or "")
                if not name:
                    continue
                date_val = r[idx.get("Date", 0)]
                if isinstance(date_val, datetime):
                    dt = date_val
                else:
                    dt = datetime.strptime(str(date_val)[:10], "%Y-%m-%d")
                reps = r[idx.get("Reps", 0)]
                if reps in (None, "", 0):
                    continue
                pattern = guess_pattern(name)
                credit = credit_triceps(pattern)
                if credit > 0:
                    rows.append((dt, name, credit))
        else:
            print(f"unsupported {path}")
            continue
        summarize(path.name, rows)
        all_rows.extend(rows)

    if len([p for p in paths if Path(p).exists()]) > 1 and all_rows:
        summarize("COMBINED (all files, no dedup)", all_rows)
        # naive dedup: same calendar day + exercise name + credit
        seen: set[tuple[str, str]] = set()
        deduped: list[tuple[datetime, str, float]] = []
        for dt, name, credit in sorted(all_rows):
            key = (dt.date().isoformat(), normalize_name(name))
            if key in seen:
                continue
            seen.add(key)
            deduped.append((dt, name, credit))
        summarize("COMBINED (dedupe by date+exercise name)", deduped)


if __name__ == "__main__":
    main()

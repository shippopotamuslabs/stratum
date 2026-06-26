"""Exercise name normalization — keep in sync with scripts/embedding/normalize.py."""

from __future__ import annotations

import re

_SUPERSET_SUFFIX = re.compile(r"\s*∈\s*SS\d+\s*$", re.IGNORECASE)
_TRAILING_EQUIPMENT_PAREN = re.compile(r"\s*\(([^)]+)\)\s*$")
_DASH_VARIANT_SUFFIX = re.compile(r"\s+-\s+([^-]+)$")
_NON_ALNUM = re.compile(r"[^a-z0-9\s]")
_MULTI_SPACE = re.compile(r"\s+")


def normalize_for_matching(raw: str) -> str:
    no_superset = _SUPERSET_SUFFIX.sub("", raw)
    paren_moved = _move_trailing_equipment_paren_to_front(no_superset)
    dash_moved = _move_dash_variant_to_front(paren_moved)
    lowercased = dash_moved.lower()
    punctuation_to_spaces = _NON_ALNUM.sub(" ", lowercased)
    return _MULTI_SPACE.sub(" ", punctuation_to_spaces.strip())


def _move_trailing_equipment_paren_to_front(value: str) -> str:
    match = _TRAILING_EQUIPMENT_PAREN.search(value)
    if match is None:
        return value
    equipment = match.group(1).strip()
    base = value[: match.start()].strip()
    if not base:
        return value
    return f"{equipment} {base}"


def _move_dash_variant_to_front(value: str) -> str:
    match = _DASH_VARIANT_SUFFIX.search(value)
    if match is None:
        return value
    variant = match.group(1).strip()
    base = value[: match.start()].strip()
    if not base or not variant:
        return value
    return f"{variant} {base}"

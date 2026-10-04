"""Contract regression: every v1 fixture request solves to a response that
validates against the v1 response schema, keeps the required structure, and
carries no back-to-back low-tier small-muscle isolation (STR-39).

Exact numeric match with the stored *.result.json is NOT required (the Python
solver intentionally differs where STR-39 applies); divergences are reported by
the golden comparison, not asserted here.
"""

import json
import re

import pytest
from jsonschema import Draft202012Validator

from solver.model import solve_payload

from .conftest import FIXTURES_DIR, SCHEMA_DIR, fixture_deadline
from .support import assert_no_back_to_back_low_tier_small_isolation

RESPONSE_SCHEMA = json.loads((SCHEMA_DIR / "response.schema.json").read_text())
REQUEST_SCHEMA = json.loads((SCHEMA_DIR / "request.schema.json").read_text())
VALIDATOR = Draft202012Validator(RESPONSE_SCHEMA)
REQUEST_VALIDATOR = Draft202012Validator(REQUEST_SCHEMA)

FIXTURE_NAMES = [
    "3day-fullbody-maint",
    "4day-arms-spec",
    "5day-shoulders-spec",
    "6day-rotating-cycle8",
]

OBJECTIVE_KEYS = [
    "total",
    "recoveryPenalty",
    "specializedMevShortfall",
    "coSpecializationImbalance",
    "specializeAimDeviation",
    "maintenanceAimDeviation",
    "minimumVolumeAimDeviation",
    "roleMinimumShortfall",
    "pushPullImbalance",
    "sessionFillShortfall",
]

SLOT_ID_RE = re.compile(r"^s\d+_.+$")


def _load(name):
    payload = json.loads((FIXTURES_DIR / f"{name}.request.json").read_text())
    return payload


@pytest.mark.slow
@pytest.mark.parametrize("name", FIXTURE_NAMES)
def test_fixture_request_validates(name):
    errors = list(REQUEST_VALIDATOR.iter_errors(_load(name)))
    assert errors == []


@pytest.mark.slow
@pytest.mark.parametrize("name", FIXTURE_NAMES)
def test_fixture_solves_and_validates(name):
    payload = _load(name)
    response = solve_payload(payload, max_time_seconds=fixture_deadline())
    errors = list(VALIDATOR.iter_errors(response))
    assert errors == []

    session_count = payload["context"]["trainingDaysPerWeek"]
    assert len(response["sessions"]) == session_count
    assert len(response["plans"]) == session_count
    assert len(response["cycleSlotIndexByPosition"]) == session_count
    assert len(response["activeSlotIdsBySession"]) == session_count
    assert len(response["regionFilterBySession"]) == session_count
    assert response["modelVersion"] == "v1"

    # Symmetry break: position i -> sorted train slot i (strictly increasing).
    days = response["cycleSlotIndexByPosition"]
    assert days == sorted(days), days
    assert len(set(days)) == len(days)

    assert sorted(response["objective"].keys()) == sorted(OBJECTIVE_KEYS)
    for key in OBJECTIVE_KEYS:
        assert isinstance(response["objective"][key], int)

    # Kotlin readObjective quirk: these buckets have no slacks behind them.
    assert response["objective"]["specializedMevShortfall"] == 0
    assert response["objective"]["roleMinimumShortfall"] == 0

    # Encoder emits canonical role names only (never the Isolation alias).
    for plan in response["plans"]:
        assert plan["position"] >= 0
        filled_ids = set()
        for f in plan["filledSlots"]:
            assert SLOT_ID_RE.match(f["slotId"]), f["slotId"]
            assert f["role"] in ("Anchor", "SecondaryCompound", "Accessory", "Optional")
            assert isinstance(f["setCount"], int) and f["setCount"] > 0
            filled_ids.add(f["slotId"].split("_", 1)[1])
        assert set(response["activeSlotIdsBySession"][plan["position"]]) == filled_ids

    assert_no_back_to_back_low_tier_small_isolation(payload, response)

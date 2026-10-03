"""CI §6.3 — round-trip: fixture request -> solver -> schema-valid response + structure."""

import json

import pytest

from contract import (
    DEFAULT_CONTRACT_ROOT,
    discover_majors,
    load_schema,
    major_of,
    request_fixtures,
    validator_for,
)
from solver_client import solve_request

#: The 10 SynthesisObjectiveBreakdown int fields (response schema $defs).
EXPECTED_OBJECTIVE_FIELDS = {
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
}


def _cases():
    cases = [
        (major, fixture)
        for major in discover_majors()
        for fixture in request_fixtures(DEFAULT_CONTRACT_ROOT, major)
    ]
    ids = [f"v{major}/{fixture.name}" for major, fixture in cases]
    return cases, ids


_CASES, _IDS = _cases()


def assert_response_structure(response: dict, days: int) -> None:
    """Boundary structural checks: counts line up with the request's day count."""
    assert len(response["sessions"]) == days, (
        f"sessions {len(response['sessions'])} != trainingDaysPerWeek {days}"
    )
    assert len(response["plans"]) == days, (
        f"plans {len(response['plans'])} != trainingDaysPerWeek {days}"
    )
    assert len(response["regionFilterBySession"]) == days, (
        f"regionFilterBySession {len(response['regionFilterBySession'])} != {days}"
    )
    assert len(response["activeSlotIdsBySession"]) == days, (
        f"activeSlotIdsBySession {len(response['activeSlotIdsBySession'])} != {days}"
    )
    for i, plan in enumerate(response["plans"]):
        for slot in plan["filledSlots"]:
            set_count = slot["setCount"]
            # type() is int, not isinstance: bool is an int subclass, not a count.
            assert type(set_count) is int and set_count >= 0, (
                f"plan {i} slot {slot.get('slotId')}: setCount {set_count!r} not an int >= 0"
            )
    for i, slot_ids in enumerate(response["activeSlotIdsBySession"]):
        for slot_id in slot_ids:
            assert isinstance(slot_id, str) and slot_id, (
                f"session {i}: active slot id {slot_id!r} not a non-empty string"
            )
    objective = response["objective"]
    assert set(objective) == EXPECTED_OBJECTIVE_FIELDS, (
        f"objective fields {sorted(objective)} != expected {sorted(EXPECTED_OBJECTIVE_FIELDS)}"
    )
    for field, value in objective.items():
        assert type(value) is int, f"objective.{field} {value!r} not an int"


@pytest.mark.parametrize("major,fixture", _CASES, ids=_IDS)
def test_round_trip_validates_and_structures(major, fixture):
    with open(fixture, encoding="utf-8") as f:
        request = json.load(f)
    days = request["context"]["trainingDaysPerWeek"]

    response = solve_request(request)

    schema = load_schema(DEFAULT_CONTRACT_ROOT, major, "response")
    errors = list(validator_for(schema).iter_errors(response))
    assert not errors, f"{fixture.name}: response invalid: {[e.message for e in errors]}"

    assert major_of(response) == major, (
        f"{fixture.name}: response major v{major_of(response)} != request major v{major}"
    )
    assert_response_structure(response, days)


def test_objective_field_set_matches_schema():
    """Pin the '10 int fields' contract to the schema itself (drift-proof)."""
    for major in discover_majors():
        schema = load_schema(DEFAULT_CONTRACT_ROOT, major, "response")
        required = set(schema["$defs"]["objectiveBreakdown"]["required"])
        assert required == EXPECTED_OBJECTIVE_FIELDS, (
            f"v{major}: schema objective fields {sorted(required)} drifted from "
            f"expected {sorted(EXPECTED_OBJECTIVE_FIELDS)} — amend the suite with the contract"
        )

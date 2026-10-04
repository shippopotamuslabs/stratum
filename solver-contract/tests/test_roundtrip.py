"""CI §6.3 — round-trip: fixture request -> solver -> schema-valid response + structure."""

import json

import pytest

from assertions import (
    EXPECTED_OBJECTIVE_FIELDS,
    assert_response_structure,
    assert_response_validates,
)
from contract import (
    DEFAULT_CONTRACT_ROOT,
    discover_majors,
    load_schema,
    request_fixtures,
)
from solver_client import solve_request


def _cases():
    cases = [
        (major, fixture)
        for major in discover_majors()
        for fixture in request_fixtures(DEFAULT_CONTRACT_ROOT, major)
    ]
    ids = [f"v{major}/{fixture.name}" for major, fixture in cases]
    return cases, ids


_CASES, _IDS = _cases()


@pytest.mark.parametrize("major,fixture", _CASES, ids=_IDS)
def test_round_trip_validates_and_structures(major, fixture):
    with open(fixture, encoding="utf-8") as f:
        request = json.load(f)
    days = request["context"]["trainingDaysPerWeek"]

    response = solve_request(request)

    assert_response_validates(response, major)
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

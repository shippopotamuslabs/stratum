"""CI §6.4 — leniency rows (VERSIONING.md §2, language-agnostic subset).

Covered here, through the solver entrypoint:
  1. missing modelVersion dispatches as v1;
  2. unknown major rejected (never guessed);
  3. unknown fields tolerated (ignored by the solver, though strict schema
     validation still flags them — that is the drift-detection firewall, §6.6);
  4. unknown minor (v1.x) accepted as v1.

Plus a boundary-tolerance smoke for the request-side default rows (missing
fatigueProfile/incrementTiers, missing restSlotIndices/cycleLength, unknown
free-form strings): the solver must never crash on them. Semantic
correctness of the defaults is the real solver's unit-test job; this suite
only pins "solves + response validates".
"""

import copy
import json

import pytest

from assertions import assert_response_structure, assert_response_validates
from contract import (
    DEFAULT_CONTRACT_ROOT,
    discover_majors,
    load_schema,
    major_of,
    parse_model_version,
    request_fixtures,
    validator_for,
)
from solver_client import solve_request


def _first_request() -> dict:
    major = discover_majors()[0]
    fixture = request_fixtures(DEFAULT_CONTRACT_ROOT, major)[0]
    with open(fixture, encoding="utf-8") as f:
        return json.load(f)


def test_missing_model_version_dispatches_as_v1():
    request = _first_request()
    del request["modelVersion"]
    assert parse_model_version(request) == (1, 0)

    response = solve_request(request)

    major = assert_response_validates(response)
    assert major == 1
    assert_response_structure(response, request["context"]["trainingDaysPerWeek"])


@pytest.mark.parametrize("version", ["v1.1", "v1.9"])
def test_unknown_minor_accepted_as_v1(version):
    request = _first_request()
    request["modelVersion"] = version
    assert major_of(request) == 1

    response = solve_request(request)

    assert_response_validates(response)
    assert_response_structure(response, request["context"]["trainingDaysPerWeek"])


def test_unknown_major_rejected():
    request = _first_request()
    request["modelVersion"] = "v3"
    with pytest.raises(ValueError, match="unsupported major"):
        solve_request(request)


def test_malformed_model_version_rejected():
    for bad in ["1", "vx", "v1.x", "", "v", 1]:
        request = _first_request()
        request["modelVersion"] = bad
        with pytest.raises(ValueError):
            solve_request(request)


def test_unknown_fields_tolerated():
    request = _first_request()
    request["futureTopLevel"] = {"nested": [1, 2, 3]}
    request["context"]["anotherFutureField"] = "ignore-me"
    first_lift_id = next(iter(request["context"]["liftsById"]))
    request["context"]["liftsById"][first_lift_id]["futureLiftField"] = 42

    # Strict schema validation flags the drift (CI §6.6 firewall) ...
    schema = load_schema(DEFAULT_CONTRACT_ROOT, 1, "request")
    errors = list(validator_for(schema).iter_errors(request))
    assert errors, "expected strict schema to flag unknown fields (drift detection)"

    # ... but the solver ignores them and still produces a valid response.
    response = solve_request(request)
    assert_response_validates(response)
    assert_response_structure(response, request["context"]["trainingDaysPerWeek"])


def test_request_side_defaults_tolerated():
    """Missing fatigueProfile/incrementTiers/restSlot/cycleLength + unknown
    free-form strings must never crash the solver (VERSIONING.md §2 rows)."""
    base = _first_request()

    variants = {}

    no_fatigue = copy.deepcopy(base)
    for lift in no_fatigue["context"]["liftsById"].values():
        lift.pop("fatigueProfile", None)
        lift.pop("incrementTiers", None)
    variants["missing-fatigue-defaults"] = no_fatigue

    no_cycle = copy.deepcopy(base)
    no_cycle["context"].pop("restSlotIndices", None)
    no_cycle["context"].pop("cycleLength", None)
    variants["missing-cycle-space"] = no_cycle

    odd_strings = copy.deepcopy(base)
    odd_strings["context"]["trainingAge"] = "VeteranOfASpecificFuture Taxonomy"
    odd_strings["context"]["lastTrained"] = "SometimeAroundTheHeatDeath"
    variants["unknown-free-form-strings"] = odd_strings

    for name, request in variants.items():
        response = solve_request(request)
        assert_response_validates(response)
        assert_response_structure(response, request["context"]["trainingDaysPerWeek"]), name

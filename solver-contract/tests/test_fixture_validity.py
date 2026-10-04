"""CI §6.1 — fixture validity: every fixture validates against its major's schema."""

import json

import pytest

from contract import (
    DEFAULT_CONTRACT_ROOT,
    discover_majors,
    load_schema,
    request_fixtures,
    response_fixtures,
    validator_for,
)


def _cases(kind):
    loader = request_fixtures if kind == "request" else response_fixtures
    cases = [
        (major, fixture)
        for major in discover_majors()
        for fixture in loader(DEFAULT_CONTRACT_ROOT, major)
    ]
    ids = [f"v{major}/{fixture.name}" for major, fixture in cases]
    return cases, ids


_REQUEST_CASES, _REQUEST_IDS = _cases("request")
_RESPONSE_CASES, _RESPONSE_IDS = _cases("response")


@pytest.mark.parametrize("major,fixture", _REQUEST_CASES, ids=_REQUEST_IDS)
def test_request_fixture_validates(major, fixture):
    schema = load_schema(DEFAULT_CONTRACT_ROOT, major, "request")
    with open(fixture, encoding="utf-8") as f:
        payload = json.load(f)
    errors = list(validator_for(schema).iter_errors(payload))
    assert not errors, f"{fixture.name}: {[e.message for e in errors]}"


@pytest.mark.parametrize("major,fixture", _RESPONSE_CASES, ids=_RESPONSE_IDS)
def test_response_fixture_validates(major, fixture):
    schema = load_schema(DEFAULT_CONTRACT_ROOT, major, "response")
    with open(fixture, encoding="utf-8") as f:
        payload = json.load(f)
    errors = list(validator_for(schema).iter_errors(payload))
    assert not errors, f"{fixture.name}: {[e.message for e in errors]}"


def test_matrix_covers_every_discovered_major():
    """Guard: cases exist for every discovered major (fails loudly if empty)."""
    majors = discover_majors()
    assert majors, "no schema/v<N>/ majors discovered — contract tree missing?"
    for major in majors:
        assert request_fixtures(DEFAULT_CONTRACT_ROOT, major), (
            f"v{major}: schema dir exists but no *.request.json fixtures"
        )
        assert response_fixtures(DEFAULT_CONTRACT_ROOT, major), (
            f"v{major}: schema dir exists but no *.result.json/*.response.json fixtures"
        )

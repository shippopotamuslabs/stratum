"""CI §6.5 — version matrix: majors come from ``schema/v<N>/``, not test code.

Adding ``schema/v2/`` with fixtures MUST extend every parametrized test with
zero test-code changes; a discovered major with missing fixtures (or no
solver handler — proven by solving its first fixture) fails loudly.
"""

import json

import pytest

from contract import (
    DEFAULT_CONTRACT_ROOT,
    discover_majors,
    load_schema,
    major_of,
    request_fixtures,
)
from solver_client import solve_request


def test_discovered_majors_match_schema_dirs():
    majors = discover_majors()
    expected = sorted(
        int(d.name[1:])
        for d in (DEFAULT_CONTRACT_ROOT / "schema").iterdir()
        if d.is_dir() and d.name.startswith("v") and d.name[1:].isdigit()
    )
    assert majors == expected


def test_every_discovered_major_solves_its_first_fixture():
    """Handler-exists proof: the configured solver answers every major."""
    for major in discover_majors():
        fixtures = request_fixtures(DEFAULT_CONTRACT_ROOT, major)
        assert fixtures, f"v{major}: discovered schema dir has no fixtures — add some (no test changes needed)"
        with open(fixtures[0], encoding="utf-8") as f:
            request = json.load(f)
        response = solve_request(request)
        assert major_of(response) == major, (
            f"v{major}: solver answered with major v{major_of(response)} — handler missing or misrouted"
        )


def _write_minimal_contract_tree(root, majors, with_fixtures=True):
    """Scratch contract tree: minimal schemas (+ optional one request fixture)."""
    for major in majors:
        schema_dir = root / "schema" / f"v{major}"
        schema_dir.mkdir(parents=True)
        request_schema = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "properties": {"modelVersion": {"type": "string"}},
        }
        (schema_dir / "request.schema.json").write_text(
            json.dumps(request_schema), encoding="utf-8"
        )
        (schema_dir / "response.schema.json").write_text(
            json.dumps(request_schema), encoding="utf-8"
        )
        if with_fixtures:
            fixture_dir = root / "fixtures" / f"v{major}"
            fixture_dir.mkdir(parents=True)
            (fixture_dir / "only.request.json").write_text(
                json.dumps({"modelVersion": f"v{major}"}), encoding="utf-8"
            )


def test_matrix_auto_extends_without_code_changes(tmp_path):
    """Simulated v2: discovery + fixture collection pick it up untouched."""
    _write_minimal_contract_tree(tmp_path, [1, 2])
    assert discover_majors(tmp_path) == [1, 2]
    for major in (1, 2):
        assert request_fixtures(tmp_path, major) != [], f"v{major} fixtures not discovered"
        load_schema(tmp_path, major, "request")
        load_schema(tmp_path, major, "response")


def test_discovered_major_without_fixtures_fails_loudly(tmp_path):
    """Simulated v9 with schema but no fixtures: the guard predicate must fire."""
    _write_minimal_contract_tree(tmp_path, [9], with_fixtures=False)
    assert discover_majors(tmp_path) == [9]
    # Same predicate as the test_fixture_validity matrix guard:
    with pytest.raises(AssertionError, match=r"v9.*no \*\.request\.json"):
        assert request_fixtures(tmp_path, 9), (
            "v9: schema dir exists but no *.request.json fixtures"
        )


def test_discovery_ignores_non_version_entries(tmp_path):
    (tmp_path / "schema").mkdir()
    (tmp_path / "schema" / "v1").mkdir()
    (tmp_path / "schema" / "_retired").mkdir()
    (tmp_path / "schema" / "README.md").write_text("docs", encoding="utf-8")
    assert discover_majors(tmp_path) == [1]

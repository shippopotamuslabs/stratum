"""Shared contract helpers: version discovery, schema loading, modelVersion parsing.

Single source of truth for "which majors exist" is the
``solver-contract/schema/`` directory: adding ``schema/v2/`` (with fixtures)
automatically extends every parametrized test with zero code changes.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from jsonschema import Draft202012Validator

# solver-contract/ directory (parent of tests/).
DEFAULT_CONTRACT_ROOT = Path(__file__).resolve().parent.parent

_SCHEMA_DIR_RE = re.compile(r"^v(\d+)$")
_VERSION_RE = re.compile(r"^v(\d+)(?:\.(\d+))?$")

#: modelVersion assumed when the field is absent (pre-versioned payloads, §1).
DEFAULT_MAJOR = 1
DEFAULT_MINOR = 0


def discover_majors(contract_root: Path = DEFAULT_CONTRACT_ROOT) -> list[int]:
    """Return sorted supported majors from ``schema/v<N>/`` directory names."""
    schema_dir = Path(contract_root) / "schema"
    majors = []
    if not schema_dir.is_dir():
        return majors
    for entry in schema_dir.iterdir():
        if not entry.is_dir():
            continue
        m = _SCHEMA_DIR_RE.match(entry.name)
        if m:
            majors.append(int(m.group(1)))
    return sorted(majors)


def request_fixtures(
    contract_root: Path, major: int
) -> list[Path]:
    """Checked-in ``*.request.json`` fixtures for a major, sorted by name."""
    return sorted((Path(contract_root) / "fixtures" / f"v{major}").glob("*.request.json"))


def response_fixtures(
    contract_root: Path, major: int
) -> list[Path]:
    """Checked-in solver-output fixtures (``*.result.json``/``*.response.json``)."""
    d = Path(contract_root) / "fixtures" / f"v{major}"
    return sorted(d.glob("*.result.json")) + sorted(d.glob("*.response.json"))


def load_schema(contract_root: Path, major: int, kind: str) -> dict:
    """Load ``schema/v<major>/<kind>.schema.json`` (kind: request|response)."""
    path = Path(contract_root) / "schema" / f"v{major}" / f"{kind}.schema.json"
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def validator_for(schema: dict) -> Draft202012Validator:
    """Draft 2020-12 validator for a contract schema (matches its $schema)."""
    validator_cls = Draft202012Validator
    validator_cls.check_schema(schema)
    return validator_cls(schema)


def parse_model_version(payload: dict) -> tuple[int, int]:
    """Parse ``modelVersion`` into ``(major, minor)``.

    Missing field means v1 (§1). Raises :class:`ValueError` when the value is
    present but malformed (e.g. ``"1"``, ``"vx"``) — malformed is not lenient,
    it is rejected like an unknown major.
    """
    raw = payload.get("modelVersion", None)
    if raw is None:
        return (DEFAULT_MAJOR, DEFAULT_MINOR)
    if not isinstance(raw, str):
        raise ValueError(f"malformed modelVersion {raw!r}: expected 'v<major>[.<minor>]'")
    m = _VERSION_RE.match(raw)
    if not m:
        raise ValueError(f"malformed modelVersion {raw!r}: expected 'v<major>[.<minor>]'")
    return (int(m.group(1)), int(m.group(2) or 0))


def major_of(payload: dict) -> int:
    """Dispatch major for a payload (minor is ignored for routing, §1)."""
    major, _ = parse_model_version(payload)
    return major

"""Fallback ladder (STR-54): L0 full model; L1 structural relaxation on proven INFEASIBLE.

L1 softens only the unconditional hard structural floor (min active lifts per
session) to a heavy-slack penalty. Recovery forbids stay hard at every level,
so L1 output is always safe-but-thin, never unsafe.
"""

import copy
import json

import pytest
from jsonschema import Draft202012Validator
from ortools.sat.python import cp_model

from solver import wire as W
from solver.model import (
    SolverInfeasibleError,
    SynthesisModel,
    solve_payload,
)

from .conftest import FIXTURES_DIR, SCHEMA_DIR, fixture_deadline
from .support import (
    assert_no_back_to_back_low_tier_small_isolation,
    tiny_request,
)

VALIDATOR = Draft202012Validator(
    json.loads((SCHEMA_DIR / "response.schema.json").read_text())
)

FIXTURE_NAMES = [
    "3day-fullbody-maint",
    "4day-arms-spec",
    "5day-shoulders-spec",
    "6day-rotating-cycle8",
]

NARROW_8 = [
    "squat",
    "bench",
    "row",
    "curl",
    "extension",
    "latraise",
    "legcurl",
    "calfraise",
]

TINY_TARGETS = {
    "Chest": {"min": 0, "aim": 4, "max": 8},
    "Back": {"min": 0, "aim": 4, "max": 8},
    "Bicep": {"min": 4, "aim": 8, "max": 12},
    "Tricep": {"min": 4, "aim": 8, "max": 12},
}

TINY_SPEC = {"Bicep": "Specialize", "Tricep": "Specialize"}

# Production-scale forcing set: every lift credits arms (directly or via
# secondaries), so sequential days cannot staff 3 slots without small-muscle
# isolation credit on both sides of an adjacency.
PROD_NARROW_4 = ["bench", "row", "curl", "tricep-ext"]


def _tiny_narrow(days):
    slots = list(range(days))
    payload = tiny_request(
        training_days=days,
        specialization=dict(TINY_SPEC),
        targets={k: dict(v) for k, v in TINY_TARGETS.items()},
        session_length_minutes=60,
        rest_slot_indices=sorted(set(range(7)) - set(slots)),
        cycle_length=7,
    )
    # Narrow catalog (mirrors test_str39's infeasible shape): with only these
    # 8 lifts, sequential days cannot staff 3 slots without small-muscle
    # isolation credit on both sides of an adjacency.
    payload["context"]["enabledLiftIds"] = list(NARROW_8)
    return payload


def _l0_solve(payload, **kwargs):
    decoded = W.decode_request(payload)
    model = SynthesisModel(
        decoded["weeklyTargets"], decoded["context"], relaxation=0
    )
    return model.solve(**kwargs)


def _validate(response):
    errors = list(VALIDATOR.iter_errors(response))
    assert not errors, [e.message[:200] for e in errors[:3]]


def _assert_l1_engaged(response):
    """L1 output must violate the L0-only floor (else L0 was feasible)."""
    assert any(
        len(plan["filledSlots"]) < 3 for plan in response["plans"]
    ), "expected a sub-3-lift session: L1 relaxation visibly engaged"


@pytest.mark.slow
@pytest.mark.parametrize("days", [2, 3, 4])
def test_l0_raises_true_infeasible_on_tiny_narrow_sequential(days):
    payload = _tiny_narrow(days)
    with pytest.raises(SolverInfeasibleError) as exc_info:
        _l0_solve(payload, max_time_seconds=fixture_deadline())
    assert exc_info.value.status == cp_model.INFEASIBLE


@pytest.mark.slow
@pytest.mark.parametrize("days", [2, 3, 4])
def test_ladder_solves_tiny_narrow_sequential(days):
    payload = _tiny_narrow(days)
    response = solve_payload(payload, max_time_seconds=fixture_deadline())
    _validate(response)
    assert_no_back_to_back_low_tier_small_isolation(payload, response)
    _assert_l1_engaged(response)


@pytest.mark.slow
@pytest.mark.parametrize("name", ["4day-arms-spec", "6day-rotating-cycle8"])
def test_ladder_solves_prod_scale_narrow(name):
    payload = json.loads((FIXTURES_DIR / f"{name}.request.json").read_text())
    payload["context"]["enabledLiftIds"] = list(PROD_NARROW_4)
    with pytest.raises(SolverInfeasibleError):
        _l0_solve(payload, max_time_seconds=fixture_deadline())
    response = solve_payload(payload, max_time_seconds=fixture_deadline())
    _validate(response)
    assert_no_back_to_back_low_tier_small_isolation(payload, response)
    _assert_l1_engaged(response)


@pytest.mark.slow
@pytest.mark.parametrize("name", FIXTURE_NAMES)
def test_ladder_returns_l0_solution_on_fixtures(name):
    """Ladder transparency: feasible cases never touch L1 (single worker => deterministic)."""
    payload = json.loads((FIXTURES_DIR / f"{name}.request.json").read_text())
    via_ladder = solve_payload(
        copy.deepcopy(payload),
        max_time_seconds=fixture_deadline(),
        num_search_workers=1,
    )
    decoded = W.decode_request(payload)
    direct = SynthesisModel(
        decoded["weeklyTargets"], decoded["context"], relaxation=0
    ).solve(max_time_seconds=fixture_deadline(), num_search_workers=1)
    assert via_ladder["objective"] == W.encode_response(direct)["objective"]


@pytest.mark.slow
def test_ladder_solves_extreme_all_train_narrow():
    """Guarantee probe: 7 gap-1 adjacencies incl. wrap; L1 must still solve."""
    payload = tiny_request(
        training_days=7,
        specialization=dict(TINY_SPEC),
        targets={k: dict(v) for k, v in TINY_TARGETS.items()},
        session_length_minutes=60,
        rest_slot_indices=[],
        cycle_length=7,
    )
    with pytest.raises(SolverInfeasibleError):
        _l0_solve(payload, max_time_seconds=fixture_deadline())
    response = solve_payload(payload, max_time_seconds=fixture_deadline())
    _validate(response)
    assert_no_back_to_back_low_tier_small_isolation(payload, response)


def test_invalid_relaxation_level_rejected():
    payload = _tiny_narrow(2)
    decoded = W.decode_request(payload)
    with pytest.raises(ValueError):
        SynthesisModel(decoded["weeklyTargets"], decoded["context"], relaxation=2)

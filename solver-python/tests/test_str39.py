"""STR-39: 48h recovery for low-dose small-muscle isolation.

(1) Meaningful-exposure floor 1.0 for small-muscle isolation — a single hard
    set counts.
(2) Low tier (1-3 sets) triggers the 48h adjacent-day hard forbid for
    small-muscle isolation.

Implemented fresh from the STR-39 issue text (not ported from the old Kotlin
fix branch). The pre-STR-39 behavior (small-muscle isolation Low stays on the
24h lane) is reproduced by ``KotlinSemanticsModel`` below so the suite
demonstrates the defect AND the fix on the same input.
"""

import json

import pytest

from solver import domain as D
from solver.model import SynthesisModel
from solver.wire import decode_request, encode_response

from .conftest import FIXTURES_DIR, fixture_deadline
from .support import (
    assert_no_back_to_back_low_tier_small_isolation,
    tiny_request,
)


class KotlinSemanticsModel(SynthesisModel):
    """Pre-STR-39 Kotlin semantics: 2.0 floor everywhere; small-muscle
    isolation gates moderate + high tiers only."""

    def _forbid_exposure_literal(self, model, credit, muscle, position):
        return self._meaningful_literal(model, credit, muscle, position)

    def _small_muscle_isolation_gates(
        self, model, plan, muscle, credit, pos,
        isolation_class, moderate_tier, high_tier,
    ):
        return [
            self._reify_and(
                model, moderate_tier, isolation_class,
                f"adj2d_isoMod_{muscle}_{pos}",
            ),
            self._reify_and(
                model, high_tier, isolation_class,
                f"adj2d_isoHi_{muscle}_{pos}",
            ),
        ]


def _solve_variant(model_cls, payload, deadline):
    decoded = decode_request(payload)
    model = model_cls(decoded["weeklyTargets"], decoded["context"])
    return encode_response(model.solve(max_time_seconds=deadline))


def _low_tier_small_isolation_days(request_payload, response):
    """Independent scan: {muscle: sorted days} with isolation credit in [1,3]."""
    from .support import session_muscle_exposure

    days = response["cycleSlotIndexByPosition"]
    exposure = session_muscle_exposure(request_payload, response)
    out = {}
    for pos, per in exposure.items():
        for muscle, (credit, cls) in per.items():
            if D.is_large_compound_muscle(muscle) or cls != "Isolation":
                continue
            if 1.0 <= credit <= 3.0:
                out.setdefault(muscle, []).append(days[pos])
    return {m: sorted(ds) for m, ds in out.items()}


@pytest.mark.slow
def test_str39_fix_on_shoulders_fixture():
    """5-day shoulders spec: the pre-STR-39 model reproduces the back-to-back
    defect (LateralDeltoid on adjacent days); the STR-39 model must not."""
    payload = json.loads(
        (FIXTURES_DIR / "5day-shoulders-spec.request.json").read_text()
    )
    deadline = fixture_deadline()
    kotlin_response = _solve_variant(KotlinSemanticsModel, payload, deadline)
    fixed_response = _solve_variant(SynthesisModel, payload, deadline)

    low_days = _low_tier_small_isolation_days(payload, kotlin_response)
    assert low_days.get("LateralDeltoid"), (
        "expected the pre-STR-39 model to expose LateralDeltoid at low tier"
    )
    assert any(
        (b - a) % 7 == 1
        for ds in [low_days["LateralDeltoid"]]
        for a in ds
        for b in ds
        if a != b
    ), "pre-STR-39 model should reproduce the back-to-back defect"

    assert_no_back_to_back_low_tier_small_isolation(payload, fixed_response)


def _arms_spec_sequential_payload():
    return tiny_request(
        training_days=4,
        specialization={"Bicep": "Specialize", "Tricep": "Specialize"},
        targets={
            "Chest": {"min": 0, "aim": 4, "max": 8},
            "Back": {"min": 0, "aim": 4, "max": 8},
            "Bicep": {"min": 4, "aim": 8, "max": 12},
            "Tricep": {"min": 4, "aim": 8, "max": 12},
        },
        session_length_minutes=60,
        rest_slot_indices=[4, 5, 6],
        cycle_length=7,
    )


def test_str39_synthetic_arms_stays_feasible():
    """4 sequential days, arms specialized: the 1.0 floor must not cause
    infeasibility on a realistic catalog, and no low-tier small-muscle
    isolation may sit adjacent."""
    payload = _arms_spec_sequential_payload()
    response = _solve_variant(SynthesisModel, payload, 30.0)
    assert len(response["sessions"]) == 4
    assert_no_back_to_back_low_tier_small_isolation(payload, response)


def test_str39_narrow_catalog_infeasibility_is_user_safe():
    """FLAGGED known limitation (see STR-45 report): with a narrow 8-lift
    catalog, dual arms specialization at MEV on 4 sequential days is feasible
    pre-STR-39 but infeasible with the 1.0 floor — secondary credits
    (bench->Tricep, row->Bicep) plus direct work trigger the 48h forbid on
    every adjacency. The solver must fail with the user-safe infeasible
    error (never a crash, hang, or guessed schedule)."""
    from solver.model import SolverInfeasibleError

    payload = _arms_spec_sequential_payload()
    payload["context"]["enabledLiftIds"] = [
        "squat",
        "bench",
        "row",
        "curl",
        "extension",
        "latraise",
        "legcurl",
        "calfraise",
    ]
    # Pre-STR-39 semantics still solve this shape.
    kotlin_response = _solve_variant(KotlinSemanticsModel, payload, 30.0)
    assert len(kotlin_response["sessions"]) == 4
    # STR-39 keeps the 1.0 floor and fails loudly.
    with pytest.raises(SolverInfeasibleError):
        _solve_variant(SynthesisModel, payload, 30.0)

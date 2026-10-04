"""Shared response assertions for the contract suite.

Both the round-trip test (§6.3) and the leniency tests (§6.4) validate solver
responses the same way; this module is the single home for those checks so
test modules stay leaves with no test-to-test imports.
"""

from __future__ import annotations

from contract import (
    DEFAULT_CONTRACT_ROOT,
    load_schema,
    major_of,
    validator_for,
)

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


def assert_response_validates(response: dict, major: int | None = None) -> int:
    """Assert the response validates against its major's response schema.

    When ``major`` is given, also assert the response carries it. Returns the
    response's dispatch major.
    """
    actual = major_of(response)
    if major is not None:
        assert actual == major, f"response major v{actual} != expected v{major}"
    schema = load_schema(DEFAULT_CONTRACT_ROOT, actual, "response")
    errors = list(validator_for(schema).iter_errors(response))
    assert not errors, f"response invalid: {[e.message for e in errors]}"
    return actual


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

"""Canned dev solver for contract-test development (NOT a real solve).

Builds a contract-valid v1 response shaped by the request's
``context.trainingDaysPerWeek``: N sessions, N plans, N region filters, one
canned slot per session with a fixed set count. Enough structure to exercise
the JSON boundary (schema validity + structural checks); numeric quality is
explicitly out of scope — fixtures remain the shape anchors, not oracles.

At integration the real Python solver replaces this module (see README.md);
the suite must stay green against either.
"""

from __future__ import annotations

from contract import major_of

HANDLERS = {}
SUPPORTED_MAJORS: list[int] = []


def _slot_template(session_index: int) -> dict:
    return {
        "slotId": f"s{session_index}_h-press",
        "role": "Anchor",
        "pattern": "HorizontalPress",
        "primaryMuscle": "Chest",
        "repProfile": "UpperMachineOrPull",
        "lengthBiasPreference": None,
        "siblingVariant": None,
        "isCrossDomainTouch": False,
        "allowedFatigueProfiles": None,
    }


def _filled_slot(session_index: int) -> dict:
    return {**_slot_template(session_index), "setCount": 3}


def solve_v1(request: dict) -> dict:
    days = request["context"]["trainingDaysPerWeek"]
    sessions = [
        {
            "sessionType": "FullBody",
            "name": f"Session {i + 1}",
            "slots": [_slot_template(i)],
            "siblingIndex": 0,
            "position": i,
            "composition": None,
        }
        for i in range(days)
    ]
    plans = [
        {
            "sessionType": "FullBody",
            "name": f"Session {i + 1}",
            "slots": [_slot_template(i)],
            "siblingIndex": 0,
            "position": i,
            "filledSlots": [_filled_slot(i)],
        }
        for i in range(days)
    ]
    return {
        "modelVersion": "v1",
        "sessions": sessions,
        "plans": plans,
        "cycleSlotIndexByPosition": list(range(days)),
        "objective": {
            "total": 0,
            "recoveryPenalty": 0,
            "specializedMevShortfall": 0,
            "coSpecializationImbalance": 0,
            "specializeAimDeviation": 0,
            "maintenanceAimDeviation": 0,
            "minimumVolumeAimDeviation": 0,
            "roleMinimumShortfall": 0,
            "pushPullImbalance": 0,
            "sessionFillShortfall": 0,
        },
        "activeSlotIdsBySession": [["h-press"] for _ in range(days)],
        "regionFilterBySession": ["Unrestricted"] * days,
    }


HANDLERS[1] = solve_v1
SUPPORTED_MAJORS = sorted(HANDLERS)


def solve(request: dict) -> dict:
    """Entry point honoring the in-process solver contract (see solver_client)."""
    major = major_of(request)  # missing -> v1; malformed -> ValueError
    handler = HANDLERS.get(major)
    if handler is None:
        raise ValueError(
            f"unsupported major v{major}: this solver supports {[f'v{m}' for m in SUPPORTED_MAJORS]}"
        )
    return handler(request)

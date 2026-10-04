"""Shared builders and output-property checkers for solver tests.

The back-to-back checker below verifies the STR-39 output property from the
response JSON (not from the constraint encoding): it reuses the coefficient
math (``_Coefficients``) and ``decode_request`` to compute exposure, but never
touches the model's reified literals or tier gates, so it verifies behavior,
not mechanism.
"""

from __future__ import annotations

from solver import domain as D
from solver.model import _Coefficients


def make_lift(
    lift_id,
    name,
    pattern,
    primaries,
    secondaries=(),
    fatigue="Medium",
):
    return {
        "id": lift_id,
        "name": name,
        "movementPattern": pattern,
        "movementPatterns": [pattern],
        "primaryMuscles": list(primaries),
        "secondaryMuscles": list(secondaries),
        "mainstream": None,
        "progressTrend": None,
        "proven": None,
        "sessionCount": None,
        "effectivenessRank": None,
        "effectivenessEvidence": None,
        "fatigueDecaySlope": None,
        "fatigueDecayIntercept": None,
        "muscleLengthBias": None,
        "equipment": None,
        "fatigueProfile": fatigue,
        "incrementTiers": [],
        "estimatedOneRm": None,
    }


def tiny_catalog():
    lifts = {
        "squat": make_lift("squat", "Squat", "SquatPattern", ["Quad"], ["Glute"], "High"),
        "bench": make_lift(
            "bench", "Bench", "HorizontalPress", ["Chest"], ["Tricep", "AnteriorDeltoid"]
        ),
        "row": make_lift(
            "row", "Row", "HorizontalPull", ["Back"], ["Bicep", "PosteriorDeltoid"]
        ),
        "curl": make_lift("curl", "Curl", "ElbowFlexion", ["Bicep"], [], "Low"),
        "extension": make_lift(
            "extension", "Extension", "ElbowExtension", ["Tricep"], [], "Low"
        ),
        "latraise": make_lift(
            "latraise", "Lat Raise", "ShoulderAbduction", ["LateralDeltoid"], [], "Low"
        ),
        "legcurl": make_lift(
            "legcurl", "Leg Curl", "KneeFlexion", ["Hamstring"], [], "Low"
        ),
        "calfraise": make_lift(
            "calfraise", "Calf Raise", "PlantarFlexion", ["Calf"], [], "Low"
        ),
        "inc-press": make_lift(
            "inc-press",
            "Incline Press",
            "InclinePress",
            ["Chest"],
            ["Tricep", "AnteriorDeltoid"],
        ),
        "pulldown": make_lift(
            "pulldown",
            "Pulldown",
            "VerticalPull",
            ["Back"],
            ["Bicep"],
        ),
        "legext": make_lift(
            "legext", "Leg Extension", "KneeExtension", ["Quad"], [], "Low"
        ),
        "rdl": make_lift(
            "rdl",
            "RDL",
            "HipHinge",
            ["Hamstring"],
            ["Glute", "LowerBack"],
            "High",
        ),
    }
    return lifts


def tiny_request(
    training_days=1,
    specialization=None,
    targets=None,
    session_length_minutes=None,
    rest_slot_indices=None,
    cycle_length=None,
):
    lifts = tiny_catalog()
    context = {
        "trainingAge": "Intermediate",
        "lastTrained": None,
        "sessionLengthMinutes": session_length_minutes,
        "trainingDaysPerWeek": training_days,
        "specialization": dict(specialization or {}),
        "liftsById": lifts,
        "muscleVolumeSnapshotsByMuscle": {},
        "volumeCorrelationsByMuscle": {},
        "enabledLiftIds": sorted(lifts),
        "movementPatternByLiftId": {lid: l["movementPattern"] for lid, l in lifts.items()},
        "liftEffectivenessByLiftId": {},
        "restSlotIndices": rest_slot_indices,
        "cycleLength": cycle_length,
    }
    default_targets = {
        "Chest": {"min": 0, "aim": 4, "max": 8},
        "Back": {"min": 0, "aim": 4, "max": 8},
        "Quad": {"min": 0, "aim": 4, "max": 8},
        "Hamstring": {"min": 0, "aim": 2, "max": 6},
        "Bicep": {"min": 0, "aim": 2, "max": 6},
        "Tricep": {"min": 0, "aim": 2, "max": 6},
    }
    return {
        "modelVersion": "v1",
        "weeklyTargets": targets or default_targets,
        "context": context,
    }


def session_muscle_exposure(request_payload, response):
    """Per (position -> {muscle: (credit_float, dominant_class)}).

    Credits from set counts and scaled coefficients; dominant class from
    primary-mover contributions >= 1.0. Independent of the constraint
    encoding (reified literals, tier gates), which is what the checker
    verifies.
    """
    from solver.wire import decode_request

    decoded = decode_request(request_payload)
    coeff = _Coefficients(decoded["context"])
    out = {}
    for i, plan in enumerate(response["plans"]):
        per: dict[str, float] = {}
        primary_ha: set[str] = set()
        primary_uc: set[str] = set()
        for f in plan["filledSlots"]:
            slot = {
                "pattern": f["pattern"],
                "primaryMuscle": f["primaryMuscle"],
                "role": f["role"],
                "repProfile": f["repProfile"],
                "isCrossDomainTouch": f["isCrossDomainTouch"],
                "siblingVariant": f.get("siblingVariant"),
                "lengthBiasPreference": f.get("lengthBiasPreference"),
                "allowedFatigueProfiles": f.get("allowedFatigueProfiles"),
            }
            muscles = {f["primaryMuscle"]} | {
                m for m, _ in coeff.muscles_for_pattern(f["pattern"])
            }
            for m in muscles:
                c = coeff.scaled_coeff(slot, m)
                if c:
                    per[m] = per.get(m, 0) + f["setCount"] * c
            if f["primaryMuscle"] and f["setCount"] * coeff.scaled_coeff(
                slot, f["primaryMuscle"]
            ) >= 100 and f["primaryMuscle"] in per:
                stress = D.slot_lift_stress_class(slot)
                if stress == "HeavyAxial":
                    primary_ha.add(f["primaryMuscle"])
                elif stress == "UpperCompound":
                    primary_uc.add(f["primaryMuscle"])
        classes = {}
        for m in per:
            if m in primary_ha:
                classes[m] = "HeavyAxial"
            elif m in primary_uc:
                classes[m] = "UpperCompound"
            else:
                classes[m] = "Isolation"
        out[i] = {m: (c / 100.0, classes[m]) for m, c in per.items()}
    return out


def low_tier_small_isolation_days(exposure, days):
    """{muscle: [calendar days]} with isolation-dominant credit in [1.0, 3.0].

    Shared scan for the assert helper and the pre-fix defect demonstration.
    """
    low_days: dict[str, list[int]] = {}
    for pos, per in exposure.items():
        for muscle, (credit, cls) in per.items():
            if D.is_large_compound_muscle(muscle):
                continue
            if cls != "Isolation":
                continue
            if 1.0 <= credit <= 3.0:
                low_days.setdefault(muscle, []).append(days[pos])
    return low_days


def assert_no_back_to_back_low_tier_small_isolation(request_payload, response):
    """STR-39 output property: no small muscle may carry isolation-dominant
    credit in [1.0, 3.0] on two calendar-adjacent training days."""
    from solver.wire import decode_request

    decoded = decode_request(request_payload)
    ctx = decoded["context"]
    length, rests = D.materialize_cycle_space(
        ctx["trainingDaysPerWeek"], ctx["restSlotIndices"], ctx["cycleLength"]
    )
    cycle = length
    days = response["cycleSlotIndexByPosition"]
    exposure = session_muscle_exposure(request_payload, response)
    low_days = low_tier_small_isolation_days(exposure, days)
    violations = []
    for muscle, ds in low_days.items():
        for a in ds:
            for b in ds:
                if a != b and (b - a) % cycle == 1:
                    violations.append(f"{muscle}: day {a} -> day {b}")
    assert not violations, (
        "back-to-back low-tier small-muscle isolation: " + "; ".join(violations)
    )
    return low_days

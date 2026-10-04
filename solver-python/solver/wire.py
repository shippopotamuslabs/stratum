"""Lenient v1 JSON decode + canonical v1 response encode (STR-45).

Implements every decoder row of solver-contract/VERSIONING.md §2 that applies to
the Python request-decoding / response-encoding side:

- Unknown top-level or nested fields are ignored (never read).
- Missing ``modelVersion`` means v1; ``v1.<minor>`` accepts as v1; unknown major
  (``v2``, ``v3``, ...) is rejected with an error (never guessed).
- Missing ``fatigueProfile`` defaults to ``Low``; unknown names also default to
  ``Low`` (mirrors Kotlin's ``runCatching { ... } ?: Low``).
- Missing ``incrementTiers`` defaults to ``[]``.
- Missing ``restSlotIndices`` / ``cycleLength`` default to ``None`` (server
  cycle-space defaults apply in domain.materialize_cycle_space).
- Free-form strings (``trainingAge``, ``lastTrained``, ``posture``, ``evidence``,
  ...) are passed through without validation and never crash the decode.
- Unknown ``allowedFatigueProfiles`` names are dropped; an empty result means
  null (pattern match only).
- The legacy wire alias ``role: "Isolation"`` maps to ``Accessory``. Our encoder
  only emits canonical names; the alias map lives here so any Python-side
  response re-decode stays compatible.
- A missing ``regionFilterBySession`` on decode is inferred from ``sessionType``
  labels (legacy map from ProgramSynthesisWire).

Deliberate §5-minor leniency beyond the Kotlin wire decoder (FLAGGED): unknown
enum spellings that Kotlin's ``enumValueOf`` would throw on (movement patterns,
muscle-length bias, specialization levels, snapshot source) default instead of
failing — a v1.minor addition of an enum value must not break old readers.
Unknown specialization levels default to ``Maintenance``.
"""

from __future__ import annotations

import math
import re

from . import domain as D

MODEL_VERSION_V1 = "v1"

_VERSION_RE = re.compile(r"^v(\d+)(\.\d+)*$")

ROLE_ISOLATION_ALIAS = "Isolation"
ROLE_ACCESSORY = "Accessory"

# Legacy SessionTypeId -> SessionRegionFilter display-label map
# (ProgramSynthesisWire.sessionTypeIdToRegionFilter).
_SESSION_TYPE_TO_REGION_FILTER = {
    "Lower": "LowerOnly",
    "QuadsCalves": "LowerOnly",
    "GlutesHams": "LowerOnly",
    "FullBody": "Unrestricted",
    "FullPush": "Unrestricted",
    "FullPull": "Unrestricted",
}

_REGION_FILTER_TO_SESSION_TYPE = {
    "UpperOnly": "Upper",
    "LowerOnly": "Lower",
    "Unrestricted": "FullBody",
}

_VALID_FATIGUE_PROFILES = frozenset({"High", "Medium", "Low"})


class WireError(ValueError):
    """Raised when a v1 request payload cannot be decoded or is rejected."""


def parse_model_version(value) -> tuple[int, str]:
    """Return (major, canonical_minor_str). Missing/None means v1.

    Raises WireError on unknown majors or malformed values.
    """
    if value is None:
        return (1, MODEL_VERSION_V1)
    if not isinstance(value, str):
        raise WireError(f"modelVersion must be a string, was {value!r}")
    m = _VERSION_RE.match(value)
    if not m:
        raise WireError(f"Unrecognized modelVersion {value!r}")
    major = int(m.group(1))
    if major != 1:
        # Message text is pinned by the contract suite (match="unsupported major").
        raise WireError(f"unsupported major in modelVersion {value!r} (server supports v1)")
    return (major, value)


def canonical_role(name: str) -> str:
    """Map the legacy 'Isolation' wire alias to 'Accessory'."""
    if name == ROLE_ISOLATION_ALIAS:
        return ROLE_ACCESSORY
    return name


def _require_object(value, what: str) -> dict:
    if not isinstance(value, dict):
        raise WireError(f"Expected object for {what}")
    return value


def _require_list(value, what: str) -> list:
    if not isinstance(value, list):
        raise WireError(f"Expected array for {what}")
    return value


def _require_string(value, what: str) -> str:
    if not isinstance(value, str):
        raise WireError(f"Expected string for {what}")
    return value


def _require_number(value, what: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise WireError(f"Expected number for {what}")
    if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
        raise WireError(f"Non-finite number for {what}")
    return value


def _require_int(value, what: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise WireError(f"Expected int for {what}")
    return value


def _opt_string(value, what: str):
    if value is None:
        return None
    return _require_string(value, what)


def _opt_int(value, what: str):
    if value is None:
        return None
    return _require_int(value, what)


def _opt_number(value, what: str):
    if value is None:
        return None
    return _require_number(value, what)


def decode_request(payload: dict) -> dict:
    """Lenient v1 request decode. Returns normalized
    {'modelVersion': 'v1'|'v1.x', 'weeklyTargets': {...}, 'context': {...}}.

    Unknown fields are ignored at every level. Raises WireError on unknown
    majors or missing required structure.
    """
    root = _require_object(payload, "request")
    raw_version = root.get("modelVersion")
    _, version_str = parse_model_version(raw_version)

    weekly = root.get("weeklyTargets")
    if weekly is None:
        raise WireError("Missing required field: weeklyTargets")
    weekly_targets = {
        muscle: _decode_weekly_target(t, f"weeklyTargets[{muscle}]")
        for muscle, t in _require_object(weekly, "weeklyTargets").items()
    }

    ctx_raw = root.get("context")
    if ctx_raw is None:
        raise WireError("Missing required field: context")
    context = _decode_context(_require_object(ctx_raw, "context"))

    return {
        "modelVersion": version_str,
        "weeklyTargets": weekly_targets,
        "context": context,
    }


def _decode_weekly_target(value, what: str) -> dict:
    obj = _require_object(value, what)
    for key in ("min", "aim", "max"):
        if key not in obj:
            raise WireError(f"Missing required field: {what}.{key}")
    return {
        "min": _require_number(obj["min"], f"{what}.min"),
        "aim": _require_number(obj["aim"], f"{what}.aim"),
        "max": _require_number(obj["max"], f"{what}.max"),
    }


def _decode_context(obj: dict) -> dict:
    for key in (
        "trainingDaysPerWeek",
        "specialization",
        "liftsById",
        "muscleVolumeSnapshotsByMuscle",
        "volumeCorrelationsByMuscle",
        "enabledLiftIds",
        "movementPatternByLiftId",
        "liftEffectivenessByLiftId",
    ):
        if key not in obj:
            raise WireError(f"Missing required context field: {key}")
    # trainingAge: free-form, tolerated. Default "" when absent (producer MUST emit).
    training_age = obj.get("trainingAge", "")
    if training_age is None:
        training_age = ""
    if not isinstance(training_age, str):
        raise WireError("Expected string for context.trainingAge")
    # lastTrained: free-form string or null, tolerated unknown values.
    last_trained = _opt_string(obj.get("lastTrained"), "context.lastTrained")

    lifts = {
        lift_id: _decode_lift(_require_object(v, f"liftsById[{lift_id}]"))
        for lift_id, v in _require_object(obj["liftsById"], "liftsById").items()
    }
    enabled = obj.get("enabledLiftIds")
    if not isinstance(enabled, list) or any(
        not isinstance(i, str) for i in enabled
    ):
        raise WireError("Expected string array for context.enabledLiftIds")

    return {
        # Free-form; never validated.
        "trainingAge": training_age,
        "lastTrained": last_trained,
        "sessionLengthMinutes": _opt_int(
            obj.get("sessionLengthMinutes"), "context.sessionLengthMinutes"
        ),
        "trainingDaysPerWeek": _require_int(
            obj["trainingDaysPerWeek"], "context.trainingDaysPerWeek"
        ),
        "specialization": {
            muscle: _decode_specialization_level(level)
            for muscle, level in _require_object(
                obj["specialization"], "specialization"
            ).items()
        },
        "liftsById": lifts,
        "muscleVolumeSnapshotsByMuscle": {
            muscle: _decode_snapshot(
                _require_object(v, f"snapshots[{muscle}]")
            )
            for muscle, v in _require_object(
                obj["muscleVolumeSnapshotsByMuscle"], "snapshots"
            ).items()
        },
        "volumeCorrelationsByMuscle": {
            muscle: {
                "muscle": muscle,
                "correlationSlope": _require_number(
                    _require_object(v, f"corr[{muscle}]").get(
                        "correlationSlope", 0.0
                    ),
                    f"corr[{muscle}].correlationSlope",
                ),
                # Free-form evidence label; tolerated.
                "evidence": _require_object(v, f"corr[{muscle}]").get(
                    "evidence", ""
                ),
            }
            for muscle, v in _require_object(
                obj["volumeCorrelationsByMuscle"], "correlations"
            ).items()
        },
        "enabledLiftIds": set(enabled),
        # Resolved movement pattern per lift id; null allowed. Unknown names
        # (v1.minor additions) map to None rather than failing.
        "movementPatternByLiftId": {
            lift_id: _decode_pattern_or_none(v)
            for lift_id, v in _require_object(
                obj["movementPatternByLiftId"], "movementPatternByLiftId"
            ).items()
        },
        "liftEffectivenessByLiftId": {
            lift_id: _decode_effectiveness(
                _require_object(v, f"effectiveness[{lift_id}]"), lift_id
            )
            for lift_id, v in _require_object(
                obj["liftEffectivenessByLiftId"], "effectiveness"
            ).items()
        },
        # Optional cycle-space rest pattern; omitted means server defaults.
        "restSlotIndices": _decode_rest_slots(obj.get("restSlotIndices")),
        "cycleLength": _opt_int(obj.get("cycleLength"), "context.cycleLength"),
    }


_KNOWN_PATTERNS = frozenset(
    {
        "SquatPattern",
        "HipHinge",
        "KneeExtension",
        "KneeFlexion",
        "PlantarFlexion",
        "HorizontalPress",
        "VerticalPull",
        "HorizontalPull",
        "ShoulderAdduction",
        "ElbowExtension",
        "ElbowFlexion",
        "ShoulderAbduction",
        "ShoulderHorizontalAbduction",
        "ShoulderFlexion",
        "HipExtension",
        "WristFlexionExtension",
        "ScapularElevation",
        "SpinalFlexion",
        "InclinePress",
        "VerticalPress",
        "HipAbduction",
        "HipAdduction",
    }
)


def _known_pattern(name: str) -> bool:
    return name in _KNOWN_PATTERNS


def _decode_pattern_or_none(value):
    """Known movement-pattern name, else None (unknown names tolerated)."""
    return value if isinstance(value, str) and _known_pattern(value) else None


def _decode_specialization_level(value) -> str:
    if value in ("Specialize", "Maintenance", "MinimumVolume", "Omit"):
        return value
    # §5-minor leniency: unknown levels default to Maintenance.
    return "Maintenance"


def _decode_lift(obj: dict) -> dict:
    for key in ("id", "name", "movementPatterns", "primaryMuscles", "secondaryMuscles"):
        if key not in obj:
            raise WireError(f"Missing required lift field: {key}")
    raw_pattern = obj.get("movementPattern")
    movement_pattern = _decode_pattern_or_none(raw_pattern)
    raw_bias = obj.get("muscleLengthBias")
    muscle_bias = (
        raw_bias
        if raw_bias in ("Lengthened", "Shortened", "MidRange")
        else None
    )
    raw_fatigue = obj.get("fatigueProfile")
    fatigue = raw_fatigue if raw_fatigue in _VALID_FATIGUE_PROFILES else "Low"
    tiers = obj.get("incrementTiers", [])
    if tiers is None:
        tiers = []
    if not isinstance(tiers, list):
        raise WireError("Expected array for lift.incrementTiers")
    return {
        "id": _require_string(obj["id"], "lift.id"),
        "name": _require_string(obj["name"], "lift.name"),
        "movementPattern": movement_pattern,
        "movementPatterns": [
            p
            for p in _require_list(obj["movementPatterns"], "lift.movementPatterns")
            if isinstance(p, str) and _known_pattern(p)
        ],
        "primaryMuscles": [
            m
            for m in _require_list(obj["primaryMuscles"], "lift.primaryMuscles")
            if isinstance(m, str)
        ],
        "secondaryMuscles": [
            m
            for m in _require_list(obj["secondaryMuscles"], "lift.secondaryMuscles")
            if isinstance(m, str)
        ],
        "mainstream": obj.get("mainstream"),
        "progressTrend": obj.get("progressTrend"),
        "proven": obj.get("proven"),
        "sessionCount": obj.get("sessionCount"),
        "effectivenessRank": obj.get("effectivenessRank"),
        "effectivenessEvidence": obj.get("effectivenessEvidence"),
        "fatigueDecaySlope": obj.get("fatigueDecaySlope"),
        "fatigueDecayIntercept": obj.get("fatigueDecayIntercept"),
        "muscleLengthBias": muscle_bias,
        "equipment": obj.get("equipment"),
        "fatigueProfile": fatigue,
        "incrementTiers": tiers,
        "estimatedOneRm": _decode_weight_or_null(obj.get("estimatedOneRm")),
    }


def _decode_weight_or_null(value):
    if value is None:
        return None
    if not isinstance(value, dict):
        return None
    amount = value.get("amount")
    unit = value.get("unit")
    # Mirror _require_number: bool and NaN/Inf amounts are absent, not valid.
    if (
        not isinstance(amount, (int, float))
        or isinstance(amount, bool)
        or (isinstance(amount, float) and (math.isnan(amount) or math.isinf(amount)))
        or unit not in ("Lb", "Kg")
    ):
        return None
    return {"amount": amount, "unit": unit}


def _decode_snapshot(obj: dict) -> dict:
    for key in (
        "muscle",
        "retentionFloorMv",
        "paretoVolumePv",
        "recoverabilityCeilingRc",
        "growthEntryFloor",
        "maintenanceCeiling",
        "source",
    ):
        if key not in obj:
            raise WireError(f"Missing required snapshot field: {key}")
    return {
        "muscle": _require_string(obj["muscle"], "snapshot.muscle"),
        "retentionFloorMv": _require_number(obj["retentionFloorMv"], "snapshot.mv"),
        "paretoVolumePv": _require_number(obj["paretoVolumePv"], "snapshot.pv"),
        "recoverabilityCeilingRc": _require_number(obj["recoverabilityCeilingRc"], "snapshot.rc"),
        "growthEntryFloor": _require_number(obj["growthEntryFloor"], "snapshot.ge"),
        "maintenanceCeiling": _require_number(obj["maintenanceCeiling"], "snapshot.mc"),
        # Free-form posture label (or null); tolerated.
        "posture": obj.get("posture"),
        "observedDoseMax": _opt_number(obj.get("observedDoseMax"), "snapshot.max"),
        "observedDoseMedian": _opt_number(obj.get("observedDoseMedian"), "snapshot.med"),
        # Source enum; unknown values pass through (unused by the solver core).
        "source": obj["source"],
    }


def _decode_effectiveness(obj: dict, lift_id: str) -> dict:
    raw_pattern = obj.get("pattern")
    return {
        "liftId": obj.get("liftId", lift_id),
        "pattern": _decode_pattern_or_none(raw_pattern),
        "rank": obj.get("rank"),
        "effectivenessScore": obj.get("effectivenessScore"),
        # Free-form evidence label; tolerated.
        "evidence": obj.get("evidence", ""),
    }


def _decode_rest_slots(value):
    if value is None:
        return None
    if not isinstance(value, list):
        raise WireError("Expected int array for context.restSlotIndices")
    return {_require_int(i, "restSlotIndices[]") for i in value}


# --------------------------------------------------------------------------
# Response encode (canonical v1 — producers emit canonical names only)
# --------------------------------------------------------------------------


def session_type_for_region_filter(region_filter: str) -> str:
    try:
        return _REGION_FILTER_TO_SESSION_TYPE[region_filter]
    except KeyError:
        raise WireError(f"Unknown region filter {region_filter!r}") from None


def region_filter_for_session_type(session_type: str) -> str:
    """Legacy inference when regionFilterBySession is missing."""
    return _SESSION_TYPE_TO_REGION_FILTER.get(session_type, "UpperOnly")


def _encode_allowed_fatigue(fatigue):
    if fatigue is None:
        return None
    kept = [f for f in fatigue if f in _VALID_FATIGUE_PROFILES]
    return kept or None


def _encode_slot_template(slot: dict, session_index: int) -> dict:
    fatigue = slot.get("allowedFatigueProfiles")
    return {
        "slotId": f"s{session_index}_{slot['id']}",
        "role": canonical_role(slot["role"]),
        "pattern": slot["pattern"],
        "primaryMuscle": slot["primaryMuscle"],
        "repProfile": slot["repProfile"],
        "lengthBiasPreference": slot.get("lengthBiasPreference"),
        "siblingVariant": slot.get("siblingVariant"),
        "isCrossDomainTouch": bool(slot.get("isCrossDomainTouch", False)),
        "allowedFatigueProfiles": _encode_allowed_fatigue(fatigue),
    }


def _base_session(index: int, session_type: str, name: str, templates: list) -> dict:
    """Envelope shared by the session and plan variants (they differ only by
    ``composition`` vs ``filledSlots``)."""
    return {
        "sessionType": session_type,
        "name": name,
        "slots": templates,
        "siblingIndex": 0,
        "position": index,
    }


def encode_response(solution: dict) -> dict:
    """Encode a solved program to the canonical v1 response shape.

    ``solution`` keys: universe (list of SynthesisSlot dicts in universe order),
    activeSlotIdsBySession (list of ordered id lists), setCounts (map
    (sessionIndex, universeId) -> sets), cycleSlotIndexByPosition (list),
    regionFilterBySession (list), objective (10-key breakdown dict).
    """
    universe = solution["universe"]
    by_id = {s["id"]: s for s in universe}
    session_count = len(solution["activeSlotIdsBySession"])
    sessions = []
    plans = []
    for i in range(session_count):
        universe_ids = [
            uid
            for uid in solution["activeSlotIdsBySession"][i]
            if uid in by_id
            and solution["setCounts"].get((i, uid), 0) > 0
        ]
        region = solution["regionFilterBySession"][i]
        session_type = session_type_for_region_filter(region)
        name = D.placeholder_workout_name(i)
        templates = [_encode_slot_template(by_id[uid], i) for uid in universe_ids]
        filled = []
        for uid, template in zip(universe_ids, templates):
            filled.append(
                dict(template, setCount=solution["setCounts"][(i, uid)])
            )
        base = _base_session(i, session_type, name, templates)
        sessions.append(dict(base, composition=None))
        plans.append(dict(base, filledSlots=filled))
    objective = solution["objective"]
    return {
        "modelVersion": MODEL_VERSION_V1,
        "sessions": sessions,
        "plans": plans,
        "cycleSlotIndexByPosition": list(solution["cycleSlotIndexByPosition"]),
        "objective": {
            "total": int(objective["total"]),
            "recoveryPenalty": int(objective["recoveryPenalty"]),
            "specializedMevShortfall": int(objective["specializedMevShortfall"]),
            "coSpecializationImbalance": int(objective["coSpecializationImbalance"]),
            "specializeAimDeviation": int(objective["specializeAimDeviation"]),
            "maintenanceAimDeviation": int(objective["maintenanceAimDeviation"]),
            "minimumVolumeAimDeviation": int(objective["minimumVolumeAimDeviation"]),
            "roleMinimumShortfall": int(objective["roleMinimumShortfall"]),
            "pushPullImbalance": int(objective["pushPullImbalance"]),
            "sessionFillShortfall": int(objective["sessionFillShortfall"]),
        },
        "activeSlotIdsBySession": [
            list(solution["activeSlotIdsBySession"][i]) for i in range(session_count)
        ],
        "regionFilterBySession": list(solution["regionFilterBySession"]),
    }

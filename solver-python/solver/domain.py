"""Stratum program-synthesis solver service (STR-45).

Cloud Run HTTP service: POST /solve/v1 accepts a v1 request JSON body and
returns a v1 response JSON body. GET /health for liveness.

Full CP-SAT model construction lives in model.py using the OR-Tools Python API.
This module holds the domain constants/tables ported EXACTLY from committed
Kotlin (main @ ef470aa3) plus the STR-39 behavior change (see SM_* below).

Threshold/weight provenance (do NOT retune without a contract+ticket change):
  MuscleRecoveryReference.kt  - dose tiers, recovery hours matrix, mass/length surcharges
  RpReferenceTables.kt        - session/muscle caps, static secondary credits, direct floors
  SessionLengthBudget.kt      - session aim/max bands
  SynthesisObjectiveWeights.kt- tiered objective weights
  ProgramSynthesisModel.kt    - solver-only constants (caps, slack bounds, penalties)
  SlotLibrary/SessionTypeCatalog/CrossDomainTouches/SpecializedVariants -
    slot universe construction
"""

from __future__ import annotations

import math

# --------------------------------------------------------------------------
# Credit scale (VolumeAllocationCoefficients.SCALE)
# --------------------------------------------------------------------------

SCALE = 100

PRIMARY_SET_CREDIT = 1.0
SECONDARY_SET_CREDIT = 0.5
MINUTES_PER_HARD_SET = 3.0

# --------------------------------------------------------------------------
# Dose tiers (MuscleRecoveryReference.kt) — FROZEN, see STR-39 note below.
# --------------------------------------------------------------------------

MUSCLE_RECOVERY_LOW_DOSE_MAX = 3.0
MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE = 2.0
MUSCLE_RECOVERY_MODERATE_DOSE_MAX = 7.0
MUSCLE_RECOVERY_MODERATE_DOSE = 8.0
MUSCLE_RECOVERY_HEAVY_DOSE = 12.0

LARGE_COMPOUND_MUSCLES = frozenset({"Chest", "Back", "Quad", "Hamstring", "Glute"})


def is_large_compound_muscle(muscle: str) -> bool:
    return muscle in LARGE_COMPOUND_MUSCLES


# --------------------------------------------------------------------------
# STR-39 (fresh implementation from the issue text — NOT ported from the old
# Kotlin fix branch):
#   1. Meaningful-exposure floor of 1.0 for small-muscle isolation: a single
#      hard set counts as meaningful exposure.
#   2. Low tier (1-3 sets) triggers the 48h adjacent-day hard forbid for
#      small-muscle isolation.
#
# Interpretation applied here: for muscles OUTSIDE LARGE_COMPOUND_MUSCLES, a
# session whose dominant stress class is isolation and whose total credit lies
# in [1.0, LOW_DOSE_MAX] hard-forbids a meaningful repeat of that muscle on a
# calendar-adjacent training day. The shared 2.0 meaningful floor used by soft
# penalties / frequency / large-muscle rules is UNCHANGED.
# --------------------------------------------------------------------------

STR39_SMALL_MUSCLE_MEANINGFUL_FLOOR = 1.0

# --------------------------------------------------------------------------
# Caps / floors (RpReferenceTables.kt + ProgramSynthesisModel companion)
# --------------------------------------------------------------------------

SESSION_MUSCLE_SET_CAP = 11
SETS_PER_LIFT_CAP = 5
MIN_SPECIALIZED_DIRECT_SETS = 2
MIN_TOUCH_DIRECT_SETS = 2

COMPOUND_SETS_PER_SLOT_CAP = 4
ANCHOR_MIN_SETS = 2
SECONDARY_COMPOUND_MIN_SETS = 2
AIM_EQUALITY_TOLERANCE = 0.5
MAX_OVER = 1000
CREDIT_DEVIATION_SLACK_MAX = 1_000_000
RECOVERY_SLACK_CAP = 10_000
RECOVERY_GAP_PENALTY_BASE = 80
CALENDAR_ADJACENT_PENALTY_MULTIPLIER = 10
MIN_ACTIVE_LIFTS_PER_SESSION = 3
OMITTED_MUSCLE_INCIDENTAL_CEILING = 6.0
MIN_SETS_PER_ACTIVE_SLOT = 2

LOW_TIER_REPRESENTATIVE_DOSE = MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE  # 2.0
MODERATE_TIER_REPRESENTATIVE_DOSE = 5.0
HIGH_TIER_REPRESENTATIVE_DOSE = MUSCLE_RECOVERY_MODERATE_DOSE_MAX + 1.0  # 8.0

# First credit in the moderate tier: strictly above LOW_DOSE_MAX (scaled).
MODERATE_DOSE_THRESHOLD_SCALED = int(MUSCLE_RECOVERY_LOW_DOSE_MAX * SCALE) + 1  # 301

LOWER_LEG_ANCHOR_PATTERNS = frozenset({"SquatPattern", "HipHinge"})

ANTAGONIST_MAINTENANCE_PAIRS = (
    ("Chest", "Back", "chest_back"),  # (weaker, stronger, label)
    ("Quad", "Hamstring", "quad_ham"),
)
SYMMETRIC_MAINTENANCE_PAIRS = (("Bicep", "Tricep", "bicep_tricep"),)
UPPER_BODY_MAINTENANCE_PARITY_GROUP = frozenset(
    {"Chest", "Back", "Bicep", "Tricep", "LateralDeltoid"}
)
LOWER_BODY_MAINTENANCE_PARITY_GROUP = frozenset({"Quad", "Hamstring", "Calf"})

PUSH_PATTERNS = frozenset({"HorizontalPress", "VerticalPress", "InclinePress"})
PULL_PATTERNS = frozenset({"HorizontalPull", "VerticalPull"})

ALWAYS_AIM_MUSCLES = frozenset(
    {"Chest", "Back", "Bicep", "Tricep", "LateralDeltoid", "Quad", "Hamstring"}
)

LOWER_BODY_MUSCLES = frozenset({"Quad", "Hamstring", "Glute", "Calf", "LowerBack"})

MAJOR_COMPOUND_PATTERNS = frozenset(
    {
        "SquatPattern",
        "HipHinge",
        "HorizontalPress",
        "InclinePress",
        "VerticalPress",
        "HorizontalPull",
        "VerticalPull",
    }
)

LOWER_PRIMARY_PATTERNS = frozenset(
    {
        "SquatPattern",
        "HipHinge",
        "HipExtension",
        "KneeExtension",
        "KneeFlexion",
        "PlantarFlexion",
    }
)


def muscle_training_domain(muscle: str) -> str:
    return "Lower" if muscle in LOWER_BODY_MUSCLES else "Upper"


# --------------------------------------------------------------------------
# Objective weights (SynthesisObjectiveWeights.kt) — FROZEN.
# --------------------------------------------------------------------------

W_RECOVERY_PRIORITY = 3_300_000
W_RECOVERY_GENERAL = 300_000
W_SPECIALIZED_MIN_SHORTFALL = 10_000
W_CO_SPECIALIZATION_BALANCE = 1_000
W_ANTAGONIST_PAIR_CLOSENESS = 5_100
W_ANTAGONIST_WRONG_WAY_EXCESS = 8_000
W_ANTAGONIST_RIGHT_WAY_EXCESS = 300
W_SYMMETRIC_PAIR_CLOSENESS = 1_500
W_UPPER_BODY_MAINTENANCE_PARITY = 800
W_LOWER_BODY_MAINTENANCE_PARITY = 500
W_SPECIALIZE_AIM_DEVIATION = 4_900
W_MINIMUM_VOLUME_AIM_DEVIATION = 80
W_MAINTENANCE_AIM_DEVIATION = 3_700
W_FREQUENCY_SHORTFALL = 12_000
W_SLOT_ACTIVATION_COUNT = 650
W_LOWER_ANCHOR_ACTIVATION_BONUS = -550
W_PUSH_PULL_IMBALANCE = 200
W_SESSION_FILL_SHORTFALL = 6_500
W_SESSION_SET_IMBALANCE = 1_900
W_MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE = 5_500
W_MAINTENANCE_POSTERIOR_AIM_DEVIATION = 500


# --------------------------------------------------------------------------
# Static secondary credits (PATTERN_SECONDARY_MUSCLE_CREDITS)
# --------------------------------------------------------------------------

STATIC_SECONDARY_CREDITS: dict[str, list[tuple[str, float]]] = {
    "SquatPattern": [("Glute", SECONDARY_SET_CREDIT)],
    "HipHinge": [
        ("Glute", SECONDARY_SET_CREDIT),
        ("Hamstring", SECONDARY_SET_CREDIT),
        ("LowerBack", SECONDARY_SET_CREDIT),
    ],
    "HipExtension": [("Hamstring", SECONDARY_SET_CREDIT)],
    "VerticalPull": [("Bicep", SECONDARY_SET_CREDIT)],
    "HorizontalPull": [
        ("Bicep", SECONDARY_SET_CREDIT),
        ("PosteriorDeltoid", SECONDARY_SET_CREDIT),
    ],
    "HorizontalPress": [
        ("Tricep", SECONDARY_SET_CREDIT),
        ("AnteriorDeltoid", SECONDARY_SET_CREDIT),
    ],
    "InclinePress": [
        ("Tricep", SECONDARY_SET_CREDIT),
        ("AnteriorDeltoid", SECONDARY_SET_CREDIT),
    ],
    "VerticalPress": [
        ("Tricep", SECONDARY_SET_CREDIT),
        ("AnteriorDeltoid", SECONDARY_SET_CREDIT),
    ],
}


def compute_pattern_secondary_credits(
    lifts_by_id: dict, enabled_lift_ids: set
) -> dict[str, list[tuple[str, float]]]:
    """Catalog-averaged secondary credits, merged over static fallbacks.

    Port of computePatternSecondaryCredits: group ENABLED lifts by each pattern
    in their movementPatterns set; per pattern, each lift contributes
    SECONDARY_SET_CREDIT for each secondary muscle not also primary; average
    over lift count. Patterns with no enabled lifts keep the static estimate.
    """
    lifts_by_pattern: dict[str, list[dict]] = {}
    for lift_id, lift in lifts_by_id.items():
        if lift_id not in enabled_lift_ids:
            continue
        for pattern in lift.get("movementPatterns", []):
            lifts_by_pattern.setdefault(pattern, []).append(lift)
    averaged: dict[str, list[tuple[str, float]]] = {}
    for pattern, lifts in lifts_by_pattern.items():
        if not lifts:
            continue
        totals: dict[str, float] = {}
        for lift in lifts:
            primaries = set(lift.get("primaryMuscles", []))
            for muscle in lift.get("secondaryMuscles", []):
                if muscle in primaries:
                    continue
                totals[muscle] = totals.get(muscle, 0.0) + SECONDARY_SET_CREDIT
        count = float(len(lifts))
        entries = [(m, t / count) for m, t in totals.items()]
        if entries:
            averaged[pattern] = entries
    merged = {k: list(v) for k, v in STATIC_SECONDARY_CREDITS.items()}
    merged.update(averaged)
    return merged


# --------------------------------------------------------------------------
# Session length budget (SessionLengthBudget.kt)
# --------------------------------------------------------------------------

SESSION_BAND_AIM_SHORT = 30
SESSION_BAND_AIM_MEDIUM = 50
SESSION_BAND_AIM_LONG = 75
SESSION_BAND_MAX_SHORT = 40
SESSION_BAND_MAX_MEDIUM = 60
SESSION_BAND_MAX_LONG = 90


def session_length_budget(session_length_minutes) -> tuple[int, int] | None:
    """Returns (aimMinutes, maxMinutes) or None when unset."""
    if session_length_minutes is None or session_length_minutes <= 0:
        return None
    if session_length_minutes == SESSION_BAND_AIM_SHORT:
        return (SESSION_BAND_AIM_SHORT, SESSION_BAND_MAX_SHORT)
    if session_length_minutes == SESSION_BAND_AIM_MEDIUM:
        return (SESSION_BAND_AIM_MEDIUM, SESSION_BAND_MAX_MEDIUM)
    if session_length_minutes == SESSION_BAND_AIM_LONG:
        return (SESSION_BAND_AIM_LONG, SESSION_BAND_MAX_LONG)
    return (session_length_minutes, session_length_minutes)


def session_hard_set_cap(session_length_minutes) -> int | None:
    b = session_length_budget(session_length_minutes)
    if b is None:
        return None
    return math.floor(b[1] / MINUTES_PER_HARD_SET)


def session_aim_hard_sets(session_length_minutes) -> int | None:
    b = session_length_budget(session_length_minutes)
    if b is None:
        return None
    return math.floor(b[0] / MINUTES_PER_HARD_SET)


# --------------------------------------------------------------------------
# Recovery hours (MuscleRecoveryReference.kt) — FROZEN.
# --------------------------------------------------------------------------

_RECOVERY_BASE_HOURS = {
    # stressClass -> {Low, Moderate, High}
    "Isolation": {"Low": 24, "Moderate": 48, "High": 72},
    "UpperCompound": {"Low": 48, "Moderate": 72, "High": 72},
    "HeavyAxial": {"Low": 48, "Moderate": 72, "High": 96},
}

_MUSCLE_MASS_PENALTY_HOURS = 12
_LENGTHENED_STATE_PENALTY_HOURS = 12


def _volume_tier(dose: float) -> str | None:
    if dose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE:
        return None
    if dose <= MUSCLE_RECOVERY_LOW_DOSE_MAX:
        return "Low"
    if dose <= MUSCLE_RECOVERY_MODERATE_DOSE_MAX:
        return "Moderate"
    return "High"


def recovery_hours(
    earlier_dose: float,
    stress_class: str,
    muscle: str | None = None,
    muscle_length_bias: str | None = None,
    secondary_only: bool = False,
) -> int:
    tier = _volume_tier(earlier_dose)
    if tier is None:
        return 0
    base = _RECOVERY_BASE_HOURS[stress_class][tier]
    if stress_class != "Isolation":
        return base
    adjusted = base
    if tier == "Low" and not secondary_only and muscle in LARGE_COMPOUND_MUSCLES:
        adjusted += _MUSCLE_MASS_PENALTY_HOURS
    if muscle == "Hamstring":
        if muscle_length_bias == "Lengthened":
            adjusted += _LENGTHENED_STATE_PENALTY_HOURS
        elif muscle_length_bias == "Shortened":
            adjusted -= _LENGTHENED_STATE_PENALTY_HOURS
    return max(0, adjusted)


def minimum_recovery_calendar_gap(
    fractional_sets: float,
    stress_class: str,
    muscle: str | None = None,
    muscle_length_bias: str | None = None,
    primary_fractional_sets: float | None = None,
) -> int:
    if primary_fractional_sets is None:
        primary_fractional_sets = fractional_sets
    secondary_only = primary_fractional_sets < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
    hours = recovery_hours(
        fractional_sets, stress_class, muscle, muscle_length_bias, secondary_only
    )
    if hours <= 0:
        return 0
    return math.ceil(hours / 24.0)


# --------------------------------------------------------------------------
# Cycle space (MaterializeGeneratedCycleSpace.kt + GenerationCycleDefaults)
# --------------------------------------------------------------------------

MAX_GEN_TRAINING_DAYS = 6
CLASSIC_WEEK_CYCLE_LENGTH = 7
SIX_DAY_ROTATING_CYCLE_LENGTH = 8


def _default_cycle_length(training_day_count: int) -> int:
    if training_day_count == MAX_GEN_TRAINING_DAYS:
        return SIX_DAY_ROTATING_CYCLE_LENGTH
    if 1 <= training_day_count < MAX_GEN_TRAINING_DAYS:
        return CLASSIC_WEEK_CYCLE_LENGTH
    return training_day_count


def _classic_seven_day_default_rests(training_day_count: int) -> set[int] | None:
    return {
        1: {1, 2, 3, 4, 5, 6},
        2: {1, 2, 4, 5, 6},
        3: {1, 3, 5, 6},
        4: {2, 5, 6},
        5: {2, 6},
    }.get(training_day_count)


def _default_rest_slot_indices(cycle_length: int, training_day_count: int) -> set[int]:
    rest_count = max(0, cycle_length - training_day_count)
    if rest_count == 0 or cycle_length <= 0:
        return set()
    if training_day_count == MAX_GEN_TRAINING_DAYS:
        if cycle_length == SIX_DAY_ROTATING_CYCLE_LENGTH:
            return {3, 7}
        if cycle_length == CLASSIC_WEEK_CYCLE_LENGTH:
            return {6}
    if cycle_length == CLASSIC_WEEK_CYCLE_LENGTH and 1 <= training_day_count < MAX_GEN_TRAINING_DAYS:
        classic = _classic_seven_day_default_rests(training_day_count)
        if classic is not None:
            return set(classic)
    if rest_count == 1:
        return {cycle_length - 1}
    return set(range(cycle_length - rest_count, cycle_length))


def materialize_cycle_space(
    training_slot_count: int,
    rest_slot_indices_hint: set[int] | None = None,
    cycle_length_hint: int | None = None,
) -> tuple[int, set[int]]:
    """Returns (cycleLength, restSlotIndices). Port of materializeGeneratedCycleSpace."""
    if training_slot_count <= 0:
        raise ValueError(f"trainingSlotCount must be positive, was {training_slot_count}")
    if rest_slot_indices_hint is not None:
        cycle_length = (
            cycle_length_hint
            if cycle_length_hint is not None
            else (training_slot_count + len(rest_slot_indices_hint))
        )
        in_range = cycle_length > 0 and all(
            0 <= i < cycle_length for i in rest_slot_indices_hint
        )
        if in_range and cycle_length - len(rest_slot_indices_hint) == training_slot_count:
            return (cycle_length, set(rest_slot_indices_hint))
    if cycle_length_hint is not None and cycle_length_hint == training_slot_count:
        return (cycle_length_hint, set())
    if 1 <= training_slot_count <= MAX_GEN_TRAINING_DAYS:
        length = (
            cycle_length_hint
            if cycle_length_hint is not None
            else _default_cycle_length(training_slot_count)
        )
        rests = _default_rest_slot_indices(length, training_slot_count)
        if length - len(rests) == training_slot_count:
            return (length, rests)
    return (training_slot_count, set())


# --------------------------------------------------------------------------
# TrainingTimeline (TrainingTimeline.kt)
# --------------------------------------------------------------------------


class TrainingTimeline:
    def __init__(self, cycle_length: int, training_slot_indices: list[int]):
        if cycle_length <= 0:
            raise ValueError(f"cycleLength must be positive, was {cycle_length}")
        if any(i < 0 or i >= cycle_length for i in training_slot_indices):
            raise ValueError(
                f"trainingSlotIndices {training_slot_indices} must all lie in "
                f"[0, {cycle_length})"
            )
        if len(set(training_slot_indices)) != len(training_slot_indices):
            raise ValueError(
                f"trainingSlotIndices must be unique: {training_slot_indices}"
            )
        self.cycle_length = cycle_length
        self.training_slot_indices = list(training_slot_indices)

    @classmethod
    def resolve(
        cls,
        training_slot_count: int,
        rest_slot_indices: set[int] | None = None,
        cycle_length: int | None = None,
    ) -> "TrainingTimeline":
        length, rests = materialize_cycle_space(
            training_slot_count, rest_slot_indices, cycle_length
        )
        train = sorted(s for s in range(length) if s not in rests)
        return cls(length, train)

    @property
    def rest_slot_indices(self) -> set[int]:
        train = set(self.training_slot_indices)
        return {i for i in range(self.cycle_length) if i not in train}

    def gap(self, earlier_slot: int, later_slot: int) -> int:
        if self.cycle_length <= 0:
            return 0
        return (later_slot - earlier_slot + self.cycle_length) % self.cycle_length

    def pairs_with_max_gap(self, max_gap: int) -> list[tuple[int, int]]:
        if max_gap < 1 or len(self.training_slot_indices) < 2:
            return []
        return [
            (e, l)
            for e in self.training_slot_indices
            for l in self.training_slot_indices
            if e != l and 1 <= self.gap(e, l) <= max_gap
        ]

    def adjacent_training_pairs(self) -> list[tuple[int, int]]:
        return [(e, l) for (e, l) in self.pairs_with_max_gap(1) if self.gap(e, l) == 1]

    def is_rest_sandwiched(self, slot: int) -> bool:
        train = set(self.training_slot_indices)
        if slot not in train:
            return False
        prev = (slot - 1 + self.cycle_length) % self.cycle_length
        nxt = (slot + 1) % self.cycle_length
        return prev not in train and nxt not in train


# --------------------------------------------------------------------------
# Region filters (SessionRegionFilterPolicy.kt + SlotRegionCompatibility.kt)
# --------------------------------------------------------------------------

# Ordinal freeze: UpperOnly = 0, LowerOnly = 1, Unrestricted = 2.
REGION_FILTERS = ("UpperOnly", "LowerOnly", "Unrestricted")
REGION_FILTER_ORDINAL = {name: i for i, name in enumerate(REGION_FILTERS)}


def region_filters_by_session_position(
    timeline: TrainingTimeline,
) -> list[set[str]]:
    if not timeline.training_slot_indices:
        return []
    out = []
    for slot in timeline.training_slot_indices:
        if timeline.is_rest_sandwiched(slot):
            out.append({"Unrestricted"})
        else:
            out.append({"Unrestricted", "UpperOnly", "LowerOnly"})
    return out


def allowed_region_filters_for_pattern(
    pattern: str, primary_muscle: str, is_cross_domain_touch: bool
) -> set[str]:
    if is_cross_domain_touch:
        if muscle_training_domain(primary_muscle) == "Lower":
            return {"UpperOnly", "Unrestricted"}
        return {"LowerOnly", "Unrestricted"}
    if pattern in LOWER_PRIMARY_PATTERNS or muscle_training_domain(primary_muscle) == "Lower":
        return {"LowerOnly", "Unrestricted"}
    return {"UpperOnly", "Unrestricted"}


def session_type_label_for_region_filter(region_filter: str) -> str:
    return {
        "UpperOnly": "Upper",
        "LowerOnly": "Lower",
        "Unrestricted": "FullBody",
    }[region_filter]


def placeholder_workout_name(position: int) -> str:
    return f"Session {position + 1}"


# --------------------------------------------------------------------------
# Slot catalog (SessionTypeCatalog.kt). Tuples:
# (slotId, role, pattern, primaryMuscle, repProfile,
#  lengthBias|None, siblingVariant|None, allowedFatigue|None)
# --------------------------------------------------------------------------

_LOWER_SLOTS = [
    ("squat", "Anchor", "SquatPattern", "Quad", "LowerFreeWeightCompound", None, None, None),
    ("hinge", "Anchor", "HipHinge", "Hamstring", "LowerFreeWeightCompound", None, None, None),
    ("quad-iso", "Accessory", "KneeExtension", "Quad", "Isolation", None, None, ("Low", "Medium")),
    ("ham-iso", "Accessory", "KneeFlexion", "Hamstring", "Isolation", None, None, ("Low", "Medium")),
    ("calf", "Optional", "PlantarFlexion", "Calf", "Isolation", None, None, None),
    ("glute", "Anchor", "HipExtension", "Glute", "LowerFreeWeightCompound", None, None, None),
]

_UPPER_SLOTS = [
    ("h-press", "Anchor", "HorizontalPress", "Chest", "LowerMachineOrUpperPress", None, None, None),
    ("v-pull", "SecondaryCompound", "VerticalPull", "Back", "UpperMachineOrPull", None, "A", None),
    ("h-pull", "SecondaryCompound", "HorizontalPull", "Back", "UpperMachineOrPull", "Lengthened", "A", None),
    ("incline", "SecondaryCompound", "InclinePress", "Chest", "LowerMachineOrUpperPress", None, "B", None),
    ("lat-raise", "Accessory", "ShoulderAbduction", "LateralDeltoid", "Isolation", None, None, None),
    ("bicep", "Accessory", "ElbowFlexion", "Bicep", "Isolation", None, None, None),
    ("tricep", "Accessory", "ElbowExtension", "Tricep", "Isolation", None, None, None),
    ("rear-delt", "Optional", "ShoulderHorizontalAbduction", "PosteriorDeltoid", "Isolation", None, None, None),
]

_UPPER_PUSH_SLOTS = [
    ("h-press", "Anchor", "HorizontalPress", "Chest", "LowerMachineOrUpperPress", None, None, None),
    ("v-press", "SecondaryCompound", "VerticalPress", "AnteriorDeltoid", "LowerMachineOrUpperPress", None, None, None),
    ("incline", "SecondaryCompound", "InclinePress", "Chest", "LowerMachineOrUpperPress", None, None, None),
    ("lat-raise", "Accessory", "ShoulderAbduction", "LateralDeltoid", "Isolation", None, None, None),
    ("tricep", "Accessory", "ElbowExtension", "Tricep", "Isolation", None, None, None),
]

_UPPER_PULL_SLOTS = [
    ("v-pull", "Anchor", "VerticalPull", "Back", "UpperMachineOrPull", None, None, None),
    ("h-pull", "SecondaryCompound", "HorizontalPull", "Back", "UpperMachineOrPull", "Lengthened", None, None),
    ("rear-delt", "Accessory", "ShoulderHorizontalAbduction", "PosteriorDeltoid", "Isolation", None, None, None),
    ("bicep", "Accessory", "ElbowFlexion", "Bicep", "Isolation", None, None, None),
]

_FULL_BODY_SLOTS = [
    ("squat", "Anchor", "SquatPattern", "Quad", "LowerFreeWeightCompound", None, None, None),
    ("h-press", "Anchor", "HorizontalPress", "Chest", "LowerMachineOrUpperPress", None, None, None),
    ("h-pull", "SecondaryCompound", "HorizontalPull", "Back", "UpperMachineOrPull", None, None, None),
    ("hinge", "SecondaryCompound", "HipHinge", "Hamstring", "LowerFreeWeightCompound", None, None, None),
]


def _slot_key(slot: dict) -> str:
    return "|".join(
        [
            slot["pattern"],
            slot["primaryMuscle"],
            slot["role"],
            str(slot["isCrossDomainTouch"]).lower(),
            slot["siblingVariant"] or "Any",
        ]
    )


def _make_slot(t) -> dict:
    (slot_id, role, pattern, muscle, rep, length_bias, sibling, fatigue) = t
    return {
        "slotId": slot_id,
        "role": role,
        "pattern": pattern,
        "primaryMuscle": muscle,
        "repProfile": rep,
        "lengthBiasPreference": length_bias,
        "siblingVariant": sibling,
        "isCrossDomainTouch": False,
        "allowedFatigueProfiles": list(fatigue) if fatigue is not None else None,
    }


def base_slot_library() -> list[dict]:
    """Port of baseSlotLibrary: UPPER + LOWER + FULL_BODY + UPPER_PUSH +
    UPPER_PULL, first-wins dedup by library key (former SOURCE_SESSION_TYPES order)."""
    by_key: dict[str, dict] = {}
    for t in _UPPER_SLOTS + _LOWER_SLOTS + _FULL_BODY_SLOTS + _UPPER_PUSH_SLOTS + _UPPER_PULL_SLOTS:
        slot = _make_slot(t)
        key = _slot_key(slot)
        if key not in by_key:
            by_key[key] = slot
    return list(by_key.values())


def slot_library_key(slot: dict) -> str:
    return _slot_key(slot)


# --------------------------------------------------------------------------
# Cross-domain touches (CrossDomainTouches.kt)
# --------------------------------------------------------------------------

_TOUCH_ISOLATION_PATTERNS: dict[str, list[str]] = {
    "Bicep": ["ElbowFlexion"],
    "Tricep": ["ElbowExtension"],
    "Calf": ["PlantarFlexion"],
    "LateralDeltoid": ["ShoulderAbduction"],
    "PosteriorDeltoid": ["ShoulderHorizontalAbduction"],
    "AnteriorDeltoid": ["VerticalPress"],
    "Chest": ["HorizontalPress", "InclinePress"],
    "Back": ["HorizontalPull", "VerticalPull"],
    "Quad": ["KneeExtension", "SquatPattern"],
    "Hamstring": ["KneeFlexion", "HipHinge"],
    "Glute": ["HipExtension", "HipHinge"],
}

_MAX_TOUCH_SLOTS_PER_SESSION = 2
_MAX_TOUCH_SLOTS_ON_LOWER_WITH_IN_DOMAIN_SPEC = 1

_NON_TOUCH_FALLBACK_EXCLUDED = {
    "SquatPattern",
    "HipHinge",
    "HorizontalPress",
    "VerticalPress",
    "InclinePress",
}


def available_patterns_by_muscle(context: dict) -> dict[str, list[str]]:
    """Port of availablePatternsByMuscle: singular movementPattern per lift.

    Insertion-ordered (like Kotlin's LinkedHashSet-backed lists) — the touch
    fallback in _resolve_touch_pattern takes the first non-excluded pattern in
    this order, so sorting here would diverge from Kotlin.
    """
    by_muscle: dict[str, list[str]] = {}
    for lift in context["liftsById"].values():
        for muscle in list(lift.get("primaryMuscles", [])) + list(
            lift.get("secondaryMuscles", [])
        ):
            pattern = lift.get("movementPattern")
            if pattern:
                bucket = by_muscle.setdefault(muscle, [])
                if pattern not in bucket:
                    bucket.append(pattern)
    return by_muscle


def _resolve_touch_pattern(muscle: str, patterns_by_muscle: dict) -> str | None:
    available = list(patterns_by_muscle.get(muscle, []))
    if not available:
        return None
    available_set = set(available)
    for preferred in _TOUCH_ISOLATION_PATTERNS.get(muscle, []):
        if preferred in available_set:
            return preferred
    for pattern in available:
        if pattern not in _NON_TOUCH_FALLBACK_EXCLUDED:
            return pattern
    return available[0]


def _max_touch_slots_for_domain(
    session_domain: str | None, slots: list[dict], context: dict
) -> int:
    if session_domain != "Lower":
        return _MAX_TOUCH_SLOTS_PER_SESSION
    has_in_domain_specialized = any(
        not s["isCrossDomainTouch"]
        and context["specialization"].get(s["primaryMuscle"]) == "Specialize"
        for s in slots
    )
    if has_in_domain_specialized:
        return _MAX_TOUCH_SLOTS_ON_LOWER_WITH_IN_DOMAIN_SPEC
    return _MAX_TOUCH_SLOTS_PER_SESSION


def _allows_calf_cross_domain_touch(
    session_domain: str | None, session_position: int | None, context: dict
) -> bool:
    if context["trainingDaysPerWeek"] < 5:
        return True
    if session_domain != "Upper":
        return True
    if session_position is None:
        return True
    return session_position == 1


def _inject_touch_slots(
    slots: list[dict],
    context: dict,
    session_domain: str | None,
    session_position: int | None = None,
) -> None:
    session_primaries = {s["primaryMuscle"] for s in slots}
    patterns_by_muscle = available_patterns_by_muscle(context)
    touch_count = sum(1 for s in slots if s["isCrossDomainTouch"])
    budget = _max_touch_slots_for_domain(session_domain, slots, context)
    for muscle, level in context["specialization"].items():
        if level != "Specialize":
            continue
        if muscle in session_primaries:
            continue
        if muscle == "Calf" and not _allows_calf_cross_domain_touch(
            session_domain, session_position, context
        ):
            continue
        if touch_count >= budget:
            break
        if any(s["isCrossDomainTouch"] and s["primaryMuscle"] == muscle for s in slots):
            continue
        pattern = _resolve_touch_pattern(muscle, patterns_by_muscle)
        if pattern is None:
            continue
        slot_id = f"touch-{muscle.lower()}"
        if any(s["slotId"] == slot_id for s in slots):
            continue
        touch_slot = {
            "slotId": slot_id,
            "role": "Accessory",
            "pattern": pattern,
            "primaryMuscle": muscle,
            "repProfile": "Isolation",
            "lengthBiasPreference": None,
            "siblingVariant": None,
            "isCrossDomainTouch": True,
            "allowedFatigueProfiles": None,
        }
        slots.insert(_touch_slot_insert_index(slots, muscle, pattern), touch_slot)
        touch_count += 1


def _touch_slot_insert_index(slots: list[dict], muscle: str, pattern: str) -> int:
    """Port of touchSlotInsertIndex: insert after a compatible anchor to preserve
    antagonistic interleaving (order only affects search heuristics, not feasibility)."""
    if muscle in ("Bicep", "Back"):
        anchor_pattern = "HipHinge"
    elif muscle == "Tricep":
        anchor_pattern = "SquatPattern"
    elif muscle in ("Quad", "Hamstring", "Glute", "Calf"):
        anchor_pattern = "HorizontalPress"
    elif muscle in ("Chest", "AnteriorDeltoid", "LateralDeltoid", "PosteriorDeltoid"):
        anchor_pattern = "HipHinge"
    else:
        anchor_pattern = None
    if anchor_pattern is not None:
        anchor_idx = max(
            (i for i, s in enumerate(slots) if s["pattern"] == anchor_pattern),
            default=-1,
        )
        if anchor_idx >= 0:
            return anchor_idx + 1
    last_compound = max(
        (
            i
            for i, s in enumerate(slots)
            if s["role"] in ("Anchor", "SecondaryCompound")
        ),
        default=-1,
    )
    if last_compound >= 0:
        return last_compound + 1
    return len(slots)


def cross_domain_touches(context: dict) -> list[dict]:
    """Port of crossDomainTouches: touch slots from five domain shells."""
    shells = [
        ([dict(s) for s in map(_make_slot, _UPPER_SLOTS)], "Upper"),
        ([dict(s) for s in map(_make_slot, _LOWER_SLOTS)], "Lower"),
        ([dict(s) for s in map(_make_slot, _FULL_BODY_SLOTS)], None),
        ([dict(s) for s in map(_make_slot, _UPPER_PUSH_SLOTS)], "Upper"),
        ([dict(s) for s in map(_make_slot, _UPPER_PULL_SLOTS)], "Upper"),
    ]
    by_key: dict[str, dict] = {}
    for templates, domain in shells:
        promote_specialized_slots(templates, context)
        _inject_touch_slots(templates, context, domain, session_position=0)
        for slot in templates:
            if not slot["isCrossDomainTouch"]:
                continue
            key = _slot_key(slot)
            if key not in by_key:
                by_key[key] = slot
    return list(by_key.values())


# --------------------------------------------------------------------------
# Specialized variants (SpecializedVariants.kt + promoteSpecializedSlots)
# --------------------------------------------------------------------------


def promote_specialized_slots(slots: list[dict], context: dict) -> None:
    for i, slot in enumerate(slots):
        if slot["role"] != "Optional":
            continue
        if context["specialization"].get(slot["primaryMuscle"]) == "Specialize":
            slots[i] = dict(slot, role="Accessory")


def _has_multiple_lift_options(pattern: str, muscle: str, context: dict) -> bool:
    count = 0
    for lift_id, lift in context["liftsById"].items():
        if lift_id not in context["enabledLiftIds"]:
            continue
        if lift.get("movementPattern") != pattern and pattern not in lift.get(
            "movementPatterns", []
        ):
            continue
        if muscle not in lift.get("primaryMuscles", []):
            continue
        count += 1
        if count >= 2:
            return True
    return False


def _alternate_length_bias(current: str | None) -> str:
    if current in (None, "Any", "MidRange"):
        return "Lengthened"
    if current == "Lengthened":
        return "Shortened"
    return "Lengthened"  # Shortened -> Lengthened


def inject_specialized_variant_slots(slots: list[dict], context: dict) -> None:
    insertions: list[tuple[int, dict]] = []
    for muscle, level in context["specialization"].items():
        if level != "Specialize":
            continue
        in_domain = [
            s for s in slots if s["primaryMuscle"] == muscle and not s["isCrossDomainTouch"]
        ]
        if len(in_domain) != 1:
            continue
        primary = in_domain[0]
        if primary["role"] not in ("Accessory", "Anchor"):
            continue
        if not _has_multiple_lift_options(primary["pattern"], muscle, context):
            continue
        variant_id = f"{primary['slotId']}-alt"
        if any(s["slotId"] == variant_id for s in slots):
            continue
        primary_index = next(
            (i for i, s in enumerate(slots) if s["slotId"] == primary["slotId"]), -1
        )
        if primary_index < 0:
            continue
        variant = dict(
            primary,
            slotId=variant_id,
            lengthBiasPreference=_alternate_length_bias(
                primary["lengthBiasPreference"]
            ),
            siblingVariant="B",
        )
        insertions.append((primary_index, variant))
    insertions.sort(key=lambda iv: iv[0], reverse=True)
    for index, variant in insertions:
        slots.insert(index + 1, variant)


# --------------------------------------------------------------------------
# Universe (SlotUniverseBuilder.kt)
# --------------------------------------------------------------------------

MAX_UNIVERSE_SLOTS = 80

_STRICT_ISOLATION_TOUCH_PATTERNS = frozenset(
    {"ElbowFlexion", "ElbowExtension", "PlantarFlexion"}
)


def _matches_pattern(lift: dict, pattern: str, strict_primary: bool = False) -> bool:
    if strict_primary:
        return lift.get("movementPattern") == pattern
    return lift.get("movementPattern") == pattern or pattern in lift.get(
        "movementPatterns", []
    )


def candidates_for_slot(slot: dict, context: dict) -> list[dict]:
    """Port of candidatesForSlot (audit §4.4 gate: empty means slot excluded)."""
    out = []
    for lift_id, lift in context["liftsById"].items():
        if lift_id not in context["enabledLiftIds"]:
            continue
        strict = slot["isCrossDomainTouch"] and slot["pattern"] in _STRICT_ISOLATION_TOUCH_PATTERNS
        pattern_match = _matches_pattern(lift, slot["pattern"], strict)
        fatigue_match = (
            slot["allowedFatigueProfiles"] is not None
            and lift.get("fatigueProfile", "Low") in slot["allowedFatigueProfiles"]
            and slot["primaryMuscle"] in lift.get("primaryMuscles", [])
        )
        if not pattern_match and not fatigue_match:
            continue
        primary_match = slot["primaryMuscle"] in lift.get("primaryMuscles", [])
        if slot["isCrossDomainTouch"]:
            if not primary_match:
                continue
        elif slot["role"] in ("Accessory", "Anchor"):
            if not primary_match:
                continue
        elif not primary_match and slot["primaryMuscle"] not in lift.get(
            "secondaryMuscles", []
        ):
            continue
        out.append(lift)
    return out


def _is_slot_relevant(slot: dict, relevant_muscles: set[str], context: dict) -> bool:
    if (
        slot["pattern"] == "VerticalPress"
        and slot["primaryMuscle"] == "AnteriorDeltoid"
        and context["specialization"].get("AnteriorDeltoid") != "Specialize"
    ):
        return False
    if (
        slot["pattern"] == "HipExtension"
        and slot["primaryMuscle"] == "Glute"
        and context["specialization"].get("Glute") != "Specialize"
    ):
        return False
    keeps = (
        slot["primaryMuscle"] in relevant_muscles
        or context["specialization"].get(slot["primaryMuscle"]) == "Specialize"
        or slot["role"] == "Anchor"
    )
    if not keeps:
        return False
    return len(candidates_for_slot(slot, context)) > 0


def build_slot_universe(context: dict, weekly_targets: dict) -> list[dict]:
    """Port of buildSlotUniverse. Returns SynthesisSlot dicts with universe ids."""
    priority_muscles = {
        muscle
        for muscle, level in context["specialization"].items()
        if level in ("Specialize", "MinimumVolume")
    }
    relevant_muscles = {
        muscle for muscle, t in weekly_targets.items() if t.get("aim", 0) > 0
    } | priority_muscles

    base = base_slot_library()
    slots = [dict(s) for s in base]
    promote_specialized_slots(slots, context)
    inject_specialized_variant_slots(slots, context)
    touches = cross_domain_touches(context)

    raw: dict[str, dict] = {}
    for slot in slots + touches:
        if not _is_slot_relevant(slot, relevant_muscles, context):
            continue
        key = _slot_key(slot)
        if key not in raw:
            raw[key] = slot

    universe = []
    for slot in list(raw.values())[:MAX_UNIVERSE_SLOTS]:
        universe.append(
            {
                "id": _slot_key(slot),
                "pattern": slot["pattern"],
                "primaryMuscle": slot["primaryMuscle"],
                "role": slot["role"],
                "repProfile": slot["repProfile"],
                "allowedRegionFilters": sorted(
                    allowed_region_filters_for_pattern(
                        slot["pattern"],
                        slot["primaryMuscle"],
                        slot["isCrossDomainTouch"],
                    )
                ),
                "isCrossDomainTouch": slot["isCrossDomainTouch"],
                "siblingVariant": slot["siblingVariant"],
                "lengthBiasPreference": slot["lengthBiasPreference"],
                "allowedFatigueProfiles": (
                    list(slot["allowedFatigueProfiles"])
                    if slot["allowedFatigueProfiles"] is not None
                    else None
                ),
            }
        )
    return universe


# --------------------------------------------------------------------------
# Slot stress class (VolumeAllocationCoefficients.slotLiftStressClass)
# --------------------------------------------------------------------------


def slot_lift_stress_class(slot: dict) -> str:
    if slot["repProfile"] == "LowerFreeWeightCompound":
        return "HeavyAxial"
    if slot["repProfile"] == "Isolation":
        return "Isolation"
    if slot["pattern"] in MAJOR_COMPOUND_PATTERNS:
        return "UpperCompound"
    return "Isolation"


def specialization_level_for_muscle(muscle: str, context: dict) -> str:
    return context["specialization"].get(muscle, "Maintenance")


def specialization_tier_value(level: str) -> int:
    return {"Specialize": 2, "Maintenance": 1, "MinimumVolume": 0, "Omit": 1}[level]


def recovery_priority_muscles(context: dict) -> set[str]:
    return {
        muscle
        for muscle, level in context["specialization"].items()
        if level in ("Specialize", "MinimumVolume")
    }


def preferred_weekly_frequency_hits(session_count: int) -> int:
    return 2 if session_count >= 3 else 1

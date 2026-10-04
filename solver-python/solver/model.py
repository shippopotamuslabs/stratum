"""Full CP-SAT program-synthesis model in Python (STR-45).

Faithful port of the constraint structure in committed Kotlin
``core/domain-solver/.../ProgramSynthesisModel.kt`` (+ ``UnifiedStructureConstraints``,
``VolumeAllocationCoefficients``, ``SynthesisObjectiveWeights``) to the OR-Tools
Python API, using reference tables from :mod:`solver.domain`.

Parameters / weights / thresholds are EXACTLY as Kotlin (see domain.py provenance).
Deliberate divergences from Kotlin (all FLAGGED in the final report):

1. STR-39 (fresh from the STR-39 issue text, NOT ported from the old Kotlin fix
   branch): for muscles OUTSIDE ``LARGE_COMPOUND_MUSCLES``, the meaningful-
   exposure floor is 1.0 (a single hard set counts) and the low tier (1-3 sets)
   triggers the 48h adjacent-day hard forbid for isolation-class exposure. The
   shared 2.0 floor is UNCHANGED for large muscles, frequency hits, soft gap
   penalties, and primary-mover gates.
2. ``round()``: Kotlin's ``kotlin.math.round`` (half-up, ties away from zero)
   is implemented as ``floor(x + 0.5)`` — Python's ``round()`` is banker's and
   would diverge on exact-.5 scaled credits (e.g. 12.5).
3. Output buckets replicate Kotlin's ``readObjective`` prefix sums literally —
   including summing prefixes that no slack carries (``rec_adj_``, ``mevUnder_``,
   ``roleUnder_``), so ``specializedMevShortfall`` and ``roleMinimumShortfall``
   stay 0 exactly like the Kotlin ``minUnder_``/``roleUnder_`` quirk.
4. The heavy-axial gap-2 bound is replicated literally, including its vacuous
   shape (the clause never references the later session's exposure).
5. Day vars are channeled through reified ``isOnDay``/``bothOnDays`` literals
   exactly like Kotlin, even though AllDifferent + the strict-increase symmetry
   break fully determine them (position i -> sorted train slot i).
"""

from __future__ import annotations

import math
import time

from ortools.sat.python import cp_model

from . import domain as D
from . import wire as W

STATUS_OK = (cp_model.OPTIMAL, cp_model.FEASIBLE)


class SolverInfeasibleError(RuntimeError):
    """CP-SAT returned neither OPTIMAL nor FEASIBLE (user-safe message).

    Carries the raw CP-SAT status: the fallback ladder (STR-54) retries only
    on a proven INFEASIBLE, never on timeouts or solver errors.
    """

    def __init__(self, message: str, *, status=None, status_name: str = ""):
        super().__init__(message)
        self.status = status
        self.status_name = status_name


class SolverConfigError(ValueError):
    """Structurally infeasible request (user-safe message)."""


def _round_half_up(x: float) -> int:
    """Kotlin ``kotlin.math.round`` for non-negative values (half-up).

    Python's ``round()`` is banker's rounding and diverges on exact halves
    (e.g. a 12.5 scaled credit -> 12 instead of 13).
    """
    return int(math.floor(x + 0.5))


class _Coefficients:
    """Port of VolumeAllocationCoefficients (catalog-aware secondary credits)."""

    def __init__(self, context: dict):
        self.scale = D.SCALE
        self._credits = D.compute_pattern_secondary_credits(
            context["liftsById"], context["enabledLiftIds"]
        )

    def coeff(self, slot: dict, muscle: str) -> float:
        if slot["primaryMuscle"] == muscle:
            return D.PRIMARY_SET_CREDIT
        for m, credit in self._credits.get(slot["pattern"], []):
            if m == muscle:
                return credit
        return 0.0

    def scaled_coeff(self, slot: dict, muscle: str) -> int:
        return _round_half_up(self.coeff(slot, muscle) * self.scale)

    def muscles_for_pattern(self, pattern: str) -> list:
        return self._credits.get(pattern, [])

    def is_heavy_axial(self, slot: dict) -> bool:
        return D.slot_lift_stress_class(slot) == "HeavyAxial"

    def is_upper_compound(self, slot: dict) -> bool:
        return D.slot_lift_stress_class(slot) == "UpperCompound"


def _per_slot_cap(slot: dict) -> int:
    if slot["role"] == "SecondaryCompound":
        return D.COMPOUND_SETS_PER_SLOT_CAP
    if slot["role"] == "Anchor" and slot["repProfile"] != "Isolation":
        return D.COMPOUND_SETS_PER_SLOT_CAP
    return D.SETS_PER_LIFT_CAP


def _role_minimum(slot: dict, context: dict):
    if slot["role"] == "Anchor":
        return D.ANCHOR_MIN_SETS
    if slot["role"] == "SecondaryCompound":
        return D.SECONDARY_COMPOUND_MIN_SETS
    is_specialized_primary = (
        context["specialization"].get(slot["primaryMuscle"], "Maintenance")
        == "Specialize"
    )
    if not is_specialized_primary:
        return None
    if slot["repProfile"] != "Isolation":
        return None
    if slot["isCrossDomainTouch"]:
        return D.MIN_TOUCH_DIRECT_SETS
    return D.MIN_SPECIALIZED_DIRECT_SETS


# Ordered parity-group members (Kotlin setOf insertion order — slack names
# embed the pair order, so frozenset iteration would diverge).
_ANTAGONIST_PAIRS = (
    ("Chest", "Back", "chest_back"),
    ("Quad", "Hamstring", "quad_ham"),
)
_SYMMETRIC_PAIRS = (("Bicep", "Tricep", "bicep_tricep"),)
_UPPER_PARITY_ORDER = ("Chest", "Back", "Bicep", "Tricep", "LateralDeltoid")
_LOWER_PARITY_ORDER = ("Quad", "Hamstring", "Calf")
_ALWAYS_AIM_ORDER = (
    "Chest",
    "Back",
    "Bicep",
    "Tricep",
    "LateralDeltoid",
    "Quad",
    "Hamstring",
)

#: Fallback-ladder relaxation levels (STR-54): L0 is the full model; L1
#: softens the min-active-lifts floor to a heavy-slack penalty.
L0_FULL = 0
L1_SOFT_FLOORS = 1

#: Objective-slack name prefix for L1 lift shortfalls.
_L1_LIFT_SHORT_PREFIX = "l1LiftShort_"


class SynthesisModel:
    """Joint CP-SAT model: per-slot set counts, session calendar, tiered objective."""

    def __init__(
        self, weekly_targets: dict, context: dict, relaxation: int = L0_FULL
    ):
        if relaxation not in (L0_FULL, L1_SOFT_FLOORS):
            raise ValueError(f"unknown relaxation level: {relaxation!r} (want 0 or 1)")
        self.weekly_targets = weekly_targets
        self.context = context
        # Fallback ladder (STR-54): L0_FULL = full model; L1_SOFT_FLOORS =
        # structural floors softened to heavy-slack penalties. Recovery
        # forbids stay hard at every level.
        self.relaxation = relaxation
        self.coeff = _Coefficients(context)
        # withResolvedCycleSpace: resolve ring first, then share it everywhere.
        length, rests = D.materialize_cycle_space(
            context["trainingDaysPerWeek"],
            set(context["restSlotIndices"]) if context["restSlotIndices"] is not None else None,
            context["cycleLength"],
        )
        train = sorted(s for s in range(length) if s not in rests)
        self.timeline = D.TrainingTimeline(length, train)
        self.session_count = len(train)
        self.universe = D.build_slot_universe(context, weekly_targets)
        if not self.universe:
            raise SolverConfigError("Slot universe is empty for program synthesis.")
        # buildUnifiedSessionPlans: every session carries every universe slot.
        self.plans: list[dict] = []
        for i in range(self.session_count):
            filled = [
                {"slotId": f"s{i}_{u['id']}", "universe": u} for u in self.universe
            ]
            self.plans.append({"position": i, "filledSlots": filled})

        self._slots: list[dict] = []  # SlotVar dicts {plan, slot, x, active}
        self._muscles: list[str] = []
        self._day_vars: list = []
        self._day_var_by_position: dict[int, object] = {}
        self._objective_slacks: dict[str, object] = {}
        self._credit_vars: dict[tuple[int, str], object] = {}
        self._meaningful: dict[tuple[int, str], object] = {}
        # STR-39: 1.0-floor exposure + low-tier literals for small muscles.
        self._small_meaningful: dict[tuple[int, str], object] = {}
        self._small_low_tier: dict[tuple[int, str], object] = {}
        self._heavy_axial_exp: dict[tuple[int, str], object] = {}
        self._upper_compound_exp: dict[tuple[int, str], object] = {}
        self._isolation_exp: dict[tuple[int, str], object] = {}
        self._low_tier: dict[tuple[int, str], object] = {}
        self._moderate_tier: dict[tuple[int, str], object] = {}
        self._moderate_dose: dict[tuple[int, str], object] = {}
        self._high_dose: dict[tuple[int, str], object] = {}
        self._req_two_day: dict[tuple[int, str], object] = {}
        self._region_filter_var: dict[int, object] = {}
        self._is_on_day: dict[tuple[int, int], object] = {}
        self._both_on_days: dict[tuple[tuple[int, int], tuple[int, int]], object] = {}
        self._ha_primary_exp: dict[str, object] = {}
        self._ha_primary_hard: dict[str, object] = {}
        self._weighted_terms: list[tuple[object, int]] = []

    # -- public API -----------------------------------------------------

    def check_feasible_configuration(self) -> None:
        session_count = self.session_count
        if self.context["trainingDaysPerWeek"] < 1:
            raise SolverConfigError(
                "trainingDaysPerWeek must be at least 1 "
                f"(was {self.context['trainingDaysPerWeek']})"
            )
        for muscle, target in self.weekly_targets.items():
            level = self.context["specialization"].get(muscle, "Maintenance")
            if level != "Specialize" or target["min"] <= 0.0:
                continue
            if session_count <= 1:
                raise SolverConfigError(
                    f"Cannot specialize {muscle} (MEV {target['min']}) with only "
                    "one training day — adjacent-day recovery forbids training "
                    "it twice. Add training days."
                )

    def build_model(self) -> cp_model.CpModel:
        model = cp_model.CpModel()
        for plan in self.plans:
            for filled in plan["filledSlots"]:
                slot = filled["universe"]
                cap = _per_slot_cap(slot)
                x = model.NewIntVar(0, cap, f"x_{filled['slotId']}")
                active = model.NewBoolVar(f"active_{filled['slotId']}")
                model.Add(x == 0).OnlyEnforceIf(active.Not())
                self._slots.append(
                    {"plan": plan, "slot": slot, "x": x, "active": active,
                     "slotId": filled["slotId"]}
                )
        muscle_set: list[str] = []
        for muscle in self.weekly_targets:
            if muscle not in muscle_set:
                muscle_set.append(muscle)
        for sv in self._slots:
            m = sv["slot"]["primaryMuscle"]
            if m not in muscle_set:
                muscle_set.append(m)
        for sv in self._slots:
            for m, _c in self.coeff.muscles_for_pattern(sv["slot"]["pattern"]):
                if m not in muscle_set:
                    muscle_set.append(m)
        self._muscles = muscle_set

        self._add_calendar_assignment(model)
        self._add_unified_structure_constraints(model)
        self._add_per_session_muscle_caps(model)
        self._add_session_time_caps(model)
        self._add_weekly_volume_ceilings(model)
        self._add_lower_leg_anchor_floors(model)
        self._add_min_sets_per_active_slot(model)
        self._add_role_minimum_constraints(model)
        self._add_minimum_active_lifts_constraint(model)
        self._add_adjacent_meaningful_exposure_forbids(model)
        self._add_heavy_axial_spacing_forbids(model)
        self._build_objective(model)
        return model

    def solve(
        self,
        max_time_seconds: float = 30.0,
        num_search_workers: int | None = None,
    ) -> dict:
        """Build, solve, and return a wire.encode_response-ready solution dict."""
        self.check_feasible_configuration()
        model = self.build_model()
        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = float(max_time_seconds)
        if num_search_workers is not None:
            solver.parameters.num_search_workers = int(num_search_workers)
        status = solver.Solve(model)
        if status not in STATUS_OK:
            raise SolverInfeasibleError(
                f"CP-SAT synthesis returned {solver.StatusName(status)} — try "
                "relaxing specialization or adding a training day.",
                status=status,
                status_name=solver.StatusName(status),
            )
        return self._read_solution(solver, status)

    # -- calendar / structure ------------------------------------------

    def _add_calendar_assignment(self, model: cp_model.CpModel) -> None:
        training_slots = self.timeline.training_slot_indices
        if len(training_slots) != len(self.plans):
            raise SolverConfigError(
                f"Training slot count {len(training_slots)} must match session "
                f"count {len(self.plans)} (cycleLength={self.timeline.cycle_length})."
            )
        for plan in sorted(self.plans, key=lambda p: p["position"]):
            day = model.NewIntVarFromDomain(
                cp_model.Domain.FromValues(training_slots),
                f"day_{plan['position']}",
            )
            self._day_vars.append(day)
            self._day_var_by_position[plan["position"]] = day
        model.AddAllDifferent(self._day_vars)
        # Symmetry breaking: strictly increasing day vars canonicalize the
        # assignment (position i -> sorted train slot i).
        for i in range(1, len(self._day_vars)):
            model.Add(self._day_vars[i - 1] < self._day_vars[i])

    def _add_unified_structure_constraints(self, model: cp_model.CpModel) -> None:
        universe_by_id = {u["id"]: u for u in self.universe}
        filters_by_position = D.region_filters_by_session_position(self.timeline)
        if not filters_by_position:
            raise SolverConfigError("regionFiltersBySessionPosition is empty")
        if len(filters_by_position) != len(self.plans):
            raise SolverConfigError("region filter positions != session count")
        for plan in self.plans:
            allowed = filters_by_position[plan["position"]]
            if not allowed:
                raise SolverConfigError(
                    f"allowed region filters empty for position {plan['position']}"
                )
            ordinals = sorted(D.REGION_FILTER_ORDINAL[f] for f in allowed)
            var = model.NewIntVarFromDomain(
                cp_model.Domain.FromValues(ordinals),
                f"regionFilter_{plan['position']}",
            )
            self._region_filter_var[plan["position"]] = var
        by_session: dict[int, list[dict]] = {}
        for sv in self._slots:
            by_session.setdefault(sv["plan"]["position"], []).append(sv)
        for session_index, session_vars in by_session.items():
            region_var = self._region_filter_var[session_index]
            allowed = filters_by_position[session_index]
            for region_filter in allowed:
                is_filter = model.NewBoolVar(
                    f"isRegionFilter_{session_index}_{region_filter}"
                )
                ordinal = D.REGION_FILTER_ORDINAL[region_filter]
                model.Add(region_var == ordinal).OnlyEnforceIf(is_filter)
                model.Add(region_var != ordinal).OnlyEnforceIf(is_filter.Not())
                for sv in session_vars:
                    active = sv["active"]
                    universe_id = _parse_session_slot_id(sv["slotId"])
                    if universe_id is None:
                        continue
                    synthesis_slot = universe_by_id.get(universe_id)
                    if synthesis_slot is None:
                        continue
                    if region_filter not in synthesis_slot["allowedRegionFilters"]:
                        model.Add(active == 0).OnlyEnforceIf(is_filter)
            groups: dict[tuple[str, str], list[dict]] = {}
            for sv in session_vars:
                key = (sv["slot"]["pattern"], sv["slot"]["primaryMuscle"])
                groups.setdefault(key, []).append(sv)
            for group in groups.values():
                if len(group) <= 1:
                    continue
                model.Add(sum(sv["active"] for sv in group) <= 1)
            lower_anchors = [
                sv for sv in session_vars
                if sv["slot"]["pattern"] in D.LOWER_LEG_ANCHOR_PATTERNS
            ]
            if lower_anchors and "LowerOnly" in allowed:
                is_lower = model.NewBoolVar(f"isLowerRegionFilter_{session_index}")
                model.Add(
                    region_var == D.REGION_FILTER_ORDINAL["LowerOnly"]
                ).OnlyEnforceIf(is_lower)
                model.Add(
                    region_var != D.REGION_FILTER_ORDINAL["LowerOnly"]
                ).OnlyEnforceIf(is_lower.Not())
                model.Add(
                    sum(sv["active"] for sv in lower_anchors) >= 1
                ).OnlyEnforceIf(is_lower)

    # -- volume guardrails ----------------------------------------------

    def _add_per_session_muscle_caps(self, model: cp_model.CpModel) -> None:
        cap_scaled = D.SESSION_MUSCLE_SET_CAP * self.coeff.scale
        by_plan: dict[int, list[dict]] = {}
        for sv in self._slots:
            by_plan.setdefault(sv["plan"]["position"], []).append(sv)
        for slot_vars in by_plan.values():
            for muscle in self._muscles:
                terms = [
                    (sv["x"], self.coeff.scaled_coeff(sv["slot"], muscle))
                    for sv in slot_vars
                ]
                terms = [(x, c) for x, c in terms if c != 0]
                if not terms:
                    continue
                model.Add(
                    sum(x * c for x, c in terms) <= cap_scaled
                )

    def _add_session_time_caps(self, model: cp_model.CpModel) -> None:
        cap = D.session_hard_set_cap(self.context.get("sessionLengthMinutes"))
        if cap is None:
            return
        by_plan: dict[int, list[dict]] = {}
        for sv in self._slots:
            by_plan.setdefault(sv["plan"]["position"], []).append(sv)
        for slot_vars in by_plan.values():
            model.Add(sum(sv["x"] for sv in slot_vars) <= cap)

    def _add_weekly_volume_ceilings(self, model: cp_model.CpModel) -> None:
        for muscle in self._muscles:
            weekly = self._weekly_credit_expr(model, muscle)
            if weekly is None:
                continue
            level = self.context["specialization"].get(muscle, "Maintenance")
            if level == "Omit":
                snapshot = self.context["muscleVolumeSnapshotsByMuscle"].get(muscle)
                mv_ceiling = (
                    snapshot["retentionFloorMv"]
                    if snapshot is not None
                    else D.OMITTED_MUSCLE_INCIDENTAL_CEILING
                )
                model.Add(weekly <= int(mv_ceiling * self.coeff.scale))
                continue
            target = self.weekly_targets.get(muscle)
            if target is None:
                continue
            model.Add(weekly <= int(target["max"] * self.coeff.scale))

    def _add_lower_leg_anchor_floors(self, model: cp_model.CpModel) -> None:
        session_cap = D.session_hard_set_cap(
            self.context.get("sessionLengthMinutes")
        )
        if session_cap is None:
            return
        if session_cap < D.ANCHOR_MIN_SETS * 2:
            return
        for sv in self._slots:
            if sv["slot"]["role"] != "Anchor":
                continue
            if sv["slot"]["pattern"] not in D.LOWER_LEG_ANCHOR_PATTERNS:
                continue
            model.Add(sv["x"] >= D.ANCHOR_MIN_SETS).OnlyEnforceIf(sv["active"])

    def _add_min_sets_per_active_slot(self, model: cp_model.CpModel) -> None:
        session_cap = D.session_hard_set_cap(
            self.context.get("sessionLengthMinutes")
        )
        if session_cap is None:
            return
        if session_cap < 12:
            return
        for sv in self._slots:
            model.Add(sv["x"] >= D.MIN_SETS_PER_ACTIVE_SLOT).OnlyEnforceIf(
                sv["active"]
            )

    def _add_role_minimum_constraints(self, model: cp_model.CpModel) -> None:
        for sv in self._slots:
            min_sets = _role_minimum(sv["slot"], self.context)
            if min_sets is None:
                continue
            model.Add(sv["x"] >= min_sets).OnlyEnforceIf(sv["active"])

    def _add_minimum_active_lifts_constraint(self, model: cp_model.CpModel) -> None:
        session_cap = D.session_hard_set_cap(
            self.context.get("sessionLengthMinutes")
        )
        if session_cap is None:
            return
        if session_cap < 12:
            return
        by_plan: dict[int, list[dict]] = {}
        for sv in self._slots:
            by_plan.setdefault(sv["plan"]["position"], []).append(sv)
        for plan in self.plans:
            actives = [sv["active"] for sv in by_plan.get(plan["position"], [])]
            if len(actives) < D.MIN_ACTIVE_LIFTS_PER_SESSION:
                continue
            self._apply_min_active_lifts_floor(model, actives, plan["position"])

    def _apply_min_active_lifts_floor(
        self, model: cp_model.CpModel, actives: list, position: int
    ) -> None:
        """Hard floor at L0; heavy-slack penalty at L1 (STR-54).

        This is the only unconditional hard structural floor, so L1
        provably terminates: near-zero volume satisfies every remaining
        hard constraint (caps, structure) and every recovery forbid
        vacuously. The slack penalty feeds the true objective (`total`)
        like every other weighted term — the 10 breakdown terms are
        unweighted explanatory slacks, never a partition of `total`
        (a v1.1 `fallbackLevel` field can expose engagement to clients).
        """
        if self.relaxation >= L1_SOFT_FLOORS:
            short = self._track_slack(
                model,
                f"{_L1_LIFT_SHORT_PREFIX}{position}",
                D.MIN_ACTIVE_LIFTS_PER_SESSION,
            )
            model.Add(sum(actives) + short >= D.MIN_ACTIVE_LIFTS_PER_SESSION)
            self._weighted_terms.append((short, D.W_L1_ACTIVE_LIFTS_SHORTFALL))
        else:
            model.Add(sum(actives) >= D.MIN_ACTIVE_LIFTS_PER_SESSION)

    # -- recovery forbids ------------------------------------------------

    def _add_adjacent_meaningful_exposure_forbids(
        self, model: cp_model.CpModel
    ) -> None:
        if not self._muscles:
            return
        adjacent_pairs = self.timeline.adjacent_training_pairs()
        for muscle in self._muscles:
            sessions = [
                plan
                for plan in self.plans
                if self._max_session_credit_scaled(plan, muscle) > 0
            ]
            for i in range(len(sessions)):
                for j in range(i + 1, len(sessions)):
                    plan_i = sessions[i]
                    plan_j = sessions[j]
                    credit_i = self._session_muscle_credit_var(
                        model, plan_i, muscle
                    )
                    credit_j = self._session_muscle_credit_var(
                        model, plan_j, muscle
                    )
                    if credit_i is None or credit_j is None:
                        continue
                    if D.is_large_compound_muscle(muscle):
                        meaningful_i = self._meaningful_literal(
                            model, credit_i, muscle, plan_i["position"]
                        )
                        meaningful_j = self._meaningful_literal(
                            model, credit_j, muscle, plan_j["position"]
                        )
                    else:
                        meaningful_i = self._forbid_exposure_literal(
                            model, credit_i, muscle, plan_i["position"]
                        )
                        meaningful_j = self._forbid_exposure_literal(
                            model, credit_j, muscle, plan_j["position"]
                        )
                    requires_i = self._requires_two_day_literal(
                        model, plan_i, muscle, credit_i
                    )
                    requires_j = self._requires_two_day_literal(
                        model, plan_j, muscle, credit_j
                    )
                    for d1, d2 in adjacent_pairs:
                        earlier_is_i = self._channel_day_pair(
                            model, plan_i["position"], plan_j["position"], d1, d2
                        )
                        model.AddBoolOr(
                            [
                                earlier_is_i.Not(),
                                meaningful_i.Not(),
                                meaningful_j.Not(),
                                requires_i.Not(),
                            ]
                        )
                        earlier_is_j = self._channel_day_pair(
                            model, plan_j["position"], plan_i["position"], d1, d2
                        )
                        model.AddBoolOr(
                            [
                                earlier_is_j.Not(),
                                meaningful_i.Not(),
                                meaningful_j.Not(),
                                requires_j.Not(),
                            ]
                        )

    def _add_heavy_axial_spacing_forbids(self, model: cp_model.CpModel) -> None:
        # Replicated literally, including the vacuous shape: the clause never
        # references the later session's exposure — a moderate+ heavy-axial
        # primary dose on the earlier session forbids ANY later session from
        # sitting exactly gap-2 after it.
        gap_two_pairs = [
            (e, l)
            for (e, l) in self.timeline.pairs_with_max_gap(2)
            if self.timeline.gap(e, l) == 2
        ]
        if not gap_two_pairs:
            return
        heavy_axial_slots = [
            sv for sv in self._slots if self.coeff.is_heavy_axial(sv["slot"])
        ]
        if not heavy_axial_slots:
            return
        for sv in heavy_axial_slots:
            plan_earlier = sv["plan"]
            heavy_primary = self._ha_primary_exposure_literal(model, sv)
            hard_forbid = self._ha_primary_hard_forbid_literal(model, sv)
            for plan_later in self.plans:
                if plan_later["position"] == plan_earlier["position"]:
                    continue
                for d1, d2 in gap_two_pairs:
                    earlier_is_earlier = self._channel_day_pair(
                        model,
                        plan_earlier["position"],
                        plan_later["position"],
                        d1,
                        d2,
                    )
                    model.AddBoolOr(
                        [
                            earlier_is_earlier.Not(),
                            hard_forbid.Not(),
                            heavy_primary.Not(),
                        ]
                    )

    # -- credit vars and exposure literals --------------------------------

    def _max_session_credit_scaled(self, plan: dict, muscle: str) -> int:
        total = 0
        for sv in self._slots:
            if sv["plan"] is not plan:
                continue
            cap = _per_slot_cap(sv["slot"])
            total += cap * self.coeff.scaled_coeff(sv["slot"], muscle)
        return total

    def _session_muscle_credit_var(
        self, model: cp_model.CpModel, plan: dict, muscle: str
    ):
        key = (plan["position"], muscle)
        if key in self._credit_vars:
            return self._credit_vars[key]
        terms = [
            (sv["x"], self.coeff.scaled_coeff(sv["slot"], muscle))
            for sv in self._slots
            if sv["plan"] is plan
        ]
        terms = [(x, c) for x, c in terms if c != 0]
        if not terms:
            return None
        max_credit = self._max_session_credit_scaled(plan, muscle)
        credit = model.NewIntVar(
            0, max_credit, f"credit_{plan['position']}_{muscle}"
        )
        model.Add(credit == sum(x * c for x, c in terms))
        self._credit_vars[key] = credit
        return credit

    def _weekly_credit_expr(self, model: cp_model.CpModel, muscle: str):
        terms = [
            (sv["x"], self.coeff.scaled_coeff(sv["slot"], muscle))
            for sv in self._slots
        ]
        terms = [(x, c) for x, c in terms if c != 0]
        if not terms:
            return None
        return sum(x * c for x, c in terms)

    def _meaningful_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        key = (position, muscle)
        if key in self._meaningful:
            return self._meaningful[key]
        min_scaled = int(
            D.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE * self.coeff.scale
        )
        lit = model.NewBoolVar(f"meaningful_{muscle}_{position}")
        model.Add(credit >= min_scaled).OnlyEnforceIf(lit)
        model.Add(credit <= min_scaled - 1).OnlyEnforceIf(lit.Not())
        self._meaningful[key] = lit
        return lit

    def _small_meaningful_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        """STR-39: 1.0 floor — a single hard set counts as meaningful exposure
        for small-muscle isolation."""
        key = (position, muscle)
        if key in self._small_meaningful:
            return self._small_meaningful[key]
        floor_scaled = int(D.STR39_SMALL_MUSCLE_MEANINGFUL_FLOOR * self.coeff.scale)
        lit = model.NewBoolVar(f"smallMeaningful_{muscle}_{position}")
        model.Add(credit >= floor_scaled).OnlyEnforceIf(lit)
        model.Add(credit <= floor_scaled - 1).OnlyEnforceIf(lit.Not())
        self._small_meaningful[key] = lit
        return lit

    def _small_low_tier_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        """STR-39: small-muscle low tier is [1.0, LOW_DOSE_MAX] (1-3 sets)."""
        key = (position, muscle)
        if key in self._small_low_tier:
            return self._small_low_tier[key]
        small = self._small_meaningful_literal(model, credit, muscle, position)
        moderate = self._moderate_dose_literal(model, credit, muscle, position)
        lit = self._reify_and(model, small, moderate.Not(),
                              f"smallLowDose_{muscle}_{position}")
        self._small_low_tier[key] = lit
        return lit

    def _moderate_dose_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        key = (position, muscle)
        if key in self._moderate_dose:
            return self._moderate_dose[key]
        threshold = D.MODERATE_DOSE_THRESHOLD_SCALED
        lit = model.NewBoolVar(f"moderate_{muscle}_{position}")
        model.Add(credit >= threshold).OnlyEnforceIf(lit)
        model.Add(credit <= threshold - 1).OnlyEnforceIf(lit.Not())
        self._moderate_dose[key] = lit
        return lit

    def _high_dose_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        key = (position, muscle)
        if key in self._high_dose:
            return self._high_dose[key]
        high_scaled = int(D.MUSCLE_RECOVERY_MODERATE_DOSE_MAX * self.coeff.scale)
        lit = model.NewBoolVar(f"high_{muscle}_{position}")
        model.Add(credit >= high_scaled + 1).OnlyEnforceIf(lit)
        model.Add(credit <= high_scaled).OnlyEnforceIf(lit.Not())
        self._high_dose[key] = lit
        return lit

    def _low_dose_tier_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        key = (position, muscle)
        if key in self._low_tier:
            return self._low_tier[key]
        meaningful = self._meaningful_literal(model, credit, muscle, position)
        moderate = self._moderate_dose_literal(model, credit, muscle, position)
        lit = self._reify_and(
            model, meaningful, moderate.Not(), f"lowDose_{muscle}_{position}"
        )
        self._low_tier[key] = lit
        return lit

    def _moderate_dose_tier_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        key = (position, muscle)
        if key in self._moderate_tier:
            return self._moderate_tier[key]
        moderate = self._moderate_dose_literal(model, credit, muscle, position)
        high = self._high_dose_literal(model, credit, muscle, position)
        lit = self._reify_and(
            model, moderate, high.Not(), f"moderateTier_{muscle}_{position}"
        )
        self._moderate_tier[key] = lit
        return lit

    def _slot_contributes_one_credit_literal(
        self, model: cp_model.CpModel, sv: dict, muscle: str
    ):
        coeff = self.coeff.scaled_coeff(sv["slot"], muscle)
        if coeff == 0:
            return None
        one_credit = self.coeff.scale
        max_slot_credit = _per_slot_cap(sv["slot"]) * coeff
        slot_credit = model.NewIntVar(
            0, max_slot_credit, f"slotCredit_{sv['slotId']}_{muscle}"
        )
        model.Add(slot_credit == sv["x"] * coeff)
        contributes = model.NewBoolVar(f"slotContrib_{sv['slotId']}_{muscle}")
        model.Add(slot_credit >= one_credit).OnlyEnforceIf(contributes)
        model.Add(slot_credit <= one_credit - 1).OnlyEnforceIf(
            contributes.Not()
        )
        model.AddImplication(sv["active"].Not(), contributes.Not())
        return contributes

    def _heavy_axial_exposure_literal(
        self, model: cp_model.CpModel, plan: dict, muscle: str
    ):
        key = (plan["position"], muscle)
        if key in self._heavy_axial_exp:
            return self._heavy_axial_exp[key]
        contributions = []
        for sv in self._slots:
            if sv["plan"] is not plan:
                continue
            if not self.coeff.is_heavy_axial(sv["slot"]):
                continue
            if sv["slot"]["primaryMuscle"] != muscle:
                continue
            lit = self._slot_contributes_one_credit_literal(model, sv, muscle)
            if lit is not None:
                contributions.append(lit)
        if not contributions:
            absent = model.NewBoolVar(f"heavyAxial_{muscle}_{plan['position']}")
            model.Add(absent == 0)
            self._heavy_axial_exp[key] = absent
            return absent
        lit = self._reify_or(
            model, contributions, f"heavyAxial_{muscle}_{plan['position']}"
        )
        self._heavy_axial_exp[key] = lit
        return lit

    def _upper_compound_exposure_literal(
        self, model: cp_model.CpModel, plan: dict, muscle: str
    ):
        key = (plan["position"], muscle)
        if key in self._upper_compound_exp:
            return self._upper_compound_exp[key]
        contributions = []
        for sv in self._slots:
            if sv["plan"] is not plan:
                continue
            if not self.coeff.is_upper_compound(sv["slot"]):
                continue
            if sv["slot"]["primaryMuscle"] != muscle:
                continue
            lit = self._slot_contributes_one_credit_literal(model, sv, muscle)
            if lit is not None:
                contributions.append(lit)
        if not contributions:
            absent = model.NewBoolVar(
                f"upperCompound_{muscle}_{plan['position']}"
            )
            model.Add(absent == 0)
            self._upper_compound_exp[key] = absent
            return absent
        has_upper = self._reify_or(
            model, contributions, f"upperCompoundRaw_{muscle}_{plan['position']}"
        )
        heavy_axial = self._heavy_axial_exposure_literal(model, plan, muscle)
        lit = self._reify_and(
            model, has_upper, heavy_axial.Not(),
            f"upperCompound_{muscle}_{plan['position']}",
        )
        self._upper_compound_exp[key] = lit
        return lit

    def _isolation_exposure_literal(
        self, model: cp_model.CpModel, plan: dict, muscle: str
    ):
        key = (plan["position"], muscle)
        if key in self._isolation_exp:
            return self._isolation_exp[key]
        heavy_axial = self._heavy_axial_exposure_literal(model, plan, muscle)
        upper_compound = self._upper_compound_exposure_literal(
            model, plan, muscle
        )
        lit = self._reify_and(
            model, heavy_axial.Not(), upper_compound.Not(),
            f"isolation_{muscle}_{plan['position']}",
        )
        self._isolation_exp[key] = lit
        return lit

    def _primary_meaningful_exposure_literal(
        self, model: cp_model.CpModel, plan: dict, muscle: str
    ):
        primary_slots = [
            sv for sv in self._slots
            if sv["plan"] is plan and sv["slot"]["primaryMuscle"] == muscle
        ]
        if not primary_slots:
            absent = model.NewBoolVar(
                f"priMeaningful_{muscle}_{plan['position']}"
            )
            model.Add(absent == 0)
            return absent
        max_primary = max(
            1, sum(_per_slot_cap(sv["slot"]) for sv in primary_slots)
            * self.coeff.scale,
        )
        primary_credit = model.NewIntVar(
            0, max_primary, f"priCredit_{muscle}_{plan['position']}"
        )
        model.Add(
            primary_credit
            == sum(sv["x"] for sv in primary_slots) * self.coeff.scale
        )
        min_scaled = int(
            D.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE * self.coeff.scale
        )
        lit = model.NewBoolVar(f"priMeaningful_{muscle}_{plan['position']}")
        model.Add(primary_credit >= min_scaled).OnlyEnforceIf(lit)
        model.Add(primary_credit <= min_scaled - 1).OnlyEnforceIf(lit.Not())
        return lit

    def _requires_two_day_literal(
        self, model: cp_model.CpModel, plan: dict, muscle: str, credit
    ):
        key = (plan["position"], muscle)
        if key in self._req_two_day:
            return self._req_two_day[key]
        pos = plan["position"]
        isolation_class = self._isolation_exposure_literal(model, plan, muscle)
        upper_class = self._upper_compound_exposure_literal(model, plan, muscle)
        heavy_class = self._heavy_axial_exposure_literal(model, plan, muscle)
        low_tier = self._low_dose_tier_literal(model, credit, muscle, pos)
        moderate_tier = self._moderate_dose_tier_literal(
            model, credit, muscle, pos
        )
        high_tier = self._high_dose_literal(model, credit, muscle, pos)
        gates = []
        if D.is_large_compound_muscle(muscle):
            has_primary = self._primary_meaningful_exposure_literal(
                model, plan, muscle
            )
            for tier_lit, tag in (
                (low_tier, "isoLo"),
                (moderate_tier, "isoMod"),
                (high_tier, "isoHi"),
            ):
                raw = self._reify_and(
                    model, tier_lit, isolation_class,
                    f"adj2d_{tag}Raw_{muscle}_{pos}",
                )
                gates.append(
                    self._reify_and(
                        model, raw, has_primary, f"adj2d_{tag}_{muscle}_{pos}"
                    )
                )
        else:
            gates.extend(
                self._small_muscle_isolation_gates(
                    model, plan, muscle, credit, pos,
                    isolation_class, moderate_tier, high_tier,
                )
            )
        for tier_lit, tag in (
            (low_tier, "ucLo"),
            (moderate_tier, "ucMod"),
            (high_tier, "ucHi"),
        ):
            gates.append(
                self._reify_and(
                    model, tier_lit, upper_class, f"adj2d_{tag}_{muscle}_{pos}"
                )
            )
        for tier_lit, tag in (
            (low_tier, "haLo"),
            (moderate_tier, "haMod"),
            (high_tier, "haHi"),
        ):
            gates.append(
                self._reify_and(
                    model, tier_lit, heavy_class, f"adj2d_{tag}_{muscle}_{pos}"
                )
            )
        if not gates:
            absent = model.NewBoolVar(f"reqTwoDay_{muscle}_{pos}")
            model.Add(absent == 0)
            self._req_two_day[key] = absent
            return absent
        lit = self._reify_or(model, gates, f"reqTwoDay_{muscle}_{pos}")
        self._req_two_day[key] = lit
        return lit

    def _forbid_exposure_literal(
        self, model: cp_model.CpModel, credit, muscle: str, position: int
    ):
        """Exposure floor used by the adjacent-day hard forbid.

        STR-39: 1.0 for small muscles (a single hard set counts); the shared
        2.0 floor for large muscles. Separated for testability — the pre-STR-39
        behavior is the plain 2.0-floor literal for every muscle.
        """
        if D.is_large_compound_muscle(muscle):
            return self._meaningful_literal(model, credit, muscle, position)
        return self._small_meaningful_literal(model, credit, muscle, position)

    def _small_muscle_isolation_gates(
        self, model: cp_model.CpModel, plan: dict, muscle: str, credit,
        pos: int, isolation_class, moderate_tier, high_tier,
    ) -> list:
        """Isolation-class 48h gates for small muscles.

        STR-39: the low tier (1-3 sets at the 1.0 floor) triggers the forbid.
        Pre-STR-39 (Kotlin at ef470aa3) only gated moderate and high tiers —
        small-muscle isolation Low stayed on the 24h lane. Separated so the
        golden-output comparison can attribute divergences precisely.
        """
        small_low = self._small_low_tier_literal(model, credit, muscle, pos)
        return [
            self._reify_and(
                model, small_low, isolation_class,
                f"adj2d_isoSmLo_{muscle}_{pos}",
            ),
            self._reify_and(
                model, moderate_tier, isolation_class,
                f"adj2d_isoMod_{muscle}_{pos}",
            ),
            self._reify_and(
                model, high_tier, isolation_class,
                f"adj2d_isoHi_{muscle}_{pos}",
            ),
        ]

    def _ha_primary_exposure_literal(self, model: cp_model.CpModel, sv: dict):
        key = sv["slotId"]
        if key in self._ha_primary_exp:
            return self._ha_primary_exp[key]
        has_min = model.NewBoolVar(f"haPriMin_{sv['slotId']}")
        model.Add(sv["x"] >= 1).OnlyEnforceIf(has_min)
        model.Add(sv["x"] <= 0).OnlyEnforceIf(has_min.Not())
        lit = self._reify_and(
            model, sv["active"], has_min, f"haPriExp_{sv['slotId']}"
        )
        self._ha_primary_exp[key] = lit
        return lit

    def _ha_primary_hard_forbid_literal(self, model: cp_model.CpModel, sv: dict):
        key = sv["slotId"]
        if key in self._ha_primary_hard:
            return self._ha_primary_hard[key]
        hard_scaled = int(D.MUSCLE_RECOVERY_MODERATE_DOSE * self.coeff.scale)
        hard_dose = model.NewBoolVar(f"haPriHardDose_{sv['slotId']}")
        model.Add(sv["x"] >= hard_scaled).OnlyEnforceIf(hard_dose)
        model.Add(sv["x"] <= hard_scaled - 1).OnlyEnforceIf(hard_dose.Not())
        lit = self._reify_and(
            model, sv["active"], hard_dose, f"haPriHard_{sv['slotId']}"
        )
        self._ha_primary_hard[key] = lit
        return lit

    # -- reification helpers (literal Kotlin clause shapes) ----------------

    @staticmethod
    def _reify_and(model: cp_model.CpModel, left, right, name: str):
        """Half-reified AND, exactly as Kotlin ``reifyAndLiteral``."""
        result = model.NewBoolVar(name)
        model.AddBoolOr([left.Not(), right.Not(), result])
        model.AddBoolOr([left, right, result.Not()])
        return result

    @staticmethod
    def _reify_or(model: cp_model.CpModel, terms: list, name: str):
        if len(terms) == 1:
            return terms[0]
        result = model.NewBoolVar(name)
        model.AddBoolOr(terms).OnlyEnforceIf(result)
        model.AddBoolAnd([t.Not() for t in terms]).OnlyEnforceIf(result.Not())
        return result

    def _is_on_day_literal(
        self, model: cp_model.CpModel, position: int, day: int
    ):
        key = (position, day)
        if key in self._is_on_day:
            return self._is_on_day[key]
        day_var = self._day_var_by_position[position]
        on_day = model.NewBoolVar(f"onDay_{position}_{day}")
        model.Add(day_var == day).OnlyEnforceIf(on_day)
        model.Add(day_var != day).OnlyEnforceIf(on_day.Not())
        self._is_on_day[key] = on_day
        return on_day

    def _channel_day_pair(
        self, model: cp_model.CpModel, pos_i: int, pos_j: int, d1: int, d2: int
    ):
        key = ((pos_i, pos_j), (d1, d2))
        if key in self._both_on_days:
            return self._both_on_days[key]
        on_i = self._is_on_day_literal(model, pos_i, d1)
        on_j = self._is_on_day_literal(model, pos_j, d2)
        both = model.NewBoolVar(f"both_{pos_i}_{pos_j}_{d1}_{d2}")
        model.AddBoolOr([on_i.Not(), on_j.Not(), both])
        model.AddBoolOr([on_i, on_j, both.Not()])
        self._both_on_days[key] = both
        return both

    def _track_slack(
        self, model: cp_model.CpModel, name: str, max_value: int
    ):
        slack = model.NewIntVar(0, max_value, name)
        self._objective_slacks[name] = slack
        return slack

    def _track_slack_default(self, model: cp_model.CpModel, name: str):
        return self._track_slack(model, name, D.MAX_OVER)

    # -- objective ----------------------------------------------------------

    def _build_objective(self, model: cp_model.CpModel) -> None:
        terms = self._weighted_terms
        priority = D.recovery_priority_muscles(self.context)
        self._add_recovery_penalties(model, priority)
        self._add_push_pull_balance(model)
        self._add_session_fill_penalties(model)
        self._add_session_set_balance_penalties(model)
        for muscle in self._muscles:
            target = self.weekly_targets.get(muscle)
            if target is None:
                continue
            weekly = self._weekly_credit_expr(model, muscle)
            if weekly is None:
                continue
            level = self.context["specialization"].get(muscle, "Maintenance")
            if level == "Specialize":
                prefix = "spec"
            elif level == "MinimumVolume":
                prefix = "minvol"
            else:
                prefix = "maint"
            over = self._track_slack(
                model, f"over_{prefix}_{muscle}", D.CREDIT_DEVIATION_SLACK_MAX
            )
            under = self._track_slack(
                model, f"under_{prefix}_{muscle}", D.CREDIT_DEVIATION_SLACK_MAX
            )
            aim_scaled = int(target["aim"] * self.coeff.scale)
            model.Add(weekly - over + under == aim_scaled)
            if level == "Specialize":
                weight = D.W_SPECIALIZE_AIM_DEVIATION
            elif level == "MinimumVolume":
                weight = D.W_MINIMUM_VOLUME_AIM_DEVIATION
            elif muscle == "PosteriorDeltoid":
                weight = D.W_MAINTENANCE_POSTERIOR_AIM_DEVIATION
            else:
                weight = D.W_MAINTENANCE_AIM_DEVIATION
            terms.append((over, weight))
            terms.append((under, weight))
            if target["min"] > 0.0 and level == "Specialize":
                min_scaled = int(target["min"] * self.coeff.scale)
                min_under = self._track_slack(
                    model, f"minUnder_{muscle}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                model.Add(weekly + min_under >= min_scaled)
                terms.append((min_under, D.W_SPECIALIZED_MIN_SHORTFALL))
        for sv in self._slots:
            terms.append(
                (sv["active"], D.W_SLOT_ACTIVATION_COUNT * self.coeff.scale)
            )
        for sv in self._slots:
            if sv["slot"]["role"] != "Anchor":
                continue
            if sv["slot"]["pattern"] not in D.LOWER_LEG_ANCHOR_PATTERNS:
                continue
            terms.append(
                (
                    sv["active"],
                    D.W_LOWER_ANCHOR_ACTIVATION_BONUS * self.coeff.scale,
                )
            )
        self._add_maintenance_rear_delt_discouragement(model)
        self._add_antagonist_maintenance_balance(model)
        self._add_symmetric_maintenance_balance(model)
        self._add_upper_body_maintenance_parity(model)
        self._add_lower_body_maintenance_parity(model)
        self._add_co_specialization_balance(model)
        self._add_major_muscle_frequency_penalties(model)
        model.Minimize(sum(var * w for var, w in terms))

    def _add_recovery_penalties(
        self, model: cp_model.CpModel, priority_muscles: set
    ) -> None:
        training_slots = self.timeline.training_slot_indices
        for muscle in self._muscles:
            sessions_with_muscle = [
                plan
                for plan in self.plans
                if self._max_session_credit_scaled(plan, muscle) > 0
            ]
            if len(sessions_with_muscle) < 2:
                continue
            if muscle in priority_muscles:
                weight = D.W_RECOVERY_PRIORITY
            else:
                weight = D.W_RECOVERY_GENERAL
            for i in range(len(sessions_with_muscle)):
                for j in range(i + 1, len(sessions_with_muscle)):
                    self._add_recovery_penalties_for_session_pair(
                        model,
                        sessions_with_muscle[i],
                        sessions_with_muscle[j],
                        muscle,
                        weight,
                        training_slots,
                    )

    def _add_recovery_penalties_for_session_pair(
        self, model, plan_i, plan_j, muscle, weight, training_slots
    ) -> None:
        for earlier_slot in training_slots:
            for later_slot in training_slots:
                if earlier_slot == later_slot:
                    continue
                calendar_gap = self.timeline.gap(earlier_slot, later_slot)
                if calendar_gap <= 0:
                    continue
                self._add_gap_penalty(
                    model, plan_i, plan_j, muscle, weight,
                    earlier_slot, later_slot, calendar_gap,
                )

    def _add_gap_penalty(
        self, model, plan_i, plan_j, muscle, weight,
        earlier_day, later_day, calendar_gap,
    ) -> None:
        credit_i = self._session_muscle_credit_var(model, plan_i, muscle)
        credit_j = self._session_muscle_credit_var(model, plan_j, muscle)
        if credit_i is None or credit_j is None:
            return
        meaningful_i = self._meaningful_literal(
            model, credit_i, muscle, plan_i["position"]
        )
        meaningful_j = self._meaningful_literal(
            model, credit_j, muscle, plan_j["position"]
        )
        both = self._channel_day_pair(
            model, plan_i["position"], plan_j["position"], earlier_day, later_day
        )
        adjacent = calendar_gap == 1
        penalty_scale = (
            D.CALENDAR_ADJACENT_PENALTY_MULTIPLIER if adjacent else 1
        )
        prefix = (
            "rec_pri_"
            if weight == D.W_RECOVERY_PRIORITY
            else "rec_gen_"
        )
        isolation_class = self._isolation_exposure_literal(model, plan_i, muscle)
        upper_class = self._upper_compound_exposure_literal(model, plan_i, muscle)
        heavy_class = self._heavy_axial_exposure_literal(model, plan_i, muscle)
        has_primary_i = self._primary_meaningful_exposure_literal(
            model, plan_i, muscle
        )
        low_tier = self._low_dose_tier_literal(
            model, credit_i, muscle, plan_i["position"]
        )
        moderate_tier = self._moderate_dose_tier_literal(
            model, credit_i, muscle, plan_i["position"]
        )
        high_tier = self._high_dose_literal(
            model, credit_i, muscle, plan_i["position"]
        )
        specs = (
            ("Isolation", D.LOW_TIER_REPRESENTATIVE_DOSE, low_tier, isolation_class),
            ("Isolation", D.MODERATE_TIER_REPRESENTATIVE_DOSE, moderate_tier, isolation_class),
            ("Isolation", D.HIGH_TIER_REPRESENTATIVE_DOSE, high_tier, isolation_class),
            ("UpperCompound", D.LOW_TIER_REPRESENTATIVE_DOSE, low_tier, upper_class),
            ("UpperCompound", D.MODERATE_TIER_REPRESENTATIVE_DOSE, moderate_tier, upper_class),
            ("UpperCompound", D.HIGH_TIER_REPRESENTATIVE_DOSE, high_tier, upper_class),
            ("HeavyAxial", D.LOW_TIER_REPRESENTATIVE_DOSE, low_tier, heavy_class),
            ("HeavyAxial", D.MODERATE_TIER_REPRESENTATIVE_DOSE, moderate_tier, heavy_class),
            ("HeavyAxial", D.HIGH_TIER_REPRESENTATIVE_DOSE, high_tier, heavy_class),
        )
        for index, (stress_class, rep_dose, tier_gate, class_gate) in enumerate(specs):
            isolation_needs_primary = (
                D.is_large_compound_muscle(muscle) and stress_class == "Isolation"
            )
            required = D.minimum_recovery_calendar_gap(
                rep_dose, stress_class, muscle,
                primary_fractional_sets=rep_dose,
            )
            if required <= 0 or calendar_gap >= required:
                continue
            shortfall = required - calendar_gap
            max_penalty = min(
                shortfall * shortfall * D.RECOVERY_GAP_PENALTY_BASE * penalty_scale,
                D.RECOVERY_SLACK_CAP,
            )
            slack = self._track_slack(
                model,
                f"{prefix}{muscle}_{earlier_day}_{later_day}_{calendar_gap}"
                f"_{plan_i['position']}_{plan_j['position']}_{index}",
                D.RECOVERY_SLACK_CAP,
            )
            c = model.Add(slack >= max_penalty)
            c.OnlyEnforceIf(both)
            c.OnlyEnforceIf(meaningful_i)
            c.OnlyEnforceIf(meaningful_j)
            c.OnlyEnforceIf(tier_gate)
            c.OnlyEnforceIf(class_gate)
            if isolation_needs_primary:
                c.OnlyEnforceIf(has_primary_i)
            self._weighted_terms.append((slack, weight))

    def _add_push_pull_balance(self, model: cp_model.CpModel) -> None:
        push = [sv["x"] for sv in self._slots if sv["slot"]["pattern"] in D.PUSH_PATTERNS]
        pull = [sv["x"] for sv in self._slots if sv["slot"]["pattern"] in D.PULL_PATTERNS]
        if not push or not pull:
            return
        push_expr = sum(push)
        pull_expr = sum(pull)
        over = self._track_slack_default(model, "ppOver")
        under = self._track_slack_default(model, "ppUnder")
        model.Add(push_expr - pull_expr - over + under == 0)
        pp_weight = D.W_PUSH_PULL_IMBALANCE * self.coeff.scale
        self._weighted_terms.append((over, pp_weight))
        self._weighted_terms.append((under, pp_weight))

    def _add_session_fill_penalties(self, model: cp_model.CpModel) -> None:
        target_sets = D.session_aim_hard_sets(
            self.context.get("sessionLengthMinutes")
        )
        if target_sets is None:
            return
        by_plan: dict[int, list[dict]] = {}
        for sv in self._slots:
            by_plan.setdefault(sv["plan"]["position"], []).append(sv)
        for plan in self.plans:
            slot_vars = by_plan.get(plan["position"], [])
            if not slot_vars:
                continue
            total = sum(sv["x"] for sv in slot_vars)
            under = self._track_slack_default(
                model, f"sessUnder_{plan['position']}"
            )
            model.Add(total + under >= target_sets)
            self._weighted_terms.append(
                (under, D.W_SESSION_FILL_SHORTFALL * self.coeff.scale)
            )

    def _add_session_set_balance_penalties(self, model: cp_model.CpModel) -> None:
        if len(self.plans) < 2:
            return
        session_totals = []
        for plan in self.plans:
            slot_vars = [sv for sv in self._slots if sv["plan"] is plan]
            if not slot_vars:
                return
            session_totals.append(
                (plan["position"], sum(sv["x"] for sv in slot_vars))
            )
        if len(session_totals) < len(self.plans):
            return
        n = len(self.plans)
        weekly_sum = sum(total for _, total in session_totals)
        for position, total in session_totals:
            under = self._track_slack_default(model, f"sessBalUnder_{position}")
            model.Add(n * total - weekly_sum + n * under >= 0)
            self._weighted_terms.append(
                (under, D.W_SESSION_SET_IMBALANCE * self.coeff.scale)
            )

    def _add_maintenance_rear_delt_discouragement(
        self, model: cp_model.CpModel
    ) -> None:
        posterior_level = self.context["specialization"].get(
            "PosteriorDeltoid", "Maintenance"
        )
        if posterior_level == "Specialize":
            return
        for sv in self._slots:
            if sv["slot"]["primaryMuscle"] != "PosteriorDeltoid":
                continue
            sets = model.NewIntVar(0, D.MAX_OVER, f"rearDirect_{sv['slotId']}")
            model.Add(sets == sv["x"])
            scaled = D.W_MAINTENANCE_POSTERIOR_DIRECT_DISCOURAGE * self.coeff.scale
            self._weighted_terms.append((sets, scaled))

    def _should_balance_antagonist(self, muscle_a: str, muscle_b: str) -> bool:
        level_a = self.context["specialization"].get(muscle_a, "Maintenance")
        level_b = self.context["specialization"].get(muscle_b, "Maintenance")
        return level_a != "Omit" and level_b != "Omit"

    def _should_balance_symmetric(self, muscle_a: str, muscle_b: str) -> bool:
        level_a = self.context["specialization"].get(muscle_a, "Maintenance")
        level_b = self.context["specialization"].get(muscle_b, "Maintenance")
        if level_a == "Omit" or level_b == "Omit":
            return False
        return D.specialization_tier_value(level_a) == D.specialization_tier_value(
            level_b
        )

    def _add_antagonist_maintenance_balance(self, model: cp_model.CpModel) -> None:
        for weaker, stronger, label in _ANTAGONIST_PAIRS:
            if not self._should_balance_antagonist(weaker, stronger):
                continue
            weaker_credit = self._weekly_credit_expr(model, weaker)
            stronger_credit = self._weekly_credit_expr(model, stronger)
            if weaker_credit is None or stronger_credit is None:
                continue
            val_weaker = D.specialization_tier_value(
                self.context["specialization"].get(weaker, "Maintenance")
            )
            val_stronger = D.specialization_tier_value(
                self.context["specialization"].get(stronger, "Maintenance")
            )
            if val_weaker == val_stronger:
                wrong = D.W_ANTAGONIST_WRONG_WAY_EXCESS + D.W_ANTAGONIST_PAIR_CLOSENESS
                right = D.W_ANTAGONIST_RIGHT_WAY_EXCESS + D.W_ANTAGONIST_PAIR_CLOSENESS
            elif val_stronger > val_weaker:
                wrong = D.W_ANTAGONIST_WRONG_WAY_EXCESS + D.W_ANTAGONIST_PAIR_CLOSENESS
                right = 0
            else:
                wrong = 0
                right = 0
            if wrong > 0 or right > 0:
                wrong_excess = self._track_slack(
                    model, f"antWrong_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                right_excess = self._track_slack(
                    model, f"antRight_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                model.Add(
                    weaker_credit - stronger_credit - wrong_excess + right_excess
                    == 0
                )
                if wrong > 0:
                    self._weighted_terms.append((wrong_excess, wrong))
                if right > 0:
                    self._weighted_terms.append((right_excess, right))

    def _add_symmetric_maintenance_balance(self, model: cp_model.CpModel) -> None:
        for muscle_a, muscle_b, label in _SYMMETRIC_PAIRS:
            if not self._should_balance_symmetric(muscle_a, muscle_b):
                continue
            credit_a = self._weekly_credit_expr(model, muscle_a)
            credit_b = self._weekly_credit_expr(model, muscle_b)
            if credit_a is None or credit_b is None:
                continue
            excess_a = self._track_slack(
                model, f"symOver_{label}", D.CREDIT_DEVIATION_SLACK_MAX
            )
            excess_b = self._track_slack(
                model, f"symUnder_{label}", D.CREDIT_DEVIATION_SLACK_MAX
            )
            model.Add(credit_a - credit_b - excess_a + excess_b == 0)
            self._weighted_terms.append(
                (excess_a, D.W_SYMMETRIC_PAIR_CLOSENESS)
            )
            self._weighted_terms.append(
                (excess_b, D.W_SYMMETRIC_PAIR_CLOSENESS)
            )

    def _add_upper_body_maintenance_parity(self, model: cp_model.CpModel) -> None:
        members = [
            m for m in _UPPER_PARITY_ORDER
            if self.context["specialization"].get(m, "Maintenance") == "Maintenance"
        ]
        if len(members) < 2:
            return
        for i in range(len(members)):
            for j in range(i + 1, len(members)):
                muscle_a, muscle_b = members[i], members[j]
                credit_a = self._weekly_credit_expr(model, muscle_a)
                credit_b = self._weekly_credit_expr(model, muscle_b)
                if credit_a is None or credit_b is None:
                    continue
                label = f"{muscle_a}_{muscle_b}"
                excess_a = self._track_slack(
                    model, f"ubpOver_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                excess_b = self._track_slack(
                    model, f"ubpUnder_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                model.Add(credit_a - credit_b - excess_a + excess_b == 0)
                self._weighted_terms.append(
                    (excess_a, D.W_UPPER_BODY_MAINTENANCE_PARITY)
                )
                self._weighted_terms.append(
                    (excess_b, D.W_UPPER_BODY_MAINTENANCE_PARITY)
                )

    def _add_lower_body_maintenance_parity(self, model: cp_model.CpModel) -> None:
        members = [
            m for m in _LOWER_PARITY_ORDER
            if self.context["specialization"].get(m, "Maintenance") == "Maintenance"
        ]
        if len(members) < 2:
            return
        for i in range(len(members)):
            for j in range(i + 1, len(members)):
                muscle_a, muscle_b = members[i], members[j]
                credit_a = self._weekly_credit_expr(model, muscle_a)
                credit_b = self._weekly_credit_expr(model, muscle_b)
                if credit_a is None or credit_b is None:
                    continue
                label = f"{muscle_a}_{muscle_b}"
                excess_a = self._track_slack(
                    model, f"lbpOver_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                excess_b = self._track_slack(
                    model, f"lbpUnder_{label}", D.CREDIT_DEVIATION_SLACK_MAX
                )
                model.Add(credit_a - credit_b - excess_a + excess_b == 0)
                self._weighted_terms.append(
                    (excess_a, D.W_LOWER_BODY_MAINTENANCE_PARITY)
                )
                self._weighted_terms.append(
                    (excess_b, D.W_LOWER_BODY_MAINTENANCE_PARITY)
                )

    def _add_co_specialization_balance(self, model: cp_model.CpModel) -> None:
        specialized = [
            m for m in self._muscles
            if self.context["specialization"].get(m, "Maintenance") == "Specialize"
        ]
        if len(specialized) < 2:
            return
        for i in range(len(specialized)):
            for j in range(i + 1, len(specialized)):
                aim_a = self.weekly_targets.get(specialized[i], {}).get("aim")
                aim_b = self.weekly_targets.get(specialized[j], {}).get("aim")
                if aim_a is None or aim_b is None:
                    continue
                if abs(aim_a - aim_b) > D.AIM_EQUALITY_TOLERANCE:
                    continue
                credit_a = self._weekly_credit_expr(model, specialized[i])
                credit_b = self._weekly_credit_expr(model, specialized[j])
                if credit_a is None or credit_b is None:
                    continue
                diff_over = self._track_slack(
                    model,
                    f"diffOver_{specialized[i]}_{specialized[j]}",
                    D.CREDIT_DEVIATION_SLACK_MAX,
                )
                diff_under = self._track_slack(
                    model,
                    f"diffUnder_{specialized[i]}_{specialized[j]}",
                    D.CREDIT_DEVIATION_SLACK_MAX,
                )
                model.Add(credit_a - credit_b - diff_over + diff_under == 0)
                self._weighted_terms.append(
                    (diff_over, D.W_CO_SPECIALIZATION_BALANCE)
                )
                self._weighted_terms.append(
                    (diff_under, D.W_CO_SPECIALIZATION_BALANCE)
                )

    def _add_major_muscle_frequency_penalties(
        self, model: cp_model.CpModel
    ) -> None:
        session_count = len(self.plans)
        target_hits = D.preferred_weekly_frequency_hits(session_count)
        if target_hits <= 1:
            return
        weight = D.W_FREQUENCY_SHORTFALL * self.coeff.scale
        for muscle in _ALWAYS_AIM_ORDER:
            level = self.context["specialization"].get(muscle, "Maintenance")
            if level in ("Omit", "MinimumVolume"):
                continue
            target = self.weekly_targets.get(muscle)
            if target is None:
                continue
            if target["aim"] <= 0.0:
                continue
            hit_literals = []
            for plan in self.plans:
                credit = self._session_muscle_credit_var(model, plan, muscle)
                if credit is None:
                    continue
                hit_literals.append(
                    self._meaningful_literal(
                        model, credit, muscle, plan["position"]
                    )
                )
            if not hit_literals:
                continue
            hits = model.NewIntVar(0, len(hit_literals), f"freqHits_{muscle}")
            model.Add(hits == sum(hit_literals))
            shortfall = self._track_slack(
                model, f"freqUnder_{muscle}", target_hits
            )
            model.Add(hits + shortfall >= target_hits)
            self._weighted_terms.append((shortfall, weight))

    # -- solution readout ----------------------------------------------------

    def _read_solution(self, solver: cp_model.CpSolver, status) -> dict:
        set_counts: dict[tuple[int, str], int] = {}
        active_by_session: dict[int, list[str]] = {}
        for sv in self._slots:
            parsed = _parse_session_slot_id(sv["slotId"])
            if parsed is None:
                continue
            session_index, universe_id = parsed
            is_active = bool(solver.BooleanValue(sv["active"]))
            sets = int(solver.Value(sv["x"]))
            if is_active and sets > 0:
                set_counts[(session_index, universe_id)] = sets
                active_by_session.setdefault(session_index, []).append(universe_id)
        ordered_plans = sorted(self.plans, key=lambda p: p["position"])
        cycle_slots = [
            int(solver.Value(self._day_var_by_position[p["position"]]))
            for p in ordered_plans
        ]
        region_filters = [
            _REGION_FILTERS_BY_ORDINAL[
                int(solver.Value(self._region_filter_var[p["position"]]))
            ]
            for p in ordered_plans
        ]
        # Universe order for active ids (matches Kotlin's insertion-ordered sets).
        universe_order = {u["id"]: n for n, u in enumerate(self.universe)}
        active_ids = [
            sorted(
                active_by_session.get(p["position"], []),
                key=lambda uid: universe_order.get(uid, 1 << 30),
            )
            for p in ordered_plans
        ]
        return {
            "universe": self.universe,
            "activeSlotIdsBySession": active_ids,
            "setCounts": set_counts,
            "cycleSlotIndexByPosition": cycle_slots,
            "regionFilterBySession": region_filters,
            "objective": self._read_objective(solver),
            "status": solver.StatusName(status),
        }

    def _read_objective(self, solver: cp_model.CpSolver) -> dict:
        def prefix_sum(prefix: str) -> int:
            return sum(
                int(solver.Value(var))
                for name, var in self._objective_slacks.items()
                if name.startswith(prefix)
            )

        return {
            "total": int(solver.ObjectiveValue()),
            "recoveryPenalty": (
                prefix_sum("rec_pri_")
                + prefix_sum("rec_gen_")
                + prefix_sum("rec_adj_")
            ),
            "specializedMevShortfall": prefix_sum("mevUnder_"),
            "coSpecializationImbalance": (
                prefix_sum("diffOver_") + prefix_sum("diffUnder_")
            ),
            "specializeAimDeviation": (
                prefix_sum("over_spec_") + prefix_sum("under_spec_")
            ),
            "maintenanceAimDeviation": (
                prefix_sum("over_maint_") + prefix_sum("under_maint_")
            ),
            "minimumVolumeAimDeviation": (
                prefix_sum("over_minvol_") + prefix_sum("under_minvol_")
            ),
            "roleMinimumShortfall": prefix_sum("roleUnder_"),
            "pushPullImbalance": (
                prefix_sum("ppOver_") + prefix_sum("ppUnder_")
            ),
            "sessionFillShortfall": prefix_sum("sessUnder_"),
        }


_REGION_FILTERS_BY_ORDINAL = ("UpperOnly", "LowerOnly", "Unrestricted")


def _parse_session_slot_id(slot_id: str):
    if not slot_id.startswith("s"):
        return None
    underscore = slot_id.find("_")
    if underscore <= 1:
        return None
    try:
        session_index = int(slot_id[1:underscore])
    except ValueError:
        return None
    return (session_index, slot_id[underscore + 1:])


def solve_request(
    decoded: dict,
    max_time_seconds: float = 30.0,
    num_search_workers: int | None = None,
    relaxation: int = L0_FULL,
) -> dict:
    """Solve an already-decoded v1 request; returns a wire-ready solution dict."""
    model = SynthesisModel(
        decoded["weeklyTargets"], decoded["context"], relaxation=relaxation
    )
    return model.solve(
        max_time_seconds=max_time_seconds, num_search_workers=num_search_workers
    )


def _solve_with_fallback(
    decoded: dict,
    max_time_seconds: float,
    num_search_workers: int | None,
) -> tuple[dict, int]:
    """Run the L0-then-L1 fallback ladder; returns (solution, level engaged).

    Retries once at L1 on a proven INFEASIBLE only — timeouts and solver
    errors propagate (L1 cannot fix those, and a second full-deadline
    attempt would just double latency). The engaged level is returned (not
    just logged) so tests pin engagement without parsing stdout.
    """
    started = time.time()
    try:
        solution = solve_request(
            decoded,
            max_time_seconds=max_time_seconds,
            num_search_workers=num_search_workers,
            relaxation=L0_FULL,
        )
    except SolverInfeasibleError as exc:
        if exc.status != cp_model.INFEASIBLE:
            raise
        proof_seconds = time.time() - started
        print(
            f"solver: L0 infeasible ({exc.status_name}, proved in "
            f"{proof_seconds:.2f}s); retrying at L1"
        )
        solution = solve_request(
            decoded,
            max_time_seconds=max_time_seconds,
            num_search_workers=num_search_workers,
            relaxation=L1_SOFT_FLOORS,
        )
        print(
            f"solver: L1 solved in {time.time() - started:.2f}s "
            "(fallback engaged)"
        )
        return solution, L1_SOFT_FLOORS
    return solution, L0_FULL


def solve_payload(
    payload: dict,
    max_time_seconds: float = 30.0,
    num_search_workers: int | None = None,
) -> dict:
    """Decode a raw v1 request payload, solve via the fallback ladder (STR-54),
    and encode the v1 response."""
    decoded = W.decode_request(payload)
    solution, _level = _solve_with_fallback(
        decoded,
        max_time_seconds=max_time_seconds,
        num_search_workers=num_search_workers,
    )
    return W.encode_response(solution)

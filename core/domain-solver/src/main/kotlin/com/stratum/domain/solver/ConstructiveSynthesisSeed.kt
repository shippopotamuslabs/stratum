@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.stratum.domain.solver

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.sessionAimHardSetsForLength
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.context.resolveTrainingTimeline
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.structure.isSlotCompatibleWithRegionFilter
import com.stratum.domain.program.generation.structure.regionFiltersBySessionPosition
import com.stratum.domain.program.generation.synthesis.parseSessionSlotId
import com.stratum.domain.program.generation.universe.SynthesisSlot
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.schedule.TrainingTimeline
import kotlin.math.max
import kotlin.math.min

/**
 * Partial warm-start for CP-SAT. Hints are soft — the solver may abandon them.
 *
 * **Region filters:** Unrestricted whenever allowed (mix-full families); pure U/L only when
 * Unrestricted is not in that session's domain.
 *
 * **Slot families (Unrestricted):**
 * - Rest-sandwiched weekday → [SeedFamily.Full] (squat+hinge+press+pull seed)
 * - Dense weekday → alternate [SeedFamily.PressHinge] (chest/ham/tri/delt) and
 *   [SeedFamily.PullSquat] (back/quad/bi/delt) so first incumbents look like real programs
 *
 * **Volume structure (no new objective weights):** when adding sets, always prefer the muscle
 * with the largest remaining weekly aim among selected slots — equalizes Chest↔Back and
 * Quad↔Ham when aims match (maintenance). Dense Unrestricted seeds use at most one lower
 * anchor so legs don't double-dip vs upper.
 */
data class SynthesisSeedAssignment(
    val regionFilterBySession: Map<Int, SessionRegionFilter>,
    val weekdayBySession: Map<Int, Int>,
    /** Only slots with sets > 0. Absence means "no hint" for that slot. */
    val setsBySlotId: Map<String, Int>,
    val seedFamilyBySession: Map<Int, SeedFamily> = emptyMap(),
) {
    val activeSlotCount: Int get() = setsBySlotId.count { (_, sets) -> sets > 0 }
    val totalSets: Int get() = setsBySlotId.values.sum()
}

/** Soft structure template for Unrestricted warm-start — not a solver decision variable. */
enum class SeedFamily {
    /** Chest / ham / tri / lateral — press + hinge day. */
    PressHinge,
    /** Back / quad / bi / lateral — pull + squat day. */
    PullSquat,
    /** True full-body anchors (rest-sandwich days only). */
    Full,
    /** Pure upper or pure lower region filter. */
    RegionGated,
}

object ConstructiveSynthesisSeed {
    private const val MIN_ACTIVE_LIFTS = 3
    private const val MIN_SETS_PER_ACTIVE = 2
    private const val MAX_ACTIVE_PER_SESSION = 4
    private const val MAX_SETS_PER_ACTIVE = 3
    private const val COMPOUND_CAP = 4
    private const val DEFAULT_SESSION_CAP = 16


    // --- Family A: chest / ham / tri / delt ---
    private val PRESS_HINGE_MUSCLES =
        setOf("Chest", "Hamstring", "Tricep", "LateralDeltoid", "AnteriorDeltoid", "PosteriorDeltoid")
    private val PRESS_HINGE_PATTERNS =
        setOf("HorizontalPress", "InclinePress", "HipHinge", "KneeFlexion", "ElbowExtension", "ShoulderAbduction", "VerticalPress")

    // --- Family B: back / quad / bi / delt ---
    private val PULL_SQUAT_MUSCLES =
        setOf("Back", "Quad", "Bicep", "LateralDeltoid", "PosteriorDeltoid", "Glute")
    private val PULL_SQUAT_PATTERNS =
        setOf(
            "VerticalPull",
            "HorizontalPull",
            "SquatPattern",
            "KneeExtension",
            "ElbowFlexion",
            "ShoulderAbduction",
            "ShoulderHorizontalAbduction",
            "HipExtension",
        )

    private val FULL_BODY_PATTERNS =
        setOf("SquatPattern", "HipHinge", "HorizontalPress", "HorizontalPull", "VerticalPull")

    private val LOWER_ANCHOR_PATTERNS = setOf("SquatPattern", "HipHinge")

    fun build(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
        universe: List<SynthesisSlot>,
        plans: List<SessionSlotPlan>,
    ): SynthesisSeedAssignment {
        val sortedPlans = plans.sortedBy { it.position }
        val sessionCount = sortedPlans.size
        val timeline = context.resolveTrainingTimeline()
        val regionFilters = proposeRegionFilters(sessionCount, timeline)
        val trainingSlots = timeline.trainingSlotIndices
        require(trainingSlots.size == sessionCount) {
            "Seed training slot count ${trainingSlots.size} != session count $sessionCount " +
                "(cycleLength=${timeline.cycleLength})"
        }
        val weekdayBySession =
            sortedPlans.mapIndexed { index, plan -> plan.position to trainingSlots[index] }.toMap()
        val regionFilterBySession =
            sortedPlans.mapIndexed { index, plan -> plan.position to regionFilters[index] }.toMap()

        // Soft fill toward band aim; never seed above band max hard ceiling.
        val sessionAim =
            sessionAimHardSetsForLength(context.sessionLengthMinutes) ?: DEFAULT_SESSION_CAP
        val sessionMax =
            sessionHardSetCapForLength(context.sessionLengthMinutes) ?: sessionAim
        val sessionBudget =
            max(MIN_ACTIVE_LIFTS * MIN_SETS_PER_ACTIVE, sessionAim).coerceAtMost(sessionMax)
        val aimLeft =
            weeklyTargets.mapValues { (_, target) -> target.aim }.toMutableMap()

        val setsBySlotId = linkedMapOf<String, Int>()
        val familyBySession = linkedMapOf<Int, SeedFamily>()
        var unrestrictedFamilyIndex = 0

        for ((index, plan) in sortedPlans.withIndex()) {
            val regionFilter = regionFilterBySession.getValue(plan.position)
            val day = weekdayBySession.getValue(plan.position)
            val family =
                proposeSeedFamily(
                    regionFilter = regionFilter,
                    dayIndex = day,
                    timeline = timeline,
                    unrestrictedFamilyIndex = unrestrictedFamilyIndex,
                )
            if (regionFilter == SessionRegionFilter.Unrestricted && family != SeedFamily.Full) {
                unrestrictedFamilyIndex++
            }
            familyBySession[plan.position] = family
            allocateSession(
                plan = plan,
                regionFilter = regionFilter,
                family = family,
                universe = universe,
                context = context,
                sessionBudget = sessionBudget,
                aimLeft = aimLeft,
                setsBySlotId = setsBySlotId,
            )
        }

        return SynthesisSeedAssignment(
            regionFilterBySession = regionFilterBySession,
            weekdayBySession = weekdayBySession,
            setsBySlotId = setsBySlotId,
            seedFamilyBySession = familyBySession,
        )
    }

    fun proposeRegionFilters(sessionCount: Int, timeline: TrainingTimeline): List<SessionRegionFilter> {
        if (sessionCount <= 0) return emptyList()
        val perSession = regionFiltersBySessionPosition(timeline)
        require(perSession.size == sessionCount) {
            "regionFiltersBySessionPosition size ${perSession.size} != sessionCount $sessionCount " +
                "(timeline=$timeline)"
        }
        var upperLowerIndex = 0
        return perSession.map { allowed ->
            when {
                SessionRegionFilter.Unrestricted in allowed -> SessionRegionFilter.Unrestricted
                SessionRegionFilter.LowerOnly in allowed && SessionRegionFilter.UpperOnly in allowed -> {
                    val filter =
                        if (upperLowerIndex % 2 == 0) {
                            SessionRegionFilter.LowerOnly
                        } else {
                            SessionRegionFilter.UpperOnly
                        }
                    upperLowerIndex++
                    filter
                }
                else -> allowed.first()
            }
        }
    }

    /** Classic weekday list entry — resolves a 7-day timeline from named weekdays. */
    fun proposeRegionFilters(sessionCount: Int, weekdays: List<String>): List<SessionRegionFilter> {
        val trainingSlots =
            com.stratum.domain.program.generation.split.trainingWeekdayIndices(weekdays)
        return proposeRegionFilters(
            sessionCount,
            TrainingTimeline(cycleLength = 7, trainingSlotIndices = trainingSlots),
        )
    }

    /**
     * Rest-sandwich → Full; pure U/L filter → RegionGated; else alternate PressHinge / PullSquat.
     */
    fun proposeSeedFamily(
        regionFilter: SessionRegionFilter,
        dayIndex: Int,
        timeline: TrainingTimeline,
        unrestrictedFamilyIndex: Int,
    ): SeedFamily =
        when (regionFilter) {
            SessionRegionFilter.UpperOnly, SessionRegionFilter.LowerOnly -> SeedFamily.RegionGated
            SessionRegionFilter.Unrestricted ->
                if (timeline.isRestSandwiched(dayIndex)) {
                    SeedFamily.Full
                } else if (unrestrictedFamilyIndex % 2 == 0) {
                    SeedFamily.PressHinge
                } else {
                    SeedFamily.PullSquat
                }
        }

    fun proposeSeedFamily(
        regionFilter: SessionRegionFilter,
        dayIndex: Int,
        trainingDays: Set<Int>,
        unrestrictedFamilyIndex: Int,
        cycleLength: Int = 7,
    ): SeedFamily =
        proposeSeedFamily(
            regionFilter = regionFilter,
            dayIndex = dayIndex,
            timeline =
                TrainingTimeline(
                    cycleLength = cycleLength,
                    trainingSlotIndices = trainingDays.sorted(),
                ),
            unrestrictedFamilyIndex = unrestrictedFamilyIndex,
        )

    private fun allocateSession(
        plan: SessionSlotPlan,
        regionFilter: SessionRegionFilter,
        family: SeedFamily,
        universe: List<SynthesisSlot>,
        context: ProgramGenerationContext,
        sessionBudget: Int,
        aimLeft: MutableMap<String, Double>,
        setsBySlotId: MutableMap<String, Int>,
    ) {
        val universeById = universe.associateBy { it.id }
        val candidates =
            plan.filledSlots.mapNotNull { filled ->
                val universeId = parseSessionSlotId(filled.slotId)?.second ?: return@mapNotNull null
                val synthesis = universeById[universeId] ?: return@mapNotNull null
                if (!isSlotCompatibleWithRegionFilter(synthesis, regionFilter)) return@mapNotNull null
                filled to synthesis
            }
        if (candidates.isEmpty()) return

        val ranked =
            candidates
                .sortedWith(
                    compareByDescending<Pair<FilledSlot, SynthesisSlot>> { (_, s) ->
                        familyAffinity(family, s)
                    }
                        .thenByDescending { (_, s) -> specializationPriority(context, s.primaryMuscle) }
                        .thenByDescending { (filled, _) -> rolePriority(filled.role) }
                        .thenByDescending { (_, s) -> aimLeft[s.primaryMuscle] ?: 0.0 }
                        .thenBy { it.first.slotId },
                )
                .distinctBy { (_, s) -> s.pattern to s.primaryMuscle }

        val selected = pickFamilySlots(ranked, family)
        if (selected.isEmpty()) return

        val sets = linkedMapOf<String, Int>()
        selected.forEach { (filled, _) -> sets[filled.slotId] = MIN_SETS_PER_ACTIVE }
        var used = sets.values.sum()

        // Drain aimLeft by preferred remaining aim (balances equal-aim antagonist pairs).
        while (used < sessionBudget) {
            val best =
                selected.maxByOrNull { (filled, synthesis) ->
                    val cap = min(perSlotCap(filled), MAX_SETS_PER_ACTIVE)
                    val current = sets.getValue(filled.slotId)
                    if (current >= cap) return@maxByOrNull Double.NEGATIVE_INFINITY
                    val need = aimLeft[synthesis.primaryMuscle] ?: 0.0
                    // Soft pair-balance: prefer the lagging antagonist when both are selected.
                    need + specializationPriority(context, synthesis.primaryMuscle) * 50.0 +
                        pairBalanceNudge(synthesis.primaryMuscle, aimLeft, selected)
                } ?: break
            val (filled, synthesis) = best
            val cap = min(perSlotCap(filled), MAX_SETS_PER_ACTIVE)
            val current = sets.getValue(filled.slotId)
            if (current >= cap) break
            val anyNeed = selected.any { (_, s) -> (aimLeft[s.primaryMuscle] ?: 0.0) > 0.5 }
            if (!anyNeed) break
            sets[filled.slotId] = current + 1
            used++
            aimLeft[synthesis.primaryMuscle] = (aimLeft[synthesis.primaryMuscle] ?: 0.0) - 1.0
        }

        for ((_, synthesis) in selected) {
            aimLeft[synthesis.primaryMuscle] =
                (aimLeft[synthesis.primaryMuscle] ?: 0.0) - MIN_SETS_PER_ACTIVE.toDouble()
        }

        for ((slotId, count) in sets) {
            if (count > 0) setsBySlotId[slotId] = count
        }
    }

    /**
     * Prefer slots matching the family; for dense Unrestricted A/B, at most one lower anchor.
     */
    private fun pickFamilySlots(
        ranked: List<Pair<FilledSlot, SynthesisSlot>>,
        family: SeedFamily,
    ): List<Pair<FilledSlot, SynthesisSlot>> {
        val preferred = ranked.filter { (_, s) -> familyAffinity(family, s) > 0 }
        val pool = preferred.ifEmpty { ranked }
        val out = mutableListOf<Pair<FilledSlot, SynthesisSlot>>()
        var lowerAnchors = 0
        val maxLowerAnchors =
            when (family) {
                SeedFamily.Full -> 2
                SeedFamily.PressHinge, SeedFamily.PullSquat -> 1
                SeedFamily.RegionGated -> 2
            }
        for (pair in pool) {
            if (out.size >= MAX_ACTIVE_PER_SESSION) break
            val isLowerAnchor = pair.second.pattern in LOWER_ANCHOR_PATTERNS
            if (isLowerAnchor && lowerAnchors >= maxLowerAnchors) continue
            out += pair
            if (isLowerAnchor) lowerAnchors++
        }
        if (out.size < MIN_ACTIVE_LIFTS) {
            for (pair in ranked) {
                if (out.size >= MIN_ACTIVE_LIFTS) break
                if (out.any { it.second.id == pair.second.id }) continue
                val isLowerAnchor = pair.second.pattern in LOWER_ANCHOR_PATTERNS
                if (isLowerAnchor && lowerAnchors >= maxLowerAnchors) continue
                out += pair
                if (isLowerAnchor) lowerAnchors++
            }
        }
        return out.take(MAX_ACTIVE_PER_SESSION)
    }

    private fun familyAffinity(family: SeedFamily, slot: SynthesisSlot): Int =
        when (family) {
            SeedFamily.PressHinge ->
                (if (slot.primaryMuscle in PRESS_HINGE_MUSCLES) 2 else 0) +
                    (if (slot.pattern in PRESS_HINGE_PATTERNS) 2 else 0) +
                    (if (slot.pattern in PULL_SQUAT_PATTERNS - PRESS_HINGE_PATTERNS) -2 else 0)
            SeedFamily.PullSquat ->
                (if (slot.primaryMuscle in PULL_SQUAT_MUSCLES) 2 else 0) +
                    (if (slot.pattern in PULL_SQUAT_PATTERNS) 2 else 0) +
                    (if (slot.pattern in PRESS_HINGE_PATTERNS - PULL_SQUAT_PATTERNS) -2 else 0)
            SeedFamily.Full ->
                if (slot.pattern in FULL_BODY_PATTERNS || slot.role == SlotRole.Anchor) 3 else 0
            SeedFamily.RegionGated -> 0
        }

    /**
     * When both members of an antagonist pair are among [selected], nudge the one with higher
     * remaining aim so equal-aim maintenance pairs stay even without new objective terms.
     */
    private fun pairBalanceNudge(
        muscle: String,
        aimLeft: Map<String, Double>,
        selected: List<Pair<FilledSlot, SynthesisSlot>>,
    ): Double {
        val partner =
            when (muscle) {
                "Chest" -> "Back"
                "Back" -> "Chest"
                "Quad" -> "Hamstring"
                "Hamstring" -> "Quad"
                "Bicep" -> "Tricep"
                "Tricep" -> "Bicep"
                else -> return 0.0
            }
        val selectedMuscles = selected.map { it.second.primaryMuscle }.toSet()
        if (muscle !in selectedMuscles || partner !in selectedMuscles) return 0.0
        val mine = aimLeft[muscle] ?: 0.0
        val theirs = aimLeft[partner] ?: 0.0
        return (mine - theirs) * 0.25
    }

    private fun specializationPriority(context: ProgramGenerationContext, muscleName: String): Int {
        val muscle = runCatching { MuscleGroup.valueOf(muscleName) }.getOrNull() ?: return 0
        return when (context.specialization[muscle] ?: SpecializationLevel.Maintenance) {
            SpecializationLevel.Specialize -> 2
            SpecializationLevel.MinimumVolume -> 1
            SpecializationLevel.Maintenance -> 0
            SpecializationLevel.Omit -> -1
        }
    }

    private fun rolePriority(role: SlotRole): Int =
        when (role) {
            SlotRole.Anchor -> 3
            SlotRole.SecondaryCompound -> 2
            SlotRole.Accessory -> 1
            else -> 0
        }

    private fun perSlotCap(slot: FilledSlot): Int =
        when {
            slot.role == SlotRole.SecondaryCompound -> COMPOUND_CAP
            slot.role == SlotRole.Anchor && slot.repProfile != RpRepProfile.Isolation -> COMPOUND_CAP
            else -> SETS_PER_LIFT_CAP
        }
}

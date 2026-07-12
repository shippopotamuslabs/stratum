@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.stratum.domain.solver

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import com.stratum.domain.program.generation.synthesis.SynthesisSlot
import com.stratum.domain.program.generation.synthesis.isSlotCompatibleWithDomain
import com.stratum.domain.program.generation.synthesis.parseSessionSlotId
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import kotlin.math.max
import kotlin.math.min

/**
 * Partial warm-start for CP-SAT. Hints are soft — the solver may abandon them.
 *
 * v3: **all Full domains** — cold/search consistently prefers mixed sessions over pure U/L
 * for 4+ day programs (U/L seeds regressed first-obj vs cold). Sparse incomplete set hints.
 * - Domains: Full for every session.
 * - Calendar: sorted weekdays (symmetry-break aligned).
 * - Sets: few actives at floor sets — do **not** pack to session cap.
 * - Inactive slots are **not** hinted.
 */
data class SynthesisSeedAssignment(
    val domainBySession: Map<Int, SynthesisSessionDomain>,
    val weekdayBySession: Map<Int, Int>,
    /** Only slots with sets > 0. Absence means "no hint" for that slot. */
    val setsBySlotId: Map<String, Int>,
) {
    val activeSlotCount: Int get() = setsBySlotId.count { (_, sets) -> sets > 0 }
    val totalSets: Int get() = setsBySlotId.values.sum()
}

object ConstructiveSynthesisSeed {
    private const val MIN_ACTIVE_LIFTS = 3
    private const val MIN_SETS_PER_ACTIVE = 2
    /** Keep seed well under a full session so recovery soft costs stay modest. */
    private const val MAX_ACTIVE_PER_SESSION = 3
    private const val MAX_SETS_PER_ACTIVE = 3
    private const val COMPOUND_CAP = 4
    private const val DEFAULT_SESSION_CAP = 16
    /** Fraction of session hard-set budget the seed may use (rest left free for search). */
    private const val SESSION_FILL_FRACTION = 0.45

    fun build(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
        universe: List<SynthesisSlot>,
        plans: List<SessionSlotPlan>,
    ): SynthesisSeedAssignment {
        val sortedPlans = plans.sortedBy { it.position }
        val sessionCount = sortedPlans.size
        val domains = proposeDomains(sessionCount)
        val weekdays = trainingWeekdayIndices(context.weekdays)
        require(weekdays.size == sessionCount) {
            "Seed weekday count ${weekdays.size} != session count $sessionCount"
        }
        val weekdayBySession =
            sortedPlans.mapIndexed { index, plan -> plan.position to weekdays[index] }.toMap()
        val domainBySession =
            sortedPlans.mapIndexed { index, plan -> plan.position to domains[index] }.toMap()

        val sessionCap =
            sessionHardSetCapForLength(context.sessionLengthMinutes) ?: DEFAULT_SESSION_CAP
        val sessionBudget =
            max(MIN_ACTIVE_LIFTS * MIN_SETS_PER_ACTIVE, (sessionCap * SESSION_FILL_FRACTION).toInt())
        val aimLeft =
            weeklyTargets.mapValues { (_, target) -> target.aim }.toMutableMap()

        // Incomplete: only positive set counts are recorded (no explicit zeros).
        val setsBySlotId = linkedMapOf<String, Int>()

        for (plan in sortedPlans) {
            val domain = domainBySession.getValue(plan.position)
            allocateSession(
                plan = plan,
                domain = domain,
                universe = universe,
                context = context,
                sessionBudget = sessionBudget,
                aimLeft = aimLeft,
                setsBySlotId = setsBySlotId,
            )
        }

        return SynthesisSeedAssignment(
            domainBySession = domainBySession,
            weekdayBySession = weekdayBySession,
            setsBySlotId = setsBySlotId,
        )
    }

    /**
     * All sessions Full. Empirically the solver lands on mixed (Full) days even for 4–6 day
     * programs; U/L alternate seeds produced worse first incumbents than cold search.
     */
    fun proposeDomains(sessionCount: Int): List<SynthesisSessionDomain> {
        if (sessionCount <= 0) return emptyList()
        return List(sessionCount) { SynthesisSessionDomain.Full }
    }

    private fun allocateSession(
        plan: SessionSlotPlan,
        domain: SynthesisSessionDomain,
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
                if (!isSlotCompatibleWithDomain(synthesis, domain)) return@mapNotNull null
                filled to synthesis
            }
        if (candidates.isEmpty()) return

        val ranked =
            candidates
                .sortedWith(
                    compareByDescending<Pair<FilledSlot, SynthesisSlot>> { (_, s) ->
                        specializationPriority(context, s.primaryMuscle)
                    }
                        .thenByDescending { (filled, _) -> rolePriority(filled.role) }
                        .thenByDescending { (_, s) -> aimLeft[s.primaryMuscle] ?: 0.0 }
                        .thenBy { it.first.slotId },
                )
                .distinctBy { (_, s) -> s.pattern to s.primaryMuscle }

        val targetActives =
            min(MAX_ACTIVE_PER_SESSION, ranked.size)
                .coerceAtLeast(min(MIN_ACTIVE_LIFTS, ranked.size))
        val selected = ranked.take(targetActives)
        if (selected.isEmpty()) return

        val sets = linkedMapOf<String, Int>()
        selected.forEach { (filled, _) -> sets[filled.slotId] = MIN_SETS_PER_ACTIVE }
        var used = sets.values.sum()

        while (used < sessionBudget) {
            val best =
                selected.maxByOrNull { (filled, synthesis) ->
                    val cap = min(perSlotCap(filled), MAX_SETS_PER_ACTIVE)
                    val current = sets.getValue(filled.slotId)
                    if (current >= cap) return@maxByOrNull Double.NEGATIVE_INFINITY
                    val need = aimLeft[synthesis.primaryMuscle] ?: 0.0
                    need + specializationPriority(context, synthesis.primaryMuscle) * 50.0
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
            SlotRole.Isolation -> 1
            else -> 0
        }

    private fun perSlotCap(slot: FilledSlot): Int =
        when {
            slot.role == SlotRole.SecondaryCompound -> COMPOUND_CAP
            slot.role == SlotRole.Anchor && slot.repProfile != RpRepProfile.Isolation -> COMPOUND_CAP
            else -> SETS_PER_LIFT_CAP
        }
}

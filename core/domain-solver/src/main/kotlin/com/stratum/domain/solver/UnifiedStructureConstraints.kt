@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.stratum.domain.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.LinearExpr
import com.google.ortools.util.Domain
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.universe.SynthesisSlot
import com.stratum.domain.program.generation.structure.isSlotCompatibleWithRegionFilter
import com.stratum.domain.program.generation.synthesis.parseSessionSlotId
import com.stratum.domain.program.generation.structure.regionFiltersBySessionPosition
import com.stratum.domain.program.schedule.TrainingTimeline

internal fun addUnifiedStructureConstraints(
    model: CpModel,
    plans: List<SessionSlotPlan>,
    universe: List<SynthesisSlot>,
    slotVars: List<ProgramSynthesisModel.SlotVar>,
    state: UnifiedStructureState,
    timeline: TrainingTimeline,
) {
    val universeById = universe.associateBy { it.id }
    val filtersByPosition = regionFiltersBySessionPosition(timeline)
    require(filtersByPosition.isNotEmpty()) {
        "regionFiltersBySessionPosition is empty for timeline=$timeline"
    }
    require(filtersByPosition.size == plans.size) {
        "region filter positions ${filtersByPosition.size} != session count ${plans.size} " +
            "(timeline=$timeline)"
    }
    plans.forEach { plan ->
        val allowed = filtersByPosition[plan.position]
        require(allowed.isNotEmpty()) {
            "allowed region filters empty for session position ${plan.position}"
        }
        val filterOrdinals = allowed.map { it.ordinal.toLong() }.sorted().toLongArray()
        val regionFilterVar =
            model.newIntVarFromDomain(
                Domain.fromValues(filterOrdinals),
                "regionFilter_${plan.position}",
            )
        state.regionFilterVarByPosition[plan.position] = regionFilterVar
    }

    val bySession = slotVars.groupBy { sv -> sv.plan.position }
    for ((sessionIndex, sessionVars) in bySession) {
        val regionFilterVar = state.regionFilterVarByPosition.getValue(sessionIndex)
        val allowed = filtersByPosition[sessionIndex]
        // Only branch on region filters still in the per-session search universe.
        for (regionFilter in allowed) {
            val isFilter = model.newBoolVar("isRegionFilter_${sessionIndex}_${regionFilter.name}")
            model.addEquality(regionFilterVar, regionFilter.ordinal.toLong()).onlyEnforceIf(isFilter)
            model.addDifferent(regionFilterVar, regionFilter.ordinal.toLong()).onlyEnforceIf(isFilter.not())
            for (sv in sessionVars) {
                val active = sv.active ?: continue
                val universeId = parseSessionSlotId(sv.slot.slotId)?.second ?: continue
                val synthesisSlot = universeById[universeId] ?: continue
                if (!isSlotCompatibleWithRegionFilter(synthesisSlot, regionFilter)) {
                    model.addEquality(active, 0).onlyEnforceIf(isFilter)
                }
            }
        }

        val uniquenessGroups =
            sessionVars.groupBy { sv ->
                Pair(sv.slot.pattern, sv.slot.primaryMuscle)
            }
        for ((_, group) in uniquenessGroups) {
            if (group.size <= 1) continue
            val actives = group.mapNotNull { it.active }
            if (actives.isEmpty()) continue
            model.addLessOrEqual(LinearExpr.sum(actives.toTypedArray()), 1L)
        }

        val lowerAnchors =
            sessionVars.filter { sv ->
                sv.slot.pattern in LOWER_LEG_ANCHOR_PATTERNS && sv.active != null
            }
        if (lowerAnchors.isNotEmpty() && SessionRegionFilter.LowerOnly in allowed) {
            val isLower = model.newBoolVar("isLowerRegionFilter_$sessionIndex")
            model.addEquality(regionFilterVar, SessionRegionFilter.LowerOnly.ordinal.toLong())
                .onlyEnforceIf(isLower)
            model.addDifferent(regionFilterVar, SessionRegionFilter.LowerOnly.ordinal.toLong())
                .onlyEnforceIf(isLower.not())
            val anchorActiveSum =
                LinearExpr.sum(lowerAnchors.mapNotNull { it.active }.toTypedArray())
            model.addGreaterOrEqual(anchorActiveSum, 1L).onlyEnforceIf(isLower)
        }
    }
}

private val LOWER_LEG_ANCHOR_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

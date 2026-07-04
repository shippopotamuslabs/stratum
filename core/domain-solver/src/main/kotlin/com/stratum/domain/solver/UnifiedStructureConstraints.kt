@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.stratum.domain.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.Literal
import com.google.ortools.util.Domain
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import com.stratum.domain.program.generation.synthesis.SynthesisSlot
import com.stratum.domain.program.generation.synthesis.isSlotCompatibleWithDomain
import com.stratum.domain.program.generation.synthesis.parseSessionSlotId

internal fun addUnifiedStructureConstraints(
    model: CpModel,
    plans: List<SessionSlotPlan>,
    universe: List<SynthesisSlot>,
    slotVars: List<ProgramSynthesisModel.SlotVar>,
    state: UnifiedStructureState,
) {
    val universeById = universe.associateBy { it.id }
    plans.forEach { plan ->
        val domainVar =
            model.newIntVarFromDomain(
                Domain.fromValues(longArrayOf(0, 1, 2)),
                "domain_${plan.position}",
            )
        state.domainVarByPosition[plan.position] = domainVar
    }

    val bySession = slotVars.groupBy { sv -> sv.plan.position }
    for ((sessionIndex, sessionVars) in bySession) {
        val domainVar = state.domainVarByPosition.getValue(sessionIndex)
        for (domain in SynthesisSessionDomain.entries) {
            val isDomain = model.newBoolVar("isDomain_${sessionIndex}_${domain.name}")
            model.addEquality(domainVar, domain.ordinal.toLong()).onlyEnforceIf(isDomain)
            model.addDifferent(domainVar, domain.ordinal.toLong()).onlyEnforceIf(isDomain.not())
            for (sv in sessionVars) {
                val active = sv.active ?: continue
                val universeId = parseSessionSlotId(sv.slot.slotId)?.second ?: continue
                val synthesisSlot = universeById[universeId] ?: continue
                if (!isSlotCompatibleWithDomain(synthesisSlot, domain)) {
                    model.addEquality(active, 0).onlyEnforceIf(isDomain)
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
        if (lowerAnchors.isNotEmpty()) {
            val isLower = model.newBoolVar("isLowerDomain_$sessionIndex")
            model.addEquality(domainVar, SynthesisSessionDomain.Lower.ordinal.toLong()).onlyEnforceIf(isLower)
            model.addDifferent(domainVar, SynthesisSessionDomain.Lower.ordinal.toLong()).onlyEnforceIf(isLower.not())
            val anchorActiveSum =
                LinearExpr.sum(lowerAnchors.mapNotNull { it.active }.toTypedArray())
            model.addGreaterOrEqual(anchorActiveSum, 1L).onlyEnforceIf(isLower)
        }
    }
}

private val LOWER_LEG_ANCHOR_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

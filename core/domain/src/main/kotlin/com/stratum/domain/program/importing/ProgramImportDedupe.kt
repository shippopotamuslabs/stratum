package com.stratum.domain.program.importing

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId

internal object ProgramImportDedupe {
    fun shouldSkip(
        candidate: Program,
        existing: List<Program>,
        exportedId: String?,
        liftNamesById: Map<LiftId, String> = emptyMap(),
    ): Boolean {
        val userPrograms =
            existing.filter { it.id != ProgramId.ImportHistory }
        if (exportedId != null && userPrograms.any { it.id.value == exportedId }) {
            return true
        }
        return userPrograms.any { existingProgram ->
            softMatch(existingProgram, candidate, liftNamesById)
        }
    }

    /**
     * Soft match: same name (ignore case) + cycleLength + ordered training workout names +
     * per training workout ordered lift **names** + per lift set counts.
     *
     * Lift display names come from [liftNamesById]; when a lift is missing from the map,
     * [LiftId.value] is used as a fallback (same policy as program export).
     */
    private fun softMatch(
        existing: Program,
        candidate: Program,
        liftNamesById: Map<LiftId, String>,
    ): Boolean {
        if (!existing.name.equals(candidate.name, ignoreCase = true)) return false
        if (existing.cycleLength != candidate.cycleLength) return false
        val existingWorkouts = existing.workouts.sortedBy { it.position }
        val candidateWorkouts = candidate.workouts.sortedBy { it.position }
        if (existingWorkouts.size != candidateWorkouts.size) return false
        return existingWorkouts.indices.all { i ->
            workoutsStructureEqual(existingWorkouts[i], candidateWorkouts[i], liftNamesById)
        }
    }

    private fun workoutsStructureEqual(
        existing: com.stratum.domain.program.Workout,
        candidate: com.stratum.domain.program.Workout,
        liftNamesById: Map<LiftId, String>,
    ): Boolean {
        if (existing.name != candidate.name) return false
        val eLifts = existing.workoutLifts.sortedBy { it.position }
        val cLifts = candidate.workoutLifts.sortedBy { it.position }
        if (eLifts.size != cLifts.size) return false
        return eLifts.indices.all { j ->
            liftDisplayName(eLifts[j].liftId, liftNamesById) ==
                liftDisplayName(cLifts[j].liftId, liftNamesById) &&
                eLifts[j].sets.size == cLifts[j].sets.size
        }
    }

    private fun liftDisplayName(
        liftId: LiftId,
        liftNamesById: Map<LiftId, String>,
    ): String = liftNamesById[liftId] ?: liftId.value
}

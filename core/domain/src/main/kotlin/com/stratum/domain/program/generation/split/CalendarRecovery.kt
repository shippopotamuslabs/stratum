package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.structure.SessionRegionFilter

/**
 * Presentation-only name → [SessionTypeId] heuristic.
 *
 * **Not structure truth.** Workout names are polish (placeholders, catalog display, or LLM
 * renames) and must not drive recovery, variety, or synthesis. Production generation paths
 * use [com.stratum.domain.program.generation.structure.SessionRegionFilter] /
 * muscle-dose metadata instead. Kept for unit tests and any UI that still maps fixed catalog
 * name prefixes to labels.
 */
internal fun inferSessionTypeFromWorkoutName(name: String): SessionTypeId? {
    val normalized = name.trim().lowercase()
    return when {
        normalized.startsWith("lower") -> SessionTypeId.Lower
        normalized.startsWith("upper push") -> SessionTypeId.UpperPush
        normalized.startsWith("upper pull") -> SessionTypeId.UpperPull
        normalized.startsWith("upper") -> SessionTypeId.Upper
        normalized.startsWith("full push") -> SessionTypeId.FullPush
        normalized.startsWith("full pull") -> SessionTypeId.FullPull
        normalized.startsWith("full body") -> SessionTypeId.FullBody
        normalized.startsWith("compound pair") -> SessionTypeId.CompoundPair
        normalized.startsWith("aux pair") -> SessionTypeId.AuxPair
        normalized.startsWith("single anchor") -> SessionTypeId.SingleAnchor
        normalized.startsWith("chest & tris") -> SessionTypeId.ChestTris
        normalized.startsWith("back & bis") -> SessionTypeId.BackBis
        normalized.startsWith("chest") -> SessionTypeId.Chest
        normalized.startsWith("back") -> SessionTypeId.Back
        normalized.startsWith("shoulders") -> SessionTypeId.Shoulders
        normalized.startsWith("arms") -> SessionTypeId.Arms
        normalized.startsWith("quads") -> SessionTypeId.QuadsCalves
        normalized.startsWith("glutes") -> SessionTypeId.GlutesHams
        else -> null
    }
}

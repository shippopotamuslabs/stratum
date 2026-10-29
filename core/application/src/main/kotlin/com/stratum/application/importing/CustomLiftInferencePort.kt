package com.stratum.application.importing

import com.stratum.domain.importing.InferredCustomLiftMetadata
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Equipment

/** An imported exercise name that did not match the bundled seed catalog. */
data class UnmatchedExerciseCandidate(
    val exerciseNameRaw: String,
    val loggedSetCount: Int,
)

/**
 * How a custom import lift is loaded for weight increments.
 *
 * Seed ids map 1:1 onto bundled [com.stratum.domain.lift.Equipment] rows. Custom names go through
 * find-or-create (reuse existing by name / alias, else mint with an open band). Unknown loading
 * uses seed [SeedEquipmentIds.Default] (+2.5 lb) so progression always has a lattice.
 */

/** Cloud LLM port for inferring custom-lift metadata from exercise names. */
interface CustomLiftInferencePort {
    suspend fun inferMetadata(
        candidates: List<UnmatchedExerciseCandidate>,
    ): Result<List<InferredCustomLiftMetadata>>
}

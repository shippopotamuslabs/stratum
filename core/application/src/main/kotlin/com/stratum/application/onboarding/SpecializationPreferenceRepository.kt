package com.stratum.application.onboarding

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Port for reading and mutating the [SpecializationPreference] captured during onboarding.
 *
 * Implemented in `:core:data` (Room) and faked in `:core:testing`. Domain and feature code
 * depend only on this interface. Storage replaces the whole preference atomically — there is
 * one preference per install (per program, once OS-6 links rows to a `program_id`).
 */
interface SpecializationPreferenceRepository {
    /**
     * Hot stream of the current specialization preference. Emits [SpecializationPreference.Empty]
     * until the user first saves anything (a skipped step stays empty).
     */
    fun observe(): Flow<SpecializationPreference>

    /**
     * Replace the stored preference wholesale. Called as the user advances from or back into
     * the Specialization step so edits survive navigation.
     */
    suspend fun save(preference: SpecializationPreference)
}

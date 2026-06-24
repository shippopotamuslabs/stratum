package com.stratum.domain.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Port for reading and mutating the [UserTrainingProfile] captured during onboarding.
 *
 * Implemented in `:core:data` (Room) and faked in `:core:testing`. Domain and feature
 * code depend only on this interface. A single profile row exists per install.
 */
interface UserTrainingProfileRepository {
    /**
     * Hot stream of the current profile draft. Emits `null` until the user first
     * saves any field.
     */
    fun observeProfile(): Flow<UserTrainingProfile?>

    /**
     * Persist the profile draft. Called as the user advances between the Profile and
     * Schedule steps so back-navigation preserves edits.
     */
    suspend fun saveProfile(profile: UserTrainingProfile)
}

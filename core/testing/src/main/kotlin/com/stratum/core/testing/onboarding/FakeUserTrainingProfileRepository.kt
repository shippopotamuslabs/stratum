package com.stratum.core.testing.onboarding

import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [UserTrainingProfileRepository]. Defaults to `null` (no draft saved
 * yet) so tests can exercise the empty pre-flow state, then `seed()` a known profile.
 */
class FakeUserTrainingProfileRepository(
    initial: UserTrainingProfile? = null,
) : UserTrainingProfileRepository {
    private val profile = MutableStateFlow(initial)

    override fun observeProfile(): Flow<UserTrainingProfile?> = profile

    override suspend fun saveProfile(profile: UserTrainingProfile) {
        this.profile.value = profile
    }

    /** Test hook: the current profile draft (or `null`). */
    val current: UserTrainingProfile? get() = profile.value

    /** Test hook: seed a specific profile state. */
    fun seed(profile: UserTrainingProfile?) {
        this.profile.value = profile
    }
}

package com.stratum.core.testing.onboarding

import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.SpecializationPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [SpecializationPreferenceRepository]. Defaults to
 * [SpecializationPreference.Empty] (the skip / maintenance-balanced default) so tests can
 * exercise the empty state, then `seed()` a known preference. [save] replaces wholesale —
 * mirroring the Room implementation's transactional clear+insert.
 */
class FakeSpecializationPreferenceRepository(
    initial: SpecializationPreference = SpecializationPreference.Empty,
) : SpecializationPreferenceRepository {
    private val preference = MutableStateFlow(initial)

    override fun observe(): Flow<SpecializationPreference> = preference

    override suspend fun save(preference: SpecializationPreference) {
        this.preference.value = preference
    }

    /** Test hook: the current preference. */
    val current: SpecializationPreference get() = preference.value

    /** Test hook: seed a specific preference state. */
    fun seed(preference: SpecializationPreference) {
        this.preference.value = preference
    }
}

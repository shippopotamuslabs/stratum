package com.stratum.domain.onboarding.identity

import com.stratum.domain.lift.identity.EntityId
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Typed ids for single-row / per-muscle onboarding preference tables.
 *
 * Well-known string values (not random UUIDs) so a new device can address the same
 * Firestore document after hydrate. User-created aggregates elsewhere use random UUIDs.
 */
@JvmInline
value class UserProfileId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "UserProfileId cannot be blank" }
    }

    companion object {
        /** Singleton training-profile row / cloud doc id. */
        const val DEFAULT_VALUE: String = "profile"
        val Default: UserProfileId = UserProfileId(DEFAULT_VALUE)
    }
}

@JvmInline
value class OnboardingProgressId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "OnboardingProgressId cannot be blank" }
    }

    companion object {
        const val DEFAULT_VALUE: String = "onboarding_progress"
        val Default: OnboardingProgressId = OnboardingProgressId(DEFAULT_VALUE)
    }
}

@JvmInline
value class GymPreferenceId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "GymPreferenceId cannot be blank" }
    }

    companion object {
        const val DEFAULT_VALUE: String = "gym_preference"
        val Default: GymPreferenceId = GymPreferenceId(DEFAULT_VALUE)
    }
}

/**
 * Per-muscle specialization row. Document id is deterministic from [MuscleGroup] so two
 * devices writing the same muscle preference converge on one Firestore document.
 */
@JvmInline
value class SpecializationPreferenceId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "SpecializationPreferenceId cannot be blank" }
    }

    companion object {
        fun forMuscle(muscle: MuscleGroup): SpecializationPreferenceId =
            SpecializationPreferenceId("specialization_${muscle.name}")
    }
}

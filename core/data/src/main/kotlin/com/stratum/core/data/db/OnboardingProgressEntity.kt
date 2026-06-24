package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.onboarding.OnboardingStep

/**
 * Single-row table tracking onboarding wizard progress.
 *
 * Schema authorized for OS-1 (see docs/onboarding-vertical-slices.md §OS-1 and
 * AGENTS.md §4.5 Schema Lock).
 *
 * [id] is fixed at [ID] — only one progress row exists per install. [currentStep] is a
 * typed [OnboardingStep] (decoded by [EnumConverters]).
 */
@Entity(tableName = "onboarding_progress")
data class OnboardingProgressEntity(
    @PrimaryKey val id: Int = ID,
    val currentStep: OnboardingStep,
    val startedAt: Long,
    val completedAt: Long?,
) {
    companion object {
        const val ID = 0
    }
}

package com.stratum.feature.program.ui

/** Presentation state for the coach check-in wizard (no domain types). */
data class CheckInUiState(
    val isOpen: Boolean = false,
    val phase: CheckInPhase = CheckInPhase.Subjective,
    val overallRecovery: RecoverySignalUi = RecoverySignalUi.Good,
    /**
     * Gate for localized issues. `null` = unanswered; `false` = all muscles good;
     * `true` = user will flag specific muscles.
     */
    val hasLocalizedIssues: Boolean? = null,
    val availableMuscles: List<CheckInMuscleOptionUi> = emptyList(),
    val flaggedMuscleKeys: Set<String> = emptySet(),
    val muscleSignals: List<CheckInMuscleSignalUi> = emptyList(),
    val recommendations: List<CheckInRecommendationUi> = emptyList(),
    /** Lift ids already on the active program — exclude from swap picker. */
    val programLiftIds: Set<String> = emptySet(),
    val isEvaluating: Boolean = false,
    val isApplying: Boolean = false,
    val errorMessage: String? = null,
    val swapPickerRecommendationId: String? = null,
    val swapCandidates: List<CheckInSwapCandidateUi> = emptyList(),
) {
    val stepIndex: Int
        get() =
            when (phase) {
                CheckInPhase.Subjective -> 0
                CheckInPhase.Recommendations -> 1
            }

    val stepCount: Int = 2

    val canAdvanceSubjective: Boolean
        get() =
            when (hasLocalizedIssues) {
                null -> false
                false -> true
                true ->
                    flaggedMuscleKeys.isNotEmpty() &&
                        flaggedMuscleKeys.all { key ->
                            muscleSignals.any { it.muscleKey == key && it.signal != RecoverySignalUi.Good }
                        }
            }

    val primaryActionEnabled: Boolean
        get() =
            when (phase) {
                CheckInPhase.Subjective -> canAdvanceSubjective && !isEvaluating
                CheckInPhase.Recommendations -> !isApplying
            }
}

enum class CheckInPhase {
    Subjective,
    Recommendations,
}

enum class RecoverySignalUi {
    Good,
    MildSoreness,
    SevereUnderRecovery,
    JointPain,
}

data class CheckInMuscleOptionUi(
    val muscleKey: String,
    val displayName: String,
)

data class CheckInMuscleSignalUi(
    val muscleKey: String,
    val displayName: String,
    val signal: RecoverySignalUi,
)

/** Sealed presentation recommendations — only Swap is tappable for re-pick. */
sealed interface CheckInRecommendationUi {
    val id: String
    val title: String
    val rationale: String
    val accepted: Boolean
    val liftName: String
    val currentLiftId: String
    val currentSetCount: Int

    fun withAccepted(accepted: Boolean): CheckInRecommendationUi

    data class Swap(
        override val id: String,
        override val title: String,
        override val rationale: String,
        override val accepted: Boolean,
        override val liftName: String,
        override val currentLiftId: String,
        override val currentSetCount: Int,
        val suggestedLiftId: String,
        val suggestedLiftName: String,
    ) : CheckInRecommendationUi {
        override fun withAccepted(accepted: Boolean): CheckInRecommendationUi = copy(accepted = accepted)
    }

    data class IncreaseVolume(
        override val id: String,
        override val title: String,
        override val rationale: String,
        override val accepted: Boolean,
        override val liftName: String,
        override val currentLiftId: String,
        override val currentSetCount: Int,
        val addSets: Int,
    ) : CheckInRecommendationUi {
        override fun withAccepted(accepted: Boolean): CheckInRecommendationUi = copy(accepted = accepted)
    }

    data class CutVolume(
        override val id: String,
        override val title: String,
        override val rationale: String,
        override val accepted: Boolean,
        override val liftName: String,
        override val currentLiftId: String,
        override val currentSetCount: Int,
        val removeSets: Int,
    ) : CheckInRecommendationUi {
        override fun withAccepted(accepted: Boolean): CheckInRecommendationUi = copy(accepted = accepted)

        val isFloorOnly: Boolean get() = removeSets == 0
    }
}

data class CheckInSwapCandidateUi(
    val liftId: String,
    val name: String,
    val selected: Boolean,
)

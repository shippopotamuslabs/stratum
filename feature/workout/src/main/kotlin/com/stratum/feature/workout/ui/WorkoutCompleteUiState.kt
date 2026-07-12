package com.stratum.feature.workout.ui

/**
 * Presentation state for the workout completion screen. Domain models never reach
 * composables (ARCHITECTURE.md §4.3).
 */
sealed interface WorkoutCompleteUiState {
    data object Loading : WorkoutCompleteUiState
    data object Error : WorkoutCompleteUiState

    data class Loaded(
        val workoutName: String,
        val durationFormatted: String,
        val bestSets: List<LiftBestSetUi>,
        /** Post-workout equipment increment proposals (max 3). Empty when none. */
        val equipmentProposals: List<EquipmentProposalCardUi> = emptyList(),
        /** Post-workout rep-range shift proposals (max 3). Empty when none. */
        val repRangeProposals: List<RepRangeShiftCardUi> = emptyList(),
        /** Blast-radius confirmation while accepting a shared-default amend. */
        val blastRadiusConfirm: BlastRadiusConfirmUi? = null,
        /** One-shot snackbar / status message after accept. */
        val userMessage: String? = null,
    ) : WorkoutCompleteUiState
}

/**
 * One row in the per-lift performance table. All numeric values arrive as
 * presentation strings — the composables never see raw doubles/ints.
 */
data class LiftBestSetUi(
    val liftName: String,
    val performanceLabel: String,
    val rirLabel: String,
    val estimatedOneRmLabel: String,
    val isPersonalRecord: Boolean,
)

/**
 * Presentation model for a single post-workout equipment-increment proposal card.
 * Mapped by [EquipmentProposalUiMapper] from domain proposals.
 */
data class EquipmentProposalCardUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val tierSummary: String,
    val liftNames: String,
    val primaryButtonLabel: String,
    /** True when Accept of primary requires blast-radius confirmation first. */
    val requiresConfirm: Boolean,
    val blastRadiusCount: Int,
    /**
     * Optional secondary action label (e.g. "Create custom instead" / "Update shared instead").
     * Null when no useful alternative.
     */
    val alternativeButtonLabel: String? = null,
    /** True while an apply write is in flight for this card (disables re-tap). */
    val isApplying: Boolean = false,
)

/**
 * Confirm dialog content for seed / shared-default amend.
 */
data class BlastRadiusConfirmUi(
    val proposalId: String,
    val title: String,
    val body: String,
    val confirmLabel: String,
)

/**
 * Presentation model for a single post-workout rep-range shift proposal card.
 * Mapped by [RepRangeShiftUiMapper] from domain proposals. Title/subtitle copy is
 * localized in the composable from these fields + string resources.
 */
data class RepRangeShiftCardUi(
    val id: String,
    val liftName: String,
    val currentBand: String,
    val proposedBand: String,
    val lastSetFloor: Int,
    val bandSummary: String,
    /** True while an apply write is in flight for this card (disables re-tap). */
    val isApplying: Boolean = false,
)

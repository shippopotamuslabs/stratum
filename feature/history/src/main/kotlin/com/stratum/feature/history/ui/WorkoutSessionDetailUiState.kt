package com.stratum.feature.history.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.share.ShareLiftRowUi
import com.stratum.core.ui.share.WORKOUT_SHARE_MAX_LIFTS
import com.stratum.core.ui.share.WorkoutShareContent
import com.stratum.core.ui.share.buildWorkoutShareContent
import java.time.LocalDate
import java.time.LocalTime

sealed interface WorkoutSessionDetailUiState {
    data object Loading : WorkoutSessionDetailUiState
    data object Error : WorkoutSessionDetailUiState

    data class Loaded(
        val workoutName: String,
        val startDate: LocalDate,
        val startTime: LocalTime,
        val finishDate: LocalDate,
        val finishTime: LocalTime,
        val completedAtLabel: String,
        val durationFormatted: String,
        val totalSetsLabel: String,
        val personalRecordsLabel: String,
        val personalRecordCount: Int = 0,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val lifts: List<HistoryLiftUi>,
    ) : WorkoutSessionDetailUiState
}

data class HistoryLiftUi(
    val liftName: String,
    val sets: List<HistorySetUi>,
    /** Top working or PR set for recap / share surfaces. Null if no sets. */
    val bestSet: HistorySetUi? = null,
)

data class HistorySetUi(
    val setIndex: Int,
    val performanceLabel: String,
    /** Logged RIR for the set; null when not recorded. */
    val rir: Int?,
    val isPersonalRecord: Boolean,
)

fun WorkoutSessionDetailUiState.Loaded.toShareContent(
    maxLifts: Int = WORKOUT_SHARE_MAX_LIFTS,
): WorkoutShareContent {
    val liftRows =
        lifts.mapNotNull { lift ->
            (lift.bestSet ?: lift.sets.firstOrNull())?.let { topSet ->
                ShareLiftRowUi(
                    liftName = lift.liftName,
                    performanceLabel = topSet.performanceLabel,
                    isPersonalRecord = lift.sets.any { it.isPersonalRecord },
                )
            }
        }
    return buildWorkoutShareContent(
        workoutName = workoutName,
        durationFormatted = durationFormatted,
        liftRows = liftRows,
        maxLifts = maxLifts,
        overridePersonalRecordCount = personalRecordCount,
    )
}

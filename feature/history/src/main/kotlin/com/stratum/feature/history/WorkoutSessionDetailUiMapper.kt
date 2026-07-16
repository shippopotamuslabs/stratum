package com.stratum.feature.history

import com.stratum.core.ui.component.formatSetPerformance
import com.stratum.core.ui.component.formatWorkoutDuration
import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.feature.history.ui.HistoryDaySessionSummaryUi
import com.stratum.feature.history.ui.HistoryLiftUi
import com.stratum.feature.history.ui.HistorySetUi
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Maps domain session detail → history presentation models. */
internal object WorkoutSessionDetailUiMapper {
    fun toDetailLoaded(detail: WorkoutSessionDetail): WorkoutSessionDetailUiState.Loaded =
        WorkoutSessionDetailUiState.Loaded(
            workoutName = detail.workoutName,
            completedAtLabel = formatCompletedAt(detail.completedAt),
            durationFormatted = formatWorkoutDuration(detail.durationMillis),
            totalSetsLabel = detail.totalSets.toString(),
            personalRecordsLabel = detail.personalRecordCount.toString(),
            upperVolumeByMuscle = detail.muscleVolumes.toUpperVolumeCards(),
            lowerVolumeByMuscle = detail.muscleVolumes.toLowerVolumeCards(),
            lifts =
                detail.lifts.map { lift ->
                    HistoryLiftUi(
                        liftName = lift.liftName,
                        sets =
                            lift.sets.map { set ->
                                HistorySetUi(
                                    setIndex = set.setIndex,
                                    performanceLabel =
                                        formatSetPerformance(
                                            set.weight.amount,
                                            set.reps,
                                        ),
                                    rir = set.rir,
                                    isPersonalRecord = set.isPersonalRecord,
                                )
                            },
                    )
                },
        )

    fun toDaySummary(detail: WorkoutSessionDetail): HistoryDaySessionSummaryUi =
        HistoryDaySessionSummaryUi(
            sessionId = detail.sessionId.value,
            workoutName = detail.workoutName,
            completedAtLabel = formatCompletedAt(detail.completedAt),
            durationFormatted = formatWorkoutDuration(detail.durationMillis),
            totalSetsLabel = detail.totalSets.toString(),
            personalRecordsLabel = detail.personalRecordCount.toString(),
        )

    fun formatDayTitle(dateIso: String): String {
        val date = LocalDate.parse(dateIso)
        return DAY_TITLE_FORMATTER.format(date)
    }

    fun formatCompletedAt(
        instant: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val zoned = instant.atZone(zoneId)
        return COMPLETED_AT_FORMATTER.format(zoned)
    }

    private val COMPLETED_AT_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US)

    private val DAY_TITLE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
}

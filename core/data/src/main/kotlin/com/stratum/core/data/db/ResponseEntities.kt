package com.stratum.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.ExperimentStatus
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

@Entity(tableName = "response_epochs")
data class ResponseEpochEntity(
    @PrimaryKey val id: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val anchorLiftIdsCsv: String,
    val splitReason: EpochSplitReason,
)

@Entity(tableName = "volume_response_observations")
data class VolumeResponseObservationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val muscle: MuscleGroup,
    val epochId: String,
    val anchorLiftId: String,
    val stimulusEpochId: String?,
    val stimulusExperimentId: String?,
    val stimulusMuscle: MuscleGroup?,
    val stimulusPattern: MovementPattern?,
    val stimulusLiftId: String?,
    val stimulusWeeklyHardSets: Double,
    val stimulusSessionsPerWeek: Double,
    val stimulusIntensityProxy: Double?,
    val outcomeAnchorLiftId: String,
    val outcomeMetric: OutcomeMetric,
    val outcomeValue: Double,
    val outcomeWindowWeeks: Int,
    val outcomeRSquared: Double?,
    val outcomeOutlierCount: Int,
    val outcomeCompletenessScore: Double,
    val correlationSlope: Double,
    val confidenceScore: Double,
    val evidence: EvidenceLevel,
    val weeklyPointsUsed: Int,
    val epochWeeksUsed: Int,
    val observedMinWeeklySets: Double,
    val observedMaxWeeklySets: Double,
    val bestObservedMinWeeklySets: Double?,
    val bestObservedMaxWeeklySets: Double?,
)

@Entity(tableName = "lift_effectiveness_observations")
data class LiftEffectivenessObservationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val pattern: MovementPattern,
    val liftId: String,
    val epochId: String,
    val effectivenessScore: Double,
    val peerLiftCount: Int,
    val rank: Int,
    val evidence: EvidenceLevel,
    val confidenceScore: Double,
)

@Entity(tableName = "experiment_blocks")
data class ExperimentBlockEntity(
    @PrimaryKey val id: String,
    val prescribedEpochId: String?,
    val prescribedExperimentId: String?,
    val prescribedMuscle: MuscleGroup?,
    val prescribedPattern: MovementPattern?,
    val prescribedLiftId: String?,
    val prescribedWeeklyHardSets: Double,
    val prescribedSessionsPerWeek: Double,
    val prescribedIntensityProxy: Double?,
    val outcomeWindowStartEpochMillis: Long,
    val outcomeWindowEndEpochMillis: Long,
    val status: ExperimentStatus,
)

@Entity(tableName = "response_profile_metadata")
data class ResponseProfileMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val epochsAnalyzed: Int,
    val analysisWindowStartEpochMillis: Long,
    val analysisWindowEndEpochMillis: Long,
    val lastExtractedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

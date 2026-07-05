package com.stratum.core.data.db

import androidx.room.TypeConverter
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.ImportJobId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.ExperimentStatus
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import com.stratum.domain.response.modeling.OutcomeModelType
import com.stratum.domain.response.modeling.VolumePosture

/**
 * Single source of truth for enum ↔ String and typed-id ↔ String persistence across the
 * database. Registered on [StratumDatabase] via `@TypeConverters` alongside
 * [WeekdaySetConverter].
 *
 * Enums are stored as their `.name`. Decoding uses `runCatching` so a corrupted, renamed, or
 * downgraded value resolves to `null` rather than throwing — deterministic on-disk reads
 * (never crashes on schema drift or manual edits). Nullable decoders pair with nullable
 * entity columns; non-nullable enums ([OnboardingStep], [MuscleGroup], [SpecializationLevel])
 * are used only on NOT NULL columns where the Room layer guarantees a value.
 *
 * OS-4 adds typed-id value classes ([LiftId]) here too: the converter unwraps `.value` on the
 * way to SQL and re-wraps on the way back, so entities and DAOs stay typed. This keeps one
 * registered converter class for the whole DB (the pocket-crew pattern).
 *
 * This replaces the hand-rolled `.name` / `valueOf` mapping previously scattered across
 * mappers (OS-1 / OS-2), standardizing every enum round-trip in one place.
 */
@Suppress("TooManyFunctions")
class EnumConverters {
    @TypeConverter
    fun fromMuscleGroup(muscle: MuscleGroup?): String? = muscle?.name

    @TypeConverter
    fun toMuscleGroup(value: String?): MuscleGroup? =
        value?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromSpecializationLevel(level: SpecializationLevel?): String? = level?.name

    @TypeConverter
    fun toSpecializationLevel(value: String?): SpecializationLevel? =
        value?.let { runCatching { SpecializationLevel.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromOnboardingStep(step: OnboardingStep?): String? = step?.name

    @TypeConverter
    fun toOnboardingStep(value: String?): OnboardingStep? =
        value?.let { runCatching { OnboardingStep.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromTrainingAge(age: TrainingAge?): String? = age?.name

    @TypeConverter
    fun toTrainingAge(value: String?): TrainingAge? =
        value?.let { runCatching { TrainingAge.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromLastTrainedBucket(bucket: LastTrainedBucket?): String? = bucket?.name

    @TypeConverter
    fun toLastTrainedBucket(value: String?): LastTrainedBucket? =
        value?.let { runCatching { LastTrainedBucket.valueOf(it) }.getOrNull() }

    // --- OS-4: lift catalog enums + typed id -------------------------------------------

    @TypeConverter
    fun fromMovementPattern(pattern: MovementPattern?): String? = pattern?.name

    @TypeConverter
    fun toMovementPattern(value: String?): MovementPattern? =
        value?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromGymPreset(preset: GymPreset?): String? = preset?.name

    @TypeConverter
    fun toGymPreset(value: String?): GymPreset? =
        value?.let { runCatching { GymPreset.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromMuscleLengthBias(bias: MuscleLengthBias?): String? = bias?.name

    @TypeConverter
    fun toMuscleLengthBias(value: String?): MuscleLengthBias? =
        value?.let { runCatching { MuscleLengthBias.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromKinematicProfile(profile: KinematicProfile?): String? = profile?.name

    @TypeConverter
    fun toKinematicProfile(value: String?): KinematicProfile? =
        value?.let { runCatching { KinematicProfile.valueOf(it) }.getOrNull() }

    /**
     * Unwraps a typed [LiftId] to its String `.value` for SQL, and re-wraps on the way back
     * (the pocket-crew identity pattern: entities/DAOs stay typed, the converter is the only
     * String boundary). Non-null on both sides — [LiftId] rejects blank at construction, so a
     * stored null would indicate corruption; a `runCatching` decode keeps that from crashing.
     */
    @TypeConverter
    fun fromLiftId(id: LiftId?): String? = id?.value

    @TypeConverter
    fun toLiftId(value: String?): LiftId? =
        value?.let { runCatching { LiftId(it) }.getOrNull() }

    @TypeConverter
    fun fromImportJobStatus(status: ImportJobStatus?): String? = status?.name

    @TypeConverter
    fun toImportJobStatus(value: String?): ImportJobStatus? =
        value?.let { runCatching { ImportJobStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromImportJobId(id: ImportJobId?): String? = id?.value

    @TypeConverter
    fun toImportJobId(value: String?): ImportJobId? =
        value?.let { runCatching { ImportJobId(it) }.getOrNull() }

    @TypeConverter
    fun fromHistoricalSessionId(id: HistoricalSessionId?): String? = id?.value

    @TypeConverter
    fun toHistoricalSessionId(value: String?): HistoricalSessionId? =
        value?.let { runCatching { HistoricalSessionId(it) }.getOrNull() }

    @TypeConverter
    fun fromLoggedSetId(id: LoggedSetId?): String? = id?.value

    @TypeConverter
    fun toLoggedSetId(value: String?): LoggedSetId? =
        value?.let { runCatching { LoggedSetId(it) }.getOrNull() }

    @TypeConverter
    fun fromEpochId(id: EpochId?): String? = id?.value

    @TypeConverter
    fun toEpochId(value: String?): EpochId? =
        value?.let { runCatching { EpochId(it) }.getOrNull() }

    @TypeConverter
    fun fromExperimentId(id: ExperimentId?): String? = id?.value

    @TypeConverter
    fun toExperimentId(value: String?): ExperimentId? =
        value?.let { runCatching { ExperimentId(it) }.getOrNull() }

    @TypeConverter
    fun fromWeekday(weekday: Weekday?): String? = weekday?.name

    @TypeConverter
    fun toWeekday(value: String?): Weekday? =
        value?.let { runCatching { Weekday.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromEpochSplitReason(reason: EpochSplitReason?): String? = reason?.name

    @TypeConverter
    fun toEpochSplitReason(value: String?): EpochSplitReason? =
        value?.let { runCatching { EpochSplitReason.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromEvidenceLevel(level: EvidenceLevel?): String? = level?.name

    @TypeConverter
    fun toEvidenceLevel(value: String?): EvidenceLevel? =
        value?.let { runCatching { EvidenceLevel.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromOutcomeMetric(metric: OutcomeMetric?): String? = metric?.name

    @TypeConverter
    fun toOutcomeMetric(value: String?): OutcomeMetric? =
        value?.let { runCatching { OutcomeMetric.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromExperimentStatus(status: ExperimentStatus?): String? = status?.name

    @TypeConverter
    fun toExperimentStatus(value: String?): ExperimentStatus? =
        value?.let { runCatching { ExperimentStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromLandmarkConfidenceTier(tier: LandmarkConfidenceTier?): String? = tier?.name

    @TypeConverter
    fun toLandmarkConfidenceTier(value: String?): LandmarkConfidenceTier? =
        value?.let { runCatching { LandmarkConfidenceTier.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromIntervalMethod(method: IntervalMethod?): String? = method?.name

    @TypeConverter
    fun toIntervalMethod(value: String?): IntervalMethod? =
        value?.let { runCatching { IntervalMethod.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromOutcomeModelType(type: OutcomeModelType?): String? = type?.name

    @TypeConverter
    fun toOutcomeModelType(value: String?): OutcomeModelType? =
        value?.let { runCatching { OutcomeModelType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromVolumePosture(posture: VolumePosture?): String? = posture?.name

    @TypeConverter
    fun toVolumePosture(value: String?): VolumePosture? =
        value?.let { runCatching { VolumePosture.valueOf(it) }.getOrNull() }

    // --- OS-7: program hierarchy enums + typed ids -------------------------------------

    @TypeConverter
    fun fromProgressionScheme(scheme: ProgressionScheme?): String? = scheme?.name

    @TypeConverter
    fun toProgressionScheme(value: String?): ProgressionScheme? =
        value?.let { runCatching { ProgressionScheme.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromSetType(type: SetType?): String? = type?.name

    @TypeConverter
    fun toSetType(value: String?): SetType? =
        value?.let { runCatching { SetType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromProgramId(id: ProgramId?): String? = id?.value

    @TypeConverter
    fun toProgramId(value: String?): ProgramId? =
        value?.let { runCatching { ProgramId(it) }.getOrNull() }

    @TypeConverter
    fun fromWorkoutId(id: WorkoutId?): String? = id?.value

    @TypeConverter
    fun toWorkoutId(value: String?): WorkoutId? =
        value?.let { runCatching { WorkoutId(it) }.getOrNull() }

    @TypeConverter
    fun fromWorkoutLiftId(id: WorkoutLiftId?): String? = id?.value

    @TypeConverter
    fun toWorkoutLiftId(value: String?): WorkoutLiftId? =
        value?.let { runCatching { WorkoutLiftId(it) }.getOrNull() }

    @TypeConverter
    fun fromLiftSetId(id: LiftSetId?): String? = id?.value

    @TypeConverter
    fun toLiftSetId(value: String?): LiftSetId? =
        value?.let { runCatching { LiftSetId(it) }.getOrNull() }

    @TypeConverter
    fun fromWorkoutSupersetGroupId(id: WorkoutSupersetGroupId?): String? = id?.value

    @TypeConverter
    fun toWorkoutSupersetGroupId(value: String?): WorkoutSupersetGroupId? =
        value?.let { runCatching { WorkoutSupersetGroupId(it) }.getOrNull() }

    // --- Workout logging: training session enums + typed ids ---------------------------

    @TypeConverter
    fun fromTrainingSessionId(id: com.stratum.domain.training.identity.TrainingSessionId?): String? = id?.value

    @TypeConverter
    fun toTrainingSessionId(value: String?): com.stratum.domain.training.identity.TrainingSessionId? =
        value?.let { runCatching { com.stratum.domain.training.identity.TrainingSessionId(it) }.getOrNull() }

    @TypeConverter
    fun fromProgramChangeEventId(id: com.stratum.domain.training.identity.ProgramChangeEventId?): String? =
        id?.value

    @TypeConverter
    fun toProgramChangeEventId(value: String?): com.stratum.domain.training.identity.ProgramChangeEventId? =
        value?.let { runCatching { com.stratum.domain.training.identity.ProgramChangeEventId(it) }.getOrNull() }

    @TypeConverter
    fun fromTrainingSessionStatus(status: com.stratum.domain.training.TrainingSessionStatus?): String? = status?.name

    @TypeConverter
    fun toTrainingSessionStatus(value: String?): com.stratum.domain.training.TrainingSessionStatus? =
        value?.let { runCatching { com.stratum.domain.training.TrainingSessionStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromTrainingSessionKind(kind: com.stratum.domain.training.TrainingSessionKind?): String? = kind?.name

    @TypeConverter
    fun toTrainingSessionKind(value: String?): com.stratum.domain.training.TrainingSessionKind? =
        value?.let { runCatching { com.stratum.domain.training.TrainingSessionKind.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromTrainingSessionOrigin(origin: com.stratum.domain.training.TrainingSessionOrigin?): String? = origin?.name

    @TypeConverter
    fun toTrainingSessionOrigin(value: String?): com.stratum.domain.training.TrainingSessionOrigin? =
        value?.let { runCatching { com.stratum.domain.training.TrainingSessionOrigin.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromProgramChangeEventType(type: com.stratum.domain.training.ProgramChangeEventType?): String? = type?.name

    @TypeConverter
    fun toProgramChangeEventType(value: String?): com.stratum.domain.training.ProgramChangeEventType? =
        value?.let { runCatching { com.stratum.domain.training.ProgramChangeEventType.valueOf(it) }.getOrNull() }
}

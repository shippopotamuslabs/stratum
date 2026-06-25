package com.stratum.core.data.db

import androidx.room.TypeConverter
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday

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

    @TypeConverter
    fun fromWeekday(weekday: Weekday?): String? = weekday?.name

    @TypeConverter
    fun toWeekday(value: String?): Weekday? =
        value?.let { runCatching { Weekday.valueOf(it) }.getOrNull() }

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
}

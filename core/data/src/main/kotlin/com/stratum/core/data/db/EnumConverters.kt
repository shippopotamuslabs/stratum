package com.stratum.core.data.db

import androidx.room.TypeConverter
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday

/**
 * Single source of truth for enum ↔ String persistence across the database. Registered on
 * [StratumDatabase] via `@TypeConverters` alongside [WeekdaySetConverter].
 *
 * Enums are stored as their `.name`. Decoding uses `runCatching` so a corrupted, renamed, or
 * downgraded value resolves to `null` rather than throwing — deterministic on-disk reads
 * (never crashes on schema drift or manual edits). Nullable decoders pair with nullable
 * entity columns; non-nullable enums ([OnboardingStep], [MuscleGroup], [SpecializationLevel])
 * are used only on NOT NULL columns where the Room layer guarantees a value.
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
}

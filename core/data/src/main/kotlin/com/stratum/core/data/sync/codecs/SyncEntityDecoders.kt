package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftSetEntity
import com.stratum.core.data.db.LoggedSetEntity
import com.stratum.core.data.db.OnboardingProgressEntity
import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.SpecializationPreferenceEntity
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.core.data.db.UserProfileEntity
import com.stratum.core.data.db.WorkoutEntity
import com.stratum.core.data.db.WorkoutLiftEntity
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toPresetCsv
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.onboarding.identity.GymPreferenceId
import com.stratum.domain.onboarding.identity.OnboardingProgressId
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId
import com.stratum.domain.onboarding.identity.UserProfileId
import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEntityDecoders @Inject constructor() {
    fun remoteUpdatedAt(data: Map<String, Any?>): Long =
        (data[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong() ?: 0L

    fun remoteDeviceId(data: Map<String, Any?>): String? =
        data[FirestoreSyncMetadata.DEVICE_ID] as? String

    fun userProfile(id: String, data: Map<String, Any?>): UserProfileEntity? =
        runCatching {
            UserProfileEntity(
                id = UserProfileId(id),
                trainingAge = enumOrNull<TrainingAge>(data["training_age"]),
                lastTrained = enumOrNull<LastTrainedBucket>(data["last_trained"]),
                weekdays =
                    stringList(data["weekdays"])
                        .mapNotNull { enumOrNull<Weekday>(it) }
                        .toSet(),
                sessionLengthMinutes = (data["session_length_minutes"] as? Number)?.toInt(),
                generationTrainingDayCount =
                    (data["generation_training_day_count"] as? Number)?.toInt(),
                generationCycleLength = (data["generation_cycle_length"] as? Number)?.toInt(),
                generationRestSlotIndices = intSet(data["generation_rest_slot_indices"]),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun onboarding(id: String, data: Map<String, Any?>): OnboardingProgressEntity? =
        runCatching {
            OnboardingProgressEntity(
                id = OnboardingProgressId(id),
                currentStep = enumOrNull<OnboardingStep>(data["current_step"]) ?: OnboardingStep.Profile,
                startedAt = (data["started_at"] as? Number)?.toLong() ?: 0L,
                completedAt = (data["completed_at"] as? Number)?.toLong(),
                selectedPath = enumOrNull(data["selected_path"]),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun gymPreference(id: String, data: Map<String, Any?>): GymPreferenceEntity? =
        runCatching {
            GymPreferenceEntity(
                id = GymPreferenceId(id),
                selectedPreset = enumOrNull<GymPreset>(data["selected_preset"]),
                usedHistoricalImport = data["used_historical_import"] as? Boolean ?: false,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun specialization(id: String, data: Map<String, Any?>): SpecializationPreferenceEntity? =
        runCatching {
            val muscle = enumOrNull<MuscleGroup>(data["muscle"]) ?: return null
            SpecializationPreferenceEntity(
                id = SpecializationPreferenceId(id),
                muscle = muscle,
                level = enumOrNull<SpecializationLevel>(data["level"]) ?: SpecializationLevel.Maintenance,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun equipment(id: String, data: Map<String, Any?>): EquipmentEntity? =
        runCatching {
            EquipmentEntity(
                id = EquipmentId(id),
                name = data["name"] as? String ?: return null,
                isCustom = data["is_custom"] as? Boolean ?: false,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun lift(id: String, data: Map<String, Any?>): LiftEntity? =
        runCatching {
            val patterns =
                stringList(data["movement_patterns"])
                    .mapNotNull { enumOrNull<MovementPattern>(it) }
                    .toSet()
            val primary =
                stringList(data["primary_muscles"])
                    .mapNotNull { enumOrNull<MuscleGroup>(it) }
                    .toSet()
            val secondary =
                stringList(data["secondary_muscles"])
                    .mapNotNull { enumOrNull<MuscleGroup>(it) }
                    .toSet()
            val presets =
                stringList(data["preset_tags"])
                    .mapNotNull { enumOrNull<GymPreset>(it) }
                    .toSet()
            val equipmentId =
                (data["equipment_id"] as? String)?.let { runCatching { EquipmentId(it) }.getOrNull() }
            LiftEntity(
                id = LiftId(id),
                name = data["name"] as? String ?: return null,
                movementPatternsBitmask = patterns.toBitmask(),
                primaryMusclesBitmask = primary.toBitmask(),
                secondaryMusclesBitmask = secondary.toBitmask(),
                presetTags = presets.toPresetCsv(),
                equipmentId = equipmentId,
                isCustom = data["is_custom"] as? Boolean ?: false,
                enabled = data["enabled"] as? Boolean ?: false,
                description = data["description"] as? String,
                imageUrisCsv = stringList(data["image_uris"]).joinToString(",").ifBlank { null },
                muscleLengthBias = enumOrNull(data["muscle_length_bias"]) ?: MuscleLengthBias.MidRange,
                mainstream = data["mainstream"] as? Boolean ?: false,
                kinematicProfile = enumOrNull(data["kinematic_profile"]) ?: KinematicProfile.Isolation,
                loadType = enumOrNull(data["load_type"]) ?: LoadType.Weighted,
                defaultRestSeconds = (data["default_rest_seconds"] as? Number)?.toInt(),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun incrementTier(id: String, data: Map<String, Any?>): IncrementTierEntity? =
        runCatching {
            val liftIdRaw = data["lift_id"] as? String
            val equipmentIdRaw = data["equipment_id"] as? String
            // XOR: exactly one of lift_id / equipment_id must be set.
            if (liftIdRaw == null && equipmentIdRaw == null) return null
            if (liftIdRaw != null && equipmentIdRaw != null) return null
            IncrementTierEntity(
                id = IncrementTierId(id),
                liftId = liftIdRaw?.let { LiftId(it) },
                equipmentId = equipmentIdRaw?.let { EquipmentId(it) },
                lowerBound = (data["lower_bound"] as? Number)?.toDouble() ?: return null,
                upperBound = (data["upper_bound"] as? Number)?.toDouble(),
                increment = (data["increment"] as? Number)?.toDouble() ?: return null,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun program(id: String, data: Map<String, Any?>): ProgramEntity? =
        runCatching {
            ProgramEntity(
                id = ProgramId(id),
                name = data["name"] as? String ?: return null,
                createdAt = data["created_at"] as? String ?: return null,
                cycleLength = (data["cycle_length"] as? Number)?.toInt() ?: 1,
                trainingWeekdays =
                    stringList(data["training_weekdays"])
                        .mapNotNull { enumOrNull<Weekday>(it) }
                        .toSet(),
                currentCycleNumber = (data["current_cycle_number"] as? Number)?.toInt() ?: 1,
                cyclePosition = (data["cycle_position"] as? Number)?.toInt() ?: 0,
                isActive = data["is_active"] as? Boolean ?: false,
                restSlotIndices = intSet(data["rest_slot_indices"]),
                cycleStartEpochDay = (data["cycle_start_epoch_day"] as? Number)?.toLong(),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun workout(id: String, data: Map<String, Any?>): WorkoutEntity? =
        runCatching {
            WorkoutEntity(
                id = WorkoutId(id),
                programId = ProgramId(data["program_id"] as? String ?: return null),
                name = data["name"] as? String ?: return null,
                weekday = enumOrNull(data["weekday"]),
                position = (data["position"] as? Number)?.toInt() ?: 0,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun workoutLift(id: String, data: Map<String, Any?>): WorkoutLiftEntity? =
        runCatching {
            WorkoutLiftEntity(
                id = WorkoutLiftId(id),
                workoutId = WorkoutId(data["workout_id"] as? String ?: return null),
                liftId = LiftId(data["lift_id"] as? String ?: return null),
                progressionScheme =
                    enumOrNull(data["progression_scheme"]) ?: ProgressionScheme.Double,
                position = (data["position"] as? Number)?.toInt() ?: 0,
                supersetGroupId = (data["superset_group_id"] as? String)?.let(::WorkoutSupersetGroupId),
                useRestTimerBetweenLifts = data["use_rest_timer_between_lifts"] as? Boolean ?: false,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun liftSet(id: String, data: Map<String, Any?>): LiftSetEntity? =
        runCatching {
            LiftSetEntity(
                id = LiftSetId(id),
                workoutLiftId = WorkoutLiftId(data["workout_lift_id"] as? String ?: return null),
                position = (data["position"] as? Number)?.toInt() ?: 0,
                setType = enumOrNull(data["set_type"]) ?: SetType.Standard,
                repMin = (data["rep_min"] as? Number)?.toInt() ?: 0,
                repMax = (data["rep_max"] as? Number)?.toInt() ?: 0,
                rirGoal = (data["rir_goal"] as? Number)?.toInt() ?: 0,
                isTopSet = data["is_top_set"] as? Boolean ?: false,
                restSeconds = (data["rest_seconds"] as? Number)?.toInt() ?: DEFAULT_LIFT_SET_REST_SECONDS,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun trainingSession(id: String, data: Map<String, Any?>): TrainingSessionEntity? =
        runCatching {
            TrainingSessionEntity(
                id = TrainingSessionId(id),
                programId = (data["program_id"] as? String)?.let(::ProgramId),
                workoutId = (data["workout_id"] as? String)?.let(::WorkoutId),
                status = enumOrNull(data["status"]) ?: TrainingSessionStatus.COMPLETED,
                kind = enumOrNull(data["kind"]) ?: TrainingSessionKind.PROGRAM_WORKOUT,
                origin = enumOrNull(data["origin"]) ?: TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = (data["cycle_number_snapshot"] as? Number)?.toInt() ?: 0,
                weekdaySnapshot = enumOrNull(data["weekday_snapshot"]),
                cycleSlotIndexSnapshot = (data["cycle_slot_index_snapshot"] as? Number)?.toInt(),
                startedAtEpochMillis = (data["started_at_epoch_millis"] as? Number)?.toLong() ?: 0L,
                endedAtEpochMillis = (data["ended_at_epoch_millis"] as? Number)?.toLong(),
                sourceApp = data["source_app"] as? String,
                programNameSnapshot = data["program_name_snapshot"] as? String,
                workoutNameSnapshot = data["workout_name_snapshot"] as? String,
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun loggedSet(id: String, data: Map<String, Any?>): LoggedSetEntity? =
        runCatching {
            LoggedSetEntity(
                id = LoggedSetId(id),
                sessionId = TrainingSessionId(data["session_id"] as? String ?: return null),
                exerciseNameRaw = data["exercise_name_raw"] as? String ?: "",
                liftId = (data["lift_id"] as? String)?.let(::LiftId),
                setIndex = (data["set_index"] as? Number)?.toInt() ?: 0,
                weightAmount = (data["weight_amount"] as? Number)?.toDouble() ?: 0.0,
                weightUnit = data["weight_unit"] as? String ?: "Lb",
                reps = (data["reps"] as? Number)?.toInt() ?: 0,
                rir = (data["rir"] as? Number)?.toInt(),
                completedAtEpochMillis = (data["completed_at_epoch_millis"] as? Number)?.toLong() ?: 0L,
                liftName = data["lift_name"] as? String ?: "",
                movementPatternsBitmask = (data["movement_patterns_bitmask"] as? Number)?.toInt() ?: 0,
                primaryMusclesBitmask = (data["primary_muscles_bitmask"] as? Number)?.toInt() ?: 0,
                secondaryMusclesBitmask = (data["secondary_muscles_bitmask"] as? Number)?.toInt() ?: 0,
                kinematicProfile = enumOrNull(data["kinematic_profile"]) ?: KinematicProfile.NonAxialCompound,
                muscleLengthBias = enumOrNull(data["muscle_length_bias"]) ?: MuscleLengthBias.MidRange,
                workoutLiftId = (data["workout_lift_id"] as? String)?.let(::WorkoutLiftId),
                progressionScheme = enumOrNull(data["progression_scheme"]),
                setType = enumOrNull(data["set_type"]),
                repMin = (data["rep_min"] as? Number)?.toInt(),
                repMax = (data["rep_max"] as? Number)?.toInt(),
                rirGoal = (data["rir_goal"] as? Number)?.toInt(),
                isPersonalRecord = data["is_personal_record"] as? Boolean,
                estimatedOneRm = (data["estimated_one_rm"] as? Number)?.toDouble(),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    fun programChangeEvent(id: String, data: Map<String, Any?>): ProgramChangeEventEntity? =
        runCatching {
            ProgramChangeEventEntity(
                id = ProgramChangeEventId(id),
                programId = ProgramId(data["program_id"] as? String ?: return null),
                eventType = enumOrNull(data["event_type"]) ?: return null,
                occurredAtEpochMillis = (data["occurred_at_epoch_millis"] as? Number)?.toLong() ?: 0L,
                workoutLiftId = (data["workout_lift_id"] as? String)?.let(::WorkoutLiftId),
                previousLiftId = (data["previous_lift_id"] as? String)?.let(::LiftId),
                newLiftId = (data["new_lift_id"] as? String)?.let(::LiftId),
                workoutId = (data["workout_id"] as? String)?.let(::WorkoutId),
                completedCycleNumber = (data["completed_cycle_number"] as? Number)?.toInt(),
                updatedAtEpochMillis = remoteUpdatedAt(data),
                pendingSync = SyncFlags.CLEAN,
            )
        }.getOrNull()

    private inline fun <reified T : Enum<T>> enumOrNull(raw: Any?): T? {
        val name = raw as? String ?: return null
        return runCatching { enumValueOf<T>(name) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun stringList(raw: Any?): List<String> =
        when (raw) {
            is List<*> -> raw.mapNotNull { it as? String }
            is String -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

    private fun intSet(raw: Any?): Set<Int> =
        when (raw) {
            is List<*> ->
                raw.mapNotNull { item ->
                    when (item) {
                        is Number -> item.toInt()
                        is String -> item.trim().toIntOrNull()
                        else -> null
                    }
                }.toSet()
            is String ->
                raw.split(",")
                    .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
                    .toSet()
            else -> emptySet()
        }
}

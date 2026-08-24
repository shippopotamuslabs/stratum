package com.stratum.core.data.sync.codecs

import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftSessionNoteEntity
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
import com.stratum.core.data.mapper.toMuscleSet
import com.stratum.core.data.mapper.toMovementPatternSet
import com.stratum.core.data.mapper.toPresetSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room entity → Firestore primitive maps (no domain objects, no Firebase types in signatures
 * beyond Map). Decode lives in [SyncEntityDecoders].
 */
@Singleton
class SyncEntityEncoders @Inject constructor() {
    fun userProfile(e: UserProfileEntity): Map<String, Any?> =
        mapOf(
            "training_age" to e.trainingAge?.name,
            "last_trained" to e.lastTrained?.name,
            "weekdays" to e.weekdays.map { it.name },
            "session_length_minutes" to e.sessionLengthMinutes,
            "generation_training_day_count" to e.generationTrainingDayCount,
            "generation_cycle_length" to e.generationCycleLength,
            "generation_rest_slot_indices" to e.generationRestSlotIndices.sorted(),
        )

    fun onboarding(e: OnboardingProgressEntity): Map<String, Any?> =
        mapOf(
            "current_step" to e.currentStep.name,
            "started_at" to e.startedAt,
            "completed_at" to e.completedAt,
            "selected_path" to e.selectedPath?.name,
        )

    fun gymPreference(e: GymPreferenceEntity): Map<String, Any?> =
        mapOf(
            "selected_preset" to e.selectedPreset?.name,
            "used_historical_import" to e.usedHistoricalImport,
        )

    fun specialization(e: SpecializationPreferenceEntity): Map<String, Any?> =
        mapOf(
            "muscle" to e.muscle.name,
            "level" to e.level.name,
        )

    fun equipment(e: EquipmentEntity): Map<String, Any?> =
        mapOf(
            "name" to e.name,
            "is_custom" to e.isCustom,
        )

    fun lift(e: LiftEntity): Map<String, Any?> =
        mapOf(
            "name" to e.name,
            "movement_patterns" to e.movementPatternsBitmask.toMovementPatternSet().map { it.name },
            "primary_muscles" to e.primaryMusclesBitmask.toMuscleSet().map { it.name },
            "secondary_muscles" to e.secondaryMusclesBitmask.toMuscleSet().map { it.name },
            "preset_tags" to e.presetTags.toPresetSet().map { it.name },
            "equipment_id" to e.equipmentId?.value,
            "is_custom" to e.isCustom,
            "enabled" to e.enabled,
            "description" to e.description,
            "image_uris" to (e.imageUrisCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList()),
            "muscle_length_bias" to e.muscleLengthBias.name,
            "mainstream" to e.mainstream,
            "fatigue_profile" to e.fatigueProfile.name,
            "load_type" to e.loadType.name,
            "default_rest_seconds" to e.defaultRestSeconds,
            "pinned_note" to e.pinnedNote.orEmpty(),
        )

    fun incrementTier(e: IncrementTierEntity): Map<String, Any?> =
        mapOf(
            "lift_id" to e.liftId?.value,
            "equipment_id" to e.equipmentId?.value,
            "lower_bound" to e.lowerBound,
            "upper_bound" to e.upperBound,
            "increment" to e.increment,
        )

    fun program(e: ProgramEntity): Map<String, Any?> =
        mapOf(
            "name" to e.name,
            "created_at" to e.createdAt,
            "cycle_length" to e.cycleLength,
            "training_weekdays" to e.trainingWeekdays.map { it.name },
            "current_cycle_number" to e.currentCycleNumber,
            "cycle_position" to e.cyclePosition,
            "is_active" to e.isActive,
            "rest_slot_indices" to e.restSlotIndices.sorted(),
            "cycle_start_epoch_day" to e.cycleStartEpochDay,
        )

    fun workout(e: WorkoutEntity): Map<String, Any?> =
        mapOf(
            "program_id" to e.programId.value,
            "name" to e.name,
            "weekday" to e.weekday?.name,
            "position" to e.position,
        )

    fun workoutLift(e: WorkoutLiftEntity): Map<String, Any?> =
        mapOf(
            "workout_id" to e.workoutId.value,
            "lift_id" to e.liftId.value,
            "progression_scheme" to e.progressionScheme.name,
            "position" to e.position,
            "superset_group_id" to e.supersetGroupId?.value,
            "use_rest_timer_between_lifts" to e.useRestTimerBetweenLifts,
            "pinned_note" to e.pinnedNote.orEmpty(),
        )

    fun liftSet(e: LiftSetEntity): Map<String, Any?> =
        mapOf(
            "workout_lift_id" to e.workoutLiftId.value,
            "position" to e.position,
            "set_type" to e.setType.name,
            "rep_min" to e.repMin,
            "rep_max" to e.repMax,
            "rir_goal" to e.rirGoal,
            "is_top_set" to e.isTopSet,
            "rest_seconds" to e.restSeconds,
            "myo_rep_kind" to e.myoRepKind?.name,
        )

    fun trainingSession(e: TrainingSessionEntity): Map<String, Any?> =
        mapOf(
            "program_id" to e.programId?.value,
            "workout_id" to e.workoutId?.value,
            "status" to e.status.name,
            "kind" to e.kind.name,
            "origin" to e.origin.name,
            "cycle_number_snapshot" to e.cycleNumberSnapshot,
            "weekday_snapshot" to e.weekdaySnapshot?.name,
            "cycle_slot_index_snapshot" to e.cycleSlotIndexSnapshot,
            "started_at_epoch_millis" to e.startedAtEpochMillis,
            "ended_at_epoch_millis" to e.endedAtEpochMillis,
            "source_app" to e.sourceApp,
            "program_name_snapshot" to e.programNameSnapshot,
            "workout_name_snapshot" to e.workoutNameSnapshot,
        )

    fun loggedSet(e: LoggedSetEntity): Map<String, Any?> =
        mapOf(
            "session_id" to e.sessionId.value,
            "exercise_name_raw" to e.exerciseNameRaw,
            "lift_id" to e.liftId?.value,
            "set_index" to e.setIndex,
            "weight_amount" to e.weightAmount,
            "weight_unit" to e.weightUnit,
            "reps" to e.reps,
            "rir" to e.rir,
            "partial_reps" to e.partialReps,
            "completed_at_epoch_millis" to e.completedAtEpochMillis,
            "lift_name" to e.liftName,
            "movement_patterns_bitmask" to e.movementPatternsBitmask,
            "primary_muscles_bitmask" to e.primaryMusclesBitmask,
            "secondary_muscles_bitmask" to e.secondaryMusclesBitmask,
            "fatigue_profile" to e.fatigueProfile.name,
            "muscle_length_bias" to e.muscleLengthBias.name,
            "workout_lift_id" to e.workoutLiftId?.value,
            "progression_scheme" to e.progressionScheme?.name,
            "set_type" to e.setType?.name,
            "rep_min" to e.repMin,
            "rep_max" to e.repMax,
            "rir_goal" to e.rirGoal,
            "is_personal_record" to e.isPersonalRecord,
            "estimated_one_rm" to e.estimatedOneRm,
        )

    fun programChangeEvent(e: ProgramChangeEventEntity): Map<String, Any?> =
        mapOf(
            "program_id" to e.programId.value,
            "event_type" to e.eventType.name,
            "occurred_at_epoch_millis" to e.occurredAtEpochMillis,
            "workout_lift_id" to e.workoutLiftId?.value,
            "previous_lift_id" to e.previousLiftId?.value,
            "new_lift_id" to e.newLiftId?.value,
            "workout_id" to e.workoutId?.value,
            "completed_cycle_number" to e.completedCycleNumber,
        )

    fun liftSessionNote(e: LiftSessionNoteEntity): Map<String, Any?> =
        mapOf(
            "session_id" to e.sessionId.value,
            "lift_id" to e.liftId.value,
            "workout_lift_id" to e.workoutLiftId.value,
            "body" to e.body,
        )
}

package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Program hierarchy persistence (OS-7). Four normalized tables for the
 * `Program → Workout → WorkoutLift → LiftSet` template hierarchy, with value-class PKs and FK
 * cascade deletes so re-saving a program clears children for that program id only.
 *
 * Multi-program library (`is_active`) authorized for create-program (AGENTS.md §4.5 Schema Lock) —
 * **v1** entity set, no version bump, no migration (reinstall to apply; pre-release policy).
 *
 * Enums ([ProgressionScheme], [SetType]) and typed ids are stored via `EnumConverters` (the single
 * registered converter class). `weekday` is nullable on [WorkoutEntity] ("weekday-bound where
 * possible"). `LiftSetEntity` carries no weight/load column (straight sets — single load per lift).
 */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: ProgramId,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    /** Full cycle slot count (train + rest). Schema v1 reinstall — AGENTS.md §4.5. */
    @ColumnInfo(name = "cycle_length") val cycleLength: Int,
    @ColumnInfo(name = "training_weekdays") val trainingWeekdays: Set<Weekday> = emptySet(),
    @ColumnInfo(name = "current_cycle_number") val currentCycleNumber: Int = 1,
    @ColumnInfo(name = "cycle_position") val cyclePosition: Int = 0,
    /** At most one user program is active; import-history and library rows stay false. */
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    /** Rest slot indices in [0, cycle_length); CSV via [IntSetConverter]. */
    @ColumnInfo(name = "rest_slot_indices") val restSlotIndices: Set<Int> = emptySet(),
    /** Epoch day of slot 0 for the current cycle; null until set on activate. */
    @ColumnInfo(name = "cycle_start_epoch_day") val cycleStartEpochDay: Long? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("program_id")],
)
data class WorkoutEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WorkoutId,
    @ColumnInfo(name = "program_id") val programId: ProgramId,
    val name: String,
    val weekday: Weekday?,
    val position: Int,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

@Entity(
    tableName = "workout_lifts",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["lift_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workout_id"), Index("lift_id")],
)
data class WorkoutLiftEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WorkoutLiftId,
    @ColumnInfo(name = "workout_id") val workoutId: WorkoutId,
    @ColumnInfo(name = "lift_id") val liftId: LiftId,
    @ColumnInfo(name = "progression_scheme") val progressionScheme: ProgressionScheme,
    val position: Int,
    @ColumnInfo(name = "superset_group_id") val supersetGroupId: WorkoutSupersetGroupId? = null,
    @ColumnInfo(name = "use_rest_timer_between_lifts") val useRestTimerBetweenLifts: Boolean = false,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_lift_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_lift_id")],
)
data class LiftSetEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: LiftSetId,
    @ColumnInfo(name = "workout_lift_id") val workoutLiftId: WorkoutLiftId,
    val position: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "rep_min") val repMin: Int,
    @ColumnInfo(name = "rep_max") val repMax: Int,
    @ColumnInfo(name = "rir_goal") val rirGoal: Int,
    @ColumnInfo(name = "is_top_set") val isTopSet: Boolean,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
    /** Null unless [setType] is MyoRep. Room v2. */
    @ColumnInfo(name = "myo_rep_kind")
    val myoRepKind: MyoRepKind? = null,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long = 0L,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = SyncFlags.CLEAN,
)

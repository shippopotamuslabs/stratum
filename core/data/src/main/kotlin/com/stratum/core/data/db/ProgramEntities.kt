package com.stratum.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Program hierarchy persistence (OS-7). Four normalized tables for the
 * `Program → Workout → WorkoutLift → LiftSet` template hierarchy, with value-class PKs and FK
 * cascade deletes so re-saving a program (regeneration) clears children atomically. Schema
 * authorized for OS-7 (vertical-slices §OS-7; AGENTS.md §4.5 Schema Lock) and added to the **v1**
 * entity set — no version bump, no migration (reinstall to apply; pre-release policy).
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
    val weekday: String?,
    val position: Int,
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
    ],
    indices = [Index("workout_id")],
)
data class WorkoutLiftEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: WorkoutLiftId,
    @ColumnInfo(name = "workout_id") val workoutId: WorkoutId,
    @ColumnInfo(name = "lift_id") val liftId: LiftId,
    @ColumnInfo(name = "progression_scheme") val progressionScheme: ProgressionScheme,
    val position: Int,
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
)

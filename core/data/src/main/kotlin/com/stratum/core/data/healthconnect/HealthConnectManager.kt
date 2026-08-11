package com.stratum.core.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.stratum.domain.healthconnect.HealthConnectPermissionDeniedException
import com.stratum.domain.healthconnect.HealthConnectSyncPort
import com.stratum.domain.healthconnect.HealthConnectUnavailableException
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectSyncPort {

    override suspend fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun hasWritePermission(): Boolean {
        if (!isAvailable()) return false
        val client = HealthConnectClient.getOrCreate(context)
        val writePermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        val grantedPermissions = client.permissionController.getGrantedPermissions()
        return grantedPermissions.contains(writePermission)
    }

    override suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ): Result<Unit> = runCatching {
        if (!isAvailable()) {
            throw HealthConnectUnavailableException()
        }
        if (!hasWritePermission()) {
            throw HealthConnectPermissionDeniedException()
        }

        val client = HealthConnectClient.getOrCreate(context)
        val startTime = session.startedAt
        val endTime = session.endedAt ?: Instant.now()
        val zoneId = ZoneId.systemDefault()
        val startOffset = zoneId.rules.getOffset(startTime)
        val endOffset = zoneId.rules.getOffset(endTime)

        val title = session.workoutNameSnapshot ?: session.programNameSnapshot ?: "Stratum Workout"
        val notes = formatSessionNotes(session, sets)

        val segments = if (sets.isNotEmpty()) {
            listOf(
                ExerciseSegment(
                    startTime = startTime,
                    endTime = endTime,
                    segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
                ),
            )
        } else {
            emptyList()
        }

        val record = ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            title = title,
            notes = notes,
            segments = segments,
            metadata = Metadata(
                clientRecordId = session.id.value,
            ),
        )

        client.insertRecords(listOf(record))
    }

    companion object {
        fun formatSessionNotes(session: TrainingSession, sets: List<LoggedSet>): String {
            val builder = StringBuilder()
            val workoutTitle =
                session.workoutNameSnapshot ?: session.programNameSnapshot ?: "Stratum Workout"
            builder.append("Workout: ").append(workoutTitle).append("\n")
            if (sets.isEmpty()) {
                builder.append("No sets logged.")
                return builder.toString()
            }

            val setsByExercise = sets.groupBy { it.exerciseNameRaw }
            setsByExercise.forEach { (exerciseName, exerciseSets) ->
                builder.append(exerciseName).append(":\n")
                exerciseSets.sortedBy { it.setIndex }.forEach { set ->
                    builder.append("  • Set ").append(set.setIndex + 1).append(": ")
                        .append(set.weight.amount).append(" ")
                        .append(set.weight.unit.name.lowercase())
                        .append(" × ").append(set.reps).append(" reps")
                    if (set.rir != null) {
                        builder.append(" @ ").append(set.rir).append(" RIR")
                    }
                    if (set.isPersonalRecord) {
                        builder.append(" (PR)")
                    }
                    builder.append("\n")
                }
                builder.append("\n")
            }
            return builder.toString().trimEnd()
        }
    }
}

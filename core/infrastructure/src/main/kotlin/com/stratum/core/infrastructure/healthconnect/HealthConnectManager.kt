package com.stratum.core.infrastructure.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.stratum.application.healthconnect.HEALTH_SYNC_UNAVAILABLE_MESSAGE
import com.stratum.application.healthconnect.HealthConnectGrantedPermissionsOutcome
import com.stratum.application.healthconnect.HealthConnectPushOutcome
import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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

    override suspend fun grantedPermissions(): HealthConnectGrantedPermissionsOutcome {
        if (!isAvailable()) {
            val cause = HealthConnectUnavailableException()
            return HealthConnectGrantedPermissionsOutcome.Failed(
                kind = HealthSyncFailureKind.Terminal,
                message = HEALTH_SYNC_UNAVAILABLE_MESSAGE,
                diagnostics = listOf(HealthSyncDiagnostic.OperationFailed(cause)),
            )
        }
        return try {
            HealthConnectGrantedPermissionsOutcome.Success(
                HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            val classified = error.toClassifiedHealthSyncFailure()
            HealthConnectGrantedPermissionsOutcome.Failed(
                kind = classified.kind,
                message = classified.message,
                diagnostics = listOf(HealthSyncDiagnostic.OperationFailed(error)),
            )
        }
    }

    override suspend fun pushSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ): HealthConnectPushOutcome {
        try {
            pushSessionOrThrow(session, sets)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            val classified = error.toClassifiedHealthSyncFailure()
            return HealthConnectPushOutcome.Failed(
                kind = classified.kind,
                message = classified.message,
                diagnostics = listOf(HealthSyncDiagnostic.OperationFailed(error)),
            )
        }
        return HealthConnectPushOutcome.Success
    }

    private suspend fun pushSessionOrThrow(
        session: TrainingSession,
        sets: List<LoggedSet>,
    ) {
        if (!isAvailable()) {
            throw HealthConnectUnavailableException()
        }
        if (!hasWritePermission()) {
            throw HealthConnectPermissionDeniedException()
        }

        val client = HealthConnectClient.getOrCreate(context)
        val startTime = session.startedAt
        val endTime = session.liveEnd()
        val zoneId = ZoneId.systemDefault()
        val startOffset = zoneId.rules.getOffset(startTime)
        val endOffset = zoneId.rules.getOffset(endTime)

        val title = session.workoutNameSnapshot ?: session.programNameSnapshot ?: "Stratum Workout"
        val notes = formatSessionNotes(session, sets)
        val segments = HealthConnectExerciseMapper.buildSegments(
            session = session,
            sets = sets,
            sessionEndFallback = endTime,
        )

        val record = buildSessionRecord(
            startTime = startTime,
            startOffset = startOffset,
            endTime = endTime,
            endOffset = endOffset,
            title = title,
            notes = notes,
            segments = segments,
            clientRecordId = session.id.value,
        )

        client.insertRecords(listOf(record))
    }

    private fun buildSessionRecord(
        startTime: Instant,
        startOffset: ZoneOffset,
        endTime: Instant,
        endOffset: ZoneOffset,
        title: String,
        notes: String,
        segments: List<ExerciseSegment>,
        clientRecordId: String,
    ): ExerciseSessionRecord {
        val metadata = Metadata.activelyRecorded(
            device = Device(type = Device.TYPE_PHONE),
            clientRecordId = clientRecordId,
        )
        return try {
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                exerciseType = HealthConnectExerciseMapper.SESSION_EXERCISE_TYPE,
                title = title,
                notes = notes,
                segments = segments,
                metadata = metadata,
            )
        } catch (_: IllegalArgumentException) {
            // Prefer a valid session+notes write over failing the entire sync.
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                exerciseType = HealthConnectExerciseMapper.SESSION_EXERCISE_TYPE,
                title = title,
                notes = notes,
                segments = emptyList(),
                metadata = metadata,
            )
        }
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

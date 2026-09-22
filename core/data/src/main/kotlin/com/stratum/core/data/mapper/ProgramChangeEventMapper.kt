package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramChangeEventEntity
import com.stratum.core.data.db.ProgramChangeEventPayloadDto
import com.stratum.core.data.db.eventType
import com.stratum.core.data.db.toDomainPayload
import com.stratum.core.data.db.toDto
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant
import kotlinx.serialization.json.Json

/**
 * JSON codec for [ProgramChangeEventPayloadDto]. Config matches the DataStore convention
 * (`WorkoutOverlayRecordSerializer`): unknown keys/values are tolerated so a newer writer
 * never breaks an older reader at parse time (a DTO whose subtype is still unknown fails
 * decode instead and is skipped by the uniform null contract below).
 */
private val eventPayloadJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal fun ProgramChangeEventPayloadDto.encodeToString(): String =
    eventPayloadJson.encodeToString(ProgramChangeEventPayloadDto.serializer(), this)

internal fun decodeEventPayloadDto(raw: String): ProgramChangeEventPayloadDto? =
    runCatching {
        eventPayloadJson.decodeFromString(ProgramChangeEventPayloadDto.serializer(), raw)
    }.getOrNull()

internal fun ProgramChangeEvent.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): ProgramChangeEventEntity {
    // Exhaustive over the sealed payload: a new subtype without a toDto() branch is a
    // compile error, not a silently-null column set.
    val dto = payload.toDto()
    // Fail fast on eventType/payload mismatch instead of persisting a row whose
    // discriminator disagrees with its body.
    require(eventType == dto.eventType()) {
        "ProgramChangeEvent id=${id.value}: eventType=$eventType does not match payload $dto"
    }
    return ProgramChangeEventEntity(
        id = id,
        programId = programId,
        eventType = eventType,
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        payloadJson = dto.encodeToString(),
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )
}

/**
 * Maps a [ProgramChangeEventEntity] back to the domain model.
 *
 * Uniform null contract: **any** row whose payload cannot be reconstructed maps to `null`
 * and is skipped by readers — corrupt JSON, an eventType/DTO mismatch, or pre-v6 rows
 * whose required flat columns are absent (including legacy `SCHEME_CHANGED` rows, which
 * predate scheme persistence and cannot be reconstructed). No branch throws: throwing for
 * some corruption kinds while skipping others forces every reader to reason about which
 * corruption is fatal, and previously crashed the coaching check-in path
 * (`NotImplementedError` on `SCHEME_CHANGED`).
 */
internal fun ProgramChangeEventEntity.toDomain(): ProgramChangeEvent? {
    val payload = payloadFromJson() ?: payloadFromLegacyColumns() ?: return null
    return ProgramChangeEvent(
        id = id,
        programId = programId,
        eventType = eventType,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        payload = payload,
    )
}

private fun ProgramChangeEventEntity.payloadFromJson(): ProgramChangeEventPayload? {
    val raw = payloadJson ?: return null
    val dto = decodeEventPayloadDto(raw) ?: return null
    if (dto.eventType() != eventType) return null
    return dto.toDomainPayload()
}

private fun ProgramChangeEventEntity.payloadFromLegacyColumns(): ProgramChangeEventPayload? =
    when (eventType) {
        ProgramChangeEventType.LIFT_SWAPPED -> {
            val liftId = workoutLiftId
            val previous = previousLiftId
            val new = newLiftId
            if (liftId != null && previous != null && new != null) {
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = liftId,
                    previousLiftId = previous,
                    newLiftId = new,
                )
            } else {
                null
            }
        }
        ProgramChangeEventType.WORKOUT_EDITED ->
            workoutId?.let { ProgramChangeEventPayload.WorkoutEdited(it) }
        ProgramChangeEventType.PROGRAM_REGENERATED ->
            ProgramChangeEventPayload.ProgramRegenerated
        ProgramChangeEventType.CYCLE_COMPLETED ->
            completedCycleNumber?.let { ProgramChangeEventPayload.CycleCompleted(it) }
        // Pre-v6 SCHEME_CHANGED rows carry no scheme data (the payload postdates them).
        ProgramChangeEventType.SCHEME_CHANGED -> null
    }

/**
 * Legacy flat payload columns derived from a DTO. Sync-compat only: pre-v6 readers decode
 * Firestore documents via the flat fields, so the encoder emits both representations.
 * Frozen — new event types are JSON-only on the wire for new readers and are simply
 * skipped by old ones.
 */
internal data class LegacyEventPayloadColumns(
    val workoutLiftId: String?,
    val previousLiftId: String?,
    val newLiftId: String?,
    val workoutId: String?,
    val completedCycleNumber: Int?,
)

internal fun ProgramChangeEventPayloadDto.toLegacyColumns(): LegacyEventPayloadColumns =
    when (this) {
        is ProgramChangeEventPayloadDto.LiftSwapped ->
            LegacyEventPayloadColumns(
                workoutLiftId = workoutLiftId,
                previousLiftId = previousLiftId,
                newLiftId = newLiftId,
                workoutId = null,
                completedCycleNumber = null,
            )
        is ProgramChangeEventPayloadDto.WorkoutEdited ->
            LegacyEventPayloadColumns(
                workoutLiftId = null,
                previousLiftId = null,
                newLiftId = null,
                workoutId = workoutId,
                completedCycleNumber = null,
            )
        is ProgramChangeEventPayloadDto.ProgramRegenerated ->
            LegacyEventPayloadColumns(
                workoutLiftId = null,
                previousLiftId = null,
                newLiftId = null,
                workoutId = null,
                completedCycleNumber = null,
            )
        is ProgramChangeEventPayloadDto.CycleCompleted ->
            LegacyEventPayloadColumns(
                workoutLiftId = null,
                previousLiftId = null,
                newLiftId = null,
                workoutId = null,
                completedCycleNumber = completedCycleNumber,
            )
        // SCHEME_CHANGED has no pre-v6 flat representation; old readers skip it.
        is ProgramChangeEventPayloadDto.SchemeChanged ->
            LegacyEventPayloadColumns(
                workoutLiftId = workoutLiftId,
                previousLiftId = null,
                newLiftId = null,
                workoutId = null,
                completedCycleNumber = null,
            )
    }

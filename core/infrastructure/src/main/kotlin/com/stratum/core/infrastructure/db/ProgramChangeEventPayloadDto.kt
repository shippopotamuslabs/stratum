package com.stratum.core.infrastructure.db

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON-serializable mirror of [ProgramChangeEventPayload], stored as a single `payload_json`
 * column on [ProgramChangeEventEntity].
 *
 * Why a JSON blob instead of one nullable column pair per event type: each wide-table column
 * is populated by a disjoint subset of event types, so every new event type added more
 * top-level nullables, more mapper `?:` branches, and more sync codec fields — with the
 * required-together invariants invisible to the type system. A self-describing payload keeps
 * those invariants in types: adding an event type means adding one DTO subtype (a compile
 * error everywhere the `when` is not extended), with zero schema churn and zero sync changes.
 *
 * Conventions (match `WorkoutOverlayDto`):
 * - All DTOs live in `:core:data`; the domain payload stays annotation-free.
 * - Domain id value classes are unwrapped to plain [String]; their `isNotBlank` invariants
 *   are re-applied in [toDomainPayload] via `runCatching`, so a corrupt blob maps to `null`
 *   instead of crashing the value-class `init`.
 * - Domain enums ([ProgressionScheme]) are referenced directly — kotlinx-serialization
 *   encodes enums by name natively.
 * - `@SerialName` values intentionally match [ProgramChangeEventType] names so the stored
 *   discriminator and the DTO subtype can be cross-checked on read.
 */
@Serializable
sealed interface ProgramChangeEventPayloadDto {
    @Serializable
    @SerialName("LIFT_SWAPPED")
    data class LiftSwapped(
        val workoutLiftId: String,
        val previousLiftId: String,
        val newLiftId: String,
    ) : ProgramChangeEventPayloadDto

    @Serializable
    @SerialName("WORKOUT_EDITED")
    data class WorkoutEdited(val workoutId: String) : ProgramChangeEventPayloadDto

    @Serializable
    @SerialName("PROGRAM_REGENERATED")
    data object ProgramRegenerated : ProgramChangeEventPayloadDto

    @Serializable
    @SerialName("CYCLE_COMPLETED")
    data class CycleCompleted(val completedCycleNumber: Int) : ProgramChangeEventPayloadDto

    @Serializable
    @SerialName("SCHEME_CHANGED")
    data class SchemeChanged(
        val workoutLiftId: String,
        val liftId: String,
        val previous: ProgressionScheme,
        val current: ProgressionScheme,
    ) : ProgramChangeEventPayloadDto
}

/** The [ProgramChangeEventType] a [ProgramChangeEventPayloadDto] subtype carries. */
internal fun ProgramChangeEventPayloadDto.eventType(): ProgramChangeEventType =
    when (this) {
        is ProgramChangeEventPayloadDto.LiftSwapped -> ProgramChangeEventType.LIFT_SWAPPED
        is ProgramChangeEventPayloadDto.WorkoutEdited -> ProgramChangeEventType.WORKOUT_EDITED
        is ProgramChangeEventPayloadDto.ProgramRegenerated -> ProgramChangeEventType.PROGRAM_REGENERATED
        is ProgramChangeEventPayloadDto.CycleCompleted -> ProgramChangeEventType.CYCLE_COMPLETED
        is ProgramChangeEventPayloadDto.SchemeChanged -> ProgramChangeEventType.SCHEME_CHANGED
    }

/** Exhaustive domain → DTO. Cannot fail: every field is non-null on the domain side. */
internal fun ProgramChangeEventPayload.toDto(): ProgramChangeEventPayloadDto =
    when (this) {
        is ProgramChangeEventPayload.LiftSwapped ->
            ProgramChangeEventPayloadDto.LiftSwapped(
                workoutLiftId = workoutLiftId.value,
                previousLiftId = previousLiftId.value,
                newLiftId = newLiftId.value,
            )
        is ProgramChangeEventPayload.WorkoutEdited ->
            ProgramChangeEventPayloadDto.WorkoutEdited(workoutId = workoutId.value)
        is ProgramChangeEventPayload.ProgramRegenerated ->
            ProgramChangeEventPayloadDto.ProgramRegenerated
        is ProgramChangeEventPayload.CycleCompleted ->
            ProgramChangeEventPayloadDto.CycleCompleted(completedCycleNumber = completedCycleNumber)
        is ProgramChangeEventPayload.SchemeChanged ->
            ProgramChangeEventPayloadDto.SchemeChanged(
                workoutLiftId = workoutLiftId.value,
                liftId = liftId.value,
                previous = previous,
                current = current,
            )
    }

/**
 * DTO → domain. Returns `null` for corrupt blobs (blank ids, or any other value-class
 * rejection) so readers skip the row instead of crashing — see the uniform null contract in
 * `ProgramChangeEventMapper.toDomain`.
 */
internal fun ProgramChangeEventPayloadDto.toDomainPayload(): ProgramChangeEventPayload? =
    runCatching {
        when (this) {
            is ProgramChangeEventPayloadDto.LiftSwapped ->
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = WorkoutLiftId(workoutLiftId),
                    previousLiftId = LiftId(previousLiftId),
                    newLiftId = LiftId(newLiftId),
                )
            is ProgramChangeEventPayloadDto.WorkoutEdited ->
                ProgramChangeEventPayload.WorkoutEdited(workoutId = WorkoutId(workoutId))
            is ProgramChangeEventPayloadDto.ProgramRegenerated ->
                ProgramChangeEventPayload.ProgramRegenerated
            is ProgramChangeEventPayloadDto.CycleCompleted ->
                ProgramChangeEventPayload.CycleCompleted(completedCycleNumber = completedCycleNumber)
            is ProgramChangeEventPayloadDto.SchemeChanged ->
                ProgramChangeEventPayload.SchemeChanged(
                    workoutLiftId = WorkoutLiftId(workoutLiftId),
                    liftId = LiftId(liftId),
                    previous = previous,
                    current = current,
                )
        }
    }.getOrNull()

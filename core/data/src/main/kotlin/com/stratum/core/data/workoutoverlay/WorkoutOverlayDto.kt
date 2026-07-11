package com.stratum.core.data.workoutoverlay

import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs that serialize the transient domain [com.stratum.domain.workout.WorkoutOverlay] to JSON
 * for Jetpack DataStore persistence (process-death restore).
 *
 * Design rules:
 * - **Domain value classes are unwrapped to plain [String]** (e.g. `WorkoutLiftId`, `LiftId`,
 *   `LiftSetId`, `OverlaySetInstanceId`). Their `require(isNotBlank())` invariants are
 *   re-applied in [WorkoutOverlayMapper.toDomain]; a blank/`null` id is skipped defensively
 *   so a corrupt record can never crash the value-class `init`.
 * - **Maps keyed by [com.stratum.domain.workout.SetSlotId] / `WorkoutLiftId` become entry
 *   lists** because kotlinx-serialization JSON object keys must be strings, and the sealed
 *   [SetSlotIdDto] keys cannot be stringified cleanly.
 * - **Domain enums ([SetType], [MyoRepKind], [WeightDto.WeightUnitDto]) are referenced
 *   directly** — kotlinx-serialization serializes enums by name natively (matches the
 *   `LiftSeedDto` convention in `:core:data`).
 * - **`recommendations` is intentionally absent** — it is recomputed by
 *   `RecomputeRecommendationsUseCase` on the first render after restore.
 *
 * All DTOs live in `:core:data`; the domain `WorkoutOverlay` stays annotation-free
 * (ARCHITECTURE.md §5).
 */

@Serializable
data class WeightDto(
    val amount: Double,
    val unit: WeightUnitDto,
) {
    @Serializable
    enum class WeightUnitDto { Lb, Kg }
}

@Serializable
sealed interface SetSlotIdDto {
    @Serializable
    @SerialName("template")
    data class Template(val liftSetId: String) : SetSlotIdDto

    @Serializable
    @SerialName("overlay")
    data class Overlay(val instanceId: String) : SetSlotIdDto
}

@Serializable
data class SetSlotDto(
    val slotId: SetSlotIdDto,
    val workoutLiftId: String,
    val liftId: String,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
    val myoRepKind: MyoRepKind? = null,
)

@Serializable
data class LoggedSetValueDto(
    val slotId: SetSlotIdDto,
    val weight: WeightDto,
    val reps: Int,
    val rir: Int? = null,
)

/** One entry of the `loggedSets` map (slot key + value). */
@Serializable
data class LoggedSetEntryDto(
    val slotId: SetSlotIdDto,
    val value: LoggedSetValueDto,
)

@Serializable
data class SlotRecommendationDto(
    val slotId: SetSlotIdDto,
    val recommendedWeight: WeightDto,
    val baselineExpectedReps: Int,
    val previousWeight: WeightDto? = null,
    val previousReps: Int? = null,
)

/** One entry of the `dynamicBaselines` map (slot key + baseline). */
@Serializable
data class DynamicBaselineEntryDto(
    val slotId: SetSlotIdDto,
    val value: SlotRecommendationDto,
)

/**
 * DTO mirror of [com.stratum.domain.workout.StructuralMutation]. Sealed with `@SerialName`
 * discriminators so the undo stack round-trips across process death.
 */
@Serializable
sealed interface StructuralMutationDto {
    @Serializable
    @SerialName("add_set")
    data class AddSet(
        val afterSlotId: SetSlotIdDto,
        val newSlot: SetSlotDto,
    ) : StructuralMutationDto

    @Serializable
    @SerialName("delete_set")
    data class DeleteSet(val slotId: SetSlotIdDto) : StructuralMutationDto

    @Serializable
    @SerialName("generate_myorep_mini_set")
    data class GenerateMyoRepMiniSet(
        val activationSlotId: SetSlotIdDto,
        val newSlot: SetSlotDto,
    ) : StructuralMutationDto

    @Serializable
    @SerialName("add_drop_set")
    data class AddDropSet(
        val afterSlotId: SetSlotIdDto,
        val newSlot: SetSlotDto,
    ) : StructuralMutationDto
}

/** One entry of the `swappedLifts` map (workoutLift key + replacement lift value). */
@Serializable
data class SwappedLiftEntryDto(
    val workoutLiftId: String,
    val liftId: String,
)

/** One entry of the `supersetGroupIdOverrides` map. `groupId == null` ⇒ "removed from superset". */
@Serializable
data class SupersetGroupOverrideEntryDto(
    val workoutLiftId: String,
    val groupId: String?,
)

/** One entry of the `useRestTimerBetweenLiftsOverrides` map. */
@Serializable
data class RestTimerOverrideEntryDto(
    val workoutLiftId: String,
    val enabled: Boolean,
)

/** One entry of the `restSecondsOverrides` map (slot key + rest duration). */
@Serializable
data class RestSecondsOverrideEntryDto(
    val slotId: SetSlotIdDto,
    val restSeconds: Int,
)

/** DTO mirror of [com.stratum.domain.workout.OverlayAddedLift]. */
@Serializable
data class OverlayAddedLiftDto(
    val workoutLiftId: String,
    val liftId: String,
    val progressionScheme: ProgressionScheme = ProgressionScheme.Double,
    val supersetGroupId: String? = null,
    val useRestTimerBetweenLifts: Boolean = false,
)

/**
 * DTO mirror of [com.stratum.domain.workout.WorkoutOverlay] minus `recommendations`.
 * All collection-typed domain fields become entry lists / nullable lists.
 */
@Serializable
data class WorkoutOverlayDto(
    val loggedSets: List<LoggedSetEntryDto> = emptyList(),
    val addedSlots: List<SetSlotDto> = emptyList(),
    val deletedSlotIds: List<SetSlotIdDto> = emptyList(),
    val undoStack: List<StructuralMutationDto> = emptyList(),
    val dynamicBaselines: List<DynamicBaselineEntryDto> = emptyList(),
    val swappedLifts: List<SwappedLiftEntryDto> = emptyList(),
    val deletedLiftIds: List<String> = emptyList(),
    val addedLifts: List<OverlayAddedLiftDto> = emptyList(),
    val supersetGroupIdOverrides: List<SupersetGroupOverrideEntryDto> = emptyList(),
    val useRestTimerBetweenLiftsOverrides: List<RestTimerOverrideEntryDto> = emptyList(),
    val restSecondsOverrides: List<RestSecondsOverrideEntryDto> = emptyList(),
    val orderedLiftIds: List<String>? = null,
)

/**
 * DataStore root. [sessionId] tags the stored overlay with the active training session; a
 * `null` value (or a mismatch) means "no overlay for the current session". On restore the
 * store returns the overlay only when [sessionId] matches the resumed session.
 */
@Serializable
data class WorkoutOverlayRecord(
    val sessionId: String? = null,
    val overlay: WorkoutOverlayDto = WorkoutOverlayDto(),
)

package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.SupersetLinkableLift

/**
 * Presentation state for the live workout screen (Slice 7). Domain models never reach
 * composables (ARCHITECTURE.md §4.3).
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState

    data class Loaded(
        val workoutId: String,
        val workoutName: String,
        val lifts: List<RenderedLiftUi>,
        val isFinalizing: Boolean,
        val sessionStartedAtEpochMilli: Long,
        val hasIncompleteLifts: Boolean = false,
        /**
         * When true, complete flow may offer "save structural edits to the program day".
         * Not raw overlay dirtiness — ad-hoc sessions always false (no template to update).
         */
        val hasDirtyEdits: Boolean = false,
        /**
         * Increments when Settings weight unit flips so live draft stores clear and reseed
         * (avoids reinterpreting typed magnitudes under the new unit).
         */
        val displayUnitEpoch: Int = 0,
    ) : WorkoutUiState

    data class Error(val message: String) : WorkoutUiState
}

data class RenderedLiftUi(
    override val workoutLiftId: String,
    override val liftId: String,
    val liftName: String,
    val sets: List<RenderedSetUi>,
    override val supersetGroupId: String? = null,
    override val useRestTimerBetweenLifts: Boolean = false,
    /**
     * Whether the per-set table renders a weight column. False for bodyweight lifts (no
     * external load to log). Derived from the lift's loading modality in [WorkoutUiMapper].
     */
    val showWeightColumn: Boolean = true,
    /**
     * Header label for the weight column — `"Weight"` (weighted), `"Assist"` (assisted), or
     * `""` when [showWeightColumn] is false (bodyweight). Pure presentation copy; the
     * domain `LoadType` never reaches composables (ARCHITECTURE.md §4.3).
     */
    val weightHeaderLabel: String = "Weight",
    /** Primary catalog image URI for the lift navigator tab, or null for initials fallback. */
    val imageUrl: String? = null,
    /**
     * Two-letter initials for navigator fallback: first letter of the first two words, or
     * first two letters of a single word (e.g. "Barbell Bench Press" → `BB`, "Squat" → `SQ`).
     */
    val navigatorInitials: String = "",
    /**
     * Catalog/default inter-set rest for this lift (seconds). Used when a myo cluster
     * **exits** so the rest timer is not stuck on the short mini-rest value.
     */
    val defaultRestSeconds: Int = 120,
    /** Ordered pinned notes shown on the lift card (all rows, not just the first). */
    val workoutPinnedNotes: List<String> = emptyList(),
    val liftPinnedNotes: List<String> = emptyList(),
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    val unitLabel: String = "lb",
    /**
     * Catalog equipment backing this lift, if assigned. Drives "Edit equipment" routing
     * (see `resolveEquipmentEditorDestination`).
     */
    val equipmentId: String? = null,
    /** True when the lift carries its own per-lift override tiers (not equipment defaults). */
    val hasIncrementOverride: Boolean = false,
    /**
     * True when a logged weight this session sits off the lift's increment lattice.
     * Drives the "Adjust Equipment" chip under the lift title. The session runtime
     * clears it for dismissed (X) and saved-away (tier-edited) cards.
     */
    val showAdjustEquipmentChip: Boolean = false,
) : SupersetLinkableLift {
    override val name: String get() = liftName
}

/**
 * Catalog equipment data for one workout lift, joined by id in
 * `LiveWorkoutSessionRuntime.render` (same pattern as `imageUrlByLiftId`).
 */
data class EquipmentEditTarget(
    val equipmentId: String?,
    val hasIncrementOverride: Boolean,
)

/**
 * Past notes for one completed session: a single date/workout header with every
 * note body from that session in append order (newest session first).
 */
data class PastSessionNotesGroupUi(
    val dateLabel: String,
    val workoutName: String,
    val bodies: List<String>,
)

/** One note draft in the editor. A null [id] means "append as new"; blank bodies drop on save. */
data class NoteDraftUi(
    val id: String? = null,
    val body: String = "",
)

/** Loaded note lists for one slot (ids are stable row ids; empty when untouched). */
data class NoteListsUi(
    val session: List<NoteDraftUi> = emptyList(),
    val workout: List<NoteDraftUi> = emptyList(),
    val lift: List<NoteDraftUi> = emptyList(),
)

/**
 * Dirty-tab payload from Manage Notes → [com.stratum.feature.workout.WorkoutViewModel.saveLiftNotes].
 * A null list means "tab untouched" (whole-list replace only for edited tabs).
 */
data class LiftNoteListsSaveRequest(
    val workoutLiftId: String,
    val liftId: String,
    val sessionNotes: List<NoteDraftUi>? = null,
    val workoutNotes: List<NoteDraftUi>? = null,
    val liftNotes: List<NoteDraftUi>? = null,
) {
    val isDirty: Boolean get() = sessionNotes != null || workoutNotes != null || liftNotes != null
}

/** Full-screen notes-editor target (conditional content in [WorkoutScreen], not a nav destination). */
data class EditingNotesTarget(
    val workoutLiftId: String,
    val liftId: String,
    val liftName: String,
    val tab: LiftNoteTab,
)

data class WorkoutLiftNotesActions(
    val onLoadNoteLists: suspend (workoutLiftId: String, liftId: String) -> NoteListsUi =
        { _, _ -> NoteListsUi() },
    val onSave: (LiftNoteListsSaveRequest) -> Unit = {},
    val onLoadPastNotes: suspend (liftId: String) -> List<PastSessionNotesGroupUi> = { emptyList() },
)

/**
 * Presentation set type for the live workout set-type chip (S / M / MM / D).
 * Mapped from domain [com.stratum.domain.program.SetType] + MyoRepKind — no domain types
 * in UI state (ARCHITECTURE.md §4.3).
 */
enum class WorkoutSetKind {
    Standard,
    MyoRepFloor,
    MyoRepMatch,
    DropSet,
}

/** True for either myo-rep subtype (floor or match). */
fun WorkoutSetKind.isMyoRepKind(): Boolean =
    this == WorkoutSetKind.MyoRepFloor || this == WorkoutSetKind.MyoRepMatch

data class RenderedSetUi(
    val slotKey: String,
    val positionLabel: String,
    val setTypeLabel: String,
    /** Current set type for the picker; drives chip letter via [setTypeLabel]. */
    val setKind: WorkoutSetKind = WorkoutSetKind.Standard,
    /**
     * Kinds the user may pick for this set (placement rules). Empty when the set cannot
     * change type (auto-generated myo minis).
     */
    val allowedSetKinds: List<WorkoutSetKind> = listOf(WorkoutSetKind.Standard),
    /** False for auto-generated myo mini-sets — badge is display-only. */
    val canChangeSetType: Boolean = true,
    /**
     * True when this set is a myo activation that currently has auto-generated minis.
     * Used to prompt before demoting the activation off MyoRep.
     */
    val hasGeneratedMyoMinis: Boolean = false,
    val repRangeLabel: String?,
    val rirLabel: String,
    val recommendedWeightLabel: String,
    val recommendedRepsLabel: String,
    val loggedWeightLabel: String?,
    val loggedRepsLabel: String?,
    /**
     * UI magnitude of the last logged weight (assist: absolute value). Used when amending
     * a completed set so empty drafts fall back to the prior log, not a recommendation.
     */
    val loggedWeightAmount: Double? = null,
    val loggedReps: Int? = null,
    val loggedPartialReps: Int? = null,
    val isComplete: Boolean,
    val previousWeightLabel: String? = null,
    /** Second line under prev weight×reps, e.g. `"2 RIR"`; null when no prior RIR. */
    val previousRirLabel: String? = null,
    val rirGoal: Int,
    val repMin: Int,
    val repMax: Int,
    val recommendedWeightAmount: Double,
    val recommendedReps: Int,
    val hasLiveRepsRecommendation: Boolean = false,
    val isProjected: Boolean = false,
    val loggedRir: Int? = null,
    /**
     * Whether this set row shows an editable weight cell. False for bodyweight lifts — the
     * weight lever is absent, so the cell is hidden and the set logs at 0. Derived per-set
     * from the owning lift's loading modality in [WorkoutUiMapper].
     */
    val showWeightInput: Boolean = true,
    /**
     * True when the owning lift is assisted — weights are stored negative but displayed as
     * their absolute value. The cell edits a positive assistance amount; [WorkoutViewModel]
     * negates it before persisting. Controls the checkbox weight-gate (uses magnitude, not
     * sign) and label formatting.
     */
    val assistMode: Boolean = false,
    /** Configured rest duration for this set (seconds), including live overlay overrides. */
    val restSeconds: Int = 0,
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    val unitLabel: String = "lb",
)

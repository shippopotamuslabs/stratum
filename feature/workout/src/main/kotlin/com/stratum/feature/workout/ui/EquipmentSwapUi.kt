package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.EquipmentSwapSheetState

/**
 * Equipment swap sheet for one live lift card: target ids plus the shared sheet state.
 * Ids are opaque strings (ARCHITECTURE.md §4.3 — no domain types in presentation).
 */
data class EquipmentSwapUi(
    val workoutLiftId: String,
    val liftId: String,
    val liftName: String,
    val sheet: EquipmentSwapSheetState,
)

/**
 * Partial-swap prompt (STR-122): the target lift has [loggedCount] logged sets this
 * session. The user chooses whether they keep the old equipment or are rewritten to
 * the newly selected piece.
 */
data class EquipmentRewritePromptUi(
    val workoutLiftId: String,
    val liftId: String,
    val liftName: String,
    val oldName: String?,
    val newId: String,
    val newName: String,
    val loggedCount: Int,
)

/**
 * Swap sheet + rewrite prompt state and callbacks for `WorkoutScreen` — one bag
 * so the screen stays under Detekt `LongParameterList`.
 */
data class EquipmentSwapHost(
    val sheet: EquipmentSwapUi? = null,
    val onQuery: (String) -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onDismiss: () -> Unit = {},
    /** Create-new entry point — the route arms the return path, then navigates. */
    val onCreateNew: () -> Unit = {},
    /** Partial-swap prompt, or null when no decision is pending. */
    val rewritePrompt: EquipmentRewritePromptUi? = null,
    val onRewrite: () -> Unit = {},
    val onKeepOld: () -> Unit = {},
    val onDismissPrompt: () -> Unit = {},
)

package com.stratum.feature.program.ui

import com.stratum.core.ui.component.EquipmentSwapSheetState

/**
 * Equipment swap sheet for one template lift card: target ids plus the shared sheet state.
 * Ids are opaque strings (ARCHITECTURE.md §4.3 — no domain types in presentation).
 */
data class EditEquipmentSwapUi(
    val workoutLiftId: String,
    val liftId: String,
    val liftName: String,
    val sheet: EquipmentSwapSheetState,
)

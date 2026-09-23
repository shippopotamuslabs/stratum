package com.stratum.liftconfig

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class LiftIncrementsKey(val liftId: String) : NavKey

@Serializable
data class LiftInformationKey(val liftId: String) : NavKey

@Serializable
data object CreateLiftKey : NavKey

/**
 * Manage / choose equipment list.
 * [selectionMode] true: row selects equipment (lift-swap style) for Create/Info.
 * false: Gym manage mode — row opens increment editor.
 */
@Serializable
data class ManageEquipmentKey(val selectionMode: Boolean = false) : NavKey

@Serializable
data class EquipmentIncrementsKey(
    val equipmentId: String,
    val sourceLiftId: String? = null,
) : NavKey

fun EntryProviderScope<NavKey>.liftIncrementsEntry(onBackClick: () -> Unit) {
    entry<LiftIncrementsKey> { key ->
        LiftIncrementsRoute(
            liftId = key.liftId,
            onBackClick = onBackClick,
        )
    }
}

fun EntryProviderScope<NavKey>.liftInformationEntry(
    onBackClick: () -> Unit,
    onChooseEquipment: () -> Unit,
) {
    entry<LiftInformationKey> { key ->
        LiftInformationRoute(
            liftId = key.liftId,
            onBackClick = onBackClick,
            onChooseEquipment = onChooseEquipment,
        )
    }
}

fun EntryProviderScope<NavKey>.createLiftEntry(
    onBackClick: () -> Unit,
    onChooseEquipment: () -> Unit,
) {
    entry<CreateLiftKey> {
        CreateLiftRoute(
            onBackClick = onBackClick,
            onChooseEquipment = onChooseEquipment,
        )
    }
}

fun EntryProviderScope<NavKey>.manageEquipmentEntry(
    onBackClick: () -> Unit,
    onOpenIncrements: (equipmentId: String) -> Unit,
) {
    entry<ManageEquipmentKey> { key ->
        ManageEquipmentRoute(
            selectionMode = key.selectionMode,
            onBackClick = onBackClick,
            onOpenIncrements = onOpenIncrements,
        )
    }
}

fun EntryProviderScope<NavKey>.equipmentIncrementsEntry(
    onBackClick: () -> Unit,
    onNavigateToLift: (liftId: String) -> Unit = {},
    onChooseEquipmentForLift: (liftId: String) -> Unit = {},
) {
    entry<EquipmentIncrementsKey> { key ->
        EquipmentIncrementsRoute(
            equipmentId = key.equipmentId,
            onBackClick = onBackClick,
            sourceLiftId = key.sourceLiftId,
            onNavigateToLift = onNavigateToLift,
            onChooseEquipmentForLift = onChooseEquipmentForLift,
        )
    }
}

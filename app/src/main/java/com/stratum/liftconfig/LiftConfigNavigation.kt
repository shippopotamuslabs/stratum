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

fun EntryProviderScope<NavKey>.liftIncrementsEntry(onBackClick: () -> Unit) {
    entry<LiftIncrementsKey> { key ->
        LiftIncrementsRoute(
            liftId = key.liftId,
            onBackClick = onBackClick,
        )
    }
}

fun EntryProviderScope<NavKey>.liftInformationEntry(onBackClick: () -> Unit) {
    entry<LiftInformationKey> { key ->
        LiftInformationRoute(
            liftId = key.liftId,
            onBackClick = onBackClick,
        )
    }
}

fun EntryProviderScope<NavKey>.createLiftEntry(onBackClick: () -> Unit) {
    entry<CreateLiftKey> {
        CreateLiftRoute(onBackClick = onBackClick)
    }
}

package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Post-onboarding shell hosting Dashboard / Program / Gym bottom navigation. */
@Serializable
data class MainTabKey(val isPreviewMode: Boolean = false) : NavKey

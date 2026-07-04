package com.stratum.core.ui.component

import androidx.compose.ui.graphics.vector.ImageVector

/** One destination in [StratumNavigationBar]. */
data class StratumNavigationBarItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

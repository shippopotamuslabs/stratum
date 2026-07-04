package com.stratum.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Bottom navigation bar for primary app destinations. Wraps M3 [NavigationBar] with Stratum
 * semantic colors so active tabs use [StratumThemeAccessor.colors.accent] (UI_DESIGN.md §3).
 */
@Composable
fun StratumNavigationBar(
    items: List<StratumNavigationBarItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty()) { "StratumNavigationBar requires at least one item." }
    require(selectedIndex in items.indices) {
        "selectedIndex $selectedIndex is out of bounds for ${items.size} items."
    }

    val colors = StratumThemeAccessor.colors
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = StratumThemeAccessor.typography.bodyLarge,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        indicatorColor = colors.surfaceSunken,
                        unselectedIconColor = colors.textMuted,
                        unselectedTextColor = colors.textMuted,
                    ),
            )
        }
    }
}

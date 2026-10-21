package com.stratum.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassHazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur

/** Gospel indicator fill for the glass bar: accent at 15% (§12.8). */
private const val GLASS_INDICATOR_ALPHA = 0.15f

/**
 * Bottom navigation bar for primary app destinations. Wraps M3 [NavigationBar] with Stratum
 * semantic colors so active tabs use [StratumThemeAccessor.colors.accent] (UI_DESIGN.md §3).
 *
 * When hosted under a glass shell (a shared HazeState provided via
 * `LocalStratumHazeState`) and the theme family's glass is enabled (Tonal — §12.8),
 * the bar is ALWAYS glass: transparent container, no tonal elevation, the shared
 * `ShippopotamusHazeBottomBar` style blurring the screen's `hazeSource` content beneath it,
 * and a 32dp pill indicator in accent at 15%. Without a host state (previews, tests)
 * or under Neo Brutalist (sanctioned carve-out, §11.3 ruling 1) it keeps the solid
 * container.
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
    val typography = StratumThemeAccessor.typography
    val haze = StratumThemeAccessor.haze
    val hazeState = rememberGlassHazeState()
    val glass = hazeState != null

    val itemColors =
        if (glass) {
            NavigationBarItemDefaults.colors(
                selectedIconColor = colors.textPrimary,
                selectedTextColor = colors.textPrimary,
                indicatorColor = colors.accent.copy(alpha = GLASS_INDICATOR_ALPHA),
                unselectedIconColor = colors.textMuted,
                unselectedTextColor = colors.textMuted,
            )
        } else {
            NavigationBarItemDefaults.colors(
                selectedIconColor = colors.accent,
                selectedTextColor = colors.accent,
                indicatorColor = colors.surfaceSunken,
                unselectedIconColor = colors.textMuted,
                unselectedTextColor = colors.textMuted,
            )
        }

    NavigationBar(
        modifier =
            if (glass) {
                modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = haze.bottomBar)
            } else {
                modifier
            },
        containerColor = if (glass) Color.Transparent else colors.surfaceSunken,
        contentColor = colors.textPrimary,
        tonalElevation = if (glass) 0.dp else NavigationBarDefaults.Elevation,
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
                        // §12.3: nav labels are labelMedium on both bar paths.
                        style = typography.labelMedium,
                    )
                },
                colors = itemColors,
            )
        }
    }
}

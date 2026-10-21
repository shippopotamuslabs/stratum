package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassHazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/** Gospel hairline under a scrolled glass top bar: border-default at 40% (§12.8). */
private const val GLASS_HAIRLINE_ALPHA = 0.4f

/**
 * Stratum's centered top bar. Wraps Material 3 [CenterAlignedTopAppBar] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 app bar APIs.
 *
 * Glass treatment (§12.8): when hosted under a glass shell (a shared HazeState
 * provided via `LocalStratumHazeState`) and the theme family's glass is enabled
 * (Tonal), the bar is transparent at rest and becomes glass when [scrolled]
 * — the shared `ShippopotamusHazeTopBar` style blurs the screen's `hazeSource` content
 * passing underneath, with a 1dp hairline at 40% as separation. The host must let its
 * content actually scroll beneath the bar (see `StratumTabScaffold`'s overlay mode).
 * Without a host state (previews, tests) or under Neo Brutalist (sanctioned carve-out,
 * §11.3 ruling 1) the bar keeps its solid [containerColor].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumTopBar(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    bottom: @Composable (() -> Unit)? = null,
    containerColor: Color = StratumThemeAccessor.colors.surfaceBase,
    scrolled: Boolean = false,
) {
    val haze = StratumThemeAccessor.haze
    val hazeState = rememberGlassHazeState()
    val glass = hazeState != null
    val effectiveContainerColor = if (glass) Color.Transparent else containerColor
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = effectiveContainerColor,
        scrolledContainerColor = effectiveContainerColor,
        navigationIconContentColor = StratumThemeAccessor.colors.textPrimary,
        titleContentColor = StratumThemeAccessor.colors.textPrimary,
        actionIconContentColor = StratumThemeAccessor.colors.textPrimary
    )
    val emptyNav: @Composable () -> Unit = {}
    val emptyActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}

    val clearStyle = remember { HazeBlurStyle { blurRadius(0.dp) } }
    val hairlineColor = StratumThemeAccessor.colors.borderSubtle.copy(alpha = GLASS_HAIRLINE_ALPHA)
    val columnModifier =
        if (glass) {
            var glassModifier =
                modifier
                    .fillMaxWidth()
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = if (scrolled) haze.topBar else clearStyle,
                    )
            if (scrolled) {
                glassModifier =
                    glassModifier.drawWithContent {
                        drawContent()
                        drawLine(
                            color = hairlineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
            }
            glassModifier
        } else {
            modifier
                .fillMaxWidth()
                .background(containerColor)
        }

    Column(modifier = columnModifier) {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon ?: emptyNav,
            actions = actions ?: emptyActions,
            colors = colors,
        )
        bottom?.invoke()
    }
}

@Composable
fun StratumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    bottom: @Composable (() -> Unit)? = null,
    containerColor: Color = StratumThemeAccessor.colors.surfaceBase,
    scrolled: Boolean = false,
) {
    StratumTopBar(
        titleContent = {
            Text(
                text = title,
                style = StratumThemeAccessor.typography.titleLarge,
                color = StratumThemeAccessor.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        bottom = bottom,
        containerColor = containerColor,
        scrolled = scrolled,
    )
}

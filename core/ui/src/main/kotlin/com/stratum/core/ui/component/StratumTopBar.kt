package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassHazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur

/** Gospel hairline: the glazed top bar’s permanent lower edge, border-default at 40% (§12.8). */
private const val GLASS_HAIRLINE_ALPHA = 0.4f

/**
 * Glass floor (STR-155): surface-base at 65% painted UNDER the blur so the bar never
 * goes fully transparent — e.g. when its source subtree has no content behind it yet.
 * Matches the style tint depth (§12.8 ShippopotamusHazeTopBar) so blur + floor agree.
 */
internal const val GLASS_BACKGROUND_FLOOR_ALPHA = 0.65f

/**
 * Stratum's centered top bar. Wraps Material 3 [CenterAlignedTopAppBar] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 app bar APIs.
 *
 * Glass treatment (§12.8, as amended 2026-10-20): when hosted under a glass shell
 * (a shared HazeState provided via `LocalStratumHazeState`) and the theme family's
 * glass is enabled (Tonal), the bar is ALWAYS glazed — the shared
 * `ShippopotamusHazeTopBar` style blurs the screen's `hazeSource` content passing
 * underneath, over a surface-base-at-65% floor so the bar never goes fully
 * transparent, with a 1dp hairline at 40% as its permanent lower edge. Sean's
 * amendment repealed the original transparent-at-rest state in full ("The Gospel
 * of Glaze says that it must remain glazed"), so there is no scrolled variant.
 * The host must let its content actually scroll beneath the bar (see
 * `StratumTabScaffold`'s overlay mode).
 * Without a host state (previews, tests) or under Neo Brutalist (sanctioned carve-out,
 * §11.3 ruling 1) the bar keeps its solid [containerColor].
 *
 * The hairline is the bar's own lower edge: with a [bottom] slot it is drawn
 * directly below the bar and above the slot (STR-203) — slot content glazes
 * beneath the bar but starts below the hairline — and without a slot it is
 * the column's lower edge, which is the same line. Solid bars carry no
 * hairline.
 *
 * @param bottomRequiresGlass When true, [bottom] is composed only under
 * glass; on solid hosts it is not composed at all, so callers whose solid
 * layout already renders the same content in-flow can declare the slot
 * unconditionally without duplicating it. Defaults to false, which
 * preserves the historical behaviour (the slot shows on glass and solid).
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
    bottomRequiresGlass: Boolean = false,
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

    // STR-203: opt-in glass-only slot — suppressed entirely on solid hosts.
    val effectiveBottom: @Composable (() -> Unit)? =
        if (bottomRequiresGlass && !glass) null else bottom
    val hairlineColor = StratumThemeAccessor.colors.borderSubtle.copy(alpha = GLASS_HAIRLINE_ALPHA)
    val floorColor = StratumThemeAccessor.colors.surfaceBase.copy(alpha = GLASS_BACKGROUND_FLOOR_ALPHA)
    // STR-203: the hairline is the bar's lower edge, not the column's. With a
    // bottom slot it sits between the bar and the slot; without one the
    // column's lower edge is the bar's, so drawing it there is identical.
    val hairlineModifier =
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = hairlineColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    val columnModifier =
        if (glass) {
            val glazed =
                modifier
                    .fillMaxWidth()
                    .background(floorColor)
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = haze.topBar,
                    )
            if (effectiveBottom != null) glazed else glazed.then(hairlineModifier)
        } else {
            modifier
                .fillMaxWidth()
                .background(containerColor)
        }

    // Single bar call site: the hairline placement branches around it.
    val appBar: @Composable () -> Unit = {
        CenterAlignedTopAppBar(
            title = titleContent,
            navigationIcon = navigationIcon ?: emptyNav,
            actions = actions ?: emptyActions,
            colors = colors,
        )
    }
    Column(modifier = columnModifier) {
        if (glass && effectiveBottom != null) {
            Box(modifier = hairlineModifier) {
                appBar()
            }
            effectiveBottom()
        } else {
            appBar()
            effectiveBottom?.invoke()
        }
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
    bottomRequiresGlass: Boolean = false,
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
        bottomRequiresGlass = bottomRequiresGlass,
    )
}

package com.stratum.core.ui.component

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.stratum.core.ui.theme.StratumThemeAccessor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color

/**
 * Stratum's centered top bar. Wraps Material 3 [CenterAlignedTopAppBar] using
 * `StratumTheme` semantic roles so feature code never imports raw M3 app bar APIs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumTopBar(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    bottom: @Composable (() -> Unit)? = null,
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = StratumThemeAccessor.colors.surfaceBase,
        scrolledContainerColor = Color.Unspecified,
        navigationIconContentColor = StratumThemeAccessor.colors.textPrimary,
        titleContentColor = StratumThemeAccessor.colors.textPrimary,
        actionIconContentColor = StratumThemeAccessor.colors.textPrimary
    )
    val emptyNav: @Composable () -> Unit = {}
    val emptyActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StratumThemeAccessor.colors.surfaceBase)
    ) {
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
    )
}

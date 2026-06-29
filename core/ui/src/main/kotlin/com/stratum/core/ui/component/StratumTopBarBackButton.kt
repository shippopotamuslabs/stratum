package com.stratum.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Standard top-bar back affordance for Stratum screens. Features use this instead of raw M3
 * navigation icon buttons (UI_DESIGN.md §4).
 */
@Composable
fun StratumTopBarBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left),
            contentDescription = contentDescription,
            tint = StratumThemeAccessor.colors.textPrimary,
        )
    }
}

package com.stratum.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = StratumThemeAccessor.colors.surfaceBase,
        contentColor = StratumThemeAccessor.colors.textPrimary,
        content = content,
    )
}

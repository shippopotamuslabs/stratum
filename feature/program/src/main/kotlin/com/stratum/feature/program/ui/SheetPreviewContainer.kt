package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Inline stand-in for the StratumBottomSheet inner container: same background, padding,
 * and spacing the sheet body is designed for. Sheet chrome itself (scrim, drag handle,
 * window) is framework-owned and uncoverable by screenshot tests.
 *
 * Deliberately mirrors [com.stratum.core.ui.component.StratumBottomSheet]'s inner
 * Column — keep the background, paddings, and arrangement in sync with it.
 */
@Composable
internal fun SheetContentPreview(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .padding(horizontal = StratumSpacing.s12)
                .padding(top = StratumSpacing.s4, bottom = StratumSpacing.s32),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            content = content,
        )
    }
}

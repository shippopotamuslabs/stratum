package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private val HairlineHeight = 1.dp

/**
 * Stratum band divider (§4.1). The default is the hairline row separator
 * (1dp, border-subtle) used between list rows in both families. [band] renders
 * the geology-band variant instead: a full-width sunken stripe separating
 * strata sections (the §2 banding motif), never a neo border.
 */
@Composable
fun StratumDivider(
    modifier: Modifier = Modifier,
    band: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    if (band) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(StratumSpacing.s8)
                    .background(colors.surfaceSunken),
        )
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(HairlineHeight)
                    .background(colors.borderSubtle),
        )
    }
}

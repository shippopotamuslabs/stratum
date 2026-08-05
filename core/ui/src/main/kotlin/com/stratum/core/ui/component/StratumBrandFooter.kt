package com.stratum.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Classic “── STRATUM ──” brand rule: horizontal lines with the wordmark sitting on the border.
 * Shared by share cards and other product chrome that need a discreet self-plug.
 */
@Composable
fun StratumBrandFooter(modifier: Modifier = Modifier) {
    val lineColor = StratumThemeAccessor.colors.borderStrong
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(lineColor),
        )
        Image(
            painter = painterResource(R.drawable.stratum_logo),
            contentDescription = stringResource(R.string.stratum_brand_cd),
            modifier = Modifier.height(18.dp),
            contentScale = ContentScale.FillHeight,
            colorFilter = ColorFilter.tint(StratumThemeAccessor.colors.textPrimary),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(lineColor),
        )
    }
}

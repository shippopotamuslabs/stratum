package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

@Composable
fun StratumGoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Sign in with Google",
) {
    val isDark = StratumThemeAccessor.colors.surfaceBase.luminance() < 0.5f
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    // Google brand chrome is fixed; border width still follows theme geometry (neo 2dp / tonal 1dp).
    val backgroundColor = if (isDark) Color(0xFF131314) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val borderColor =
        if (elevations.useHardShadow) {
            StratumThemeAccessor.colors.borderStrong
        } else if (isDark) {
            Color(0xFF8E918F)
        } else {
            Color(0xFF747775)
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .stratumButtonHardShadow(enabled = enabled, shape = shapes.button)
                .background(backgroundColor, shapes.button)
                .border(shapes.borderWidth, borderColor, shapes.button)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = StratumSpacing.s12, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_google),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
        Text(
            text = text,
            color = textColor,
            modifier = Modifier.padding(start = StratumSpacing.s8),
        )
    }
}

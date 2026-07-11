package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.R
import com.stratum.core.ui.R as CoreUiR

@Composable
fun WelcomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(CoreUiR.drawable.stratum_logo),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.9f),
            contentScale = ContentScale.FillWidth,
            colorFilter = ColorFilter.tint(StratumThemeAccessor.colors.textPrimary),
        )
        Spacer(modifier = Modifier.height(28.dp))
        StratumText(
            text = stringResource(R.string.welcome_message),
            muted = true,
        )
    }
}

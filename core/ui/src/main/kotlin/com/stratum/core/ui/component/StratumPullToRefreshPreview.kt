package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumPullToRefreshTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        PullToRefreshPreviewBody()
    }
}

@PreviewLightDark
@Composable
private fun StratumPullToRefreshNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = false) {
        PullToRefreshPreviewBody()
    }
}

@Composable
private fun PullToRefreshPreviewBody() {
    StratumPullToRefresh(
        isRefreshing = false,
        onRefresh = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            StratumText(text = "Pull to refresh", muted = true)
        }
    }
}

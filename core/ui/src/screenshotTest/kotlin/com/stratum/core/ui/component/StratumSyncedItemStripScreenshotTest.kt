package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for [StratumSyncedItemStrip] (STR-300). Thin `@PreviewTest` wrappers
 * over the shared preview content so Studio previews and goldens cannot drift.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun syncedItemStripTextTabsTonal() {
    SyncedItemStripTextTabsPreview(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun syncedItemStripTextTabsNeo() {
    SyncedItemStripTextTabsPreview(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun syncedItemStripInitialsTonal() {
    SyncedItemStripInitialsPreview(family = StratumThemeId.Tonal)
}

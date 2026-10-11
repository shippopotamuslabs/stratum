package com.stratum.equipment

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.component.EquipmentSwapLargeCatalogPreviewContent
import com.stratum.core.ui.component.EquipmentSwapPreviewContent
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.workout.ui.EquipmentRewritePromptPreviewContent

/**
 * Screenshot tests for the STR-120 equipment swap sheet + lift-card subheader.
 * Thin `@PreviewTest` wrappers over the shared preview content in `:core:ui`.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun equipmentSwapTonal() {
    EquipmentSwapPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentSwapNeo() {
    EquipmentSwapPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentSwapLargeCatalogTonal() {
    EquipmentSwapLargeCatalogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentSwapLargeCatalogNeo() {
    EquipmentSwapLargeCatalogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/** STR-122 partial-swap prompt, rendered inline without the dialog window. */
@PreviewTest
@PreviewLightDark
@Composable
fun equipmentRewritePromptTonal() {
    EquipmentRewritePromptPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentRewritePromptNeo() {
    EquipmentRewritePromptPreviewContent(family = StratumThemeId.NeoBrutalist)
}

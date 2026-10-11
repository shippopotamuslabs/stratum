package com.stratum.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.component.EquipmentSwapLargeCatalogPreviewContent
import com.stratum.core.ui.component.EquipmentSwapPreviewContent
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.workout.ui.EquipmentRewritePromptContent
import com.stratum.feature.workout.ui.EquipmentRewritePromptUi

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
    EquipmentRewritePromptScreenshotContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun equipmentRewritePromptNeo() {
    EquipmentRewritePromptScreenshotContent(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun EquipmentRewritePromptScreenshotContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
        ) {
            EquipmentRewritePromptContent(
                prompt =
                    EquipmentRewritePromptUi(
                        workoutLiftId = "wl-1",
                        liftId = "back-squat",
                        liftName = "Back Squat",
                        oldName = "Olympic Barbell",
                        newId = "ssb",
                        newName = "Safety Squat Bar",
                        loggedCount = 2,
                    ),
            )
        }
    }
}

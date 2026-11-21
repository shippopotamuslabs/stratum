package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumSyncedItemStripTextTabsTonalPreview() {
    SyncedItemStripTextTabsPreview(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumSyncedItemStripTextTabsNeoPreview() {
    SyncedItemStripTextTabsPreview(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun StratumSyncedItemStripInitialsTonalPreview() {
    SyncedItemStripInitialsPreview(family = StratumThemeId.Tonal)
}

@Composable
internal fun SyncedItemStripTextTabsPreview(family: StratumThemeId) {
    val labels = listOf("Push", "Pull", "Legs")
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(vertical = StratumSpacing.s8),
        ) {
            StratumSyncedItemStrip(
                itemCount = labels.size,
                selectedIndex = 1,
                verticalAlignment = Alignment.CenterVertically,
                trailingContent = { SyncedStripPreviewAddLabel() },
            ) { index, selected ->
                SyncedStripPreviewTextLabel(
                    label = labels[index],
                    selected = selected,
                )
            }
        }
    }
}

@Composable
internal fun SyncedItemStripInitialsPreview(family: StratumThemeId) {
    val initials = listOf("SQ", "DL")
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(vertical = StratumSpacing.s8),
        ) {
            StratumSyncedItemStrip(
                itemCount = initials.size,
                selectedIndex = 0,
                verticalAlignment = Alignment.CenterVertically,
                trailingContent = { SyncedStripPreviewAddLabel() },
            ) { index, selected ->
                SyncedStripPreviewInitials(
                    initials = initials[index],
                    selected = selected,
                )
            }
        }
    }
}

@Composable
private fun SyncedStripPreviewTextLabel(
    label: String,
    selected: Boolean,
) {
    Box(
        modifier = Modifier.padding(horizontal = StratumSpacing.s12),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = !selected,
            maxLines = 1,
        )
    }
}

@Composable
private fun SyncedStripPreviewAddLabel() {
    Box(
        modifier = Modifier.padding(horizontal = StratumSpacing.s12),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = "Add",
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
        )
    }
}

@Composable
private fun SyncedStripPreviewInitials(
    initials: String,
    selected: Boolean,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier =
            Modifier
                .size(StratumSpacing.s48)
                .background(colors.surfaceSunken, shapes.chip),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = initials,
            style = StratumThemeAccessor.typography.titleMedium,
            muted = !selected,
        )
    }
}

package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

private const val PREVIEW_INACTIVE_ALPHA = 0.4f
private val PreviewTextTabHeight = 48.dp
private val PreviewTextTabIndicator = 2.dp
private val PreviewTileSize = 56.dp
private val PreviewTileUnderline = 3.dp

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
                modifier = Modifier.height(PreviewTextTabHeight),
                trailingContent = { SyncedStripPreviewAddLabel() },
            ) { index, selected ->
                SyncedStripPreviewTextTab(
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
                trailingContent = { SyncedStripPreviewAddTile() },
            ) { index, selected ->
                SyncedStripPreviewInitialsTile(
                    initials = initials[index],
                    selected = selected,
                )
            }
        }
    }
}

@Composable
private fun SyncedStripPreviewTextTab(
    label: String,
    selected: Boolean,
) {
    val colors = StratumThemeAccessor.colors
    Column(
        modifier =
            Modifier
                .height(PreviewTextTabHeight)
                .defaultMinSize(minWidth = 72.dp)
                .drawBehind {
                    if (!selected) return@drawBehind
                    val stroke = PreviewTextTabIndicator.toPx()
                    val y = size.height - stroke / 2f
                    drawLine(
                        color = colors.accent,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
                .padding(horizontal = StratumSpacing.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
        modifier =
            Modifier
                .height(PreviewTextTabHeight)
                .padding(horizontal = StratumSpacing.s12),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = "Add +",
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = true,
        )
    }
}

@Composable
private fun SyncedStripPreviewInitialsTile(
    initials: String,
    selected: Boolean,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(PreviewTileSize)
                    .alpha(if (selected) 1f else PREVIEW_INACTIVE_ALPHA)
                    .clip(shapes.chip)
                    .background(colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            StratumText(
                text = initials,
                style = StratumThemeAccessor.typography.titleMedium,
                muted = !selected,
            )
        }
        Box(
            modifier =
                Modifier
                    .padding(top = StratumSpacing.s4)
                    .height(PreviewTileUnderline)
                    .width(PreviewTileSize)
                    .then(
                        if (selected) {
                            Modifier.background(colors.accent)
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}

@Composable
private fun SyncedStripPreviewAddTile() {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier =
            Modifier
                .size(PreviewTileSize)
                .clip(shapes.chip)
                .background(colors.surfaceRaised)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.chip,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(StratumSpacing.s24),
        )
    }
}

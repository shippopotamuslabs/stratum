package com.stratum.core.ui.component.musclepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumSegmentedControl

/**
 * A tappable front/back body illustration for selecting muscle groups. Tap a region to
 * toggle its specialization; tap the flip control to switch views. The deltoid region is a
 * composite — tapping it opens an inline sub-picker for Front/Side/Rear Delts.
 *
 * The picker emits display-label strings via [onMuscleToggled] so callers stay decoupled from
 * the region geometry (ARCHITECTURE.md §4.3 — UI maps to presentation labels, not domain).
 *
 * @param selectedMuscleLabels the currently-specialized display labels (e.g. "Chest",
 *  "Front Delts"). Drives which regions render highlighted.
 * @param onMuscleToggled invoked with a display label when a region or deltoid chip is tapped.
 *  Toggling an already-selected label deselects it.
 * @param enabled when false, unselected regions are disabled (cap reached); selected regions
 *  remain tappable to deselect.
 */
@Composable
fun StratumMusclePicker(
    selectedMuscleLabels: Set<String>,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var view by remember { mutableStateOf(BodyView.Front) }
    var deltoidPickerOpen by remember { mutableStateOf(false) }

    val regions = remember(view) { if (view == BodyView.Front) BODY_PATHS_FRONT else BODY_PATHS_BACK }

    val configuration = LocalConfiguration.current
    val diagramMaxHeight = remember(configuration) {
        (configuration.screenHeightDp * 0.45f).dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BodyCanvas(
            regions = regions,
            selectedMuscleLabels = selectedMuscleLabels,
            enabled = enabled,
            onTapTarget = { target ->
                when (target) {
                    is BodyTarget.Single -> onMuscleToggled(target.muscle.displayLabel)
                    is BodyTarget.DeltoidRegion -> deltoidPickerOpen = true
                }
            },
            modifier = Modifier
                // Set explicit height and aspect ratio constraints to prevent layout conflicts.
                .height(diagramMaxHeight)
                .aspectRatio(BODY_ASPECT_RATIO)
                .semantics { contentDescription = "Body map, ${view.name.lowercase()} view" },
        )

        FlipControl(
            view = view,
            onFlip = {
                deltoidPickerOpen = false
                view = it
            },
        )

        if (deltoidPickerOpen) {
            DeltoidSubPickerDialog(
                selectedLabels = selectedMuscleLabels,
                enabled = enabled,
                onToggle = onMuscleToggled,
                onDismiss = { deltoidPickerOpen = false },
            )
        }
    }
}

/**
 * The body silhouette's width:height. Chosen to frame the front view (≈0.51) snugly so the
 * silhouette renders large and sits toward the top of the Canvas, leaving room below for the
 * deltoid sub-picker chips. The back view (≈0.40) is height-limited and centers horizontally
 * via [com.stratum.core.ui.component.musclepicker.transformMatrix]; the flip control keeps both
 * views usable at this ratio.
 */
private const val BODY_ASPECT_RATIO = 0.50f

/**
 * Segmented front/back view toggle. Segments share an outer border with rounded end caps and
 * a flat inner edge; the active view fills with accent.
 */
@Composable
private fun FlipControl(view: BodyView, onFlip: (BodyView) -> Unit) {
    val options = BodyView.entries
    StratumSegmentedControl(
        segments = options.map { it.name },
        selectedIndex = options.indexOf(view),
        onSelectedIndexChange = { index -> onFlip(options[index]) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Body view selector" },
    )
}

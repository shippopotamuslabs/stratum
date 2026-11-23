package com.stratum.core.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator

/**
 * Preview and screenshot override. Production leaves this null and reads the window.
 */
val LocalPanePosture = staticCompositionLocalOf<PanePosture?> { null }

/** Current window posture: [LocalPanePosture] when provided, otherwise the live window. */
@Composable
fun rememberPanePosture(): PanePosture {
    LocalPanePosture.current?.let { return it }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val fold = activity?.let { rememberFoldSnapshot(it) }
    val widthDp =
        if (activity == null) {
            LocalConfiguration.current.screenWidthDp
        } else {
            val widthPx =
                WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds.width()
            with(LocalDensity.current) { widthPx.toDp() }.value.toInt()
        }
    return decidePanePosture(widthDp = widthDp, fold = fold)
}

@Composable
private fun rememberFoldSnapshot(activity: Activity): PaneFoldSnapshot? {
    val layoutInfo =
        remember(activity) {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
        }.collectAsStateWithLifecycle(initialValue = null)
    val features = layoutInfo.value?.displayFeatures ?: return null
    val feature =
        features.filterIsInstance<FoldingFeature>().firstOrNull { folding ->
            folding.orientation == FoldingFeature.Orientation.VERTICAL
        } ?: features.filterIsInstance<FoldingFeature>().firstOrNull()
    val widthPx =
        WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds.width()
    return feature?.toPaneFoldSnapshot(widthPx)
}

/** Reduces a Jetpack WindowManager [FoldingFeature] to the numbers [decidePanePosture] reads. */
fun FoldingFeature.toPaneFoldSnapshot(windowWidthPx: Int): PaneFoldSnapshot {
    val foldState =
        if (state == FoldingFeature.State.FLAT) {
            PaneFoldState.Flat
        } else {
            PaneFoldState.HalfOpened
        }
    val axis =
        if (orientation == FoldingFeature.Orientation.VERTICAL) {
            PaneHingeAxis.Vertical
        } else {
            PaneHingeAxis.Horizontal
        }
    return PaneFoldSnapshot(
        state = foldState,
        axis = axis,
        hingeLeftPx = bounds.left,
        hingeRightPx = bounds.right,
        windowWidthPx = windowWidthPx,
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

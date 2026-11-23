package com.stratum.core.ui.posture

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
val LocalFoldPosture = staticCompositionLocalOf<FoldPosture?> { null }

@Composable
fun rememberFoldPosture(): FoldPosture {
    LocalFoldPosture.current?.let { return it }
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
    return decideFoldPosture(widthDp = widthDp, fold = fold)
}

@Composable
private fun rememberFoldSnapshot(activity: Activity): FoldSnapshot? {
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
    return feature?.toFoldSnapshot(widthPx)
}

fun FoldingFeature.toFoldSnapshot(windowWidthPx: Int): FoldSnapshot {
    val foldState =
        if (state == FoldingFeature.State.FLAT) {
            FoldState.Flat
        } else {
            FoldState.HalfOpened
        }
    val axis =
        if (orientation == FoldingFeature.Orientation.VERTICAL) {
            HingeAxis.Vertical
        } else {
            HingeAxis.Horizontal
        }
    return FoldSnapshot(
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

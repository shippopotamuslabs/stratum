package com.stratum.feature.program.ui

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
import com.stratum.feature.program.SheetFoldSnapshot
import com.stratum.feature.program.SheetFoldState
import com.stratum.feature.program.SheetHingeAxis
import com.stratum.feature.program.SheetPosture
import com.stratum.feature.program.decideSheetPosture

/**
 * Preview and screenshot override. Production leaves this null and reads the window.
 */
internal val LocalSheetPosture = staticCompositionLocalOf<SheetPosture?> { null }

@Composable
internal fun rememberSheetPosture(): SheetPosture {
    LocalSheetPosture.current?.let { return it }
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
    return decideSheetPosture(widthDp = widthDp, fold = fold)
}

@Composable
private fun rememberFoldSnapshot(activity: Activity): SheetFoldSnapshot? {
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
    return feature?.toSheetFoldSnapshot(widthPx)
}

internal fun FoldingFeature.toSheetFoldSnapshot(windowWidthPx: Int): SheetFoldSnapshot {
    val foldState =
        if (state == FoldingFeature.State.FLAT) {
            SheetFoldState.Flat
        } else {
            SheetFoldState.HalfOpened
        }
    val axis =
        if (orientation == FoldingFeature.Orientation.VERTICAL) {
            SheetHingeAxis.Vertical
        } else {
            SheetHingeAxis.Horizontal
        }
    return SheetFoldSnapshot(
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

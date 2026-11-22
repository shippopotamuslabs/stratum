package com.stratum.feature.workout.ui

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
import com.stratum.feature.workout.WorkoutFoldSnapshot
import com.stratum.feature.workout.WorkoutFoldState
import com.stratum.feature.workout.WorkoutHingeAxis
import com.stratum.feature.workout.WorkoutPosture
import com.stratum.feature.workout.decideWorkoutPosture

/**
 * Preview and screenshot override. Production leaves this null and reads the window.
 */
internal val LocalWorkoutPosture = staticCompositionLocalOf<WorkoutPosture?> { null }

@Composable
internal fun rememberWorkoutPosture(): WorkoutPosture {
    LocalWorkoutPosture.current?.let { return it }
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
    return decideWorkoutPosture(widthDp = widthDp, fold = fold)
}

@Composable
private fun rememberFoldSnapshot(activity: Activity): WorkoutFoldSnapshot? {
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
    return feature?.toWorkoutFoldSnapshot(widthPx)
}

internal fun FoldingFeature.toWorkoutFoldSnapshot(windowWidthPx: Int): WorkoutFoldSnapshot {
    val foldState =
        if (state == FoldingFeature.State.FLAT) {
            WorkoutFoldState.Flat
        } else {
            WorkoutFoldState.HalfOpened
        }
    val axis =
        if (orientation == FoldingFeature.Orientation.VERTICAL) {
            WorkoutHingeAxis.Vertical
        } else {
            WorkoutHingeAxis.Horizontal
        }
    return WorkoutFoldSnapshot(
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

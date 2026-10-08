package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a chart model update and reports failures instead of crashing the composition.
 */
internal suspend fun runCatchingChartModel(
    reporter: UiRenderErrorReporter,
    onFailure: () -> Unit,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        reporter.report(error)
        onFailure()
    }
}

/**
 * Synchronous chart-model path for inspection mode (Studio `@Preview` + screenshot goldens),
 * where the `LaunchedEffect` model transaction never settles before capture and Vico would
 * otherwise render a blank host. Returns null outside inspection mode (production keeps the
 * async `CartesianChartModelProducer` path) or when the sync build fails, in which case callers
 * fall back to the producer host.
 */
@Composable
internal fun rememberInspectionChartModel(
    key: Any?,
    build: () -> CartesianChartModel,
): CartesianChartModel? {
    val inspectionMode = LocalInspectionMode.current
    return remember(key, inspectionMode) {
        if (inspectionMode) {
            runCatching { build() }.getOrNull()
        } else {
            null
        }
    }
}

/** Direct-model equivalent of `columnModel { series(x, y) }`. */
internal fun syncColumnLayerModel(
    x: List<Double>,
    y: List<Float>,
): ColumnCartesianLayerModel =
    ColumnCartesianLayerModel(
        series =
            listOf(
                x.zip(y) { xi, yi ->
                    ColumnCartesianLayerModel.Entry(x = xi, y = yi.toDouble())
                },
            ),
    )

/**
 * Direct-model equivalent of `lineModel { series(...) }` for one or more series. Each series
 * takes its x values from [xs] by index; a series without x values falls back to indices,
 * matching `series(y = ...)` semantics.
 */
internal fun syncLineLayerModel(
    ys: List<List<Float>>,
    xs: List<List<Double>> = emptyList(),
): LineCartesianLayerModel =
    LineCartesianLayerModel(
        series =
            ys.mapIndexed { seriesIndex, values ->
                val seriesX = xs.getOrNull(seriesIndex)
                values.mapIndexed { index, value ->
                    val xi = seriesX?.getOrNull(index) ?: index.toDouble()
                    LineCartesianLayerModel.Entry(x = xi, y = value.toDouble())
                }
            },
    )

package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel

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

/**
 * Canonical Vico host: owns the content-description semantics and the inspection-model
 * vs producer overload choice. [modifier] must already carry the chart's sizing;
 * [scrollState] is passed through only when the chart scrolls (otherwise Vico's default
 * applies, exactly as before).
 */
@Composable
internal fun StratumCartesianChartHost(
    chart: CartesianChart,
    modelProducer: CartesianChartModelProducer,
    inspectionModel: CartesianChartModel?,
    modifier: Modifier,
    contentDescription: String,
    scrollState: VicoScrollState? = null,
) {
    val hostModifier = modifier.semantics { this.contentDescription = contentDescription }
    if (scrollState != null) {
        if (inspectionModel != null) {
            CartesianChartHost(
                chart = chart,
                model = inspectionModel,
                modifier = hostModifier,
                scrollState = scrollState,
            )
        } else {
            CartesianChartHost(
                chart = chart,
                modelProducer = modelProducer,
                modifier = hostModifier,
                scrollState = scrollState,
            )
        }
    } else if (inspectionModel != null) {
        CartesianChartHost(
            chart = chart,
            model = inspectionModel,
            modifier = hostModifier,
        )
    } else {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = hostModifier,
        )
    }
}

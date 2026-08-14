package com.stratum.core.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.stratum.core.ui.theme.StratumThemeAccessor

/** How compact-chart tap markers render the Y value. */
enum class CompactChartValueFormat {
    Number,
    HoursMinutes,
}

/**
 * Compact-chart Vico host config: no pan/zoom (so a parent [androidx.compose.foundation.pager.HorizontalPager]
 * can fling), and a tap-toggle marker so a point or bar can show its value.
 */
@Composable
internal fun rememberStratumChartScrollState(): VicoScrollState =
    rememberVicoScrollState(scrollEnabled = false)

@Composable
internal fun rememberStratumChartMarkerController(): CartesianMarkerController =
    CartesianMarkerController.rememberToggleOnTap()

@Composable
internal fun rememberStratumChartMarker(
    decimalCount: Int,
    colorCode: Boolean = false,
    thousandsSeparator: String = ",",
    valueFormat: CompactChartValueFormat = CompactChartValueFormat.Number,
): DefaultCartesianMarker {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val labelStyle = remember(colors.textPrimary, typography.dataLarge) {
        typography.dataLarge.copy(
            color = colors.textPrimary,
            fontSize = MARKER_LABEL_SP.sp,
        )
    }
    val background = rememberShapeComponent(
        fill = Fill(colors.surfaceRaised),
        shape = RoundedCornerShape(MARKER_CORNER_DP.dp),
    )
    val label = rememberTextComponent(
        style = labelStyle,
        padding = Insets(horizontal = MARKER_PAD_H_DP.dp, vertical = MARKER_PAD_V_DP.dp),
        background = background,
    )
    val guideline = rememberLineComponent(
        fill = Fill(colors.borderSubtle),
        thickness = MARKER_GUIDELINE_DP.dp,
    )
    val formatter = remember(decimalCount, colorCode, thousandsSeparator, valueFormat) {
        when (valueFormat) {
            CompactChartValueFormat.Number ->
                DefaultCartesianMarker.ValueFormatter.default(
                    decimalCount = decimalCount,
                    thousandsSeparator = thousandsSeparator,
                    colorCode = colorCode,
                )
            CompactChartValueFormat.HoursMinutes ->
                ColorCodedYMarkerFormatter(
                    formatY = ::formatHoursAsDuration,
                    colorCode = colorCode,
                )
        }
    }
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = formatter,
        guideline = guideline,
    )
}

/**
 * Vico's default marker prints raw Y as a decimal. Sleep stages are stored as hours (0.8 = 48m);
 * this formatter keeps color-coding while showing the same hours/minutes copy as the donut legend.
 */
internal class ColorCodedYMarkerFormatter(
    private val formatY: (Double) -> String,
    private val colorCode: Boolean,
) : DefaultCartesianMarker.ValueFormatter {
    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ): CharSequence = buildAnnotatedString {
        targets.forEachIndexed { targetIndex, target ->
            when (target) {
                is LineCartesianLayerMarkerTarget -> {
                    target.points.forEachIndexed { index, point ->
                        appendY(formatY(point.entry.y), point.color)
                        if (index != target.points.lastIndex) append(", ")
                    }
                }
                is ColumnCartesianLayerMarkerTarget -> {
                    target.columns.forEachIndexed { index, column ->
                        appendY(formatY(column.entry.y), column.color)
                        if (index != target.columns.lastIndex) append(", ")
                    }
                }
                else -> error("Unexpected CartesianMarker.Target: ${target::class.simpleName}")
            }
            if (targetIndex != targets.lastIndex) append(", ")
        }
    }

    private fun AnnotatedString.Builder.appendY(
        text: String,
        color: Color,
    ) {
        if (colorCode) {
            withStyle(SpanStyle(color = color)) { append(text) }
        } else {
            append(text)
        }
    }
}

private const val MARKER_LABEL_SP = 11
private const val MARKER_CORNER_DP = 4
private const val MARKER_PAD_H_DP = 8
private const val MARKER_PAD_V_DP = 4
private const val MARKER_GUIDELINE_DP = 1

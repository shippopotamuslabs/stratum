package com.stratum.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Multi-line light", showBackground = true)
@Composable
private fun StratumMultiLineChartLightPreview() {
    MultiLineChartPreviewContent(family = StratumThemeId.Tonal)
}

@Preview(name = "Multi-line dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StratumMultiLineChartDarkPreview() {
    MultiLineChartPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@Preview(name = "Multi-line neo dark", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StratumMultiLineChartNeoDarkPreview() {
    MultiLineChartPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

@Composable
internal fun MultiLineChartPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        MultiLineChartPreviewBody()
    }
}

@Preview(name = "Multi-line vitals light", showBackground = true)
@Composable
private fun StratumMultiLineVitalsLightPreview() {
    MultiLineVitalsPreviewContent(family = StratumThemeId.Tonal)
}

@Preview(name = "Multi-line vitals dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StratumMultiLineVitalsDarkPreview() {
    MultiLineVitalsPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@Preview(name = "Multi-line vitals neo dark", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StratumMultiLineVitalsNeoDarkPreview() {
    MultiLineVitalsPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

/**
 * Single-series vitals chart (STR-108): same RHR week the dashboard recovery tab shows,
 * with the fitted Vitals scale and y-axis the app enables. Default line color matches
 * the app's RecoveryVitalTrend (no explicit color passed there either).
 */
@Composable
internal fun MultiLineVitalsPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        StratumMultiLineChart(
            primarySeries = previewTrendValues(54f, 53f, 55f, 52f, 53f, 51f, 52f),
            dayLabels = listOf("M", "T", "W", "R", "F", "Sa", "Su"),
            yScale = DetailYScale.Vitals,
            contentDescription = "Resting heart rate for the last 7 days",
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(16.dp),
            showYAxis = true,
        )
    }
}

@Composable
private fun MultiLineChartPreviewBody() {
    val colors = StratumThemeAccessor.colors
    StratumMultiLineChart(
        primarySeries = previewTrendValues(3.2f, 3.0f, 2.4f, 3.4f, 3.1f, 3.6f, 3.2f),
        secondarySeries = previewTrendValues(1.5f, 1.6f, 1.1f, 1.7f, 1.4f, 1.9f, 1.6f),
        tertiarySeries = previewTrendValues(1.6f, 1.7f, 1.3f, 1.8f, 1.6f, 2.0f, 1.7f),
        quaternarySeries = previewTrendValues(0.7f, 0.4f, 0.9f, 0.5f, 0.6f, 0.3f, 0.7f),
        primaryColor = colors.textMuted,
        secondaryColor = colors.accent,
        tertiaryColor = colors.textData,
        quaternaryColor = colors.error,
        dayLabels = listOf("M", "T", "W", "R", "F", "Sa", "Su"),
        contentDescription = "Sleep stages for the last 7 nights",
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(16.dp),
    )
}

private fun previewTrendValues(vararg values: Float): List<TrendPointUi> =
    values.mapIndexed { index, value -> TrendPointUi(weekIndex = index, value = value) }

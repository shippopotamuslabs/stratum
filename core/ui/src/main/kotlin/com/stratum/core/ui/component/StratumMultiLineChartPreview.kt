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

@Composable
private fun MultiLineChartPreviewBody() {
    val colors = StratumThemeAccessor.colors
    StratumMultiLineChart(
        primarySeries = previewHours(3.2f, 3.0f, 2.4f, 3.4f, 3.1f, 3.6f, 3.2f),
        secondarySeries = previewHours(1.5f, 1.6f, 1.1f, 1.7f, 1.4f, 1.9f, 1.6f),
        tertiarySeries = previewHours(1.6f, 1.7f, 1.3f, 1.8f, 1.6f, 2.0f, 1.7f),
        quaternarySeries = previewHours(0.7f, 0.4f, 0.9f, 0.5f, 0.6f, 0.3f, 0.7f),
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

private fun previewHours(vararg hours: Float): List<TrendPointUi> =
    hours.mapIndexed { index, value -> TrendPointUi(weekIndex = index, value = value) }

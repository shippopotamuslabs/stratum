package com.stratum.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@Preview(name = "Donut light", showBackground = true)
@Composable
private fun StratumDonutChartLightPreview() {
    DonutChartPreviewContent(family = StratumThemeId.Tonal)
}

@Preview(name = "Donut dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StratumDonutChartDarkPreview() {
    DonutChartPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@Preview(name = "Donut neo dark", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StratumDonutChartNeoDarkPreview() {
    DonutChartPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

@Composable
internal fun DonutChartPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        DonutChartPreviewBody()
    }
}

@Composable
private fun DonutChartPreviewBody() {
    val colors = StratumThemeAccessor.colors
    StratumDonutChart(
        slices = listOf(
            DonutSliceUi(0.45f, colors.textMuted),
            DonutSliceUi(0.22f, colors.accent),
            DonutSliceUi(0.23f, colors.textData),
            DonutSliceUi(0.10f, colors.error),
        ),
        contentDescription = "Last night sleep stages",
        modifier = Modifier
            .padding(16.dp)
            .size(140.dp),
    ) {
        StratumNumber(text = "7h 12m")
    }
}

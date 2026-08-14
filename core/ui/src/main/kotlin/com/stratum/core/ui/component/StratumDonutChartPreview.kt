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
    StratumTheme {
        PreviewDonut()
    }
}

@Preview(name = "Donut dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StratumDonutChartDarkPreview() {
    StratumTheme(darkTheme = true) {
        PreviewDonut()
    }
}

@Preview(name = "Donut neo dark", showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun StratumDonutChartNeoDarkPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        PreviewDonut()
    }
}

@Composable
private fun PreviewDonut() {
    val colors = StratumThemeAccessor.colors
    StratumDonutChart(
        slices = listOf(
            DonutSliceUi(0.45f, colors.textMuted, "3h 14m"),
            DonutSliceUi(0.22f, colors.accent, "1h 35m"),
            DonutSliceUi(0.23f, colors.textData, "1h 39m"),
            DonutSliceUi(0.10f, colors.borderSubtle, "43m"),
        ),
        contentDescription = "Last night sleep stages",
        modifier = Modifier
            .padding(16.dp)
            .size(140.dp),
    ) {
        StratumNumber(text = "7h 12m")
    }
}

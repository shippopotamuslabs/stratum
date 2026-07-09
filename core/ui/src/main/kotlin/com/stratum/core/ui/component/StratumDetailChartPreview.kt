package com.stratum.core.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme

@Preview(name = "Detail chart light", showBackground = true)
@Composable
private fun StratumDetailChartLightPreview() {
    StratumTheme {
        StratumDetailChart(
            primarySeries = sampleStrengthSeries(),
            contentDescription = "Sample e1RM chart",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(16.dp),
        )
    }
}

@Preview(
    name = "Detail chart dark dual",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StratumDetailChartDarkDualPreview() {
    StratumTheme(darkTheme = true) {
        StratumDetailChart(
            primarySeries = sampleStrengthSeries(),
            secondarySeries = sampleVolumeSeries(),
            contentDescription = "Sample dual chart",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(16.dp),
        )
    }
}

@Preview(
    name = "Detail progress bars dark",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StratumDetailChartProgressBarsPreview() {
    StratumTheme(darkTheme = true) {
        StratumDetailChart(
            primarySeries = sampleProgressSeries(),
            primaryYScale = DetailYScale.Progress,
            contentDescription = "Sample progress bars",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(16.dp),
        )
    }
}

@Preview(
    name = "Detail progress + volume dark",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StratumDetailChartProgressVolumePreview() {
    StratumTheme(darkTheme = true) {
        StratumDetailChart(
            primarySeries = sampleProgressSeries(),
            secondarySeries = sampleVolumeSeries(),
            primaryYScale = DetailYScale.Progress,
            secondaryYScale = DetailYScale.Volume,
            contentDescription = "Sample progress bars + volume",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(16.dp),
        )
    }
}

private fun sampleStrengthSeries(): List<DetailChartPoint> {
    val baseDay = 20_000L
    return listOf(
        DetailChartPoint(baseDay, 210f),
        DetailChartPoint(baseDay + 7, 215f),
        DetailChartPoint(baseDay + 14, 218f),
        DetailChartPoint(baseDay + 21, 221f),
        DetailChartPoint(baseDay + 28, 219f),
        DetailChartPoint(baseDay + 35, 224f),
    )
}

private fun sampleVolumeSeries(): List<DetailChartPoint> {
    val baseDay = 20_000L
    return listOf(
        DetailChartPoint(baseDay, 8f),
        DetailChartPoint(baseDay + 7, 10f),
        DetailChartPoint(baseDay + 14, 9f),
        DetailChartPoint(baseDay + 21, 12f),
        DetailChartPoint(baseDay + 28, 11f),
        DetailChartPoint(baseDay + 35, 13f),
    )
}

/** Baseline-relative index: dips below 0 then recovers (matches dashboard bar language). */
private fun sampleProgressSeries(): List<DetailChartPoint> {
    val baseDay = 20_000L
    return listOf(
        DetailChartPoint(baseDay, -0.04f),
        DetailChartPoint(baseDay + 7, -0.03f),
        DetailChartPoint(baseDay + 14, -0.01f),
        DetailChartPoint(baseDay + 21, 0.02f),
        DetailChartPoint(baseDay + 28, 0.0f),
        DetailChartPoint(baseDay + 35, 0.05f),
        DetailChartPoint(baseDay + 42, 0.03f),
    )
}

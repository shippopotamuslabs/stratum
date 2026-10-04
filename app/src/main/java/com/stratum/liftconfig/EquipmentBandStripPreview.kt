package com.stratum.liftconfig

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme

private val PREVIEW_BANDS =
    listOf(
        IncrementTierDraft("0", "100", "5", openEnded = false),
        IncrementTierDraft("100", null, "10", openEnded = true),
    )

@Preview(name = "Band strip light", showBackground = true)
@Composable
private fun BandStripLightPreview() {
    StratumTheme {
        BandStrip(
            tiers = PREVIEW_BANDS,
            unitLabel = "lb",
            selection = 0,
            onBandClick = {},
            onAddBand = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Band strip dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun BandStripDarkPreview() {
    StratumTheme(darkTheme = true) {
        BandStrip(
            tiers = PREVIEW_BANDS,
            unitLabel = "lb",
            selection = 1,
            onBandClick = {},
            onAddBand = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

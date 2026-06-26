package com.stratum.core.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.UserEffectivenessTier

@Preview(name = "Confidence bars — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Confidence bars — light")
@Composable
private fun StratumConfidenceBarsPreview() {
    StratumTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumSignalBars(
                filledBars = 1,
                contentDescription = "One bar filled",
            )
            StratumConfidenceBars(
                tier = LandmarkConfidenceTier.Medium,
                contentDescription = "Confidence medium",
            )
            StratumEffectivenessBars(
                tier = UserEffectivenessTier.VeryEffective,
                contentDescription = "Efficacy high",
            )
        }
    }
}

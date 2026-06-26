package com.stratum.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.UserEffectivenessTier
import com.stratum.domain.response.EvidenceLevel
import com.stratum.feature.onboarding.EffectiveLiftHighlightUi
import com.stratum.feature.onboarding.ImportInsightsSummaryUi
import com.stratum.feature.onboarding.MuscleVolumeLandmarkUi
import com.stratum.feature.onboarding.VolumeResponseHighlightUi

@Preview(name = "Import insights summary — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Import insights summary — light")
@Composable
private fun ImportInsightsSummaryCardPreview() {
    StratumTheme {
        ImportInsightsSummaryCard(
            summary =
                ImportInsightsSummaryUi(
                    totalSessions = 76,
                    totalSets = 1387,
                    analysisWeeks = 26,
                    enabledLiftCount = 271,
                    provenLiftCount = 38,
                    customLiftsCreated = 5,
                    liftsWithInsights = 33,
                    supersetPairs = 32,
                    volumeLandmarks =
                        listOf(
                            MuscleVolumeLandmarkUi(
                                muscleLabel = "Calves",
                                mevWeeklyHardSets = "8",
                                mrvWeeklyHardSets = "22",
                                confidenceTier = LandmarkConfidenceTier.Medium,
                            ),
                            MuscleVolumeLandmarkUi(
                                muscleLabel = "Chest",
                                mevWeeklyHardSets = "10",
                                mrvWeeklyHardSets = "18",
                                confidenceTier = LandmarkConfidenceTier.High,
                            ),
                        ),
                    effectiveLifts =
                        listOf(
                            EffectiveLiftHighlightUi(
                                muscleLabel = "Chest",
                                liftName = "Incline Dumbbell Press",
                                effectivenessTier = UserEffectivenessTier.VeryEffective,
                                confidenceTier = LandmarkConfidenceTier.High,
                            ),
                            EffectiveLiftHighlightUi(
                                muscleLabel = "Hamstrings",
                                liftName = "Seated Band Hamstring Curl",
                                effectivenessTier = UserEffectivenessTier.ModeratelyEffective,
                                confidenceTier = LandmarkConfidenceTier.Medium,
                            ),
                        ),
                    volumeResponses =
                        listOf(
                            VolumeResponseHighlightUi(
                                muscleLabel = "Chest",
                                evidence = EvidenceLevel.Hypothesis,
                                respondsToVolume = true,
                            ),
                        ),
                ),
        )
    }
}

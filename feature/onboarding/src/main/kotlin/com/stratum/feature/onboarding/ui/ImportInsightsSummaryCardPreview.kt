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
                                muscleLabel = "Hamstrings",
                                mevWeeklyHardSets = "6",
                                mavWeeklyHardSets = "10",
                                mrvWeeklyHardSets = "16",
                                pvWeeklyHardSets = "9",
                                mevIntervalLabel = "1–11",
                                mavIntervalLabel = "6–16",
                                mrvIntervalLabel = "14–24",
                                pvIntervalLabel = "4–14",
                                confidenceTier = LandmarkConfidenceTier.Low,
                            ),
                            MuscleVolumeLandmarkUi(
                                muscleLabel = "Chest",
                                mevWeeklyHardSets = "10",
                                mavWeeklyHardSets = "15",
                                mrvWeeklyHardSets = "18",
                                pvWeeklyHardSets = "13",
                                mevIntervalLabel = "8–12",
                                mavIntervalLabel = "13–17",
                                mrvIntervalLabel = "16–21",
                                pvIntervalLabel = "11–15",
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

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
import com.stratum.feature.onboarding.MuscleVolumeSnapshotUi
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
                    volumeSnapshots =
                        listOf(
                            MuscleVolumeSnapshotUi(
                                muscleLabel = "Hamstrings",
                                mvWeeklyHardSets = "4",
                                pvWeeklyHardSets = "13",
                                rcWeeklyHardSets = "20",
                                isSaturatedPosture = false,
                                personalizedFromLogs = false,
                                observedMedianWeeklyHardSets = 3.0,
                                observedMinWeeklyHardSets = "2",
                                observedMedianWeeklyHardSetsLabel = "3",
                                observedMaxWeeklyHardSets = "6",
                                pvIntervalLabel = "9–16",
                                confidenceTier = LandmarkConfidenceTier.Low,
                            ),
                            MuscleVolumeSnapshotUi(
                                muscleLabel = "Chest",
                                mvWeeklyHardSets = "4",
                                pvWeeklyHardSets = "14",
                                rcWeeklyHardSets = "20",
                                isSaturatedPosture = false,
                                personalizedFromLogs = true,
                                observedMedianWeeklyHardSets = 12.0,
                                observedMinWeeklyHardSets = "10",
                                observedMedianWeeklyHardSetsLabel = "12",
                                observedMaxWeeklyHardSets = "16",
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

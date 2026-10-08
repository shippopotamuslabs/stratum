package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.onboarding.ImportProgramState

/**
 * Screenshot tests for onboarding (STR-95). Thin `@PreviewTest` wrappers over the shared
 * preview content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun onboardingChoosePathTonal() {
    ChoosePathPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingChoosePathNeo() {
    ChoosePathPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingProfileTonal() {
    OnboardingProfilePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewProfileFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingProfileNeo() {
    OnboardingProfilePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewProfileFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingScheduleTonal() {
    OnboardingSchedulePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewScheduleFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingScheduleNeo() {
    OnboardingSchedulePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewScheduleFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingSpecializationTonal() {
    OnboardingSpecializationPreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewSpecializationFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingSpecializationNeo() {
    OnboardingSpecializationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewSpecializationFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingMinimumVolumeTonal() {
    OnboardingMinimumVolumePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewMinimumVolumeFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingMinimumVolumeNeo() {
    OnboardingMinimumVolumePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewMinimumVolumeFilled(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingGymSetupTonal() {
    OnboardingGymSetupPreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewGymLiftsPreset(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingGymSetupNeo() {
    OnboardingGymSetupPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewGymLiftsPreset(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewSummaryTonal() {
    ReviewScreenSummaryGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewSummaryNeo() {
    ReviewScreenSummaryGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewRejectedTonal() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState = ImportProgramState.Rejected(null),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewRejectedNeo() {
    ReviewImportFailurePreview(
        family = StratumThemeId.NeoBrutalist,
        importProgramState = ImportProgramState.Rejected(null),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewErrorTonal() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState = ImportProgramState.Error(null),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewErrorNeo() {
    ReviewImportFailurePreview(
        family = StratumThemeId.NeoBrutalist,
        importProgramState = ImportProgramState.Error(null),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewTooLargeTonal() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState =
            ImportProgramState.Error(
                message = "That file is too large. Program files should be under 1 MB.",
            ),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun onboardingReviewTooLargeNeo() {
    ReviewImportFailurePreview(
        family = StratumThemeId.NeoBrutalist,
        importProgramState =
            ImportProgramState.Error(
                message = "That file is too large. Program files should be under 1 MB.",
            ),
    )
}

package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.core.ui.trainingsetup.MinimumVolumeDraftUi
import com.stratum.core.ui.trainingsetup.SESSION_BAND_KEY_MEDIUM
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.SpecializationDraftUi
import com.stratum.feature.onboarding.GymLiftsDraftUi
import com.stratum.feature.onboarding.GymPresetOptionUi
import com.stratum.feature.onboarding.ProfileDraftUi

@PreviewLightDark
@Composable
private fun ChoosePathTonalPreview() {
    ChoosePathPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ChoosePathNeoPreview() {
    ChoosePathPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun OnboardingProfileTonalPreview() {
    OnboardingProfilePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewProfileFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingProfileNeoPreview() {
    OnboardingProfilePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewProfileFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingScheduleTonalPreview() {
    OnboardingSchedulePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewScheduleFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingScheduleNeoPreview() {
    OnboardingSchedulePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewScheduleFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingSpecializationTonalPreview() {
    OnboardingSpecializationPreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewSpecializationFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingSpecializationNeoPreview() {
    OnboardingSpecializationPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewSpecializationFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingMinimumVolumeTonalPreview() {
    OnboardingMinimumVolumePreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewMinimumVolumeFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingMinimumVolumeNeoPreview() {
    OnboardingMinimumVolumePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewMinimumVolumeFilled(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingGymSetupTonalPreview() {
    OnboardingGymSetupPreviewContent(
        family = StratumThemeId.Tonal,
        draft = previewGymLiftsPreset(),
    )
}

@PreviewLightDark
@Composable
private fun OnboardingGymSetupNeoPreview() {
    OnboardingGymSetupPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        draft = previewGymLiftsPreset(),
    )
}

@Composable
internal fun ChoosePathPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ChoosePathScreen(
            onSelectGenerate = {},
            onSelectScratch = {},
            onSelectImport = {},
        )
    }
}

@Composable
internal fun OnboardingProfilePreviewContent(
    family: StratumThemeId,
    draft: ProfileDraftUi,
) {
    StratumTheme(themeId = family) {
        ProfileScreen(
            draft = draft,
            onTrainingAgeSelected = {},
            onLastTrainedSelected = {},
        )
    }
}

@Composable
internal fun OnboardingSchedulePreviewContent(
    family: StratumThemeId,
    draft: ScheduleDraftUi,
) {
    StratumTheme(themeId = family) {
        ScheduleScreen(
            draft = draft,
            onTrainingDayCountChanged = {},
            onSessionPresetSelected = {},
            onCustomSessionMinutesChange = {},
        )
    }
}

@Composable
internal fun OnboardingSpecializationPreviewContent(
    family: StratumThemeId,
    draft: SpecializationDraftUi,
) {
    StratumTheme(themeId = family) {
        SpecializationScreen(
            draft = draft,
            onMuscleToggled = {},
        )
    }
}

@Composable
internal fun OnboardingMinimumVolumePreviewContent(
    family: StratumThemeId,
    draft: MinimumVolumeDraftUi,
) {
    StratumTheme(themeId = family) {
        MinimumVolumeScreen(
            draft = draft,
            onMuscleToggled = {},
            onSuggestedMuscleToggled = {},
        )
    }
}

@Composable
internal fun OnboardingGymSetupPreviewContent(
    family: StratumThemeId,
    draft: GymLiftsDraftUi,
) {
    StratumTheme(themeId = family) {
        GymAndLiftOnboardingSetupScreen(
            draft = draft,
            presetOptions = previewGymPresets(),
            onPresetSelected = {},
            onImportClick = {},
            onImportCancel = {},
        )
    }
}

internal fun previewProfileFilled(): ProfileDraftUi =
    ProfileDraftUi(
        trainingAgeSelection = "1-3 years",
        lastTrainedSelection = "This week",
    )

internal fun previewScheduleFilled(): ScheduleDraftUi =
    ScheduleDraftUi(
        trainingDayCount = 4,
        sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
        cycleLength = 7,
        restSlotIndices = setOf(2, 5, 6),
    )

internal fun previewSpecializationFilled(): SpecializationDraftUi =
    SpecializationDraftUi(
        selectedMuscleLabels = setOf("Chest", "Back"),
    )

internal fun previewMinimumVolumeFilled(): MinimumVolumeDraftUi =
    MinimumVolumeDraftUi(
        selectedMuscleLabels = setOf("Chest", "Back"),
    )

internal fun previewGymLiftsPreset(): GymLiftsDraftUi =
    GymLiftsDraftUi(
        selectedPresetLabel = "Full gym",
        isValid = true,
    )

internal fun previewGymPresets(): List<GymPresetOptionUi> =
    listOf(
        GymPresetOptionUi(
            label = "Full gym",
            description = "Barbells, dumbbells, machines, and cables.",
        ),
        GymPresetOptionUi(
            label = "Home gym",
            description = "Adjustable dumbbells, bench, and bands.",
        ),
        GymPresetOptionUi(
            label = "Minimal",
            description = "Bodyweight plus a pull-up bar.",
        ),
    )

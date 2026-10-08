package com.stratum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.LiftSwapDropdownOption
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.defaultMuscleFilterTiers
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup

@PreviewLightDark
@Composable
private fun GymLoadedTonalPreview() {
    GymScreenPreviewContent(family = StratumThemeId.Tonal, state = previewGymLoaded())
}

@PreviewLightDark
@Composable
private fun GymLoadedNeoPreview() {
    GymScreenPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGymLoaded())
}

@PreviewLightDark
@Composable
private fun GymMergeModeTonalPreview() {
    GymScreenPreviewContent(family = StratumThemeId.Tonal, state = previewGymMergeMode())
}

@PreviewLightDark
@Composable
private fun GymMergeModeNeoPreview() {
    GymScreenPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGymMergeMode())
}

@Composable
internal fun GymScreenPreviewContent(
    family: StratumThemeId,
    state: GymUiState,
) {
    StratumTheme(themeId = family) {
        GymScreen(
            state = state,
            callbacks =
                GymScreenCallbacks(
                    onSearchQueryChanged = {},
                    onFilterIntent = {},
                    onLiftEnabledChange = { _, _ -> },
                    onNavigateToIncrements = { _, _ -> },
                    onNavigateToInformation = {},
                    onCreateNewLift = {},
                ),
        )
    }
}

internal fun previewGymLoaded(): GymUiState =
    GymUiState(
        sheet =
            previewGymSheet(
                searchQuery = "",
                options = previewGymOptions(),
            ),
    )

internal fun previewGymMergeMode(): GymUiState {
    val options = previewGymOptions()
    return GymUiState(
        sheet =
            previewGymSheet(
                searchQuery = "",
                options = options,
            ).copy(selectedOptions = listOf(options[0], options[1])),
        isMergeMode = true,
    )
}

private fun previewGymSheet(
    searchQuery: String,
    options: List<LiftSwapOptionState>,
    selectedMuscles: Set<MuscleGroup> = emptySet(),
): LiftSwapSheetState =
    LiftSwapSheetState(
        targetLiftName = "",
        options = options,
        biasOptions =
            listOf(
                LiftSwapDropdownOption(value = null, label = "Any"),
                LiftSwapDropdownOption(MuscleLengthBias.Lengthened, "Lengthened"),
                LiftSwapDropdownOption(MuscleLengthBias.MidRange, "Mid"),
                LiftSwapDropdownOption(MuscleLengthBias.Shortened, "Shortened"),
            ),
        categoryOptions =
            listOf(
                LiftSwapDropdownOption(value = null, label = "All"),
                LiftSwapDropdownOption(LiftCategory.CompoundUpper, LiftCategory.CompoundUpper.displayName),
                LiftSwapDropdownOption(LiftCategory.AccessoryUpper, LiftCategory.AccessoryUpper.displayName),
            ),
        libraryOptions =
            listOf(
                LiftSwapDropdownOption(value = null, label = "All"),
                LiftSwapDropdownOption(
                    com.stratum.core.ui.component.LiftLibraryFilter.Custom,
                    "Custom",
                ),
                LiftSwapDropdownOption(
                    com.stratum.core.ui.component.LiftLibraryFilter.StratumDefault,
                    "Stratum Default",
                ),
            ),
        muscleGroups = defaultMuscleFilterTiers(),
        selectedMuscles = selectedMuscles,
        selectedBias = null,
        selectedCategory = null,
        selectedLibrary = null,
        searchQuery = searchQuery,
    )

private fun previewGymOptions(): List<LiftSwapOptionState> =
    listOf(
        LiftSwapOptionState(
            id = "lift-1",
            name = "Barbell Bench Press",
            categoryLabel = "Compound Upper",
            primaryMuscleLabels = listOf("Chest", "Triceps"),
            lengthBiasLabel = "Mid",
            similarity = 1f,
            isCustom = false,
        ),
        LiftSwapOptionState(
            id = "lift-2",
            name = "Incline Dumbbell Press",
            categoryLabel = "Compound Upper",
            primaryMuscleLabels = listOf("Chest"),
            lengthBiasLabel = "Lengthened",
            similarity = 0.78f,
            isCustom = false,
        ),
        LiftSwapOptionState(
            id = "lift-3",
            name = "Machine Chest Press",
            categoryLabel = "Accessory Upper",
            primaryMuscleLabels = listOf("Chest"),
            lengthBiasLabel = "Shortened",
            similarity = 0.41f,
            isCustom = true,
        ),
    )

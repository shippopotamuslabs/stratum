package com.stratum.feature.program.ui

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

/**
 * Screenshot-test previews for reorder + lift-swap screens (STR-92). `darkTheme` is left
 * null so `@PreviewLightDark` genuinely varies light/dark.
 *
 * Lift-swap states mirror what [buildLiftSwapSheetState] produces (idle, searching,
 * filtered results, empty); there is no loading/error slice in the state model, so the
 * ticket's "searching/error" map to a mid-query list and the no-results empty state.
 */
@PreviewLightDark
@Composable
private fun ReorderLiftsTonalPreview() {
    ReorderLiftsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ReorderLiftsNeoPreview() {
    ReorderLiftsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun ReorderWorkoutsTonalPreview() {
    ReorderWorkoutsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ReorderWorkoutsNeoPreview() {
    ReorderWorkoutsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun LiftSwapIdleTonalPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapIdle())
}

@PreviewLightDark
@Composable
private fun LiftSwapIdleNeoPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapIdle())
}

@PreviewLightDark
@Composable
private fun LiftSwapSearchingTonalPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapSearching())
}

@PreviewLightDark
@Composable
private fun LiftSwapSearchingNeoPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapSearching())
}

@PreviewLightDark
@Composable
private fun LiftSwapResultsTonalPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapResults())
}

@PreviewLightDark
@Composable
private fun LiftSwapResultsNeoPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapResults())
}

@PreviewLightDark
@Composable
private fun LiftSwapEmptyTonalPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapEmpty())
}

@PreviewLightDark
@Composable
private fun LiftSwapEmptyNeoPreview() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapEmpty())
}

@Composable
internal fun ReorderLiftsPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ReorderLiftsScreen(
            lifts =
                listOf(
                    ReorderLiftUiState(workoutLiftId = "wl-1", name = "Barbell Bench Press"),
                    ReorderLiftUiState(workoutLiftId = "wl-2", name = "Seated Dumbbell Overhead Press"),
                    ReorderLiftUiState(workoutLiftId = "wl-3", name = "Cable Fly"),
                ),
            onReorder = { _, _ -> },
            onCancel = {},
            onConfirm = {},
        )
    }
}

@Composable
internal fun ReorderWorkoutsPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ReorderWorkoutsScreen(
            days =
                listOf(
                    ReorderWorkoutUiState(dayId = "day-1", name = "Push Day"),
                    ReorderWorkoutUiState(dayId = "day-2", name = "Pull Day"),
                    ReorderWorkoutUiState(dayId = "day-3", name = "Legs"),
                ),
            onReorder = { _, _ -> },
            onCancel = {},
            onConfirm = {},
        )
    }
}

@Composable
internal fun LiftSwapPreviewContent(family: StratumThemeId, state: LiftSwapSheetState) {
    StratumTheme(themeId = family) {
        LiftSwapScreen(
            state = state,
            onLiftClicked = {},
            onDeselectLift = {},
            onConfirmSelections = {},
            onAddAsSupersetChanged = {},
            onCreateNewLift = {},
            onBackClick = {},
            onFilterIntent = {},
            onSearchQueryChanged = {},
        )
    }
}

internal fun previewSwapIdle(): LiftSwapSheetState =
    previewSwapBase(
        searchQuery = "",
        options = previewSwapOptions(),
    )

internal fun previewSwapSearching(): LiftSwapSheetState =
    previewSwapBase(
        searchQuery = "Bench",
        options =
            listOf(
                previewSwapOptions()[0],
                previewSwapOptions()[2],
            ),
    )

internal fun previewSwapResults(): LiftSwapSheetState =
    previewSwapBase(
        searchQuery = "",
        selectedMuscles = setOf(MuscleGroup.Chest),
        options = listOf(previewSwapOptions()[0]),
    )

internal fun previewSwapEmpty(): LiftSwapSheetState =
    previewSwapBase(
        searchQuery = "zzz",
        options = emptyList(),
    )

private fun previewSwapBase(
    searchQuery: String,
    options: List<LiftSwapOptionState>,
    selectedMuscles: Set<MuscleGroup> = emptySet(),
): LiftSwapSheetState =
    LiftSwapSheetState(
        targetLiftName = "Barbell Bench Press",
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

private fun previewSwapOptions(): List<LiftSwapOptionState> =
    listOf(
        LiftSwapOptionState(
            id = "lift-1",
            name = "Close-Grip Bench Press",
            categoryLabel = "Compound Upper",
            primaryMuscleLabels = listOf("Chest", "Triceps"),
            lengthBiasLabel = "Mid",
            similarity = 0.92f,
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

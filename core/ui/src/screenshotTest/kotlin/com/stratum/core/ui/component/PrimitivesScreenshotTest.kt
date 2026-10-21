package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for core primitives (STR-94). Thin `@PreviewTest` wrappers over the shared
 * preview grids — each component renders under Tonal + Neo × light + dark to catch theme
 * drift. `StratumBottomSheet` has no golden: dialog windows are uncapturable, so sheet
 * *contents* are pinned inline per-component instead (STR-89 pattern).
 */
@PreviewTest
@PreviewLightDark
@Composable
fun primitiveButtonsTonal() {
    ButtonPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveButtonsNeo() {
    ButtonPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveButtonVariantsTonal() {
    ButtonVariantsPreviewContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveAssistChipsTonal() {
    AssistChipPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveAssistChipsNeo() {
    AssistChipPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveChoiceGroupsTonal() {
    ChoiceGroupPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveChoiceGroupsNeo() {
    ChoiceGroupPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveFilterChipsTonal() {
    FilterChipPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveFilterChipsNeo() {
    FilterChipPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveSegmentedControlsTonal() {
    SegmentedControlPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveSegmentedControlsNeo() {
    SegmentedControlPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveTextFieldsTonal() {
    TextFieldPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveTextFieldsNeo() {
    TextFieldPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitivePillTogglesTonal() {
    PillTogglePreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitivePillTogglesNeo() {
    PillTogglePreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveEdgeToEdgeCardsTonal() {
    EdgeToEdgeCardPreview(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun primitiveEdgeToEdgeCardsNeo() {
    EdgeToEdgeCardPreview(family = StratumThemeId.NeoBrutalist)
}

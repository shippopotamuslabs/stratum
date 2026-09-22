package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun LiftActionsSheetContentPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        LiftActionsSheetContent(
            onConfigureSuperset = {},
            onSwapLift = {},
            onRemoveLift = {},
            onReorderLifts = {},
            onConfigureRestTimes = {},
            onManageNotes = {},
            onViewPastSessionNotes = {},
            onEditEquipment = {},
        )
    }
}

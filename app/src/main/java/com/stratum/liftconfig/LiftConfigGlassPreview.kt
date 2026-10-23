package com.stratum.liftconfig

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the lift-config surfaces (STR-160, §12.8):
 * the existing preview contents hosted the way the lift-config Nav3 entries
 * host them — a shared HazeState provided at the route level via
 * [StratumGlassHost] — so the goldens pin the glazed top bars (floor + blur
 * + hairline, content beneath) instead of the solid fallback the unhosted
 * previews render. Tonal only: Neo keeps solid chrome under the carve-out,
 * already pinned by the Neo goldens. The dashboard graph detail has no
 * preview content: it is hosted in a dialog window, which the screenshot
 * harness cannot capture (the band-sheet precedent in
 * LiftConfigScreenshotTest).
 */
@Composable
internal fun CreateLiftGlassPreviewContent() {
    StratumGlassHost {
        CreateLiftPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewCreateLiftFilled(),
        )
    }
}

@Composable
internal fun ManageEquipmentGlassPreviewContent() {
    StratumGlassHost {
        ManageEquipmentPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewManageEquipment(),
            selectionMode = false,
        )
    }
}

@Composable
internal fun LiftInformationGlassPreviewContent() {
    StratumGlassHost {
        LiftInformationPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewLiftInformation(LiftInfoTab.Instructions),
        )
    }
}

@Composable
internal fun IncrementEditorGlassPreviewContent() {
    StratumGlassHost {
        IncrementEditorPreview(
            chrome = PREVIEW_EQUIPMENT_CHROME,
            title = "Cables",
            selection = null,
            family = StratumThemeId.Tonal,
            headerContent = PREVIEW_EQUIPMENT_NAME_FIELD,
        )
    }
}

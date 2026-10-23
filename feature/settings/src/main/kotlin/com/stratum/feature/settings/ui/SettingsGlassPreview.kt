package com.stratum.feature.settings.ui

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the settings surfaces (STR-161, §12.8):
 * the existing preview contents hosted the way the settings Nav3 entries
 * host them — a shared HazeState provided at the route level via
 * [StratumGlassHost] — so the goldens pin the glazed top bars (floor + blur
 * + hairline, content beneath) instead of the solid fallback the unhosted
 * previews render. Tonal only: Neo keeps solid chrome under the carve-out,
 * already pinned by the Neo goldens.
 */
@Composable
internal fun SettingsGlassPreviewContent() {
    StratumGlassHost {
        SettingsScreenPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewSettingsDefault(),
        )
    }
}

@Composable
internal fun LegalGlassPreviewContent() {
    StratumGlassHost {
        LegalScreenPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun ImportDataGlassPreviewContent() {
    StratumGlassHost {
        ImportDataPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun ExportDataGlassPreviewContent() {
    StratumGlassHost {
        ExportDataPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun ExportLogsGlassPreviewContent() {
    StratumGlassHost {
        ExportLogsPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewExportLogsLast30(),
        )
    }
}

@Composable
internal fun ExportProgramsGlassPreviewContent() {
    StratumGlassHost {
        ExportProgramsPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewExportProgramsSelection(),
        )
    }
}

@Composable
internal fun ImportLogsGlassPreviewContent() {
    StratumGlassHost {
        ImportLogsPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewImportLogsIdle(),
        )
    }
}

@Composable
internal fun ImportProgramGlassPreviewContent() {
    StratumGlassHost {
        ImportProgramPreviewContent(
            family = StratumThemeId.Tonal,
            uiState = previewImportProgramIdle(),
        )
    }
}

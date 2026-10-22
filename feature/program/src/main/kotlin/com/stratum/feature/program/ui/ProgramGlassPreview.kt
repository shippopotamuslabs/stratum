package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the program detail + editor surfaces
 * (STR-158, §12.8): the existing preview contents hosted the way production
 * hosts them — a shared HazeState provided at the route level via
 * [StratumGlassHost] for the four detail routes, and for CheckIn the same
 * host standing in for the Programs tab shell that hosts it in production —
 * so the goldens pin the glazed top bars (floor + blur + hairline, content
 * beneath) instead of the solid fallback the unhosted previews render. The
 * detail-route pattern follows STR-157's workout wrappers. Tonal only: Neo
 * keeps solid chrome under the carve-out, already pinned by the Neo goldens.
 */
@Composable
internal fun CreateProgramGlassPreviewContent() {
    StratumGlassHost {
        CreateProgramStepPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewEditorState(),
        )
    }
}

@Composable
internal fun EditWorkoutGlassPreviewContent() {
    StratumGlassHost {
        EditWorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun LiftSwapGlassPreviewContent() {
    StratumGlassHost {
        LiftSwapPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewSwapResults(),
        )
    }
}

@Composable
internal fun ProgramLibraryGlassPreviewContent() {
    StratumGlassHost {
        ProgramLibraryLoadedPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun CheckInGlassPreviewContent() {
    StratumGlassHost {
        CheckInPreviewContent(
            family = StratumThemeId.Tonal,
            state = subjectiveBaseState(),
        )
    }
}

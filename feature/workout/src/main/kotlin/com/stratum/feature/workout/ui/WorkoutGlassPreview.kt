package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the workout surfaces (STR-157, §12.8): the
 * existing preview contents hosted the way the workout Nav3 entries host them
 * — a shared HazeState provided at the route level via [StratumGlassHost] —
 * so the goldens pin the glazed top bars (floor + blur + hairline, content
 * beneath) instead of the solid fallback the unhosted previews render. The
 * STR-162 precedent (`ProgramsActiveWorkoutGlassPreviewContent`) established
 * this pattern for the tab shell; these are the detail-route equivalents.
 * Tonal only: Neo keeps solid chrome under the carve-out, already pinned by
 * the Neo goldens.
 */
@Composable
internal fun WorkoutSessionGlassPreviewContent() {
    StratumGlassHost {
        WorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun WorkoutCompleteGlassPreviewContent() {
    StratumGlassHost {
        WorkoutCompleteBasePreviewContent(family = StratumThemeId.Tonal)
    }
}

@Composable
internal fun ManageNotesGlassPreviewContent() {
    StratumGlassHost {
        ManageNotesScreenPreviewContent(family = StratumThemeId.Tonal)
    }
}

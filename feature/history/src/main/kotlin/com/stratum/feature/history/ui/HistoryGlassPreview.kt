package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Glass-host preview content for the history surfaces (STR-159, §12.8): the
 * existing preview contents hosted the way the history Nav3 entries host
 * them — a shared HazeState provided at the route level via
 * [StratumGlassHost] — so the goldens pin the glazed top bars (floor + blur
 * + hairline, content beneath) instead of the solid fallback the unhosted
 * previews render. The detail-route pattern follows STR-157's workout
 * wrappers. Tonal only: Neo keeps solid chrome under the carve-out, already
 * pinned by the Neo goldens.
 */
@Composable
internal fun WorkoutHistoryGlassPreviewContent() {
    StratumGlassHost {
        WorkoutHistoryPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewHistoryMonth(),
        )
    }
}

@Composable
internal fun HistoryDaySessionsGlassPreviewContent() {
    StratumGlassHost {
        HistoryDaySessionsPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewDaySessionsLoaded(),
        )
    }
}

@Composable
internal fun WorkoutSessionDetailGlassPreviewContent() {
    StratumGlassHost {
        WorkoutSessionDetailPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewSessionDetailLoaded(),
        )
    }
}

@Composable
internal fun EditHistoricalWorkoutGlassPreviewContent() {
    StratumGlassHost {
        EditHistoricalWorkoutPreviewContent(
            family = StratumThemeId.Tonal,
            state = previewEditHistoricalLoaded(),
        )
    }
}

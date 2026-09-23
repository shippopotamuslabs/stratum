package com.stratum.feature.workout.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Screen actions for [WorkoutCompleteScreen] — kept as one bag for Detekt `LongParameterList`
 * (same pattern as `WorkoutScreenActions`).
 */
data class WorkoutCompleteActions(
    val onClose: () -> Unit,
    val onShareImage: (ImageBitmap) -> Unit = {},
    val onShareCaptureFailed: () -> Unit = {},
    val onAcceptProposal: (String) -> Unit = {},
    val onAcceptAlternative: (String) -> Unit = {},
    val onAcceptRepRangeShift: (String) -> Unit = {},
    val onEditProposal: (String) -> Unit = {},
    val onSkipProposal: (String) -> Unit = {},
    val onNotNowProposal: (String) -> Unit = {},
    val onConfirmBlastRadius: () -> Unit = {},
    val onDismissBlastRadius: () -> Unit = {},
)

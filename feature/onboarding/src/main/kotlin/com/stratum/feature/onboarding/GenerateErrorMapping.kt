package com.stratum.feature.onboarding

/**
 * Maps a failed [GenerateProgramUseCase] exception to user-visible copy for the Generate step.
 * Infrastructure adapters attach readable messages; this helper keeps the ViewModel free of
 * Firebase/inference types.
 */
internal fun Throwable?.toGenerateErrorMessage(): String =
    this?.message?.takeIf { it.isNotBlank() } ?: GENERATE_ERROR_FALLBACK

internal const val GENERATE_ERROR_FALLBACK = "Program generation failed. Try again."

internal fun Throwable?.toPresetApplyErrorMessage(): String =
    this?.message?.takeIf { it.isNotBlank() } ?: PRESET_APPLY_ERROR_FALLBACK

internal const val PRESET_APPLY_ERROR_FALLBACK = "Couldn't apply that gym preset. Try again."

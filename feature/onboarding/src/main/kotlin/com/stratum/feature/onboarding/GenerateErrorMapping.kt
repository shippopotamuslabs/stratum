package com.stratum.feature.onboarding

internal fun Throwable?.toPresetApplyErrorMessage(): String =
    this?.message?.takeIf { it.isNotBlank() } ?: PRESET_APPLY_ERROR_FALLBACK

internal const val PRESET_APPLY_ERROR_FALLBACK = "Couldn't apply that gym preset. Try again."

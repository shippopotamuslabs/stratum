package com.stratum.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reports unexpected UI render failures (chart model builds, formatting) to Crashlytics via
 * the app shell. Default is a no-op so previews and tests do not need a provider.
 */
fun interface UiRenderErrorReporter {
    fun report(throwable: Throwable)
}

val LocalUiRenderErrorReporter =
    staticCompositionLocalOf<UiRenderErrorReporter> {
        UiRenderErrorReporter { }
    }

/**
 * Optional feature-level recovery. Calling this replaces the [StratumRenderBoundary] content
 * with [StratumFeatureErrorFallback]. Chart composables should report via
 * [LocalUiRenderErrorReporter] and keep a local empty/baseline fallback instead.
 */
fun interface UiRenderRecovery {
    fun recover(throwable: Throwable)
}

val LocalUiRenderRecovery =
    staticCompositionLocalOf<UiRenderRecovery> {
        UiRenderRecovery { }
    }

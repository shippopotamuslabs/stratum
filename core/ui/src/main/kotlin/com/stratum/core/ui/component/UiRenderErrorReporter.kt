package com.stratum.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reports unexpected UI side-effect failures (chart model builds) to Crashlytics via the app
 * shell. Default is a no-op so previews and tests do not need a provider.
 */
fun interface UiRenderErrorReporter {
    fun report(throwable: Throwable)
}

val LocalUiRenderErrorReporter =
    staticCompositionLocalOf<UiRenderErrorReporter> {
        UiRenderErrorReporter { }
    }

package com.stratum.core.ui.component

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a chart model update and reports failures instead of crashing the composition.
 */
internal suspend fun runCatchingChartModel(
    reporter: UiRenderErrorReporter,
    onFailure: () -> Unit,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        reporter.report(error)
        onFailure()
    }
}

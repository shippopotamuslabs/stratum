package com.stratum.domain.training

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.importing.InsightsAnalysisScope
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Shared insights + weekly-series stages used by post-finalize refresh, cloud-hydrate rebuild, and
 * Settings force recovery. Always runs on [defaultDispatcher] so UI / finalize callers never block
 * Main with full-history analytics.
 */
class LocalDerivedTrainingStages @Inject constructor(
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val refreshWeeklySeries: RefreshWeeklySeriesUseCase,
    private val loggingPort: LoggingPort,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    enum class FailPolicy {
        /** Fail the whole refresh when a stage fails (Settings recovery, hydrate rebuild). */
        Throw,

        /** Log and continue to the next stage (post-finalize / history-edit fire-and-forget). */
        LogAndContinue,
    }

    /**
     * @param includeInsights when false, only [RefreshWeeklySeriesUseCase] runs (hydrate path).
     * @param failPolicy how stage failures are handled.
     * @param logTag logging tag for stage errors.
     */
    suspend fun run(
        includeInsights: Boolean,
        failPolicy: FailPolicy,
        logTag: String,
    ): Result<Unit> =
        try {
            withContext(defaultDispatcher) {
                if (includeInsights) {
                    val insightsResult = extractTrainingInsights(scope = InsightsAnalysisScope.All)
                    if (insightsResult.isFailure) {
                        loggingPort.error(
                            logTag,
                            "Insights rollup failed: ${insightsResult.exceptionOrNull()?.message}",
                            insightsResult.exceptionOrNull(),
                        )
                        when (failPolicy) {
                            FailPolicy.Throw -> insightsResult.getOrThrow()
                            FailPolicy.LogAndContinue -> Unit
                        }
                    }
                }
                ensureActive()
                val weeklyResult = refreshWeeklySeries()
                if (weeklyResult.isFailure) {
                    loggingPort.error(
                        logTag,
                        "Weekly series rebuild failed: ${weeklyResult.exceptionOrNull()?.message}",
                        weeklyResult.exceptionOrNull(),
                    )
                    when (failPolicy) {
                        FailPolicy.Throw -> weeklyResult.getOrThrow()
                        FailPolicy.LogAndContinue -> Unit
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}

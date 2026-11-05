package com.stratum.application.training

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.application.importing.InsightsAnalysisScope
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Outcome of a [LocalDerivedTrainingStages] run (STR-243).
 *
 * Stage failures are identified by kind ([InsightsFailed] /
 * [WeeklySeriesFailed]): the stage's originating throwable is logged once
 * inside the stages (with the caller's log tag) before the branch is returned,
 * so it never crosses as a payload. [PersistenceFailed] covers a failure of
 * the stage runner itself. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface LocalDerivedTrainingStagesResult {
    data object Success : LocalDerivedTrainingStagesResult

    data object InsightsFailed : LocalDerivedTrainingStagesResult

    data object WeeklySeriesFailed : LocalDerivedTrainingStagesResult

    data object PersistenceFailed : LocalDerivedTrainingStagesResult
}

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
    ): LocalDerivedTrainingStagesResult =
        try {
            withContext(defaultDispatcher) {
                if (includeInsights) {
                    val insightsResult = extractTrainingInsights(scope = InsightsAnalysisScope.All)
                    if (insightsResult.isFailure) {
                        val error = insightsResult.exceptionOrNull()
                        loggingPort.error(
                            logTag,
                            "Insights rollup failed: ${error?.message}",
                            error,
                        )
                        if (failPolicy == FailPolicy.Throw) {
                            return@withContext LocalDerivedTrainingStagesResult.InsightsFailed
                        }
                    }
                }
                ensureActive()
                val weeklyResult = refreshWeeklySeries()
                if (weeklyResult.isFailure) {
                    val error = weeklyResult.exceptionOrNull()
                    loggingPort.error(
                        logTag,
                        "Weekly series rebuild failed: ${error?.message}",
                        error,
                    )
                    if (failPolicy == FailPolicy.Throw) {
                        return@withContext LocalDerivedTrainingStagesResult.WeeklySeriesFailed
                    }
                }
                LocalDerivedTrainingStagesResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The stage runner itself failed (not a stage result): log the
            // throwable once here, at the boundary, and return the kind.
            loggingPort.error(logTag, "Local derived stages failed: ${e.message}", e)
            LocalDerivedTrainingStagesResult.PersistenceFailed
        }
}

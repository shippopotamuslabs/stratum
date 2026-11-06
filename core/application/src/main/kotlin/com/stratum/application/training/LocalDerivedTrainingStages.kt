package com.stratum.application.training

import com.stratum.domain.di.DefaultDispatcher
import com.stratum.application.importing.ExtractTrainingInsightsOutcome
import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.importing.InsightsAnalysisScope
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Outcome of a [LocalDerivedTrainingStages] run (STR-243; STR-237 Route (i)).
 *
 * Stage failures are identified by kind ([InsightsFailed] /
 * [WeeklySeriesFailed]). Every failure record rides its branch as
 * [OperationalDiagnostic]s (legacy tag + rendered message, real throwable as
 * cause): the insights stage's records are the extract outcome's diagnostics,
 * propagated unchanged; the weekly-series stage's record is rendered here
 * with the caller's log tag; [PersistenceFailed] covers a failure of the
 * stage runner itself. Under [LocalDerivedTrainingStages.FailPolicy.LogAndContinue]
 * the stage records accumulate on [Success.diagnostics] instead, and the
 * caller owns draining them. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface LocalDerivedTrainingStagesResult {
    data class Success(
        val diagnostics: List<OperationalDiagnostic> = emptyList(),
    ) : LocalDerivedTrainingStagesResult

    data class InsightsFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LocalDerivedTrainingStagesResult

    data class WeeklySeriesFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LocalDerivedTrainingStagesResult

    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : LocalDerivedTrainingStagesResult
}

/**
 * Shared insights + weekly-series stages used by post-finalize refresh, cloud-hydrate rebuild, and
 * Settings force recovery. Always runs on [defaultDispatcher] so UI / finalize callers never block
 * Main with full-history analytics.
 */
class LocalDerivedTrainingStages @Inject constructor(
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val refreshWeeklySeries: RefreshWeeklySeriesUseCase,
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
                // LogAndContinue stage records accumulate here and ride Success.
                val continuedDiagnostics = mutableListOf<OperationalDiagnostic>()
                if (includeInsights) {
                    val insightsOutcome = extractTrainingInsights(scope = InsightsAnalysisScope.All)
                    if (insightsOutcome is ExtractTrainingInsightsOutcome.Failed) {
                        // STR-237: the extract failure rides its outcome as
                        // diagnostics; propagate them onward unchanged —
                        // exactly one record per failure, drained by the
                        // boundary that owns this result.
                        if (failPolicy == FailPolicy.Throw) {
                            return@withContext LocalDerivedTrainingStagesResult.InsightsFailed(
                                diagnostics = insightsOutcome.diagnostics,
                            )
                        }
                        continuedDiagnostics += insightsOutcome.diagnostics
                    }
                }
                ensureActive()
                val weeklyResult = refreshWeeklySeries()
                if (weeklyResult.isFailure) {
                    val error = weeklyResult.exceptionOrNull()
                    val diagnostic =
                        OperationalDiagnostic(
                            tag = logTag,
                            message = "Weekly series rebuild failed: ${error?.message}",
                            // The legacy record allowed a null cause; the
                            // carrier requires the real throwable, so a
                            // failure without one is synthesized.
                            cause = error ?: IllegalStateException(
                                "Weekly series rebuild failed without an exception",
                            ),
                        )
                    if (failPolicy == FailPolicy.Throw) {
                        return@withContext LocalDerivedTrainingStagesResult.WeeklySeriesFailed(
                            diagnostics = listOf(diagnostic),
                        )
                    }
                    continuedDiagnostics += diagnostic
                }
                LocalDerivedTrainingStagesResult.Success(diagnostics = continuedDiagnostics)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The stage runner itself failed (not a stage result): the
            // throwable rides the branch as its one diagnostic.
            LocalDerivedTrainingStagesResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = logTag,
                        message = "Local derived stages failed: ${e.message}",
                        cause = e,
                    ),
                ),
            )
        }
}

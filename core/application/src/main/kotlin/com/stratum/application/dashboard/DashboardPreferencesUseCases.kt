package com.stratum.application.dashboard

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences

/**
 * Outcome of a dashboard preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface DashboardPreferenceResult {
    data object Success : DashboardPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : DashboardPreferenceResult
}

/**
 * Observes persisted dashboard preferences. ViewModels use this instead of
 * [DashboardPreferencesRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveDashboardPreferencesUseCase @Inject constructor(
    private val repository: DashboardPreferencesRepository,
) {
    operator fun invoke(): Flow<DashboardPreferences> = repository.observe()
}

/** Persists the selected dashboard board tab (Muscles / 7-Day / Lifts). */
class SetDashboardBoardTabUseCase @Inject constructor(
    private val repository: DashboardPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(tab: DashboardBoardTab): DashboardPreferenceResult =
        try {
            repository.setSelectedTab(tab)
            DashboardPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set dashboard board tab: ${t.message}", t)
            DashboardPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetDashboardBoardTab"
    }
}

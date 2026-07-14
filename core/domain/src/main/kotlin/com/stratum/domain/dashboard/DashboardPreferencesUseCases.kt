package com.stratum.domain.dashboard

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
    suspend operator fun invoke(tab: DashboardBoardTab): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set dashboard board tab") {
            repository.setSelectedTab(tab)
        }

    private companion object {
        const val TAG = "SetDashboardBoardTab"
    }
}

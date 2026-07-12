package com.stratum.domain.workout

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetRepRangeShiftProposalsEnabledUseCase @Inject constructor(
    private val preferences: RepRangeShiftProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set rep-range shift proposals enabled") {
            preferences.setEnabled(enabled)
        }

    private companion object {
        const val TAG = "SetRepRangeShiftEnabled"
    }
}

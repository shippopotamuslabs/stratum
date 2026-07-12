package com.stratum.domain.lift

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
) {
    operator fun invoke(): Flow<Boolean> = preferences.observeEnabled()
}

class SetEquipmentProposalsEnabledUseCase @Inject constructor(
    private val preferences: EquipmentProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set equipment proposals enabled") {
            preferences.setEnabled(enabled)
        }

    private companion object {
        const val TAG = "SetEquipmentProposalsEnabled"
    }
}

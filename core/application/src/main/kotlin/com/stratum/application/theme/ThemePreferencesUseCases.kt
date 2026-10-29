package com.stratum.application.theme

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

/**
 * Observes persisted theme preferences. ViewModels use this instead of
 * [ThemePreferencesRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveThemePreferencesUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    operator fun invoke(): Flow<ThemePreferences> = repository.observe()
}

/** Persists the selected theme family (Tonal / Neo Brutalist). */
class SetThemeFamilyUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(family: ThemeFamily): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set theme family") {
            repository.setFamily(family)
        }

    private companion object {
        const val TAG = "SetThemeFamily"
    }
}

/** Persists appearance mode (System / Light / Dark). */
class SetColorModeUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(colorMode: ColorMode): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set color mode") {
            repository.setColorMode(colorMode)
        }

    private companion object {
        const val TAG = "SetColorMode"
    }
}

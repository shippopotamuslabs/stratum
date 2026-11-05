package com.stratum.application.theme

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

/**
 * Outcome of a theme preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface ThemePreferenceResult {
    data object Success : ThemePreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : ThemePreferenceResult
}

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
    suspend operator fun invoke(family: ThemeFamily): ThemePreferenceResult =
        try {
            repository.setFamily(family)
            ThemePreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set theme family: ${t.message}", t)
            ThemePreferenceResult.PersistenceFailed
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
    suspend operator fun invoke(colorMode: ColorMode): ThemePreferenceResult =
        try {
            repository.setColorMode(colorMode)
            ThemePreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set color mode: ${t.message}", t)
            ThemePreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetColorMode"
    }
}

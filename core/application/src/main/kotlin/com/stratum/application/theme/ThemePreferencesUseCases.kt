package com.stratum.application.theme

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

/**
 * Outcome of a theme preferences write (STR-243).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once. Coroutine
 * cancellation is not a branch: it propagates as cancellation.
 */
sealed interface ThemePreferenceResult {
    data object Success : ThemePreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ThemePreferenceResult
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
) {
    suspend operator fun invoke(family: ThemeFamily): ThemePreferenceResult =
        try {
            repository.setFamily(family)
            ThemePreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ThemePreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set theme family: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetThemeFamily"
    }
}

/** Persists appearance mode (System / Light / Dark). */
class SetColorModeUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(colorMode: ColorMode): ThemePreferenceResult =
        try {
            repository.setColorMode(colorMode)
            ThemePreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ThemePreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set color mode: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetColorMode"
    }
}

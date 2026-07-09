package com.stratum.domain.theme

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

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
    suspend operator fun invoke(family: ThemeFamily) {
        repository.setFamily(family)
    }
}

/** Persists appearance mode (System / Light / Dark). */
class SetColorModeUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(colorMode: ColorMode) {
        repository.setColorMode(colorMode)
    }
}

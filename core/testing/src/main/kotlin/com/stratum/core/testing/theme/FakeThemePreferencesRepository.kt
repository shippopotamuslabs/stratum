package com.stratum.core.testing.theme

import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import com.stratum.application.theme.ThemePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [ThemePreferencesRepository] for ViewModel and use-case tests. */
class FakeThemePreferencesRepository(
    initial: ThemePreferences = ThemePreferences(),
) : ThemePreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    val current: ThemePreferences
        get() = preferences.value

    fun seed(value: ThemePreferences) {
        preferences.value = value
    }

    override fun observe(): Flow<ThemePreferences> = preferences.asStateFlow()

    override suspend fun setFamily(family: ThemeFamily) {
        preferences.value = preferences.value.copy(family = family)
    }

    override suspend fun setColorMode(colorMode: ColorMode) {
        preferences.value = preferences.value.copy(colorMode = colorMode)
    }
}

package com.stratum.application.theme

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences

/**
 * Observes and updates persisted [ThemePreferences]. Implementations typically use
 * Jetpack DataStore; callers (app shell + Settings) must not block the main thread on writes.
 */
interface ThemePreferencesRepository {
    fun observe(): Flow<ThemePreferences>

    suspend fun setFamily(family: ThemeFamily)

    suspend fun setColorMode(colorMode: ColorMode)
}

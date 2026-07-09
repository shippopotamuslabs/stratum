package com.stratum.domain.theme

import kotlinx.coroutines.flow.Flow

/**
 * Observes and updates persisted [ThemePreferences]. Implementations typically use
 * Jetpack DataStore; callers (app shell + Settings) must not block the main thread on writes.
 */
interface ThemePreferencesRepository {
    fun observe(): Flow<ThemePreferences>

    suspend fun setFamily(family: ThemeFamily)

    suspend fun setColorMode(colorMode: ColorMode)
}

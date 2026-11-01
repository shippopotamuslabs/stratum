package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.theme.themePreferencesDataStore
import com.stratum.core.infrastructure.theme.toDomain
import com.stratum.core.infrastructure.theme.toRecord
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import com.stratum.application.theme.ThemePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [ThemePreferencesRepository]. Emits defaults (Tonal + System) until the first
 * successful read; unknown stored names map back to those defaults.
 */
@Singleton
class DataStoreThemePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemePreferencesRepository {

    override fun observe(): Flow<ThemePreferences> =
        context.themePreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun setFamily(family: ThemeFamily) {
        context.themePreferencesDataStore.updateData { current ->
            current.toDomain().copy(family = family).toRecord()
        }
    }

    override suspend fun setColorMode(colorMode: ColorMode) {
        context.themePreferencesDataStore.updateData { current ->
            current.toDomain().copy(colorMode = colorMode).toRecord()
        }
    }
}

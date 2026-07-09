package com.stratum.core.data.theme

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val themePrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [ThemePreferencesRecord]. Corrupt or empty files recover to defaults
 * (Tonal + System) so a bad write never blocks app launch theming.
 */
object ThemePreferencesRecordSerializer : Serializer<ThemePreferencesRecord> {

    override val defaultValue: ThemePreferencesRecord = ThemePreferencesRecord()

    override suspend fun readFrom(input: InputStream): ThemePreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            themePrefsJson.decodeFromString(ThemePreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: ThemePreferencesRecord, output: OutputStream) {
        val text = themePrefsJson.encodeToString(ThemePreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `theme_preferences.json`. */
internal val Context.themePreferencesDataStore by dataStore(
    fileName = "theme_preferences.json",
    serializer = ThemePreferencesRecordSerializer,
)

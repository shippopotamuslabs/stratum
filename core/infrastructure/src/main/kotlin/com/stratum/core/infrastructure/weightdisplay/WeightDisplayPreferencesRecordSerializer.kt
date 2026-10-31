package com.stratum.core.infrastructure.weightdisplay

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val weightDisplayPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [WeightDisplayPreferencesRecord]. Corrupt or empty files recover to
 * defaults (Lb) so a bad write never blocks weight display.
 */
object WeightDisplayPreferencesRecordSerializer : Serializer<WeightDisplayPreferencesRecord> {

    override val defaultValue: WeightDisplayPreferencesRecord = WeightDisplayPreferencesRecord()

    override suspend fun readFrom(input: InputStream): WeightDisplayPreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            weightDisplayPrefsJson.decodeFromString(WeightDisplayPreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: WeightDisplayPreferencesRecord, output: OutputStream) {
        val text = weightDisplayPrefsJson.encodeToString(WeightDisplayPreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `weight_display_preferences.json`. */
internal val Context.weightDisplayPreferencesDataStore by dataStore(
    fileName = "weight_display_preferences.json",
    serializer = WeightDisplayPreferencesRecordSerializer,
)

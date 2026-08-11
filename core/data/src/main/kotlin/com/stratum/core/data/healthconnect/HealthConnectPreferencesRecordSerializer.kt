package com.stratum.core.data.healthconnect

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val healthConnectPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

internal object HealthConnectPreferencesRecordSerializer : Serializer<HealthConnectPreferencesRecord> {
    override val defaultValue: HealthConnectPreferencesRecord = HealthConnectPreferencesRecord()

    override suspend fun readFrom(input: InputStream): HealthConnectPreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            healthConnectPrefsJson.decodeFromString(HealthConnectPreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: HealthConnectPreferencesRecord, output: OutputStream) {
        val text = healthConnectPrefsJson.encodeToString(HealthConnectPreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

internal val Context.healthConnectPreferencesDataStore by dataStore(
    fileName = "health_connect_preferences.json",
    serializer = HealthConnectPreferencesRecordSerializer,
)

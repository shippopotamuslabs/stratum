package com.stratum.core.infrastructure.dashboard

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val dashboardPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [DashboardPreferencesRecord]. Corrupt or empty files recover to defaults
 * (Muscles) so a bad write never blocks app launch.
 */
object DashboardPreferencesRecordSerializer : Serializer<DashboardPreferencesRecord> {

    override val defaultValue: DashboardPreferencesRecord = DashboardPreferencesRecord()

    override suspend fun readFrom(input: InputStream): DashboardPreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            dashboardPrefsJson.decodeFromString(DashboardPreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: DashboardPreferencesRecord, output: OutputStream) {
        val text = dashboardPrefsJson.encodeToString(DashboardPreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `dashboard_preferences.json`. */
internal val Context.dashboardPreferencesDataStore by dataStore(
    fileName = "dashboard_preferences.json",
    serializer = DashboardPreferencesRecordSerializer,
)

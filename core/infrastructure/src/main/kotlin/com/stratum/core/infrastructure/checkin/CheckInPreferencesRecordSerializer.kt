package com.stratum.core.infrastructure.checkin

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val checkInPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [CheckInPreferencesRecord]. Corrupt or empty files recover to defaults
 * (enabled + no pending) so a bad write never blocks app launch.
 */
object CheckInPreferencesRecordSerializer : Serializer<CheckInPreferencesRecord> {

    override val defaultValue: CheckInPreferencesRecord = CheckInPreferencesRecord()

    override suspend fun readFrom(input: InputStream): CheckInPreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            checkInPrefsJson.decodeFromString(CheckInPreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: CheckInPreferencesRecord, output: OutputStream) {
        val text = checkInPrefsJson.encodeToString(CheckInPreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `check_in_preferences.json`. */
internal val Context.checkInPreferencesDataStore by dataStore(
    fileName = "check_in_preferences.json",
    serializer = CheckInPreferencesRecordSerializer,
)

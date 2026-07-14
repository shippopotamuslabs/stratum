package com.stratum.core.data.program

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val programsUiPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [ProgramsUiPreferencesRecord]. Corrupt or empty files recover to defaults
 * (no expanded card) so a bad write never blocks app launch.
 */
object ProgramsUiPreferencesRecordSerializer : Serializer<ProgramsUiPreferencesRecord> {

    override val defaultValue: ProgramsUiPreferencesRecord = ProgramsUiPreferencesRecord()

    override suspend fun readFrom(input: InputStream): ProgramsUiPreferencesRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            programsUiPrefsJson.decodeFromString(ProgramsUiPreferencesRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: ProgramsUiPreferencesRecord, output: OutputStream) {
        val text = programsUiPrefsJson.encodeToString(ProgramsUiPreferencesRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `programs_ui_preferences.json`. */
internal val Context.programsUiPreferencesDataStore by dataStore(
    fileName = "programs_ui_preferences.json",
    serializer = ProgramsUiPreferencesRecordSerializer,
)

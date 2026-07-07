package com.stratum.core.data.workoutoverlay

import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import android.content.Context
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * kotlinx.serialization JSON encoder/decoder for the [WorkoutOverlayRecord] DataStore.
 *
 * [Json] config matches the existing seed-catalog convention (`LiftCatalogSeeder`):
 * - [Json.ignoreUnknownKeys] — forward-compatible with new fields.
 * - [Json.isLenient] — tolerate small JSON quirks.
 * - [Json.coerceInputValues] — unknown enum names fall back to defaults instead of aborting.
 */
private val overlayJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Custom [Serializer] for the typed `DataStore<WorkoutOverlayRecord>`. A corrupt, empty, or
 * partially-written file is recovered to [defaultValue] rather than crashing the app — the
 * overlay is transient session state, so dropping it on read failure is always safe.
 */
object WorkoutOverlayRecordSerializer : Serializer<WorkoutOverlayRecord> {

    override val defaultValue: WorkoutOverlayRecord = WorkoutOverlayRecord()

    override suspend fun readFrom(input: InputStream): WorkoutOverlayRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            overlayJson.decodeFromString(WorkoutOverlayRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: WorkoutOverlayRecord, output: OutputStream) {
        val text = overlayJson.encodeToString(WorkoutOverlayRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

/** Session-scoped typed DataStore delegate. One file, `workout_overlay.json`. */
internal val Context.workoutOverlayDataStore by dataStore(
    fileName = "workout_overlay.json",
    serializer = WorkoutOverlayRecordSerializer,
)

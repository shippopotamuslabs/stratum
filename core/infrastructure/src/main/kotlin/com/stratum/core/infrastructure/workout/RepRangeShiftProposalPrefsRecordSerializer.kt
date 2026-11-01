package com.stratum.core.infrastructure.workout

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val repRangeShiftProposalPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [RepRangeShiftProposalPrefsRecord]. Corrupt or empty files recover to
 * empty dismissals so a bad write never blocks workout-complete analysis.
 */
object RepRangeShiftProposalPrefsRecordSerializer : Serializer<RepRangeShiftProposalPrefsRecord> {

    override val defaultValue: RepRangeShiftProposalPrefsRecord = RepRangeShiftProposalPrefsRecord()

    override suspend fun readFrom(input: InputStream): RepRangeShiftProposalPrefsRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            repRangeShiftProposalPrefsJson.decodeFromString(
                RepRangeShiftProposalPrefsRecord.serializer(),
                text,
            )
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: RepRangeShiftProposalPrefsRecord, output: OutputStream) {
        val text =
            repRangeShiftProposalPrefsJson.encodeToString(
                RepRangeShiftProposalPrefsRecord.serializer(),
                t,
            )
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `rep_range_shift_proposal_prefs.json`. */
internal val Context.repRangeShiftProposalPreferencesDataStore by dataStore(
    fileName = "rep_range_shift_proposal_prefs.json",
    serializer = RepRangeShiftProposalPrefsRecordSerializer,
)

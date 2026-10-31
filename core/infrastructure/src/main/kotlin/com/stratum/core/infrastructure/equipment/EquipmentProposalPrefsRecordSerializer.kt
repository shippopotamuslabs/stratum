package com.stratum.core.infrastructure.equipment

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

private val equipmentProposalPrefsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * JSON serializer for [EquipmentProposalPrefsRecord]. Corrupt or empty files recover to
 * empty dismissals so a bad write never blocks workout-complete analysis.
 */
object EquipmentProposalPrefsRecordSerializer : Serializer<EquipmentProposalPrefsRecord> {

    override val defaultValue: EquipmentProposalPrefsRecord = EquipmentProposalPrefsRecord()

    override suspend fun readFrom(input: InputStream): EquipmentProposalPrefsRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            equipmentProposalPrefsJson.decodeFromString(
                EquipmentProposalPrefsRecord.serializer(),
                text,
            )
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: EquipmentProposalPrefsRecord, output: OutputStream) {
        val text =
            equipmentProposalPrefsJson.encodeToString(
                EquipmentProposalPrefsRecord.serializer(),
                t,
            )
        output.bufferedWriter().use { it.write(text) }
    }
}

/** App-wide typed DataStore — one file, `equipment_proposal_prefs.json`. */
internal val Context.equipmentProposalPreferencesDataStore by dataStore(
    fileName = "equipment_proposal_prefs.json",
    serializer = EquipmentProposalPrefsRecordSerializer,
)

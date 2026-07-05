package com.stratum.core.inference

import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.ImportTimestamps
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ImportedHistoryDocument(
    val sessions: List<ImportedSessionDto> = emptyList(),
    val sets: List<ImportedSetDto> = emptyList(),
)

@Serializable
private data class ImportedSessionDto(
    val id: String,
    val startedAt: String,
    val endedAt: String? = null,
    val sourceApp: String? = null,
)

@Serializable
private data class ImportedSetDto(
    val id: String,
    val sessionId: String,
    val exerciseNameRaw: String,
    val liftId: String? = null,
    val setIndex: Int,
    val weightAmount: Double,
    @SerialName("weightUnit") val weightUnitName: String = "Lb",
    val reps: Int,
    val rir: Int? = null,
    val completedAt: String,
)

internal object ImportedHistoryJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): Pair<List<TrainingSession>, List<LoggedSet>> {
        val document = json.decodeFromString<ImportedHistoryDocument>(payload)
        val sessions =
            document.sessions.map { dto ->
                ImportHistoryTrainingMapper.parseSession(
                    id = dto.id,
                    startedAt = ImportTimestamps.parse(dto.startedAt),
                    endedAt = dto.endedAt?.let(ImportTimestamps::parse),
                    sourceApp = dto.sourceApp,
                )
            }
        val sets =
            document.sets.map { dto ->
                ImportHistoryTrainingMapper.parseSet(
                    id = LoggedSetId(dto.id),
                    sessionId = TrainingSessionId(dto.sessionId),
                    exerciseNameRaw = dto.exerciseNameRaw,
                    setIndex = dto.setIndex,
                    weight = Weight(dto.weightAmount, Weight.WeightUnit.parse(dto.weightUnitName)),
                    reps = dto.reps,
                    rir = dto.rir,
                    completedAt = ImportTimestamps.parse(dto.completedAt),
                )
            }
        return sessions to sets
    }
}

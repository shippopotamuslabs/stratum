package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.db.TrainingSessionEntity
import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.identity.HistoricalSessionId
import java.time.Instant

internal fun HistoricalSession.toImportTrainingSessionEntity(): TrainingSessionEntity =
    ImportHistoryTrainingMapper.toTrainingSession(this).toEntity()

internal fun TrainingSessionEntity.toHistoricalSession(): HistoricalSession =
    HistoricalSession(
        id = HistoricalSessionId(id.value),
        startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
        endedAt = endedAtEpochMillis?.let(Instant::ofEpochMilli),
        sourceApp = sourceApp,
    )

internal fun importHistoryProgramEntity(): ProgramEntity =
    ProgramEntity(
        id = ImportHistoryTrainingMapper.programId,
        name = "Imported History",
        createdAt = Instant.EPOCH.toString(),
        cycleLength = 1,
    )

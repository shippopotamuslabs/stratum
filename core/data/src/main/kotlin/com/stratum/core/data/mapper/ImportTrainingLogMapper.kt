package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramEntity
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import java.time.Instant

internal fun importHistoryProgramEntity(): ProgramEntity =
    ProgramEntity(
        id = ImportHistoryTrainingMapper.programId,
        name = "Imported History",
        createdAt = Instant.EPOCH.toString(),
        cycleLength = 1,
        isActive = false,
    )

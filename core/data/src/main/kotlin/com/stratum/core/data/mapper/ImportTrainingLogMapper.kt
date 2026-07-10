package com.stratum.core.data.mapper

import com.stratum.core.data.db.ProgramEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import java.time.Instant

internal fun importHistoryProgramEntity(): ProgramEntity =
    ProgramEntity(
        id = ImportHistoryTrainingMapper.programId,
        name = "Imported History",
        createdAt = Instant.EPOCH.toString(),
        cycleLength = 1,
        isActive = false,
        // Sentinel FK parent — clean until user-facing programs are the sync unit of truth.
        updatedAtEpochMillis = 0L,
        pendingSync = SyncFlags.CLEAN,
    )

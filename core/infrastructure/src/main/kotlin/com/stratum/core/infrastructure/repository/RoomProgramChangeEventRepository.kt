package com.stratum.core.infrastructure.repository

import com.stratum.core.infrastructure.db.ProgramChangeEventDao
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.core.infrastructure.sync.SyncTouch
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.SkippedProgramChangeEventRow
import com.stratum.application.training.ProgramChangeEventRead
import com.stratum.application.training.ProgramChangeEventRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomProgramChangeEventRepository @Inject constructor(
    private val dao: ProgramChangeEventDao,
    private val syncTouch: SyncTouch,
) : ProgramChangeEventRepository {
    override suspend fun append(event: ProgramChangeEvent) {
        syncTouch.withWriteLock {
            dao.upsert(event.toEntity())
            syncTouch.markDirty()
        }
    }

    override suspend fun getEventsForProgram(
        programId: ProgramId,
        since: Instant?,
    ): ProgramChangeEventRead {
        val entities = dao.getForProgram(programId)
        val events = mutableListOf<ProgramChangeEvent>()
        val skippedRows = mutableListOf<SkippedProgramChangeEventRow>()
        entities
            .filter { entity -> since == null || entity.occurredAtEpochMillis >= since.toEpochMilli() }
            .forEach { entity ->
                // toDomain() returns null for any undecodable row (uniform contract).
                // The skip is returned as a diagnostic value for the caller to
                // drain; repository implementations never log (CODE_STYLE_RULES).
                val domain = entity.toDomain()
                if (domain != null) {
                    events += domain
                } else {
                    skippedRows += SkippedProgramChangeEventRow(
                        id = entity.id,
                        eventType = entity.eventType,
                    )
                }
            }
        return ProgramChangeEventRead(events = events, skippedRows = skippedRows)
    }
}

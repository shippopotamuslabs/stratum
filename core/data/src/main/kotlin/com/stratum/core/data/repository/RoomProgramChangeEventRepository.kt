package com.stratum.core.data.repository

import android.util.Log
import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventRepository
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

    override suspend fun getEventsForProgram(programId: ProgramId, since: Instant?): List<ProgramChangeEvent> {
        val entities = dao.getForProgram(programId)
        return entities
            .filter { entity -> since == null || entity.occurredAtEpochMillis >= since.toEpochMilli() }
            // toDomain() returns null for any undecodable row (uniform contract); skipped
            // rows are logged so history gaps stay observable instead of silent.
            .mapNotNull { entity ->
                entity.toDomain() ?: run {
                    Log.w(
                        TAG,
                        "Skipping undecodable program_change_event " +
                            "id=${entity.id.value} type=${entity.eventType}",
                    )
                    null
                }
            }
    }

    private companion object {
        const val TAG = "ProgramChangeEvents"
    }
}

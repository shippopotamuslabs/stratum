package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftNoteEntity
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftCatalogNote
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.onboarding.identity.GymPreferenceId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [LiftRepository]. Reads compose lift rows with the
 * equipment catalog to resolve **effective** [Lift.incrementTiers].
 */
class RoomLiftRepository @Inject constructor(
    private val database: StratumDatabase,
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
    private val equipmentDao: EquipmentDao,
    private val equipmentRepository: RoomEquipmentRepository,
    private val gymPreferenceDao: GymPreferenceDao,
    private val syncTouch: SyncTouch,
) : LiftRepository {

    override fun observeLifts(): Flow<List<Lift>> =
        combine(
            liftDao.observeLifts(),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override suspend fun getLiftsByIds(ids: Collection<LiftId>): List<Lift> {
        if (ids.isEmpty()) return emptyList()
        val distinctIds = ids.distinct()
        val rows = liftDao.getLiftsByIds(distinctIds)
        if (rows.isEmpty()) return emptyList()
        val equipmentById = equipmentSnapshotById()
        return rows.map { it.toDomain(equipmentById) }
    }

    override fun observeEnabledLifts(): Flow<List<Lift>> =
        combine(
            liftDao.observeEnabledLifts(),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeLifts(isCustom: Boolean): Flow<List<Lift>> =
        combine(
            liftDao.observeLiftsByCustom(isCustom),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeLiftsContainingPattern(pattern: MovementPattern): Flow<List<Lift>> =
        combine(
            liftDao.observeLiftsByPattern(1 shl pattern.ordinal),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeSelectedPreset(): Flow<GymPreset?> =
        gymPreferenceDao.observe().map { it?.selectedPreset }

    override suspend fun applyPreset(preset: GymPreset) {
        val ids = liftDao.idsForPresetTag(tagPattern = "%,${preset.name},%")
        if (ids.isEmpty()) return
        syncTouch.withWriteLock {
            liftDao.enableAll(ids, updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun setLiftEnabled(id: LiftId, enabled: Boolean) {
        syncTouch.withWriteLock {
            liftDao.setEnabled(id, enabled, updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun enableLifts(ids: Set<LiftId>) {
        if (ids.isEmpty()) return
        syncTouch.withWriteLock {
            liftDao.enableAll(ids.toList(), updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun setSelectedPreset(preset: GymPreset?) {
        syncTouch.withWriteLock {
            val current = gymPreferenceDao.getOrNull()
            val now = System.currentTimeMillis()
            gymPreferenceDao.upsert(
                GymPreferenceEntity(
                    id = GymPreferenceId.Default,
                    selectedPreset = preset,
                    usedHistoricalImport = current?.usedHistoricalImport ?: false,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            )
            syncTouch.cancelDelete(SyncCollection.GymPreference, GymPreferenceId.Default.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun setUsedHistoricalImport(used: Boolean) {
        syncTouch.withWriteLock {
            val current = gymPreferenceDao.getOrNull()
            val now = System.currentTimeMillis()
            gymPreferenceDao.upsert(
                GymPreferenceEntity(
                    id = GymPreferenceId.Default,
                    selectedPreset = current?.selectedPreset,
                    usedHistoricalImport = used,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            )
            syncTouch.cancelDelete(SyncCollection.GymPreference, GymPreferenceId.Default.value)
            syncTouch.markDirty()
        }
    }

    override fun observeUsedHistoricalImport(): Flow<Boolean> =
        gymPreferenceDao.observe().map { it?.usedHistoricalImport ?: false }

    override suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId {
        val id = LiftId(UUID.randomUUID().toString())
        val now = System.currentTimeMillis()
        // One write lock + one Room transaction: free-text equipment create and lift insert
        // must not leave an orphan equipment row if we crash mid-way. Public
        // findOrCreateByName takes its own lock (mutex is not reentrant), so use the
        // unlocked helper while we already hold the coordinator lock.
        syncTouch.withWriteLock {
            database.withTransaction {
                val equipmentId =
                    definition.equipmentId
                        ?: definition.equipmentName
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { name ->
                                // find-or-create: reuse existing (incl. seed aliases); mint only
                                // when unknown. createTiers apply only on first insert.
                                val createTiers =
                                    definition.equipmentCreateIncrement?.let {
                                        listOf(singleOpenTier(it))
                                    }
                                equipmentRepository.findOrCreateByNameUnlocked(
                                    name = name,
                                    nowMillis = now,
                                    createTiers = createTiers,
                                )
                            }
                        ?: definition.increment?.let { inc ->
                            // STR-82/84: per-lift overrides are gone. A stub lift that needs
                            // its own bands gets a visible "<name> Stack" equipment clone.
                            equipmentRepository.createStackCloneUnlocked(
                                baseName = definition.name,
                                tiers = listOf(singleOpenTier(inc)),
                                nowMillis = now,
                            )
                        }
                val lift =
                    Lift(
                        id = id,
                        name = definition.name,
                        movementPatterns = definition.movementPatterns,
                        primaryMuscles = definition.primaryMuscles,
                        secondaryMuscles = definition.secondaryMuscles,
                        incrementTiers = emptyList(),
                        presetTags = emptySet(),
                        equipmentId = equipmentId,
                        usesEquipmentDefaultIncrements = true,
                        enabled = true,
                        isCustom = true,
                        muscleLengthBias = definition.muscleLengthBias,
                        fatigueProfile = definition.fatigueProfile,
                        loadType = definition.loadType,
                        defaultRestSeconds = definition.defaultRestSeconds,
                    )
                syncTouch.cancelDelete(SyncCollection.Lifts, id.value)
                liftDao.upsert(lift.toEntity(now))
            }
            syncTouch.markDirty()
        }
        return id
    }

    override suspend fun setLiftEquipment(id: LiftId, equipmentId: EquipmentId?, loadType: LoadType) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                liftDao.setEquipmentIdAndLoadType(
                    id = id,
                    equipmentId = equipmentId,
                    loadType = loadType,
                    updatedAtEpochMillis = now,
                )
                // Reassigned lifts re-inherit the new equipment's adder stops; a stale
                // lift-scoped micro override must not survive the move (STR-84).
                liftDao.clearMicroOptions(id = id, updatedAtEpochMillis = now)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun updateLiftMuscles(
        id: LiftId,
        primaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
        secondaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
    ) {
        syncTouch.withWriteLock {
            liftDao.updateMuscles(
                id = id,
                primaryBitmask = primaryMuscles.toBitmask(),
                secondaryBitmask = secondaryMuscles.toBitmask(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun updateCustomLiftDetails(
        id: LiftId,
        details: CustomLiftDetails,
    ) {
        syncTouch.withWriteLock {
            liftDao.updateCustomDetails(
                id = id,
                movementPatternsBitmask = details.movementPatterns.toBitmask(),
                muscleLengthBias = details.muscleLengthBias,
                fatigueProfile = details.fatigueProfile,
                loadType = details.loadType,
                equipmentId = details.equipmentId,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun setDefaultRestSeconds(id: LiftId, restSeconds: Int?) {
        syncTouch.withWriteLock {
            liftDao.setDefaultRestSeconds(
                id = id,
                restSeconds = restSeconds,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun setPinnedNote(id: LiftId, note: String?) {
        // Scalar writes go through the list path so the note table and the legacy
        // mirror stay consistent (reads prefer the table). Preserves the first-row
        // id; a blank note clears the list.
        val clean = sanitizeLiftNoteBody(note)
        if (clean == null) {
            setPinnedNotes(id, emptyList())
            return
        }
        val existingId = database.liftNoteDao().idsForLift(id).firstOrNull()
        setPinnedNotes(id, listOf(CatalogNoteInput(id = existingId, body = clean)))
    }

    override suspend fun getPinnedNotes(id: LiftId): List<LiftCatalogNote> {
        val rows = database.liftNoteDao().getForLift(id)
        if (rows.isNotEmpty()) return rows.map { it.toCatalogNote() }
        // Client lazy-migrate for lifts that predate the v5 backfill (or were
        // written remotely by an old client): persist the legacy column as the
        // position-0 row so later reads and sync converge on the new shape.
        if (migrateLegacyCatalogPin(id)) {
            return database.liftNoteDao().getForLift(id).map { it.toCatalogNote() }
        }
        return emptyList()
    }

    /**
     * Returns true when a legacy-only pin was persisted as the position-0 row.
     * The empty-child check, parent lookup, authoritative pin read, and insert
     * all happen inside one write-locked transaction: a concurrent clear or
     * remote update that commits first leaves nothing eligible, so a stale
     * pre-lock capture can never overwrite its newer value. A second
     * concurrent migrate sees the first one's row and skips (no id churn, no
     * phantom tombstone), and the in-lock parent check keeps a mid-migrate
     * delete from violating the note-table FK out of a read path.
     */
    private suspend fun migrateLegacyCatalogPin(id: LiftId): Boolean {
        val now = System.currentTimeMillis()
        var migrated = false
        syncTouch.withWriteLock {
            database.withTransaction {
                if (database.liftNoteDao().getForLift(id).isEmpty()) {
                    val current = liftDao.getById(id)?.pinnedNote?.takeIf { it.isNotBlank() }
                    if (current != null) {
                        val body = sanitizeLiftNoteBody(current) ?: return@withTransaction
                        database.liftNoteDao().upsertAll(
                            listOf(
                                LiftNoteEntity(
                                    id = LiftCatalogNoteId(UUID.randomUUID().toString()),
                                    liftId = id,
                                    body = body,
                                    position = 0,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now,
                                    pendingSync = SyncFlags.PENDING_UPSERT,
                                ),
                            ),
                        )
                        liftDao.setPinnedNote(id = id, note = body, updatedAtEpochMillis = now)
                        migrated = true
                    }
                }
            }
            if (migrated) syncTouch.markDirty()
        }
        return migrated
    }

    override suspend fun setPinnedNotes(id: LiftId, notes: List<CatalogNoteInput>) {
        if (liftDao.getById(id) == null) return
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val currentIds = database.liftNoteDao().idsForLift(id).toSet()
                val minted =
                    notes.mapNotNull { input ->
                        sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
                    }.mapIndexed { index, (input, body) ->
                        LiftNoteEntity(
                            id = input.id?.takeIf { it in currentIds }
                                ?: LiftCatalogNoteId(UUID.randomUUID().toString()),
                            liftId = id,
                            body = body,
                            position = index,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            pendingSync = SyncFlags.PENDING_UPSERT,
                        )
                    }
                database.liftNoteDao().deleteForLift(id)
                if (minted.isNotEmpty()) {
                    database.liftNoteDao().upsertAll(minted)
                }
                val retained = minted.map { it.id }.toSet()
                val removed = currentIds.filterNot { it in retained }.map { it.value }
                if (removed.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.LiftCatalogNotes, removed, now)
                }
                // Legacy mirror: old readers (mappers, sync) see the first note until
                // the column is retired.
                liftDao.setPinnedNote(
                    id = id,
                    note = minted.firstOrNull()?.body,
                    updatedAtEpochMillis = now,
                )
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteLifts(ids: Collection<LiftId>) {
        if (ids.isEmpty()) return
        val distinct = ids.distinct()
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                // Tombstone note rows before the CASCADE delete clears them locally.
                val noteIds =
                    distinct.flatMap { id ->
                        database.liftNoteDao().idsForLift(id).map { it.value }
                    }
                for (id in distinct) {
                    liftDao.deleteById(id)
                }
                if (noteIds.isNotEmpty()) {
                    syncTouch.enqueueDelete(SyncCollection.LiftCatalogNotes, noteIds, now)
                }
                syncTouch.enqueueDelete(
                    SyncCollection.Lifts,
                    distinct.map { it.value },
                    now,
                )
            }
        }
    }

    private fun observeEquipmentById(): Flow<Map<EquipmentId, Equipment>> =
        combine(
            equipmentDao.observeAll(),
            incrementTierDao.observeAllEquipmentTiers(),
        ) { equipmentRows, allEquipmentTiers ->
            val tiersByEquipment = allEquipmentTiers.groupBy { it.equipmentId }
            equipmentRows.associate { row ->
                row.id to row.toDomain(tiersByEquipment[row.id].orEmpty())
            }
        }

    /** One-shot equipment map for suspend reads (same resolution as [observeEquipmentById]). */
    private suspend fun equipmentSnapshotById(): Map<EquipmentId, Equipment> {
        val equipmentRows = equipmentDao.getAll()
        val tiersByEquipment = incrementTierDao.getAllEquipmentTiers().groupBy { it.equipmentId }
        return equipmentRows.associate { row ->
            row.id to row.toDomain(tiersByEquipment[row.id].orEmpty())
        }
    }

    private fun singleOpenTier(increment: Weight): IncrementTier =
        IncrementTier(Weight(0.0, increment.unit), null, increment)
}

internal fun LiftNoteEntity.toCatalogNote(): LiftCatalogNote =
    LiftCatalogNote(
        id = id,
        liftId = liftId,
        body = body,
        position = position,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

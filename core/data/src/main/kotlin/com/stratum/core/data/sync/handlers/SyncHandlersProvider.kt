package com.stratum.core.data.sync.handlers

import androidx.room.withTransaction
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftNoteDao
import com.stratum.core.data.db.LiftSessionNoteDao
import com.stratum.core.data.db.LiftSessionNoteEntity
import com.stratum.core.data.db.LiftSetDao
import com.stratum.core.data.db.WorkoutLiftNoteDao
import com.stratum.core.data.db.OnboardingProgressDao
import com.stratum.core.data.db.ProgramChangeEventDao
import com.stratum.core.data.db.ProgramDao
import com.stratum.core.data.db.SpecializationPreferenceDao
import com.stratum.core.data.db.TrainingLoggedSetDao
import com.stratum.core.data.db.TrainingSessionDao
import com.stratum.core.data.db.UserProfileDao
import com.stratum.core.data.db.WorkoutDao
import com.stratum.core.data.db.WorkoutLiftDao
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.FirestoreSyncRemoteApplyPolicy
import com.stratum.core.data.sync.LocalSyncMeta
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncTouch
import com.stratum.core.data.sync.appliers.IncrementTierRemoteApplier
import com.stratum.core.data.sync.appliers.LiftRemoteApplier
import com.stratum.core.data.sync.appliers.LoggedSetRemoteApplier
import com.stratum.core.data.sync.appliers.ProgramRemoteApplier
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.onboarding.identity.GymPreferenceId
import com.stratum.domain.onboarding.identity.OnboardingProgressId
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId
import com.stratum.domain.onboarding.identity.UserProfileId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the full set of [SyncCollectionHandler]s from DAOs + codecs.
 * Generic collections (including programs / logged sets) use [entityHandler]; only lifts need a
 * custom encode path for catalog overlays.
 */
@Singleton
@Suppress("LongParameterList")
class SyncHandlersProvider @Inject constructor(
    private val database: StratumDatabase,
    private val syncTouch: SyncTouch,
    private val userProfileDao: UserProfileDao,
    private val onboardingDao: OnboardingProgressDao,
    private val gymDao: GymPreferenceDao,
    private val specializationDao: SpecializationPreferenceDao,
    private val equipmentDao: EquipmentDao,
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
    private val programDao: ProgramDao,
    private val workoutDao: WorkoutDao,
    private val workoutLiftDao: WorkoutLiftDao,
    private val liftSetDao: LiftSetDao,
    private val sessionDao: TrainingSessionDao,
    private val liftSessionNoteDao: LiftSessionNoteDao,
    private val liftNoteDao: LiftNoteDao,
    private val workoutLiftNoteDao: WorkoutLiftNoteDao,
    private val loggedSetDao: TrainingLoggedSetDao,
    private val programChangeEventDao: ProgramChangeEventDao,
    private val encoders: SyncEntityEncoders,
    private val decoders: SyncEntityDecoders,
    private val liftRemoteApplier: LiftRemoteApplier,
    private val programRemoteApplier: ProgramRemoteApplier,
    private val loggedSetRemoteApplier: LoggedSetRemoteApplier,
) {
    private val incrementTierRemoteApplier =
        IncrementTierRemoteApplier(
            incrementTierDao = incrementTierDao,
            liftDao = liftDao,
            equipmentDao = equipmentDao,
            decoders = decoders,
        )

    fun all(): List<SyncCollectionHandler> =
        listOf(
            userProfileHandler(),
            onboardingHandler(),
            gymPreferenceHandler(),
            specializationHandler(),
            equipmentHandler(),
            liftsHandler(),
            liftCatalogNotesHandler(),
            incrementTiersHandler(),
            programsHandler(),
            workoutsHandler(),
            workoutLiftsHandler(),
            workoutLiftNotesHandler(),
            liftSetsHandler(),
            trainingSessionsHandler(),
            liftSessionNotesHandler(),
            loggedSetsHandler(),
            programChangeEventsHandler(),
        )

    private fun userProfileHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.UserProfile,
            pending = { userProfileDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                userProfileDao.getOrNull(UserProfileId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { userProfileDao.deleteById(UserProfileId(it)) },
            markSynced = { id, at -> userProfileDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::userProfile,
            decodeUpsert = { id, data ->
                decoders.userProfile(id, data)?.let { userProfileDao.upsert(it); true } ?: false
            },
        )

    private fun onboardingHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.OnboardingProgress,
            pending = { onboardingDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                onboardingDao.getOrNull(OnboardingProgressId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { onboardingDao.deleteById(OnboardingProgressId(it)) },
            markSynced = { id, at -> onboardingDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::onboarding,
            decodeUpsert = { id, data ->
                decoders.onboarding(id, data)?.let { onboardingDao.upsert(it); true } ?: false
            },
        )

    private fun gymPreferenceHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.GymPreference,
            pending = { gymDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                gymDao.getOrNull(GymPreferenceId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { gymDao.deleteById(GymPreferenceId(it)) },
            markSynced = { id, at -> gymDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::gymPreference,
            decodeUpsert = { id, data ->
                decoders.gymPreference(id, data)?.let { gymDao.upsert(it); true } ?: false
            },
        )

    private fun specializationHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.SpecializationPreference,
            pending = { specializationDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                specializationDao.getById(SpecializationPreferenceId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { specializationDao.deleteById(SpecializationPreferenceId(it)) },
            markSynced = { id, at -> specializationDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::specialization,
            decodeUpsert = { id, data ->
                decoders.specialization(id, data)?.let {
                    specializationDao.upsertAll(listOf(it)); true
                } ?: false
            },
        )

    private fun equipmentHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.Equipment,
            pending = { equipmentDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                equipmentDao.getById(EquipmentId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { equipmentDao.deleteById(EquipmentId(it)) },
            markSynced = { id, at -> equipmentDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::equipment,
            decodeUpsert = { id, data ->
                decoders.equipment(id, data)?.let { equipmentDao.upsert(it); true } ?: false
            },
        )

    /** Custom encode path for catalog overlays vs full custom documents. */
    private fun liftsHandler(): SyncCollectionHandler =
        GenericSyncHandler(
            collection = SyncCollection.Lifts,
            loadPending = { limit ->
                liftDao.getPending(limit).map { entity ->
                    PendingSyncRow(
                        entity.id.value,
                        entity.updatedAtEpochMillis,
                        liftRemoteApplier.encode(entity),
                    )
                }
            },
            loadLocalMeta = { id ->
                liftDao.getById(LiftId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            deleteById = { id -> liftDao.deleteById(LiftId(id)) },
            markSynced = { id, at -> liftDao.markSyncedIfUnchanged(id, at) },
            upsertRemote = liftRemoteApplier::upsertFromRemote,
            checkHasPending = { liftDao.getPending(1).isNotEmpty() },
        )

    private fun liftCatalogNotesHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.LiftCatalogNotes,
            pending = { liftNoteDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                liftNoteDao.getById(LiftCatalogNoteId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { deleteCatalogNoteAndRefreshMirror(LiftCatalogNoteId(it)) },
            markSynced = { id, at -> liftNoteDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::liftCatalogNote,
            decodeUpsert = { id, data ->
                decoders.liftCatalogNote(id, data)?.let { liftNoteDao.upsertAll(listOf(it)); true } ?: false
            },
        )

    /**
     * Applies a remote catalog-note tombstone and recomputes the parent legacy
     * mirror from the remaining rows (clearing it when none remain), in one
     * transaction. Without the mirror update, a stale mirror plus the
     * lazy-migrate read fallback would recreate the deleted row and re-upload
     * it. The mirror write marks the parent dirty so the clearing propagates.
     */
    private suspend fun deleteCatalogNoteAndRefreshMirror(id: LiftCatalogNoteId) {
        val row = liftNoteDao.getById(id) ?: return
        database.withTransaction {
            liftNoteDao.deleteById(id)
            val remaining = liftNoteDao.getForLift(row.liftId)
            liftDao.setPinnedNote(
                id = row.liftId,
                note = remaining.firstOrNull()?.body,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Workout-slot counterpart of [deleteCatalogNoteAndRefreshMirror]: a remote
     * note tombstone must not leave a mirror that the lazy-migrate fallback
     * would treat as legacy-only data.
     */
    private suspend fun deleteWorkoutNoteAndRefreshMirror(id: WorkoutLiftNoteId) {
        val row = workoutLiftNoteDao.getById(id) ?: return
        database.withTransaction {
            workoutLiftNoteDao.deleteById(id)
            val remaining = workoutLiftNoteDao.getForWorkoutLift(row.workoutLiftId)
            workoutLiftDao.setPinnedNote(
                id = row.workoutLiftId,
                note = remaining.firstOrNull()?.body,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun incrementTiersHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.IncrementTiers,
            pending = { incrementTierDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                incrementTierDao.getById(IncrementTierId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { incrementTierDao.deleteById(IncrementTierId(it)) },
            markSynced = { id, at -> incrementTierDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::incrementTier,
            decodeUpsert = incrementTierRemoteApplier::upsertFromRemote,
        )

    private fun programsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.Programs,
            pending = { programDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                programDao.getById(ProgramId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { programDao.deleteById(ProgramId(it)) },
            markSynced = { id, at -> programDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::program,
            decodeUpsert = programRemoteApplier::upsertFromRemote,
        )

    private fun workoutsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.Workouts,
            pending = { workoutDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                workoutDao.getById(WorkoutId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { workoutDao.deleteById(WorkoutId(it)) },
            markSynced = { id, at -> workoutDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::workout,
            decodeUpsert = { id, data ->
                decoders.workout(id, data)?.let { workoutDao.upsertAll(listOf(it)); true } ?: false
            },
        )

    private fun workoutLiftsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.WorkoutLifts,
            pending = { workoutLiftDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                workoutLiftDao.getById(WorkoutLiftId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { workoutLiftDao.deleteById(WorkoutLiftId(it)) },
            markSynced = { id, at -> workoutLiftDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::workoutLift,
            decodeUpsert = { id, data ->
                val decoded = decoders.workoutLift(id, data) ?: return@entityHandler false
                val existing = workoutLiftDao.getById(WorkoutLiftId(id))
                val merged =
                    if (!data.containsKey("pinned_note") && existing != null) {
                        decoded.copy(pinnedNote = existing.pinnedNote)
                    } else {
                        decoded
                    }
                workoutLiftDao.upsertAll(listOf(merged))
                true
            },
        )

    private fun workoutLiftNotesHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.WorkoutLiftNotes,
            pending = { workoutLiftNoteDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                workoutLiftNoteDao.getById(WorkoutLiftNoteId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { deleteWorkoutNoteAndRefreshMirror(WorkoutLiftNoteId(it)) },
            markSynced = { id, at -> workoutLiftNoteDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::workoutLiftNote,
            decodeUpsert = { id, data ->
                decoders.workoutLiftNote(id, data)?.let { workoutLiftNoteDao.upsertAll(listOf(it)); true } ?: false
            },
        )

    private fun liftSetsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.LiftSets,
            pending = { liftSetDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                liftSetDao.getById(LiftSetId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { liftSetDao.deleteById(LiftSetId(it)) },
            markSynced = { id, at -> liftSetDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::liftSet,
            decodeUpsert = { id, data ->
                decoders.liftSet(id, data)?.let { liftSetDao.upsertAll(listOf(it)); true } ?: false
            },
        )

    private fun trainingSessionsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.TrainingSessions,
            pending = { sessionDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                sessionDao.getById(TrainingSessionId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { sessionDao.deleteById(TrainingSessionId(it)) },
            markSynced = { id, at -> sessionDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::trainingSession,
            decodeUpsert = { id, data ->
                decoders.trainingSession(id, data)?.let { sessionDao.upsert(it); true } ?: false
            },
        )

    private fun liftSessionNotesHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.LiftSessionNotes,
            pending = { liftSessionNoteDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                liftSessionNoteDao.getById(LiftSessionNoteId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { liftSessionNoteDao.deleteById(LiftSessionNoteId(it)) },
            markSynced = { id, at -> liftSessionNoteDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::liftSessionNote,
            decodeUpsert = { id, data ->
                val entity = decoders.liftSessionNote(id, data) ?: return@entityHandler false
                mergeSessionSlot(data, entity)
                true
            },
        )

    private fun loggedSetsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.LoggedSets,
            pending = { loggedSetDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                loggedSetDao.getById(LoggedSetId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { loggedSetDao.deleteById(LoggedSetId(it)) },
            markSynced = { id, at -> loggedSetDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::loggedSet,
            decodeUpsert = loggedSetRemoteApplier::upsertFromRemote,
        )

    private fun programChangeEventsHandler(): SyncCollectionHandler =
        entityHandler(
            collection = SyncCollection.ProgramChangeEvents,
            pending = { programChangeEventDao.getPending(it) },
            id = { it.id.value },
            updatedAt = { it.updatedAtEpochMillis },
            loadMeta = { id ->
                programChangeEventDao.getById(ProgramChangeEventId(id))
                    ?.let { LocalSyncMeta(it.pendingSync, it.updatedAtEpochMillis) }
            },
            delete = { programChangeEventDao.deleteById(ProgramChangeEventId(it)) },
            markSynced = { id, at -> programChangeEventDao.markSyncedIfUnchanged(id, at) },
            encode = encoders::programChangeEvent,
            decodeUpsert = { id, data ->
                decoders.programChangeEvent(id, data)?.let {
                    programChangeEventDao.upsert(it); true
                } ?: false
            },
        )

    /**
     * Cross-version slot merge for session notes. Per-note documents (UUID ids) merge
     * row-by-row. A legacy single-row document (`session::slot` id) represents the whole
     * slot, but siblings are displaced only when the existing remote-apply rules say so:
     * pending rows are always preserved, clean rows lose only to a strictly newer remote
     * (ties break toward remote, matching [FirestoreSyncRemoteApplyPolicy] with an
     * unknown local device). When any sibling is preserved, the losing legacy document
     * is tombstoned so all devices converge on the kept rows.
     *
     * Lock contract: production callers (pull engine, conflict adoption) already hold the
     * coordinator write lock — this path must not reacquire it (`Mutex` is non-reentrant).
     * Atomicity of rows + outbox comes from the Room transaction below.
     */
    private suspend fun mergeSessionSlot(
        data: Map<String, Any?>,
        entity: LiftSessionNoteEntity,
    ) {
        if (entity.id != LiftSessionNoteId.forSlot(entity.sessionId, entity.workoutLiftId)) {
            liftSessionNoteDao.upsert(entity)
            return
        }
        val remoteUpdatedAt =
            (data[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong() ?: 0L
        val remoteDeviceId = data[FirestoreSyncMetadata.DEVICE_ID] as? String
        database.withTransaction {
            val siblings =
                liftSessionNoteDao.getNotesForSlot(entity.sessionId, entity.workoutLiftId)
                    .filterNot { it.id == entity.id }
            if (siblings.isEmpty()) {
                liftSessionNoteDao.upsert(entity)
                return@withTransaction
            }
            val replace =
                siblings.all { row ->
                    FirestoreSyncRemoteApplyPolicy.decide(
                        localMeta = LocalSyncMeta(row.pendingSync, row.updatedAtEpochMillis),
                        remoteUpdatedAt = remoteUpdatedAt,
                        remoteDeviceId = remoteDeviceId,
                        // The handler has no device context: pending rows are still always
                        // preserved (unknown local never equals a remote device id).
                        localDeviceId = null,
                        remoteDeleted = false,
                    ) == FirestoreSyncRemoteApplyPolicy.Decision.Apply
                }
            if (replace) {
                liftSessionNoteDao.deleteBySessionAndWorkoutLift(entity.sessionId, entity.workoutLiftId)
                liftSessionNoteDao.upsert(entity)
                syncTouch.enqueueDelete(
                    SyncCollection.LiftSessionNotes,
                    siblings.map { it.id.value },
                )
            } else {
                liftSessionNoteDao.deleteById(entity.id)
                syncTouch.enqueueDelete(SyncCollection.LiftSessionNotes, entity.id.value)
            }
        }
        syncTouch.markDirty()
    }

    private fun <E> entityHandler(
        collection: SyncCollection,
        pending: suspend (Int) -> List<E>,
        id: (E) -> String,
        updatedAt: (E) -> Long,
        loadMeta: suspend (String) -> LocalSyncMeta?,
        delete: suspend (String) -> Unit,
        markSynced: suspend (String, Long) -> Unit,
        encode: (E) -> Map<String, Any?>,
        decodeUpsert: suspend (String, Map<String, Any?>) -> Boolean,
    ): SyncCollectionHandler =
        GenericSyncHandler(
            collection = collection,
            loadPending = { limit ->
                pending(limit).map { entity ->
                    PendingSyncRow(id(entity), updatedAt(entity), encode(entity))
                }
            },
            loadLocalMeta = loadMeta,
            deleteById = delete,
            markSynced = markSynced,
            upsertRemote = decodeUpsert,
            checkHasPending = { pending(1).isNotEmpty() },
        )
}

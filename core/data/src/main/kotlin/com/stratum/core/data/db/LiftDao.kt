package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the catalog + this user's availability. Mirrors the read/write shape of
 * [SpecializationPreferenceDao]: `@Upsert` for upserts, typed [LiftId] params throughout.
 */
@Dao
@Suppress("TooManyFunctions")
interface LiftDao {
    /** Every lift as a hot stream. */
    @Query("SELECT * FROM lifts ORDER BY name")
    fun observeLifts(): Flow<List<LiftEntity>>

    /** Enabled lifts only — the §5 SQL-predicate read backing [LiftRepository.observeEnabledLifts]. */
    @Query("SELECT * FROM lifts WHERE enabled = 1 ORDER BY name")
    fun observeEnabledLifts(): Flow<List<LiftEntity>>

    /** Lifts filtered by `isCustom` — the §5 SQL-predicate read backing [LiftRepository.observeLifts(isCustom)]. */
    @Query("SELECT * FROM lifts WHERE isCustom = :isCustom ORDER BY name")
    fun observeLiftsByCustom(isCustom: Boolean): Flow<List<LiftEntity>>

    /**
     * Lifts whose movement-pattern set includes the pattern encoded in [bit]. The column is a
     * bitmask (`1 shl MovementPattern.ordinal`), so a bitwise AND is the positional-safe filter:
     * the caller passes `bit = 1 shl pattern.ordinal`, and any lift carrying that pattern yields
     * a non-zero AND result. This is the SQL-predicate read backing
     * [com.stratum.domain.lift.LiftRepository.observeLiftsContainingPattern] — equivalent to
     * `observeLifts().map { it.filter { l -> pattern in l.movementPatterns } }` but executed by
     * the database (DATA_LAYER_RULES.md §5).
     */
    @Query("SELECT * FROM lifts WHERE movementPatternsBitmask & :bit != 0 ORDER BY name")
    fun observeLiftsByPattern(bit: Int): Flow<List<LiftEntity>>

    /** Snapshot of every lift (non-streaming). */
    @Query("SELECT * FROM lifts ORDER BY name")
    suspend fun getLifts(): List<LiftEntity>

    /**
     * Snapshot of lifts with tiers for the given ids only — backs
     * [com.stratum.domain.lift.LiftRepository.getLiftsByIds]. Room expands empty [ids] poorly;
     * the repository short-circuits empty collections.
     */
    @Query("SELECT * FROM lifts WHERE id IN (:ids)")
    suspend fun getLiftsByIds(ids: List<LiftId>): List<LiftEntity>

    /**
     * Ids of lifts tagged for a preset, for bulk enable. The column is a delimiter-bounded CSV
     * (`,CommercialGym,GarageGym,`). The caller must supply the SQL `LIKE` wildcards in
     * [tagPattern] — i.e. `%,CommercialGym,%` — so the match is a substring search that works
     * whether the tag is first, last, or in the middle of the CSV.
     */
    @Query("SELECT id FROM lifts WHERE presetTags LIKE :tagPattern")
    suspend fun idsForPresetTag(tagPattern: String): List<LiftId>

    /** Upsert a catalog lift row (seed + custom both go through here). */
    @Upsert
    suspend fun upsert(lift: LiftEntity)

    /** Upsert many lift rows at once (seed load). */
    @Upsert
    suspend fun upsertAll(lifts: List<LiftEntity>)

    /** Flip one lift's availability flag and mark dirty for sync. */
    @Query(
        """
        UPDATE lifts
        SET enabled = :enabled,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setEnabled(id: LiftId, enabled: Boolean, updatedAtEpochMillis: Long)

    /** Batch disable/enable for merge-hide (and similar bulk availability changes). */
    @Query(
        """
        UPDATE lifts
        SET enabled = :enabled,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id IN (:ids)
        """,
    )
    suspend fun setEnabledForIds(
        ids: List<LiftId>,
        enabled: Boolean,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * Update primary/secondary muscle bitmasks for a **custom** lift only. Catalog rows are
     * left untouched (`isCustom = 1` predicate).
     */
    @Query(
        """
        UPDATE lifts
        SET primaryMusclesBitmask = :primaryBitmask,
            secondaryMusclesBitmask = :secondaryBitmask,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id AND isCustom = 1
        """,
    )
    suspend fun updateMuscles(
        id: LiftId,
        primaryBitmask: Int,
        secondaryBitmask: Int,
        updatedAtEpochMillis: Long,
    )

    /**
     * Update non-derived metadata for a **custom** lift only. Category is not stored.
     */
    @Query(
        """
        UPDATE lifts
        SET movementPatternsBitmask = :movementPatternsBitmask,
            muscleLengthBias = :muscleLengthBias,
            fatigueProfile = :fatigueProfile,
            loadType = :loadType,
            equipmentId = :equipmentId,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id AND isCustom = 1
        """,
    )
    suspend fun updateCustomDetails(
        id: LiftId,
        movementPatternsBitmask: Int,
        muscleLengthBias: MuscleLengthBias,
        fatigueProfile: FatigueProfile,
        loadType: LoadType,
        equipmentId: EquipmentId?,
        updatedAtEpochMillis: Long,
    )

    /**
     * Assign or clear equipment on catalog or custom lifts. Does not touch override tiers
     * or [LoadType].
     */
    @Query(
        """
        UPDATE lifts
        SET equipmentId = :equipmentId,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setEquipmentId(
        id: LiftId,
        equipmentId: EquipmentId?,
        updatedAtEpochMillis: Long,
    )

    /**
     * Assign or clear equipment and persist [loadType] on catalog or custom lifts.
     * Does not touch override tiers.
     */
    @Query(
        """
        UPDATE lifts
        SET equipmentId = :equipmentId,
            loadType = :loadType,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setEquipmentIdAndLoadType(
        id: LiftId,
        equipmentId: EquipmentId?,
        loadType: LoadType,
        updatedAtEpochMillis: Long,
    )

    /**
     * Per-lift default rest override for catalog and custom lifts. Null clears the override.
     * Does not rewrite existing workout set rest values.
     */
    @Query(
        """
        UPDATE lifts
        SET defaultRestSeconds = :restSeconds,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setDefaultRestSeconds(id: LiftId, restSeconds: Int?, updatedAtEpochMillis: Long)

    /**
     * User-authored pin note for catalog and custom lifts. Null clears the note.
     */
    @Query(
        """
        UPDATE lifts
        SET pinned_note = :note,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun setPinnedNote(id: LiftId, note: String?, updatedAtEpochMillis: Long)

    /** Enable every lift in [ids] in a single statement (preset apply). */
    @Query(
        """
        UPDATE lifts
        SET enabled = 1,
            pending_sync = ${SyncFlags.PENDING_UPSERT},
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE id IN (:ids)
        """,
    )
    suspend fun enableAll(ids: List<LiftId>, updatedAtEpochMillis: Long)

    /** Count of currently-enabled lifts (validation uses this implicitly via the stream). */
    @Query("SELECT COUNT(*) FROM lifts WHERE enabled = 1")
    suspend fun enabledCount(): Int

    /** Total catalog rows (seed + custom). Used to detect an empty DB that needs seeding. */
    @Query("SELECT COUNT(*) FROM lifts")
    suspend fun countAll(): Int

    /** Bundled seed rows only — customs must not suppress catalog seeding. */
    @Query("SELECT COUNT(*) FROM lifts WHERE isCustom = 0")
    suspend fun countSeedLifts(): Int

    @Query("SELECT * FROM lifts WHERE pending_sync != ${SyncFlags.CLEAN} LIMIT :limit")
    suspend fun getPending(limit: Int = 500): List<LiftEntity>

    @Query(
        """
        UPDATE lifts
        SET pending_sync = ${SyncFlags.CLEAN}
        WHERE id = :id AND updated_at_epoch_millis = :updatedAt
        """,
    )
    suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("SELECT * FROM lifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: LiftId): LiftEntity?

    @Query("DELETE FROM lifts WHERE id = :id")
    suspend fun deleteById(id: LiftId)
}

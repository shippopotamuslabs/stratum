package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the catalog + this user's availability. Mirrors the read/write shape of
 * [SpecializationPreferenceDao]: `@Transaction` relation queries for atomic parent+child
 * reads, `@Insert(REPLACE)` for upserts, typed [LiftId] params throughout.
 */
@Dao
interface LiftDao {
    /** Atomic read of every lift with its increment tiers, as a hot stream. */
    @Transaction
    @Query("SELECT * FROM lifts ORDER BY name")
    fun observeLiftsWithTiers(): Flow<List<LiftWithTiers>>

    /** Enabled lifts only — the §5 SQL-predicate read backing [LiftRepository.observeEnabledLifts]. */
    @Transaction
    @Query("SELECT * FROM lifts WHERE enabled = 1 ORDER BY name")
    fun observeEnabledLiftsWithTiers(): Flow<List<LiftWithTiers>>

    /** Lifts filtered by `isCustom` — the §5 SQL-predicate read backing [LiftRepository.observeLifts(isCustom)]. */
    @Transaction
    @Query("SELECT * FROM lifts WHERE isCustom = :isCustom ORDER BY name")
    fun observeLiftsWithTiersByCustom(isCustom: Boolean): Flow<List<LiftWithTiers>>

    /**
     * Lifts whose movement-pattern set includes the pattern encoded in [bit]. The column is a
     * bitmask (`1 shl MovementPattern.ordinal`), so a bitwise AND is the positional-safe filter:
     * the caller passes `bit = 1 shl pattern.ordinal`, and any lift carrying that pattern yields
     * a non-zero AND result. This is the SQL-predicate read backing
     * [com.stratum.domain.lift.LiftRepository.observeLiftsContainingPattern] — equivalent to
     * `observeLifts().map { it.filter { l -> pattern in l.movementPatterns } }` but executed by
     * the database (DATA_LAYER_RULES.md §5).
     */
    @Transaction
    @Query("SELECT * FROM lifts WHERE movementPatternsBitmask & :bit != 0 ORDER BY name")
    fun observeLiftsWithTiersByPattern(bit: Int): Flow<List<LiftWithTiers>>

    /** Snapshot of every lift with tiers (non-streaming). */
    @Transaction
    @Query("SELECT * FROM lifts ORDER BY name")
    suspend fun getLiftsWithTiers(): List<LiftWithTiers>

    /**
     * Ids of lifts tagged for a preset, for bulk enable. The column is a delimiter-bounded CSV
     * (`,CommercialGym,GarageGym,`). The caller must supply the SQL `LIKE` wildcards in
     * [tagPattern] — i.e. `%,CommercialGym,%` — so the match is a substring search that works
     * whether the tag is first, last, or in the middle of the CSV.
     */
    @Query("SELECT id FROM lifts WHERE presetTags LIKE :tagPattern")
    suspend fun idsForPresetTag(tagPattern: String): List<LiftId>

    /** Insert or replace a catalog lift row (seed + custom both go through here). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lift: LiftEntity)

    /** Insert or replace many lift rows at once (seed load). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lifts: List<LiftEntity>)

    /** Flip one lift's availability flag. */
    @Query("UPDATE lifts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: LiftId, enabled: Boolean)

    /** Enable every lift in [ids] in a single statement (preset apply). */
    @Query("UPDATE lifts SET enabled = 1 WHERE id IN (:ids)")
    suspend fun enableAll(ids: List<LiftId>)

    /** Count of currently-enabled lifts (validation uses this implicitly via the stream). */
    @Query("SELECT COUNT(*) FROM lifts WHERE enabled = 1")
    suspend fun enabledCount(): Int

    /** Total catalog rows (seed + custom). Used to detect an empty DB that needs seeding. */
    @Query("SELECT COUNT(*) FROM lifts")
    suspend fun countAll(): Int

    /** Bundled seed rows only — customs must not suppress catalog seeding. */
    @Query("SELECT COUNT(*) FROM lifts WHERE isCustom = 0")
    suspend fun countSeedLifts(): Int
}

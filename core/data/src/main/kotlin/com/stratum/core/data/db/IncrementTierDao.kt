package com.stratum.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stratum.domain.lift.identity.LiftId

/**
 * DAO for a lift's increment-tier bands. Writes replace the whole set per lift (delete + insert
 * in a transaction at the repository layer) so editing tiers stays atomic. Cascade-on-delete
 * on the FK means removing a lift clears its bands automatically.
 */
@Dao
interface IncrementTierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tiers: List<IncrementTierEntity>)

    @Query("DELETE FROM increment_tiers WHERE liftId = :liftId")
    suspend fun deleteForLift(liftId: LiftId)
}

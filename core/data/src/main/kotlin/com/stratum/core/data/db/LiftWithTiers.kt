package com.stratum.core.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Read model joining a [LiftEntity] with its [IncrementTierEntity] bands. Used by
 * [LiftDao.observeLiftsWithTiers] inside a `@Transaction` so the parent + children load
 * atomically. The mapper folds this into a single domain [com.stratum.domain.lift.Lift].
 */
data class LiftWithTiers(
    @Embedded val lift: LiftEntity,
    @Relation(parentColumn = "id", entityColumn = "liftId")
    val tiers: List<IncrementTierEntity>,
)

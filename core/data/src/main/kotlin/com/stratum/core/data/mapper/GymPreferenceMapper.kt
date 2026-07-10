package com.stratum.core.data.mapper

import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.onboarding.identity.GymPreferenceId

/**
 * Maps the single [GymPreferenceEntity] row to/from the nullable [GymPreset] choice. A missing
 * row (the user hasn't reached the step) maps to `null`; a present row with a null
 * [GymPreferenceEntity.selectedPreset] means the user skipped the preset and curated manually.
 */
fun GymPreferenceEntity?.toDomain(): GymPreset? = this?.selectedPreset

fun GymPreset?.toEntity(
    usedHistoricalImport: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
): GymPreferenceEntity =
    GymPreferenceEntity(
        id = GymPreferenceId.Default,
        selectedPreset = this,
        usedHistoricalImport = usedHistoricalImport,
        updatedAtEpochMillis = nowMillis,
        pendingSync = SyncFlags.PENDING_UPSERT,
    )

package com.stratum.domain.importing

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

data class FilteredImportHistory(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val skippedSessionCount: Int,
    val skippedSetCount: Int,
)

package com.stratum.domain.importing

data class MergeImportStats(
    val sessionsInserted: Int,
    val setsInserted: Int,
    val sessionsSkipped: Int,
    val setsSkipped: Int,
)

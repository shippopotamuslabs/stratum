package com.stratum.core.testing.response

import com.stratum.application.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot

class FakeMuscleVolumeResponseSnapshotRepository : MuscleVolumeResponseSnapshotRepository {
    private var snapshots: List<MuscleVolumeResponseSnapshot> = emptyList()

    fun seed(snapshots: List<MuscleVolumeResponseSnapshot>) {
        this.snapshots = snapshots
    }

    override suspend fun replaceAll(snapshots: List<MuscleVolumeResponseSnapshot>) {
        this.snapshots = snapshots
    }

    override suspend fun getAll(): List<MuscleVolumeResponseSnapshot> = snapshots
}

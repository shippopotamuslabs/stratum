package com.stratum.core.testing.response

import com.stratum.domain.response.MuscleVolumeLandmarkEstimateRepository
import com.stratum.domain.response.modeling.MuscleVolumeLandmarkEstimate

class FakeMuscleVolumeLandmarkEstimateRepository : MuscleVolumeLandmarkEstimateRepository {
    private var estimates: List<MuscleVolumeLandmarkEstimate> = emptyList()

    fun seed(estimates: List<MuscleVolumeLandmarkEstimate>) {
        this.estimates = estimates
    }

    override suspend fun replaceAll(estimates: List<MuscleVolumeLandmarkEstimate>) {
        this.estimates = estimates
    }

    override suspend fun getAll(): List<MuscleVolumeLandmarkEstimate> = estimates
}

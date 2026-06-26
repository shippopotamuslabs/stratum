package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId

object IntensityProxyCalculator {
    fun forWeek(cleanSets: List<CleanTrainingSet>, liftId: LiftId?): Double? {
        val working =
            cleanSets.filter { row ->
                row.isWorkingSet && (liftId == null || row.sourceSet.liftId == liftId)
            }
        if (working.isEmpty()) return null

        val withRir = working.filter { it.sourceSet.rir != null }
        val rirCoverage = withRir.size.toDouble() / working.size
        if (rirCoverage >= RIR_COVERAGE_THRESHOLD) {
            return withRir.map { it.sourceSet.rir!!.toDouble() }.average()
        }

        val peakWeight = working.maxOf { it.normalizedWeight }
        return peakWeight / 100.0
    }
}

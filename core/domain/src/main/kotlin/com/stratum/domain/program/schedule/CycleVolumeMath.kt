package com.stratum.domain.program.schedule

/**
 * Converts planned **cycle** volume into **weekly-equivalent** units for landmarks / check-in
 * ceilings (MV/PV/RC are weekly).
 *
 * `weeklyEquivalent = cycleVolume * (7 / cycleLength)`. When [cycleLength] is 7 this is identity.
 */
object CycleVolumeMath {
    fun weeklyEquivalentFactor(cycleLength: Int): Double {
        require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
        return 7.0 / cycleLength.toDouble()
    }

    fun toWeeklyEquivalent(cycleVolume: Double, cycleLength: Int): Double =
        cycleVolume * weeklyEquivalentFactor(cycleLength)

    fun <K> toWeeklyEquivalent(
        cycleVolumeByKey: Map<K, Double>,
        cycleLength: Int,
    ): Map<K, Double> {
        val factor = weeklyEquivalentFactor(cycleLength)
        return cycleVolumeByKey.mapValues { (_, volume) -> volume * factor }
    }
}

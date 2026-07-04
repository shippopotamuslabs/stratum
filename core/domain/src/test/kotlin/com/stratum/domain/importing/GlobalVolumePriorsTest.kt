package com.stratum.domain.importing

import com.stratum.domain.onboarding.TrainingAge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalVolumePriorsTest {
    @Test
    fun `default MV is 4 with global PV and RC`() {
        val priors = GlobalVolumePriors.forTrainingAge(TrainingAge.Intermediate)
        assertEquals(4.0, priors.retentionFloorMv)
        assertEquals(13.0, priors.paretoVolumePv)
        assertEquals(20.0, priors.recoverabilityCeilingRc)
        assertEquals(6.0, priors.growthEntryFloor)
        assertEquals(16.5, priors.maintenanceCeiling)
    }

    @Test
    fun `late intermediate bumps MV to 5`() {
        val priors = GlobalVolumePriors.forTrainingAge(TrainingAge.LateIntermediate)
        assertEquals(5.0, priors.retentionFloorMv)
        assertEquals(13.0, priors.paretoVolumePv)
    }

    @Test
    fun `advanced bumps MV to 6`() {
        val priors = GlobalVolumePriors.forTrainingAge(TrainingAge.Advanced)
        assertEquals(6.0, priors.retentionFloorMv)
    }

    @Test
    fun `pareto volume derives from RC via 0_8 squared`() {
        assertEquals(13.0, GlobalVolumePriors.paretoVolumeFromCeiling(20.0))
    }
}

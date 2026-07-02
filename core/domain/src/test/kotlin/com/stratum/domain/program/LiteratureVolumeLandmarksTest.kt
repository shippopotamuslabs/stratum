package com.stratum.domain.program

import com.stratum.domain.importing.VolumeLandmarkPriorBands
import com.stratum.domain.importing.VolumeLandmarkPriors
import com.stratum.domain.importing.literatureMaintenanceVolumeFromMav
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiteratureVolumeLandmarksTest {
    @Test
    fun `all muscles have literature priors`() {
        val landmarks = LiteratureVolumeLandmarks.all()
        assertEquals(MuscleGroup.entries.size, landmarks.size)
        assertEquals(MuscleGroup.entries.toSet(), landmarks.map { it.muscle }.toSet())
    }

    @Test
    fun `chest priors use literature mids and bands`() {
        val chest =
            LiteratureVolumeLandmarks.all().single { it.muscle == MuscleGroup.Chest }
        assertEquals(
            (VolumeLandmarkPriorBands.MEV_LOW_STANDARD + VolumeLandmarkPriorBands.MEV_HIGH_LARGE) / 2.0,
            chest.estimatedMev,
        )
        assertEquals(VolumeLandmarkPriorBands.MEV_LOW_STANDARD, chest.mevLow)
        assertEquals(VolumeLandmarkPriorBands.MRV_HIGH_CHEST, chest.mrvHigh)
    }

    @Test
    fun `literature PV is 85 percent of MAV mid clamped between MV and MAV`() {
        for (muscle in MuscleGroup.entries) {
            val prior = VolumeLandmarkPriors.forMuscle(muscle)
            val expected = literatureMaintenanceVolumeFromMav(prior.mavMid, prior.mvMid)
            assertEquals(expected, prior.pvMid)
            assertEquals(expected, LiteratureVolumeLandmarks.all().single { it.muscle == muscle }.estimatedPv)
            assertTrue(prior.pvMid >= prior.mvMid)
            assertTrue(prior.pvMid <= prior.mavMid)
        }
    }
}

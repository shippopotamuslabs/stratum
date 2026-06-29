package com.stratum.domain.program

import com.stratum.domain.importing.VolumeLandmarkPriorBands
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
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
            (VolumeLandmarkPriorBands.MEV_LOW_LARGE + VolumeLandmarkPriorBands.MEV_HIGH_LARGE) / 2.0,
            chest.estimatedMev,
        )
        assertEquals(VolumeLandmarkPriorBands.MEV_LOW_LARGE, chest.mevLow)
        assertEquals(VolumeLandmarkPriorBands.MRV_HIGH_MAJOR, chest.mrvHigh)
    }
}

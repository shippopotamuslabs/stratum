package com.stratum.domain.program

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.TrainingAge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiteratureVolumeDefaultsTest {
    @Test
    fun `all muscles have literature defaults`() {
        val defaults = LiteratureVolumeDefaultsProvider.all()
        assertEquals(MuscleGroup.entries.size, defaults.size)
        assertEquals(MuscleGroup.entries.toSet(), defaults.map { it.muscle }.toSet())
    }

    @Test
    fun `intermediate defaults use global MV PV RC anchors`() {
        val globals = GlobalVolumePriors.forTrainingAge(TrainingAge.Intermediate)
        val chest = LiteratureVolumeDefaultsProvider.all().single { it.muscle == MuscleGroup.Chest }
        assertEquals(globals.retentionFloorMv, chest.retentionFloorMv)
        assertEquals(globals.paretoVolumePv, chest.paretoVolumePv)
        assertEquals(globals.recoverabilityCeilingRc, chest.recoverabilityCeilingRc)
        assertEquals(globals.growthEntryFloor, chest.growthEntryFloor)
        assertEquals(globals.maintenanceCeiling, chest.maintenanceCeiling)
    }

    @Test
    fun `every muscle shares the same global prior table`() {
        val reference = LiteratureVolumeDefaultsProvider.all().single { it.muscle == MuscleGroup.Chest }
        for (muscle in MuscleGroup.entries) {
            val defaults = LiteratureVolumeDefaultsProvider.all().single { it.muscle == muscle }
            assertEquals(reference.retentionFloorMv, defaults.retentionFloorMv)
            assertEquals(reference.paretoVolumePv, defaults.paretoVolumePv)
            assertEquals(reference.recoverabilityCeilingRc, defaults.recoverabilityCeilingRc)
            assertTrue(defaults.paretoVolumePv >= defaults.retentionFloorMv)
            assertTrue(defaults.recoverabilityCeilingRc >= defaults.paretoVolumePv)
        }
    }
}

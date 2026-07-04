package com.stratum.feature.program

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.core.ui.component.toProgramDisplayName

class ProgramDisplayNamesTest {
    @Test
    @DisplayName("PROG-DN-401 calf volume label is Calves not PlantarFlexion")
    fun progDn401_calfVolumeLabelIsCalves() {
        assertEquals("Calves", MuscleGroup.Calf.toVolumeDisplayName())
    }

    @Test
    @DisplayName("PROG-DN-402 plantar flexion pattern shows Calves on program screen")
    fun progDn402_plantarFlexionPatternShowsCalves() {
        assertEquals("Calves", MovementPattern.PlantarFlexion.toProgramDisplayName())
    }

    @Test
    @DisplayName("PROG-DN-403 other patterns keep domain displayName")
    fun progDn403_otherPatternsKeepDomainDisplayName() {
        assertEquals("Squat Pattern", MovementPattern.SquatPattern.toProgramDisplayName())
    }

    @Test
    @DisplayName("PROG-DN-404 quad volume label is Quads")
    fun progDn404_quadVolumeLabelIsQuads() {
        assertEquals("Quads", MuscleGroup.Quad.toVolumeDisplayName())
    }
}

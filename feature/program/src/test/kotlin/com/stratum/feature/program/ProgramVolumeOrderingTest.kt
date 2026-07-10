package com.stratum.feature.program

import com.stratum.core.ui.component.toLowerVolumeCards
import com.stratum.core.ui.component.toUpperVolumeCards
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.MuscleVolumeSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramVolumeOrderingTest {
    @Test
    @DisplayName("PROG-VO-401 upper volume follows chest-through-abs order")
    fun progVo401_upperVolumeFollowsOrder() {
        val summaries =
            listOf(
                summary(MuscleGroup.Ab, setCount = 4.0),
                summary(MuscleGroup.AnteriorDeltoid, setCount = 3.0),
                summary(MuscleGroup.Chest, setCount = 12.0),
                summary(MuscleGroup.Forearm, setCount = 2.0),
            )

        val cards = summaries.toUpperVolumeCards()

        assertEquals(
            listOf(MuscleGroup.Chest, MuscleGroup.AnteriorDeltoid, MuscleGroup.Forearm, MuscleGroup.Ab),
            cards.map { card -> card.muscle },
        )
    }

    @Test
    @DisplayName("PROG-VO-402 lower volume follows quads-through-calves order")
    fun progVo402_lowerVolumeFollowsOrder() {
        val summaries =
            listOf(
                summary(MuscleGroup.Adductor, setCount = 2.0),
                summary(MuscleGroup.Quad, setCount = 16.0),
                summary(MuscleGroup.LowerBack, setCount = 5.0),
                summary(MuscleGroup.Glute, setCount = 10.0),
            )

        val cards = summaries.toLowerVolumeCards()

        assertEquals(
            listOf(MuscleGroup.Quad, MuscleGroup.Glute, MuscleGroup.LowerBack, MuscleGroup.Adductor),
            cards.map { card -> card.muscle },
        )
    }

    private fun summary(muscle: MuscleGroup, setCount: Double): MuscleVolumeSummary =
        MuscleVolumeSummary(
            muscle = muscle,
            exerciseCount = 1,
            setCount = setCount,
        )
}

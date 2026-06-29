package com.stratum.feature.program

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
                summary(MuscleGroup.Chest, setCount = 12.0),
                summary(MuscleGroup.Tricep, setCount = 8.0),
            )

        val cards = summaries.toUpperVolumeCards()

        assertEquals(
            listOf(MuscleGroup.Chest, MuscleGroup.Tricep, MuscleGroup.Ab),
            cards.map { card -> card.muscle },
        )
    }

    @Test
    @DisplayName("PROG-VO-402 lower volume follows quads-through-calves order")
    fun progVo402_lowerVolumeFollowsOrder() {
        val summaries =
            listOf(
                summary(MuscleGroup.Calf, setCount = 6.0),
                summary(MuscleGroup.Quad, setCount = 16.0),
                summary(MuscleGroup.Glute, setCount = 10.0),
            )

        val cards = summaries.toLowerVolumeCards()

        assertEquals(
            listOf(MuscleGroup.Quad, MuscleGroup.Glute, MuscleGroup.Calf),
            cards.map { card -> card.muscle },
        )
    }

    @Test
    @DisplayName("PROG-VO-403 muscles outside tier lists are omitted from carousel")
    fun progVo403_musclesOutsideTierListsAreOmitted() {
        val summaries = listOf(summary(MuscleGroup.Trap, setCount = 5.0))

        assertEquals(emptyList<MuscleGroup>(), summaries.toUpperVolumeCards().map { it.muscle })
        assertEquals(emptyList<MuscleGroup>(), summaries.toLowerVolumeCards().map { it.muscle })
    }

    private fun summary(muscle: MuscleGroup, setCount: Double): MuscleVolumeSummary =
        MuscleVolumeSummary(
            muscle = muscle,
            exerciseCount = 1,
            setCount = setCount,
        )
}

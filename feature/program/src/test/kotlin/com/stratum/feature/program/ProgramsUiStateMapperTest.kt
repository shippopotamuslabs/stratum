package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramsUiStateMapperTest {
    @Test
    @DisplayName("PROG-GEN-408 lift names resolve from catalog ids")
    fun progGen408_liftNamesResolveFromCatalog() {
        val line =
            buildLiftLineState(
                liftId = LiftId("barbell-bench-press-medium-grip"),
                sets = sampleSets(),
                liftNamesById = mapOf("barbell-bench-press-medium-grip" to "Barbell Bench Press"),
                position = 0,
            )

        assertEquals("Barbell Bench Press", line.name)
        assertEquals(2, line.setCount)
        assertEquals("8\u201312", line.repRange)
        assertEquals("@8", line.topSetRpe)
    }

    @Test
    @DisplayName("PROG-GEN-409 slug ids title-case when catalog name is missing")
    fun progGen409_slugIdsTitleCaseWhenCatalogMissing() {
        val line =
            buildLiftLineState(
                liftId = LiftId("cable-one-arm-tricep-extension"),
                sets = sampleSets(),
                liftNamesById = emptyMap(),
                position = 0,
            )

        assertEquals("Cable One Arm Tricep Extension", line.name)
        assertEquals(2, line.setCount)
        assertEquals("8\u201312", line.repRange)
        assertEquals("@8", line.topSetRpe)
    }

    @Test
    @DisplayName("PROG-GEN-410 uuid ids without catalog entry use unknown label")
    fun progGen410_uuidWithoutCatalogUsesUnknownLabel() {
        val line =
            buildLiftLineState(
                liftId = LiftId("8ac3e0b4-75a7-4f66-b423-3c59ccf8ffbf"),
                sets = sampleSets(),
                liftNamesById = emptyMap(),
                position = 0,
            )

        assertEquals("Unknown lift", line.name)
        assertEquals(2, line.setCount)
        assertEquals("8\u201312", line.repRange)
        assertEquals("@8", line.topSetRpe)
    }

    private fun sampleSets(): List<LiftSet> =
        listOf(
            LiftSet(
                id = LiftSetId("ls-1"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                position = 0,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                isTopSet = true,
            ),
            LiftSet(
                id = LiftSetId("ls-2"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                position = 1,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                isTopSet = false,
            ),
        )
}

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
            formatLiftSummaryLine(
                liftId = LiftId("barbell-bench-press-medium-grip"),
                sets = sampleSets(),
                liftNamesById = mapOf("barbell-bench-press-medium-grip" to "Barbell Bench Press"),
            )

        assertEquals("Barbell Bench Press - 2 x 8\u201312", line)
    }

    @Test
    @DisplayName("PROG-GEN-409 slug ids title-case when catalog name is missing")
    fun progGen409_slugIdsTitleCaseWhenCatalogMissing() {
        val line =
            formatLiftSummaryLine(
                liftId = LiftId("cable-one-arm-tricep-extension"),
                sets = sampleSets(),
                liftNamesById = emptyMap(),
            )

        assertEquals("Cable One Arm Tricep Extension - 2 x 8\u201312", line)
    }

    @Test
    @DisplayName("PROG-GEN-410 uuid ids without catalog entry use unknown label")
    fun progGen410_uuidWithoutCatalogUsesUnknownLabel() {
        val line =
            formatLiftSummaryLine(
                liftId = LiftId("8ac3e0b4-75a7-4f66-b423-3c59ccf8ffbf"),
                sets = sampleSets(),
                liftNamesById = emptyMap(),
            )

        assertEquals("Unknown lift - 2 x 8\u201312", line)
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

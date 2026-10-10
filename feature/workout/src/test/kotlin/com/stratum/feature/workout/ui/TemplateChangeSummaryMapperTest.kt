package com.stratum.feature.workout.ui

import com.stratum.domain.program.SetType
import com.stratum.domain.workout.LiftAdded
import com.stratum.domain.workout.LiftMoved
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.workout.PinnedNotesChanged
import com.stratum.domain.workout.RestBetweenLiftsChanged
import com.stratum.domain.workout.RestEdited
import com.stratum.domain.workout.SetCountChanged
import com.stratum.domain.workout.SetEdited
import com.stratum.domain.workout.SupersetGroupingChanged
import com.stratum.domain.workout.TemplateChangeSummary
import com.stratum.domain.workout.TemplateSetShape
import com.stratum.domain.workout.myorep.MyoRepKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TemplateChangeSummaryMapper")
class TemplateChangeSummaryMapperTest {

    @Test
    @DisplayName("Empty summary maps to no groups")
    fun emptySummaryHasNoGroups() {
        val result = TemplateChangeSummaryMapper.map(TemplateChangeSummary(emptyList()))

        assertTrue(result.isEmpty)
        assertEquals(0, result.rowCount)
    }

    @Test
    @DisplayName("Groups follow category order and empty categories are omitted")
    fun groupsOrderedAndEmptyOmitted() {
        val summary =
            TemplateChangeSummary(
                rows =
                    listOf(
                        RestEdited("Squat", setPosition = 0, oldSeconds = 120, newSeconds = 45),
                        LiftAdded("Row", setCount = 3),
                    ),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(TemplateChangeCategoryUi.Lifts, TemplateChangeCategoryUi.Rest),
            result.groups.map { it.category },
        )
        assertEquals(2, result.rowCount)
    }

    @Test
    @DisplayName("Added lift has no old text and pluralized set count")
    fun addedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftAdded("Row", setCount = 1)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(TemplateChangeRowUi(label = "Row", oldText = null, newText = "1 set")),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Swapped lift shows old and new names")
    fun swappedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftSwapped("Squat", "Bench Press")))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(TemplateChangeRowUi(label = "Lift", oldText = "Squat", newText = "Bench Press")),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Moved lift shows 1-based positions")
    fun movedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftMoved("Squat", oldPosition = 1, newPosition = 0)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(label = "Squat", oldText = "Position 2", newText = "Position 1"),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Set count nets to one totals row")
    fun setCountRow() {
        val summary =
            TemplateChangeSummary(rows = listOf(SetCountChanged("Squat", oldCount = 2, newCount = 4, replacedCount = 0)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(TemplateChangeRowUi(label = "Squat", oldText = "2 sets", newText = "4 sets")),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Churned set count appends replaced detail to the new total")
    fun setCountRowWithReplaced() {
        val summary =
            TemplateChangeSummary(rows = listOf(SetCountChanged("Squat", oldCount = 2, newCount = 3, replacedCount = 2)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(label = "Squat", oldText = "2 sets", newText = "3 sets · 2 replaced"),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Edited set formats shape with type, band, RIR, and top marker")
    fun editedSetRow() {
        val summary =
            TemplateChangeSummary(
                rows =
                    listOf(
                        SetEdited(
                            liftName = "Bench Press",
                            setPosition = 1,
                            oldShape =
                                TemplateSetShape(
                                    setType = SetType.Standard,
                                    myoRepKind = null,
                                    repMin = 6,
                                    repMax = 10,
                                    rirGoal = 2,
                                    isTopSet = true,
                                ),
                            newShape =
                                TemplateSetShape(
                                    setType = SetType.MyoRep,
                                    myoRepKind = MyoRepKind.RepMatching,
                                    repMin = 8,
                                    repMax = 8,
                                    rirGoal = 1,
                                    isTopSet = false,
                                ),
                        ),
                    ),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(
                    label = "Bench Press · Set 2",
                    oldText = "Standard 6–10 · RIR 2 · Top",
                    newText = "Myo-Rep Match 8 · RIR 1",
                ),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("RIR-only back-off omits the zero band")
    fun rirOnlyShapeOmitsBand() {
        val shape =
            TemplateSetShape(
                setType = SetType.Standard,
                myoRepKind = null,
                repMin = 0,
                repMax = 0,
                rirGoal = 2,
                isTopSet = false,
            )
        val summary =
            TemplateChangeSummary(
                rows =
                    listOf(
                        SetEdited("Squat", setPosition = 1, oldShape = shape, newShape = shape.copy(rirGoal = 1)),
                    ),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        val row = result.groups.single().rows.single()
        assertEquals("Standard · RIR 2", row.oldText)
        assertEquals("Standard · RIR 1", row.newText)
    }

    @Test
    @DisplayName("Edited rest formats seconds as m:ss")
    fun editedRestRow() {
        val summary =
            TemplateChangeSummary(rows = listOf(RestEdited("Squat", setPosition = 0, oldSeconds = 120, newSeconds = 45)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(label = "Squat · Set 1", oldText = "2:00", newText = "0:45"),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Superset grouping names partners and ungrouped sides")
    fun supersetGroupingRow() {
        val summary =
            TemplateChangeSummary(
                rows =
                    listOf(
                        SupersetGroupingChanged("Press", oldPartners = emptyList(), newPartners = listOf("Raise", "Fly")),
                    ),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(
                    label = "Press",
                    oldText = "Not grouped",
                    newText = "Grouped with Raise, Fly",
                ),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Rest-between-lifts toggle formats as On and Off")
    fun restBetweenLiftsRow() {
        val summary =
            TemplateChangeSummary(
                rows = listOf(RestBetweenLiftsChanged("Squat", oldEnabled = false, newEnabled = true)),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(label = "Squat · Rest between lifts", oldText = "Off", newText = "On"),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Changed notes join bodies and empty sides read as no notes")
    fun pinnedNotesRow() {
        val summary =
            TemplateChangeSummary(
                rows =
                    listOf(
                        PinnedNotesChanged("Squat", oldNotes = emptyList(), newNotes = listOf("cue one", "cue two")),
                    ),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(
                TemplateChangeRowUi(label = "Squat · Notes", oldText = "No notes", newText = "cue one; cue two"),
            ),
            result.groups.single().rows,
        )
    }
}

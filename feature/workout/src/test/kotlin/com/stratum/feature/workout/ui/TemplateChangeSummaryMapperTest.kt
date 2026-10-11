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
import org.junit.jupiter.api.Assertions.assertNull
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
    @DisplayName("Added lift maps to a structural row with the raw count")
    fun addedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftAdded("Row", setCount = 1)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(LiftAddedUi(liftName = "Row", setCount = 1)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Swapped lift maps to a structural row with old and new names")
    fun swappedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftSwapped("Squat", "Bench Press")))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(LiftSwappedUi(oldName = "Squat", newName = "Bench Press")),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Moved lift maps 0-based positions to 1-based display positions")
    fun movedLiftRow() {
        val summary = TemplateChangeSummary(rows = listOf(LiftMoved("Squat", oldPosition = 1, newPosition = 0)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(LiftMovedUi(liftName = "Squat", oldPosition = 2, newPosition = 1)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Set count maps to a structural totals row")
    fun setCountRow() {
        val summary =
            TemplateChangeSummary(rows = listOf(SetCountChanged("Squat", oldCount = 2, newCount = 4, replacedCount = 0)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(SetCountChangedUi(liftName = "Squat", oldCount = 2, newCount = 4, replacedCount = 0)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Churned set count carries the replaced count structurally")
    fun setCountRowWithReplaced() {
        val summary =
            TemplateChangeSummary(rows = listOf(SetCountChanged("Squat", oldCount = 2, newCount = 3, replacedCount = 2)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(SetCountChangedUi(liftName = "Squat", oldCount = 2, newCount = 3, replacedCount = 2)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Edited set maps shapes to presentation shapes with 1-based position")
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
                SetEditedUi(
                    liftName = "Bench Press",
                    setPosition = 2,
                    oldShape =
                        TemplateSetShapeUi(
                            setKind = WorkoutSetKind.Standard,
                            repMin = 6,
                            repMax = 10,
                            rirGoal = 2,
                            isTopSet = true,
                        ),
                    newShape =
                        TemplateSetShapeUi(
                            setKind = WorkoutSetKind.MyoRepMatch,
                            repMin = 8,
                            repMax = 8,
                            rirGoal = 1,
                            isTopSet = false,
                        ),
                ),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("RIR-only back-off preserves the zero band structurally")
    fun rirOnlyShapePreservesBand() {
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

        val row = result.groups.single().rows.single() as SetEditedUi
        assertEquals(TemplateSetShapeUi(WorkoutSetKind.Standard, 0, 0, 2, false), row.oldShape)
        assertEquals(TemplateSetShapeUi(WorkoutSetKind.Standard, 0, 0, 1, false), row.newShape)
    }

    @Test
    @DisplayName("Edited rest maps to raw seconds with 1-based position")
    fun editedRestRow() {
        val summary =
            TemplateChangeSummary(rows = listOf(RestEdited("Squat", setPosition = 0, oldSeconds = 120, newSeconds = 45)))

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(RestEditedUi(liftName = "Squat", setPosition = 1, oldSeconds = 120, newSeconds = 45)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Superset grouping maps to raw partner lists")
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
                SupersetGroupingChangedUi(
                    liftName = "Press",
                    oldPartners = emptyList(),
                    newPartners = listOf("Raise", "Fly"),
                ),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Rest-between-lifts toggle maps to raw booleans")
    fun restBetweenLiftsRow() {
        val summary =
            TemplateChangeSummary(
                rows = listOf(RestBetweenLiftsChanged("Squat", oldEnabled = false, newEnabled = true)),
            )

        val result = TemplateChangeSummaryMapper.map(summary)

        assertEquals(
            listOf(RestBetweenLiftsChangedUi(liftName = "Squat", oldEnabled = false, newEnabled = true)),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("Changed notes map to raw note lists")
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
                PinnedNotesChangedUi(
                    liftName = "Squat",
                    oldNotes = emptyList(),
                    newNotes = listOf("cue one", "cue two"),
                ),
            ),
            result.groups.single().rows,
        )
    }

    @Test
    @DisplayName("enrichTemplateChangeNames backfills equipment names and leaves other rows alone")
    fun enrichBackfillsEquipmentNames() {
        val untouched =
            TemplateChangeGroupUi(
                category = TemplateChangeCategoryUi.Lifts,
                rows = listOf(LiftAddedUi(liftName = "Row", setCount = 3)),
            )
        val summary =
            TemplateChangeSummaryUi(
                groups =
                    listOf(
                        TemplateChangeGroupUi(
                            category = TemplateChangeCategoryUi.Equipment,
                            rows =
                                listOf(
                                    EquipmentSwappedUi(liftName = "Squat", oldId = "barbell", newId = "ssb"),
                                    EquipmentSwappedUi(liftName = "Press", oldId = null, newId = "unknown"),
                                ),
                        ),
                        untouched,
                    ),
            )

        val result =
            enrichTemplateChangeNames(
                summary,
                mapOf("barbell" to "Olympic Barbell", "ssb" to "Safety Squat Bar"),
            )

        val equipment =
            result.groups.single { it.category == TemplateChangeCategoryUi.Equipment }
                .rows.filterIsInstance<EquipmentSwappedUi>()
        assertEquals("Olympic Barbell", equipment[0].oldName)
        assertEquals("Safety Squat Bar", equipment[0].newName)
        assertNull(equipment[1].oldName)
        assertNull(equipment[1].newName)
        assertEquals(
            untouched,
            result.groups.single { it.category == TemplateChangeCategoryUi.Lifts },
        )
    }
}

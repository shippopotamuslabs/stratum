package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramSheetSelectionStoreTest {

    @Test
    @DisplayName("begin seeds the tapped lift, toggle appends and removes in order, clear leaves select mode")
    fun beginToggleAndClear() {
        val harness = Harness(twoDays())
        val first = ProgramSheetLiftRef("a", "a1")
        val second = ProgramSheetLiftRef("b", "b1")

        harness.store.begin(first)
        assertTrue(harness.store.selection.value.active)
        assertEquals(listOf(first), harness.store.selection.value.ordered)

        harness.store.toggle(second)
        harness.store.toggle(ProgramSheetLiftRef("a", "a2"))
        assertEquals(
            listOf(first, second, ProgramSheetLiftRef("a", "a2")),
            harness.store.selection.value.ordered,
        )

        harness.store.toggle(first)
        assertEquals(listOf(second, ProgramSheetLiftRef("a", "a2")), harness.store.selection.value.ordered)

        harness.store.clear()
        assertFalse(harness.store.selection.value.active)
        assertEquals(emptyList<ProgramSheetLiftRef>(), harness.store.selection.value.ordered)
    }

    @Test
    @DisplayName("begin stays out of select mode when the pending cut cannot be put back")
    fun beginBlockedByStuckCut() {
        val harness = Harness(twoDays(), cutReturns = false)

        harness.store.begin(ProgramSheetLiftRef("a", "a1"))

        assertFalse(harness.store.selection.value.active)
    }

    @Test
    @DisplayName("addSet applies to the live days, publishes the undo, and keeps the selection")
    fun addSetPublishesUndo() {
        val harness = Harness(twoDays())
        harness.store.begin(ProgramSheetLiftRef("a", "a1"))
        harness.store.toggle(ProgramSheetLiftRef("b", "b1"))

        harness.store.addSet("Added a set")

        assertEquals(2, lift(harness.state.value.days, "a", "a1").sets.size)
        assertEquals(2, lift(harness.state.value.days, "b", "b1").sets.size)
        assertEquals("Added a set", harness.published?.first)
        assertEquals(setOf("a", "b"), harness.published?.second?.stepsByDayId?.keys)
        assertEquals(2, harness.store.selection.value.ordered.size)
    }

    @Test
    @DisplayName("delete removes the lifts, publishes the undo, and prunes the selection")
    fun deletePrunesSelection() {
        val harness = Harness(twoDays())
        harness.store.begin(ProgramSheetLiftRef("a", "a1"))
        harness.store.toggle(ProgramSheetLiftRef("a", "a2"))

        harness.store.delete("Deleted")

        assertEquals(listOf("a3"), ids(harness.state.value.days, "a"))
        assertEquals("Deleted", harness.published?.first)
        assertNotNull(harness.published?.second)
        assertEquals(emptyList<ProgramSheetLiftRef>(), harness.store.selection.value.ordered)
        assertTrue(harness.store.selection.value.active)
    }

    @Test
    @DisplayName("a selected lift that leaves the days another way is pruned from the selection")
    fun externalRemovalPrunesSelection() {
        val harness = Harness(twoDays())
        harness.store.begin(ProgramSheetLiftRef("a", "a1"))
        harness.scope.advanceUntilIdle()

        harness.state.value =
            CreateProgramUiState(
                days =
                    listOf(
                        day("a", listOf(lift("a2", sets("a2", 1)), lift("a3", sets("a3", 1)))),
                        day("b", listOf(lift("b1", sets("b1", 1)))),
                    ),
            )
        harness.scope.advanceUntilIdle()

        assertTrue(harness.store.selection.value.active)
        assertEquals(emptyList<ProgramSheetLiftRef>(), harness.store.selection.value.ordered)
    }

    private class Harness(
        days: List<CreateProgramDayUi>,
        cutReturns: Boolean = true,
    ) {
        val state = MutableStateFlow(CreateProgramUiState(days = days))
        val scope = TestScope()
        var published: Pair<String, ProgramSheetUndo>? = null
        val store =
            ProgramSheetSelectionStore(
                scope = scope,
                uiState = state,
                applyDays = { next -> state.value = state.value.copy(days = next) },
                publishUndo = { message, undo -> published = message to undo },
                returnPendingCut = { cutReturns },
            )
    }

    private fun twoDays(): List<CreateProgramDayUi> =
        listOf(
            day(
                "a",
                listOf(
                    lift("a1", sets("a1", 1)),
                    lift("a2", sets("a2", 1)),
                    lift("a3", sets("a3", 1)),
                ),
            ),
            day("b", listOf(lift("b1", sets("b1", 1)))),
        )

    private fun ids(days: List<CreateProgramDayUi>, dayId: String): List<String> =
        days.first { day -> day.id == dayId }.editState!!.lifts.map { lift -> lift.workoutLiftId }

    private fun lift(days: List<CreateProgramDayUi>, dayId: String, workoutLiftId: String): EditLiftState =
        days.first { day -> day.id == dayId }.editState!!.lifts
            .first { lift -> lift.workoutLiftId == workoutLiftId }

    private fun day(id: String, lifts: List<EditLiftState>): CreateProgramDayUi =
        CreateProgramDayUi(
            id = id,
            name = id,
            position = 0,
            isRest = false,
            editState =
                EditWorkoutUiState.Loaded(
                    workoutId = id,
                    workoutName = id,
                    lifts = lifts,
                ),
        )

    private fun lift(id: String, sets: List<EditSetState>): EditLiftState =
        EditLiftState(
            workoutLiftId = id,
            liftId = "catalog-$id",
            name = "Name $id",
            position = 0,
            sets = sets,
        )

    private fun sets(id: String, count: Int): List<EditSetState> =
        List(count) { index ->
            EditSetState(
                setId = "$id-${index + 1}",
                setNumber = index + 1,
                repMin = 8,
                repMax = 12,
                restSeconds = 90,
                rirGoal = 2,
                isTopSet = index == 0,
            )
        }
}

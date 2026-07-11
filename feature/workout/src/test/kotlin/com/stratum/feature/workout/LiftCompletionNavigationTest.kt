package com.stratum.feature.workout

import com.stratum.feature.workout.ui.RenderedLiftUi
import com.stratum.feature.workout.ui.RenderedSetUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("LiftCompletionNavigation")
class LiftCompletionNavigationTest {

    @Nested
    @DisplayName("isLiftComplete / isWorkoutFullyComplete")
    inner class CompletionFlags {
        @Test
        @DisplayName("lift is complete only when every set is logged")
        fun liftCompleteRequiresAllSets() {
            val incomplete =
                lift(
                    id = "a",
                    sets = listOf(testSet("a-1", true), testSet("a-2", false)),
                )
            val complete =
                lift(
                    id = "b",
                    sets = listOf(testSet("b-1", true), testSet("b-2", true)),
                )
            assertFalse(isLiftComplete(incomplete))
            assertTrue(isLiftComplete(complete))
            assertFalse(isLiftComplete(lift(id = "empty", sets = emptyList())))
        }

        @Test
        @DisplayName("workout is fully complete when every lift has all sets logged")
        fun workoutFullyComplete() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", true))),
                    lift("b", sets = listOf(testSet("b-1", true), testSet("b-2", true))),
                )
            assertTrue(isWorkoutFullyComplete(lifts))
            assertFalse(
                isWorkoutFullyComplete(
                    listOf(
                        lift("a", sets = listOf(testSet("a-1", true))),
                        lift("b", sets = listOf(testSet("b-1", false))),
                    ),
                ),
            )
            assertFalse(isWorkoutFullyComplete(emptyList()))
        }
    }

    @Nested
    @DisplayName("nextLiftAfterSetLogged — non-superset (#5)")
    inner class NonSuperset {
        @Test
        @DisplayName("finishing last set on a lift jumps to next lift with 0 completed sets")
        fun finishLiftJumpsToZeroComplete() {
            // A just finished; B has partial progress; C is untouched
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", true), testSet("a-2", true))),
                    lift("b", sets = listOf(testSet("b-1", true), testSet("b-2", false))),
                    lift("c", sets = listOf(testSet("c-1", false), testSet("c-2", false))),
                )
            assertEquals("c", nextLiftAfterSetLogged(currentLiftId = "a", lifts = lifts))
        }

        @Test
        @DisplayName("intermediate set on non-superset does not change focus")
        fun intermediateSetStays() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", true), testSet("a-2", false))),
                    lift("b", sets = listOf(testSet("b-1", false))),
                )
            assertNull(nextLiftAfterSetLogged(currentLiftId = "a", lifts = lifts))
        }

        @Test
        @DisplayName("wraps forward search when zero-complete lift is before current")
        fun wrapsToEarlierZeroComplete() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", false))),
                    lift("b", sets = listOf(testSet("b-1", true))),
                    lift("c", sets = listOf(testSet("c-1", true))),
                )
            assertEquals("a", nextLiftAfterSetLogged(currentLiftId = "c", lifts = lifts))
        }
    }

    @Nested
    @DisplayName("nextLiftAfterSetLogged — superset (#6)")
    inner class Superset {
        @Test
        @DisplayName("middle set advances to next incomplete member in group")
        fun middleSetNextIncompleteMember() {
            // Logged a set on A; both A and B still incomplete
            val lifts =
                listOf(
                    lift(
                        "a",
                        group = "g1",
                        sets = listOf(testSet("a-1", true), testSet("a-2", false)),
                    ),
                    lift(
                        "b",
                        group = "g1",
                        sets = listOf(testSet("b-1", false), testSet("b-2", false)),
                    ),
                    lift("c", sets = listOf(testSet("c-1", false))),
                )
            assertEquals("b", nextLiftAfterSetLogged(currentLiftId = "a", lifts = lifts))
        }

        @Test
        @DisplayName("wraps within group to earlier incomplete member")
        fun wrapsWithinGroup() {
            val lifts =
                listOf(
                    lift(
                        "a",
                        group = "g1",
                        sets = listOf(testSet("a-1", true), testSet("a-2", false)),
                    ),
                    lift(
                        "b",
                        group = "g1",
                        sets = listOf(testSet("b-1", true), testSet("b-2", true)),
                    ),
                )
            // After logging on B (now complete), next incomplete is A
            assertEquals("a", nextLiftAfterSetLogged(currentLiftId = "b", lifts = lifts))
        }

        @Test
        @DisplayName("when only current remains incomplete in group, stay put")
        fun onlyCurrentIncompleteStays() {
            val lifts =
                listOf(
                    lift(
                        "a",
                        group = "g1",
                        sets = listOf(testSet("a-1", true), testSet("a-2", false)),
                    ),
                    lift(
                        "b",
                        group = "g1",
                        sets = listOf(testSet("b-1", true), testSet("b-2", true)),
                    ),
                )
            assertNull(nextLiftAfterSetLogged(currentLiftId = "a", lifts = lifts))
        }

        @Test
        @DisplayName("superset fully done jumps to next outside group with 0 completed sets")
        fun groupDoneJumpsOutside() {
            val lifts =
                listOf(
                    lift(
                        "a",
                        group = "g1",
                        sets = listOf(testSet("a-1", true), testSet("a-2", true)),
                    ),
                    lift(
                        "b",
                        group = "g1",
                        sets = listOf(testSet("b-1", true), testSet("b-2", true)),
                    ),
                    lift(
                        "c",
                        sets = listOf(testSet("c-1", true), testSet("c-2", false)),
                    ),
                    lift(
                        "d",
                        sets = listOf(testSet("d-1", false), testSet("d-2", false)),
                    ),
                )
            assertEquals("d", nextLiftAfterSetLogged(currentLiftId = "b", lifts = lifts))
        }

        @Test
        @DisplayName("giant set of three cycles to next incomplete member")
        fun giantSetCycles() {
            val lifts =
                listOf(
                    lift("a", group = "g1", sets = listOf(testSet("a-1", true), testSet("a-2", false))),
                    lift("b", group = "g1", sets = listOf(testSet("b-1", false))),
                    lift("c", group = "g1", sets = listOf(testSet("c-1", false))),
                )
            assertEquals("b", nextLiftAfterSetLogged(currentLiftId = "a", lifts = lifts))
            assertEquals(
                "c",
                nextLiftAfterSetLogged(
                    currentLiftId = "b",
                    lifts =
                        listOf(
                            lifts[0],
                            lifts[1].copy(sets = listOf(testSet("b-1", true))),
                            lifts[2],
                        ),
                ),
            )
        }
    }

    @Nested
    @DisplayName("fully complete workout")
    inner class FullyComplete {
        @Test
        @DisplayName("next lift is null when all sets across all lifts are logged")
        fun noNextWhenWorkoutDone() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", true))),
                    lift("b", group = "g1", sets = listOf(testSet("b-1", true))),
                    lift("c", group = "g1", sets = listOf(testSet("c-1", true))),
                )
            assertTrue(isWorkoutFullyComplete(lifts))
            assertNull(nextLiftAfterSetLogged(currentLiftId = "c", lifts = lifts))
        }
    }

    @Test
    @DisplayName("projectSetComplete marks only the matching slot")
    fun projectSetCompleteMarksSlot() {
        val lifts =
            listOf(
                lift("a", sets = listOf(testSet("a-1", false), testSet("a-2", false))),
                lift("b", sets = listOf(testSet("b-1", false))),
            )
        val projected = projectSetComplete(lifts, "a-1")
        assertTrue(projected[0].sets[0].isComplete)
        assertFalse(projected[0].sets[1].isComplete)
        assertFalse(projected[1].sets[0].isComplete)
    }

    @Nested
    @DisplayName("decideAfterSetLogged")
    inner class DecideAfter {
        @Test
        @DisplayName("last set of workout requests finish sheet once")
        fun lastSetShowsFinishSheet() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", true))),
                    lift("b", sets = listOf(testSet("b-1", false))),
                )
            assertEquals(
                AfterSetLoggedDecision.ShowCompleteWorkoutSheet,
                decideAfterSetLogged(lifts, "b-1", alreadyAutoShownCompleteSheet = false),
            )
            assertEquals(
                AfterSetLoggedDecision.None,
                decideAfterSetLogged(lifts, "b-1", alreadyAutoShownCompleteSheet = true),
            )
        }

        @Test
        @DisplayName("finishing a mid-workout lift focuses next zero-complete lift")
        fun finishLiftFocusesNext() {
            val lifts =
                listOf(
                    lift("a", sets = listOf(testSet("a-1", false))),
                    lift("b", sets = listOf(testSet("b-1", false))),
                )
            assertEquals(
                AfterSetLoggedDecision.FocusLift("b"),
                decideAfterSetLogged(lifts, "a-1", alreadyAutoShownCompleteSheet = false),
            )
        }
    }

    private fun lift(
        id: String,
        group: String? = null,
        sets: List<RenderedSetUi>,
    ): RenderedLiftUi =
        RenderedLiftUi(
            workoutLiftId = id,
            liftId = id,
            liftName = id,
            sets = sets,
            supersetGroupId = group,
        )

    private fun testSet(
        slotKey: String,
        isComplete: Boolean,
    ): RenderedSetUi =
        RenderedSetUi(
            slotKey = slotKey,
            positionLabel = "1",
            setTypeLabel = "Work",
            repRangeLabel = null,
            rirLabel = "2",
            recommendedWeightLabel = "135",
            recommendedRepsLabel = "8",
            loggedWeightLabel = if (isComplete) "135" else null,
            loggedRepsLabel = if (isComplete) "8" else null,
            isComplete = isComplete,
            rirGoal = 2,
            repMin = 6,
            repMax = 10,
            recommendedWeightAmount = 135.0,
            recommendedReps = 8,
        )
}

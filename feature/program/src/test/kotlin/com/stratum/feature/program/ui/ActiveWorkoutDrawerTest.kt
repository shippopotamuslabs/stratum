package com.stratum.feature.program.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.stratum.core.ui.component.StratumNavigationBar
import com.stratum.core.ui.component.StratumNavigationBarItem
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.theme.LocalStratumHazeState
import com.stratum.core.ui.theme.StratumGlassChrome
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import dev.chrisbanes.haze.rememberHazeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The STR-156 / STR-162 field regressions, as an interaction test: a workout
 * is minimized, and its drawer must be FULLY reachable over the glass nav
 * bar — visible, geometrically clear of the bar, and tappable (body tap and
 * chevron both resume; the delete button opens the discard confirm, whose
 * confirm fires the cancel callback).
 *
 * The harness replicates `MainTabShell`'s hosting exactly: the shared
 * HazeState is provided, the glass nav bar sits in the shell scaffold's
 * bottom slot, and the shell reserves NO bottom space for the content —
 * the composition in which the drawer twice went wrong on device. On the
 * STR-156 structure (drawer inside the content, no nav offset) the geometry
 * assertions fail and the body tap lands on the nav bar instead of the
 * drawer; on the STR-162 numbers (full footprint clearance) they pass.
 * Robolectric runs with zero window insets, so the expected clearance here
 * is exactly the bar height — the inset half of the formula is pinned in
 * StratumHazeStylesTest (dockClearance).
 *
 * One [runComposeUiTest] environment (a second never reaches idle — see
 * GlassHostActivationTest), and the test clock never auto-advances: the
 * drawer's elapsed timer loops on a 1s delay forever by design, so an
 * auto-advancing clock would chase it forever; parked, idling flushes
 * only the work each tap schedules at the current virtual time.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("Active-workout drawer over the glass nav")
class ActiveWorkoutDrawerTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("minimized drawer is fully above the nav, resumes on tap, and deletes via its confirm dialog")
    fun drawerIsReachableAndTappable() {
        var expandedWorkoutId: String? = null
        var cancelClicks = 0
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
                    CompositionLocalProvider(
                        LocalStratumHazeState provides rememberHazeState(),
                    ) {
                        StratumScaffold(
                            bottomBar = {
                                StratumNavigationBar(
                                    items = TabItems,
                                    selectedIndex = 1,
                                    onItemSelected = {},
                                )
                            },
                        ) { _ ->
                            // Glass shell rule (§12.8): no bottom space
                            // reserved for the tab content.
                            Box(modifier = Modifier.fillMaxSize()) {
                                ProgramsScreen(
                                    state = LoadedState,
                                    callbacks = ProgramsScreenCallbacks(),
                                    onSwapLiftClick = { _, _ -> },
                                    activeWorkoutBar =
                                        MinimizedWorkoutState(
                                            workoutId = "w-1",
                                            workoutName = "Minimal Lower A",
                                            nextLiftName = "Barbell Romanian Deadlift",
                                            startedAtEpochMilli = System.currentTimeMillis(),
                                        ),
                                    onExpandWorkoutClick = { expandedWorkoutId = it },
                                    onCancelWorkoutClick = { cancelClicks++ },
                                )
                            }
                        }
                    }
                }
            }
            settle()

            // Presence: every drawer element is displayed — name/timer ride in
            // the merged bar node found by the next-lift text; both buttons
            // are their own nodes.
            val drawer = onNodeWithText("Barbell Romanian Deadlift")
            drawer.assertIsDisplayed()
            onNodeWithContentDescription("Cancel Workout").assertIsDisplayed()
            onNodeWithContentDescription("Resume Workout").assertIsDisplayed()

            // Geometry: the drawer's bottom edge rests exactly on the nav
            // bar's top edge (zero insets under Robolectric), never behind
            // it. The STR-162 under-lift failed this by the missing inset
            // plus the s12 tuck; the STR-156 structure failed it outright.
            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val navTopPx = root.bottom - with(density) { StratumGlassChrome.bottomBarHeight.toPx() }
            val drawerBounds = drawer.fetchSemanticsNode().boundsInRoot
            assertTrue(
                drawerBounds.bottom <= navTopPx + 2f,
                "drawer bottom ${drawerBounds.bottom} must rest at or above the nav top $navTopPx",
            )
            assertTrue(
                drawerBounds.bottom >= navTopPx - 2f,
                "drawer bottom ${drawerBounds.bottom} must dock ON the nav top $navTopPx, not float above it",
            )

            // Body tap resumes the workout (on the occluded structure this
            // tap hit the nav bar and nothing was recorded).
            drawer.performClick()
            settle()
            assertEquals("w-1", expandedWorkoutId)

            // Chevron tap resumes too.
            expandedWorkoutId = null
            onNodeWithContentDescription("Resume Workout").performClick()
            settle()
            assertEquals("w-1", expandedWorkoutId)

            // Delete button opens the discard confirm; confirming fires the
            // cancel callback and dismisses the dialog.
            onNodeWithContentDescription("Cancel Workout").performClick()
            settle()
            onNodeWithText("Discard Workout?").assertIsDisplayed()
            onNodeWithText("Discard").performClick()
            settle()
            assertEquals(1, cancelClicks)
            onAllNodesWithText("Discard Workout?").assertCountEquals(0)
        }
    }

    private companion object {
        val TabItems =
            listOf(
                StratumNavigationBarItem(
                    label = "Dashboard",
                    icon = Icons.Outlined.Dashboard,
                    selectedIcon = Icons.Filled.Dashboard,
                ),
                StratumNavigationBarItem(
                    label = "Program",
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    selectedIcon = Icons.AutoMirrored.Filled.ListAlt,
                ),
                StratumNavigationBarItem(
                    label = "Gym",
                    icon = Icons.Outlined.FitnessCenter,
                    selectedIcon = Icons.Filled.FitnessCenter,
                ),
            )

        val LoadedState =
            ProgramsUiState.Loaded(
                program = ProgramViewState(programId = "p-1", name = "Test Program"),
                upperVolumeByMuscle = emptyList(),
                lowerVolumeByMuscle = emptyList(),
                scheduleItems =
                    listOf(
                        ScheduleItemState.Workout(
                            workout =
                                WorkoutViewState(
                                    workoutId = "w-1",
                                    name = "Minimal Lower A",
                                    position = 0,
                                    lifts =
                                        listOf(
                                            WorkoutLiftLineState(
                                                workoutLiftId = "wl-1",
                                                liftId = "barbell-romanian-deadlift",
                                                name = "Barbell Romanian Deadlift",
                                                setCount = 3,
                                                repRange = "8-12",
                                                topSetRpe = "@8",
                                                position = 0,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.settle() {
    // The clock never auto-advances in this test (see the class doc), so
    // idling only flushes work scheduled at the current virtual time —
    // recompositions from the tap under test — and never chases the
    // drawer's 1s elapsed timer into the future.
    waitForIdle()
}

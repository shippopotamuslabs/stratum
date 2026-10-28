package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * STR-233 regression: the landed position of [animateScrollToLiftTop] against a real
 * LazyList. Item offsets are content-space — at rest item 0 sits at offset 0 with
 * `viewportStartOffset == -beforeContentPadding` — so the pin line (the top edge of
 * the content area under the overlay) is offset 0. STR-193 targeted
 * `beforeContentPadding`, landing every card one top-padding below the pin.
 *
 * Robolectric display here is 320x470 px at density 1.0, so the viewport is 470px and
 * the top padding is 120px. Heights [150, 180, 150, 150] put item 2's top at offset
 * 330 (partially visible) and item 3 off-screen.
 *
 * All scenarios run inside ONE @Test method on purpose: in this Robolectric setup a
 * class with two or more runComposeUiTest @Test methods wedges the test JVM on the
 * second method (STR-162). Scenarios are separate helpers so a failure's stack and
 * message still name the case.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("Workout pin scroll landed position (STR-233)")
class WorkoutPinScrollTest {

    private class PinState(
        initialIndex: Int = 0,
        initialOffsetPx: Int = 0,
        heightsDp: List<Int>,
        bottomPaddingDp: Int,
    ) {
        val listState = LazyListState(initialIndex, initialOffsetPx)
        var heights by mutableStateOf(heightsDp)
        var bottomPaddingDp by mutableStateOf(bottomPaddingDp)
        var requestTarget by mutableStateOf(-1)
        var requestNonce by mutableStateOf(0)
        val measuredHeightsPx = mutableStateMapOf<String, Int>()
        var mutation: ((PinState) -> Unit)? = null
        val ids = listOf("a", "b", "c", "d")
    }

    @Composable
    private fun PinList(state: PinState) {
        LaunchedEffect(state.requestNonce) {
            val target = state.requestTarget
            if (state.requestNonce > 0 && target >= 0) {
                state.listState.animateScrollToLiftTop(
                    targetIndex = target,
                    liftIds = state.ids,
                    itemHeightsPx = state.measuredHeightsPx.toMap(),
                    spacingPx = 0,
                )
            }
        }
        LaunchedEffect(state.requestNonce) {
            val mutation = state.mutation
            if (state.requestNonce > 0 && mutation != null) {
                delay(MUTATION_DELAY_MS)
                mutation(state)
            }
        }
        Box(Modifier.fillMaxWidth().height(700.dp)) {
            LazyColumn(
                state = state.listState,
                contentPadding = PaddingValues(top = 120.dp, bottom = state.bottomPaddingDp.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.ids, key = { _, id -> id }) { index, id ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(state.heights[index].dp)
                            .onSizeChanged { state.measuredHeightsPx[id] = it.height },
                    )
                }
            }
        }
    }

    /** Fires a scroll to [targetIndex] in a fresh composition and asserts the landing. */
    @OptIn(ExperimentalTestApi::class)
    private fun runPinScenario(state: PinState, targetIndex: Int, expectedOffset: Int) =
        runComposeUiTest {
            setContent { PinList(state) }
            runOnIdle {
                state.requestTarget = targetIndex
                state.requestNonce = 1
            }
            runOnIdle { assertLandedAt(state, targetIndex, expectedOffset) }
        }

    private fun assertLandedAt(state: PinState, targetIndex: Int, expectedOffset: Int) {
        val info = state.listState.layoutInfo
        val offset =
            info.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset
        assertTrue(offset != null && abs(offset - expectedOffset) <= TOLERANCE_PX) {
            "target $targetIndex should land at offset $expectedOffset but was $offset " +
                "(viewportStart=${info.viewportStartOffset}, beforePad=${info.beforeContentPadding}, " +
                "items=${info.visibleItemsInfo.map { it.index to it.offset }})"
        }
    }

    /** Baseline: target partially visible, static layout. */
    private fun scenarioBaseline() =
        runPinScenario(
            state = PinState(heightsDp = listOf(150, 180, 150, 150), bottomPaddingDp = 100),
            targetIndex = 2,
            expectedOffset = 0,
        )

    /**
     * The item above the target grows mid-animation (a just-logged set's row landing
     * in the previous card after the delta was measured): the settle correction must
     * still land the target on the pin line.
     */
    private fun scenarioGrowAboveTarget() {
        val state = PinState(heightsDp = listOf(150, 180, 150, 150), bottomPaddingDp = 100)
        state.mutation = { it.heights = listOf(150, 480, 150, 150) }
        runPinScenario(state, targetIndex = 2, expectedOffset = 0)
    }

    /** The item above the target shrinks mid-animation (a completed card collapsing). */
    private fun scenarioShrinkAboveTarget() {
        val state = PinState(heightsDp = listOf(150, 180, 150, 150), bottomPaddingDp = 100)
        state.mutation = { it.heights = listOf(150, 80, 150, 150) }
        runPinScenario(state, targetIndex = 2, expectedOffset = 0)
    }

    /**
     * Last lift: not composed, and the bottom (dock) padding collapses to the
     * production pin spacer mid-animation — still lands on the pin line.
     */
    private fun scenarioLastLiftDockCollapseToSpacer() {
        val state = PinState(heightsDp = listOf(150, 150, 150, 150), bottomPaddingDp = 480)
        state.mutation = { it.bottomPaddingDp = 200 }
        runPinScenario(state, targetIndex = 3, expectedOffset = 0)
    }

    /**
     * Last lift with the bottom padding collapsed BELOW the pin spacer: max scroll
     * falls 120px short of the pin, so the closest physically reachable landing is
     * offset 120 — the spacer sizing is the only guard for this case.
     */
    private fun scenarioLastLiftDockCollapseBelowSpacer() {
        val state = PinState(heightsDp = listOf(150, 150, 150, 150), bottomPaddingDp = 480)
        state.mutation = { it.bottomPaddingDp = 80 }
        runPinScenario(state, targetIndex = 3, expectedOffset = 120)
    }

    /** Target above the current position: an upward jump lands on the pin line too. */
    private fun scenarioUpwardJump() =
        runPinScenario(
            state =
                PinState(
                    initialIndex = 2,
                    initialOffsetPx = 100,
                    heightsDp = listOf(150, 180, 150, 150),
                    bottomPaddingDp = 100,
                ),
            targetIndex = 0,
            expectedOffset = 0,
        )

    @Test
    @DisplayName("every landing path puts the target's top edge on the pin line (offset 0)")
    fun pinLandings() {
        scenarioBaseline()
        scenarioGrowAboveTarget()
        scenarioShrinkAboveTarget()
        scenarioLastLiftDockCollapseToSpacer()
        scenarioLastLiftDockCollapseBelowSpacer()
        scenarioUpwardJump()
    }

    private companion object {
        const val MUTATION_DELAY_MS = 60L
        const val TOLERANCE_PX = 2
    }
}

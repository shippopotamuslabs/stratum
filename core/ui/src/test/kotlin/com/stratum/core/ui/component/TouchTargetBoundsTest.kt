package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.workoutlog.RepsEntryMode
import com.stratum.core.ui.component.workoutlog.StratumInlineNumericCell
import com.stratum.core.ui.component.workoutlog.StratumSplitRepsCell
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetColumnBadge
import com.stratum.core.ui.theme.StratumTheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * §12.5 regression lock (STR-145): the custom interactives whose visuals are
 * smaller than 48dp must still report >= 48dp touch bounds. Each component
 * here was a sub-48 target before the pass; if a future change drops a
 * sizeIn floor or the outer-target split, these bounds shrink and the test
 * fails. One deliberate exception (STR-180): the set-log RIR corner badge,
 * whose placement on the reps cell's corner is locked instead.
 *
 * All checks live in ONE test method as sequential runComposeUiTest blocks:
 * under this module's JUnit 5 + Robolectric setup a second compose test
 * method in the same class wedges the test JVM (reproduced locally and in
 * CI on STR-145's first pipeline), while sequential blocks in one method
 * run clean.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("Touch target bounds (§12.5)")
class TouchTargetBoundsTest {

    /** Layout bounds of the node's semantics entry, in dp. */
    private fun SemanticsNodeInteraction.boundsDp(density: Density): Rect {
        val px = fetchSemanticsNode().boundsInRoot
        return Rect(
            left = px.left / density.density,
            top = px.top / density.density,
            right = px.right / density.density,
            bottom = px.bottom / density.density,
        )
    }

    private fun assertTargetAtLeast48(name: String, bounds: Rect, width: Boolean = true) {
        if (width) {
            assertTrue(bounds.width >= 48f, "$name width: ${bounds.width}dp")
        }
        assertTrue(bounds.height >= 48f, "$name height: ${bounds.height}dp")
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("every custom interactive target is at least 48dp")
    fun allCustomTargetsAreAtLeast48dp() {
        runComposeUiTest { checkRirBadge() }
        runComposeUiTest { checkRirCornerBadge() }
        runComposeUiTest { checkSetColumnBadge() }
        runComposeUiTest { checkInlineNumericCell() }
        runComposeUiTest { checkSplitRepsCell() }
        runComposeUiTest { checkActiveBadge() }
        runComposeUiTest { checkClickableSurface() }
        runComposeUiTest { checkTextFieldClear() }
        runComposeUiTest { checkAssistChipDismiss() }
        runComposeUiTest { checkDurationStepper() }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkRirBadge() {
        setContent {
            StratumTheme(darkTheme = true) {
                RirTargetBadge(rirGoal = 2, onClick = {})
            }
        }
        assertTargetAtLeast48("RIR badge", onNode(hasClickAction()).boundsDp(density))
    }

    /**
     * STR-180 exception: the set-log corner badge ([RirCornerBadge]) deliberately
     * reports only its visual bounds — a 48dp target there covers the reps cell
     * it floats on. The lock is the placement: rendered the way the set-log row
     * renders it (aligned to the cell's top end), the badge's bounds are exactly
     * the corner circle, centered on the cell's top-right corner (half in, half
     * out). The row's golden panels pin the full composition.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkRirCornerBadge() {
        setContent {
            StratumTheme(darkTheme = true) {
                Box(
                    modifier =
                        Modifier
                            .width(120.dp)
                            .height(48.dp)
                            .semantics { contentDescription = "Reps cell" },
                ) {
                    RirCornerBadge(
                        rirGoal = 2,
                        onClick = {},
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        val badge = onNode(hasClickAction()).boundsDp(density)
        val cell = onNodeWithContentDescription("Reps cell").boundsDp(density)
        val badgeDp = RirCornerBadgeSize.value
        assertTrue(
            kotlin.math.abs(badge.width - badgeDp) <= 1f,
            "corner badge width: ${badge.width}dp, expected ${badgeDp}dp",
        )
        assertTrue(
            kotlin.math.abs(badge.height - badgeDp) <= 1f,
            "corner badge height: ${badge.height}dp, expected ${badgeDp}dp",
        )
        assertTrue(
            kotlin.math.abs(badge.center.x - cell.right) <= 1f,
            "corner badge center x: ${badge.center.x}dp, cell right: ${cell.right}dp",
        )
        assertTrue(
            kotlin.math.abs(badge.center.y - cell.top) <= 1f,
            "corner badge center y: ${badge.center.y}dp, cell top: ${cell.top}dp",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkSetColumnBadge() {
        setContent {
            StratumTheme(darkTheme = true) {
                WorkoutLogSetColumnBadge(onClick = {}) {
                    Text("W")
                }
            }
        }
        assertTargetAtLeast48("set badge", onNode(hasClickAction()).boundsDp(density))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkInlineNumericCell() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumInlineNumericCell(
                    value = "135",
                    placeholder = "lb",
                    enabled = true,
                    onActivate = {},
                    modifier = Modifier.width(72.dp),
                )
            }
        }
        assertTargetAtLeast48(
            "numeric cell",
            onNode(hasClickAction()).boundsDp(density),
            width = false,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkSplitRepsCell() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumSplitRepsCell(
                    fullRepsText = "8",
                    partialRepsText = "2",
                    fullPlaceholder = "Full",
                    selectedMode = RepsEntryMode.Full,
                    enabled = true,
                    isActive = false,
                    completed = false,
                    onSelectFull = {},
                    onSelectPartial = {},
                    modifier = Modifier.width(96.dp),
                )
            }
        }
        assertTargetAtLeast48(
            "full half",
            onNodeWithContentDescription("Full reps").boundsDp(density),
            width = false,
        )
        assertTargetAtLeast48(
            "partial half",
            onNodeWithContentDescription("Partial reps").boundsDp(density),
            width = false,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkActiveBadge() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumActiveBadge(label = "Active", active = false, onClick = {})
            }
        }
        assertTargetAtLeast48("active badge", onNode(hasClickAction()).boundsDp(density))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkClickableSurface() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumSurface(onClick = {}) {
                    Box(Modifier.size(8.dp))
                }
            }
        }
        assertTargetAtLeast48("surface", onNode(hasClickAction()).boundsDp(density))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkTextFieldClear() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumTextField(
                    value = "x",
                    onValueChange = {},
                    label = "Notes",
                    options = StratumTextFieldOptions(showClearButton = true),
                )
            }
        }
        assertTargetAtLeast48(
            "clear button",
            onNodeWithContentDescription("Clear").boundsDp(density),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkAssistChipDismiss() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumAssistChip(
                    label = "Hint",
                    onClick = {},
                    onDismissClick = {},
                    dismissContentDescription = "Dismiss hint",
                )
            }
        }
        assertTargetAtLeast48(
            "dismiss",
            onNodeWithContentDescription("Dismiss hint").boundsDp(density),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.checkDurationStepper() {
        setContent {
            StratumTheme(darkTheme = true) {
                StratumDurationStepper(
                    totalMinutes = 90,
                    onTotalMinutesChange = {},
                    minMinutes = 0,
                    maxMinutes = 600,
                    hoursLabel = "Hours",
                    minutesLabel = "Minutes",
                    incrementContentDescription = "Increment",
                    decrementContentDescription = "Decrement",
                )
            }
        }
        val nodes = onAllNodesWithContentDescription("Increment").fetchSemanticsNodes()
        assertTrue(nodes.size == 2, "expected two segment steppers, found ${nodes.size}")
        nodes.forEach { node ->
            val heightDp = node.boundsInRoot.height / density.density
            assertTrue(heightDp >= 48f, "stepper button height: ${heightDp}dp")
        }
    }
}

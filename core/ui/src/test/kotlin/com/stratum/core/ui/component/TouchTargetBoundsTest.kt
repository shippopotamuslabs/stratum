package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
 * here was a sub-48 target before the pass; if a future change drops the
 * [androidx.compose.material3.minimumInteractiveComponentSize] floor or the
 * outer-target split, these bounds shrink and the test fails.
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
    @DisplayName("RIR badge target is at least 48dp")
    fun rirBadgeTarget() =
        runComposeUiTest {
            setContent {
                StratumTheme(darkTheme = true) {
                    RirTargetBadge(rirGoal = 2, onClick = {})
                }
            }
            assertTargetAtLeast48("RIR badge", onNode(hasClickAction()).boundsDp(density))
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("set column badge target is at least 48dp")
    fun setColumnBadgeTarget() =
        runComposeUiTest {
            setContent {
                StratumTheme(darkTheme = true) {
                    WorkoutLogSetColumnBadge(onClick = {}) {
                        Text("W")
                    }
                }
            }
            assertTargetAtLeast48("set badge", onNode(hasClickAction()).boundsDp(density))
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("inactive numeric cell target is at least 48dp tall")
    fun inlineNumericCellTarget() =
        runComposeUiTest {
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("split reps halves are at least 48dp tall")
    fun splitRepsCellTargets() =
        runComposeUiTest {
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("inactive Active badge target is at least 48dp")
    fun activeBadgeTarget() =
        runComposeUiTest {
            setContent {
                StratumTheme(darkTheme = true) {
                    StratumActiveBadge(label = "Active", active = false, onClick = {})
                }
            }
            assertTargetAtLeast48("active badge", onNode(hasClickAction()).boundsDp(density))
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("clickable surface floors at 48dp whatever the content")
    fun surfaceTarget() =
        runComposeUiTest {
            setContent {
                StratumTheme(darkTheme = true) {
                    StratumSurface(onClick = {}) {
                        Box(Modifier.size(8.dp))
                    }
                }
            }
            assertTargetAtLeast48("surface", onNode(hasClickAction()).boundsDp(density))
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("text field clear target is at least 48dp")
    fun textFieldClearTarget() =
        runComposeUiTest {
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("assist chip dismiss target is at least 48dp")
    fun assistChipDismissTarget() =
        runComposeUiTest {
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    @DisplayName("duration stepper buttons are at least 48dp tall")
    fun durationStepperTargets() =
        runComposeUiTest {
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

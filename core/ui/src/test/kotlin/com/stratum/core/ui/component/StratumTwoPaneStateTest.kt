package com.stratum.core.ui.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * STR-336 regression: state remembered inside [StratumTwoPane]'s primary content must
 * survive posture changes. The old structure invoked primary from a different call site
 * per posture, so a fold discarded the primary's remembered state and a scrolled list
 * restarted at the top.
 *
 * One [runComposeUiTest] environment for the whole scenario (a second environment in
 * the same fork never reaches idle, same constraint as StratumTabScaffoldTest).
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("StratumTwoPane primary state retention")
class StratumTwoPaneStateTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    @DisplayName("primary list state instance and scroll index survive single, two-pane, hinge, and back")
    fun primaryStateSurvivesPostureChanges() {
        runComposeUiTest {
            var posture by mutableStateOf<PanePosture>(PanePosture.SinglePane)
            var listState: LazyListState? = null
            setContent {
                StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
                    StratumTwoPane(
                        posture = posture,
                        primary = {
                            val state = rememberLazyListState()
                            listState = state
                            LazyColumn(
                                state = state,
                                modifier = Modifier.testTag(LIST_TAG),
                            ) {
                                items(ITEM_COUNT) { index ->
                                    Text(
                                        text = "Item $index",
                                        modifier = Modifier.height(ITEM_HEIGHT),
                                    )
                                }
                            }
                        },
                        detail = { Text("detail pane") },
                    )
                }
            }

            onNodeWithText("detail pane").assertDoesNotExist()
            onNodeWithTag(LIST_TAG).performScrollToIndex(SCROLL_TARGET)
            waitForIdle()
            val stateBefore = listState
            val indexBefore = stateBefore?.firstVisibleItemIndex ?: -1
            assertTrue(
                indexBefore > 0,
                "list must be scrolled away from the top before flipping posture",
            )

            fun assertRetained(label: String) {
                assertSame(
                    stateBefore,
                    listState,
                    "list state instance must survive into $label",
                )
                assertEquals(
                    indexBefore,
                    listState?.firstVisibleItemIndex,
                    "scroll index must survive into $label",
                )
            }

            runOnIdle { posture = PanePosture.TwoPane(hinge = null) }
            waitForIdle()
            onNodeWithText("detail pane").assertExists()
            assertRetained("two-pane")

            runOnIdle { posture = paneFlatHingeFixture() }
            waitForIdle()
            assertRetained("hinged two-pane")

            runOnIdle { posture = PanePosture.SinglePane }
            waitForIdle()
            onNodeWithText("detail pane").assertDoesNotExist()
            assertRetained("single pane again")
        }
    }

    private companion object {
        const val LIST_TAG = "primary-list"
        const val ITEM_COUNT = 50
        const val SCROLL_TARGET = 20
        val ITEM_HEIGHT = 100.dp
    }
}

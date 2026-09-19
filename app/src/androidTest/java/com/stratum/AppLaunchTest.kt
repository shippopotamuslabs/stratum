package com.stratum

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App smoke test: launching [MainActivity] boots the real Hilt graph (Room, DataStore,
 * Firebase, App Check, billing) and composes the first frame. On a device with no
 * signed-in user the shell must settle on the sign-in gate.
 *
 * `waitUntil` (not bare idle assertions) because startup passes through animated
 * transient shell states before settling.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsSignInGateForSignedOutUser() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule
                .onAllNodesWithText("Sign in with Google")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }
}

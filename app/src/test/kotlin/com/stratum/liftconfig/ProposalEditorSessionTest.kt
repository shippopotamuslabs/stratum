package com.stratum.liftconfig

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import com.stratum.feature.workout.ui.samplesLb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProposalEditorSession")
class ProposalEditorSessionTest {

    private val prefs = FakeEquipmentProposalPreferences()
    private val dismiss = DismissEquipmentProposalUseCase(prefs, FakeLoggingPort())
    private val args = listOf(ProposalTierArgs(0.0, null, 2.5))
    private val context = ProposalEditContext.PostWorkout("proposal-1", listOf(62.5))

    @Test
    @DisplayName("Given empty tiers and no context when resolved then null input and no change")
    fun emptyResolvesToNull() {
        val session = ProposalEditorSession()

        assertFalse(session.resolveInput(emptyList(), null))
        assertNull(session.input.value)
    }

    @Test
    @DisplayName("Given proposal launch when resolved twice then second reports no change")
    fun repeatResolveReportsNoChange() {
        val session = ProposalEditorSession()

        assertTrue(session.resolveInput(args, context))
        assertEquals(ProposalInput(args, context), session.input.value)
        assertFalse(session.resolveInput(args, context))
    }

    @Test
    @DisplayName("Given stored context when dismissed then fingerprint is dismissed")
    fun dismissDelegatesToUseCase() = runTest {
        val session = ProposalEditorSession()
        session.resolveInput(args, context)

        session.dismissIfPresent(dismiss)

        assertEquals(setOf("proposal-1"), prefs.dismissedFingerprints)
    }

    @Test
    @DisplayName("Given no stored context when dismissed then nothing is dismissed")
    fun dismissWithoutContextDismissesNothing() = runTest {
        val session = ProposalEditorSession()
        session.resolveInput(emptyList(), null)

        session.dismissIfPresent(dismiss)

        assertTrue(prefs.dismissedFingerprints.isEmpty())
    }

    @Test
    @DisplayName("Given live warnings-only context when dismissed then nothing is dismissed")
    fun dismissLiveContextDismissesNothing() = runTest {
        val session = ProposalEditorSession()
        val live = ProposalEditContext.Live(listOf(62.5))

        assertTrue(session.resolveInput(args, live))
        assertEquals(listOf(62.5), session.input.value?.context?.samplesLb)

        session.dismissIfPresent(dismiss)

        assertTrue(prefs.dismissedFingerprints.isEmpty())
    }
}

package com.stratum.domain.program.generation.synthesis

import com.stratum.core.testing.featureflag.FakeFeatureFlagRepository
import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FlagGatedProgramSynthesisPortTest {

    private val fakeFlags = FakeFeatureFlagRepository()
    private val stubDelegate = RecordingSynthesisPort()
    private lateinit var gated: FlagGatedProgramSynthesisPort

    @BeforeEach
    fun setUp() {
        fakeFlags.clear()
        stubDelegate.reset()
        gated =
            FlagGatedProgramSynthesisPort(
                delegate = stubDelegate,
                flags = fakeFlags,
                gate = AppFeatureFlag.CLOUD_PROGRAM_SYNTHESIS,
            )
    }

    @Test
    @DisplayName("Given gate enabled when synthesize called then delegates to cloud port")
    fun delegatesWhenGateEnabled() = runTest {
        fakeFlags.setFlag(AppFeatureFlag.CLOUD_PROGRAM_SYNTHESIS, true)
        val context = GenerationContextTestFixtures.contextWith()

        val result = gated.synthesize(emptyMap(), context)

        assertSame(stubDelegate.canned, result)
        assertEquals(1, stubDelegate.invocations)
    }

    @Test
    @DisplayName("Given gate disabled when synthesize called then throws without touching delegate")
    fun failsFastWhenGateDisabled() = runTest {
        fakeFlags.setFlag(AppFeatureFlag.CLOUD_PROGRAM_SYNTHESIS, false)
        val context = GenerationContextTestFixtures.contextWith()

        val error =
            org.junit.jupiter.api.assertThrows<ProgramSynthesisException> {
                gated.synthesize(emptyMap(), context)
            }

        assertEquals(FlagGatedProgramSynthesisPort.MESSAGE_SYNTHESIS_PAUSED, error.message)
        assertEquals(0, stubDelegate.invocations)
    }

    private class RecordingSynthesisPort : ProgramSynthesisPort {
        val canned =
            ProgramSynthesisResult(
                sessions = emptyList(),
                plans = emptyList(),
                cycleSlotIndexByPosition = emptyList(),
                objective = SynthesisObjectiveBreakdown.ZERO,
                activeSlotIdsBySession = emptyList(),
                regionFilterBySession = emptyList(),
            )
        var invocations = 0
            private set

        fun reset() {
            invocations = 0
        }

        override suspend fun synthesize(
            weeklyTargets: WeeklyVolumeTargets,
            context: ProgramGenerationContext,
        ): ProgramSynthesisResult {
            invocations++
            return canned
        }
    }
}

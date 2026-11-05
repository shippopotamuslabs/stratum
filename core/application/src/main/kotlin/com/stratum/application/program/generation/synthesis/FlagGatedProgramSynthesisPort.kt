package com.stratum.application.program.generation.synthesis

import com.stratum.domain.featureflag.FeatureFlag
import com.stratum.application.featureflag.FeatureFlagRepository
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisOutcome
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisRefusal
import com.stratum.domain.program.Program

/**
 * [ProgramSynthesisPort] decorator enforcing a Remote Config kill-switch.
 *
 * When [gate] is disabled the call returns a [ProgramSynthesisOutcome.Refused]
 * branch ([ProgramSynthesisRefusal.FeatureDisabled]) and the delegate is never
 * invoked — no Storage upload, no Callable, no polling. Checking the flag also records
 * a `flag_exposure` event, so a kill (or re-enable) is attributable in Analytics and
 * Crashlytics flag state during triage.
 */
class FlagGatedProgramSynthesisPort(
    private val delegate: ProgramSynthesisPort,
    private val flags: FeatureFlagRepository,
    private val gate: FeatureFlag,
) : ProgramSynthesisPort {
    override suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisOutcome {
        if (!flags.isEnabled(gate)) {
            return ProgramSynthesisOutcome.Refused(ProgramSynthesisRefusal.FeatureDisabled)
        }
        return delegate.synthesize(weeklyTargets, context)
    }

    companion object {
        const val MESSAGE_SYNTHESIS_PAUSED =
            "Program generation is temporarily unavailable. Please try again later."
    }
}

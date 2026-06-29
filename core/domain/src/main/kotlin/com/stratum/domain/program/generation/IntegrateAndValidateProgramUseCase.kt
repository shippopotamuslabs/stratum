@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.integration.repairWeeklyVolume
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.schema.parseAndNormalizeProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.swap.LiftSwapResult
import com.stratum.domain.program.generation.swap.applySwaps
import com.stratum.domain.program.generation.swap.buildLiftSwapRequest
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import javax.inject.Inject

data class IntegrateAndValidateOptions(
    val skipLiftSwap: Boolean = false,
    val skipFinalValidation: Boolean = false,
)

class IntegrateAndValidateProgramUseCase @Inject constructor(
    private val liftSwapPort: LiftSwapPort,
) {
    suspend operator fun invoke(
        built: ProgramDraft,
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
        selectionsBySession: List<List<SelectedSlotLift>>,
        normalizationRequest: ProgramNormalizationRequest,
        options: IntegrateAndValidateOptions = IntegrateAndValidateOptions(),
    ): NormalizedProgram {
        var draft = repairWeeklyVolume(
            program = built,
            weeklyTargets = weeklyTargets,
            context = context,
        )

        if (!options.skipLiftSwap) {
            val swapRequest = buildLiftSwapRequest(draft, context, selectionsBySession)
            val swapResult = liftSwapPort.swapLifts(swapRequest).getOrElse {
                LiftSwapResult(swaps = emptyList())
            }
            draft = applySwaps(draft, swapResult.swaps)
        }

        val normalized = parseAndNormalizeProgram(draft, normalizationRequest)
        if (!options.skipFinalValidation) {
            validateProgramMetrics(normalized.toDraft(), context)
        }
        return normalized
    }
}

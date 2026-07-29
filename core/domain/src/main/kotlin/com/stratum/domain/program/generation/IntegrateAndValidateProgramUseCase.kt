@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.integration.repairWeeklyVolume
import com.stratum.domain.program.generation.naming.applyHeuristicProgramNames
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.schema.parseAndNormalizeProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.swap.LiftSwapResult
import com.stratum.domain.program.generation.swap.applySwaps
import com.stratum.domain.program.generation.swap.buildLiftSwapRequest
import com.stratum.domain.program.generation.swap.validateSwaps
import com.stratum.domain.program.generation.validation.ProgramGenerationValidationException
import com.stratum.domain.program.generation.validation.ValidationSeverity
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
        onPolishingProgram: suspend () -> Unit = {},
        onFinalizingProgram: suspend () -> Unit = {},
    ): NormalizedProgram {
        var draft =
            repairWeeklyVolume(
                program = built,
                weeklyTargets = weeklyTargets,
                context = context,
            )

        // Polish: optional cloud lift-swap, then deterministic names (always local).
        onPolishingProgram()
        val runSwap = !options.skipLiftSwap
        if (runSwap) {
            draft =
                applyOptionalLiftSwap(
                    draft = draft,
                    context = context,
                    selectionsBySession = selectionsBySession,
                    liftSwapPort = liftSwapPort,
                )
        }

        // Name after swap so region heuristics see final lift selections.
        draft = applyHeuristicProgramNames(program = draft, liftsById = context.liftsById)

        onFinalizingProgram()
        val normalized = parseAndNormalizeProgram(draft, normalizationRequest)
        if (!options.skipFinalValidation) {
            val report = validateProgramMetrics(normalized.toDraft(), context)
            val blockingCodes = setOf("UNSELECTED_ACTIVE_SLOT", "DUPLICATE_LIFT_IN_WORKOUT")
            val blocking =
                report.issues.filter { issue ->
                    issue.severity == ValidationSeverity.Error &&
                        issue.code in blockingCodes
                }
            if (blocking.isNotEmpty()) {
                throw ProgramGenerationValidationException(report)
            }
        }
        return normalized
    }
}

internal suspend fun applyOptionalLiftSwap(
    draft: ProgramDraft,
    context: ProgramGenerationContext,
    selectionsBySession: List<List<SelectedSlotLift>>,
    liftSwapPort: LiftSwapPort,
): ProgramDraft {
    val swapRequest = buildLiftSwapRequest(draft, context, selectionsBySession)
    val swapResult =
        liftSwapPort.swapLifts(swapRequest).getOrElse { LiftSwapResult(swaps = emptyList()) }
    val validSwaps =
        validateSwaps(
            swaps = swapResult.swaps,
            program = draft,
            candidateMap = swapRequest.candidateMap,
        )
    return applySwaps(draft, validSwaps)
}

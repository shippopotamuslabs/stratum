@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.integration.repairWeeklyVolume
import com.stratum.domain.program.generation.naming.ProgramNamingPort
import com.stratum.domain.program.generation.naming.ProgramNamingResult
import com.stratum.domain.program.generation.naming.applyProgramNames
import com.stratum.domain.program.generation.naming.buildProgramNamingRequest
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.schema.parseAndNormalizeProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.swap.LiftSwapResult
import com.stratum.domain.program.generation.swap.applySwaps
import com.stratum.domain.program.generation.swap.buildLiftSwapRequest
import com.stratum.domain.program.generation.validation.ProgramGenerationValidationException
import com.stratum.domain.program.generation.validation.ValidationSeverity
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class IntegrateAndValidateOptions(
    val skipLiftSwap: Boolean = false,
    val skipProgramNaming: Boolean = false,
    val skipFinalValidation: Boolean = false,
)

class IntegrateAndValidateProgramUseCase @Inject constructor(
    private val liftSwapPort: LiftSwapPort,
    private val programNamingPort: ProgramNamingPort,
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

        val runSwap = !options.skipLiftSwap
        val runNaming = !options.skipProgramNaming
        if (runSwap || runNaming) {
            onPolishingProgram()
            draft =
                finalizeProgramWithLlm(
                    draft = draft,
                    context = context,
                    selectionsBySession = selectionsBySession,
                    liftSwapPort = liftSwapPort,
                    programNamingPort = programNamingPort,
                    runSwap = runSwap,
                    runNaming = runNaming,
                )
        }

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

internal suspend fun finalizeProgramWithLlm(
    draft: ProgramDraft,
    context: ProgramGenerationContext,
    selectionsBySession: List<List<SelectedSlotLift>>,
    liftSwapPort: LiftSwapPort,
    programNamingPort: ProgramNamingPort,
    runSwap: Boolean,
    runNaming: Boolean,
): ProgramDraft =
    coroutineScope {
        val swapDeferred =
            if (runSwap) {
                async {
                    val swapRequest = buildLiftSwapRequest(draft, context, selectionsBySession)
                    liftSwapPort.swapLifts(swapRequest).getOrElse { LiftSwapResult(swaps = emptyList()) }
                }
            } else {
                null
            }
        val namingDeferred =
            if (runNaming) {
                async {
                    val namingRequest = buildProgramNamingRequest(draft, context)
                    programNamingPort.nameProgram(namingRequest).getOrElse {
                        ProgramNamingResult(programName = null, workoutNames = emptyList())
                    }
                }
            } else {
                null
            }
        var updated = draft
        swapDeferred?.await()?.let { swapResult ->
            updated = applySwaps(updated, swapResult.swaps)
        }
        namingDeferred?.await()?.let { namingResult ->
            updated = applyProgramNames(updated, namingResult)
        }
        updated
    }

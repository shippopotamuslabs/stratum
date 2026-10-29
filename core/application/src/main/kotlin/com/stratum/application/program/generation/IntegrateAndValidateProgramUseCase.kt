@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.application.program.generation

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.integration.repairWeeklyVolume
import com.stratum.domain.program.generation.naming.applyHeuristicProgramNames
import com.stratum.domain.program.generation.schema.NormalizedProgram
import com.stratum.domain.program.generation.schema.ProgramNormalizationRequest
import com.stratum.domain.program.generation.schema.parseAndNormalizeProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.validation.ProgramGenerationValidationException
import com.stratum.domain.program.generation.validation.ValidationSeverity
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import javax.inject.Inject

data class IntegrateAndValidateOptions(
    val skipFinalValidation: Boolean = false,
)

class IntegrateAndValidateProgramUseCase @Inject constructor() {
    suspend operator fun invoke(
        built: ProgramDraft,
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
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

        // Polish: deterministic program names (always local).
        onPolishingProgram()

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

package com.stratum.core.testing.program

import com.stratum.domain.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.domain.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.domain.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.program.ProgramGenerationTelemetryLogger
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort

/**
 * Full program-generation pipeline for tests.
 *
 * Defaults to [FakeProgramSynthesisPort] so JVM unit tests (`:core:domain`, feature modules)
 * stay native-free. Real CP-SAT runs in the Python solver service (`solver-python/`,
 * STR-45); there is no Kotlin solver anymore (STR-46).
 */
fun testProgramGenerationPipeline(
    programSynthesisPort: ProgramSynthesisPort = FakeProgramSynthesisPort(),
): ProgramGenerationPipeline =
    ProgramGenerationPipeline(
        planWeeklyVolume = PlanWeeklyVolumeUseCase(),
        buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
        integrateAndValidate = IntegrateAndValidateProgramUseCase(),
        programSynthesisPort = programSynthesisPort,
        telemetryLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
    )

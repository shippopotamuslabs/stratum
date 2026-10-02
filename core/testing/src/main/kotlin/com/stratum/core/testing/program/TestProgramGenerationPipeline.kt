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
 * do not require OR-Tools native libraries. Pass
 * [com.stratum.domain.solver.CpSatProgramSynthesizer] explicitly from
 * `:core:domain-solver-tests` when exercising real CP-SAT.
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

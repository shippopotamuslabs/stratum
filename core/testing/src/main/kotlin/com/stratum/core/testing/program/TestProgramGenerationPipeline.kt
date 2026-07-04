package com.stratum.core.testing.program

import com.stratum.domain.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.domain.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.domain.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.program.ProgramGenerationTelemetryLogger
import com.stratum.domain.program.generation.naming.ProgramNamingPort
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.solver.CpSatProgramSynthesizer

fun testProgramGenerationPipeline(
    liftSwapPort: LiftSwapPort = FakeLiftSwapPort(),
    programNamingPort: ProgramNamingPort = FakeProgramNamingPort(),
    programSynthesisPort: ProgramSynthesisPort = CpSatProgramSynthesizer(),
): ProgramGenerationPipeline =
    ProgramGenerationPipeline(
        planWeeklyVolume = PlanWeeklyVolumeUseCase(),
        buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
        integrateAndValidate = IntegrateAndValidateProgramUseCase(liftSwapPort, programNamingPort),
        programSynthesisPort = programSynthesisPort,
        telemetryLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
    )

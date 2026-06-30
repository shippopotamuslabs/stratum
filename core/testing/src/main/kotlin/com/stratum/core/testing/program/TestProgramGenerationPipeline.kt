package com.stratum.core.testing.program

import com.stratum.domain.program.generation.BuildProgramWorkoutsUseCase
import com.stratum.domain.program.generation.IntegrateAndValidateProgramUseCase
import com.stratum.domain.program.generation.PlanWeeklyVolumeUseCase
import com.stratum.domain.program.generation.pipeline.ProgramGenerationPipeline
import com.stratum.domain.program.generation.prescription.SlotSetAllocationPort
import com.stratum.domain.program.generation.swap.LiftSwapPort

fun testProgramGenerationPipeline(
    liftSwapPort: LiftSwapPort = FakeLiftSwapPort(),
    slotSetAllocationPort: SlotSetAllocationPort? = null,
): ProgramGenerationPipeline =
    ProgramGenerationPipeline(
        planWeeklyVolume = PlanWeeklyVolumeUseCase(),
        buildProgramWorkouts = BuildProgramWorkoutsUseCase(),
        integrateAndValidate = IntegrateAndValidateProgramUseCase(liftSwapPort),
        slotSetAllocationPort = slotSetAllocationPort,
    )

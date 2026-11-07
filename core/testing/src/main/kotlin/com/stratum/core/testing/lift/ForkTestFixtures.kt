package com.stratum.core.testing.lift

import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.application.lift.ForkEquipmentUseCase
import com.stratum.application.lift.ForkEquipmentWithTiersUseCase
import com.stratum.application.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.application.lift.SetLiftEquipmentUseCase

/**
 * Shared fork use-case graph for ViewModel tests: fresh validators. Keeps
 * DI-graph construction in one place so a constructor change touches one
 * file instead of every test setup.
 */
fun testForkEquipmentWithTiers(
    equipmentRepository: FakeEquipmentRepository,
    liftRepository: FakeLiftRepository,
): ForkEquipmentWithTiersUseCase =
    ForkEquipmentWithTiersUseCase(
        ForkEquipmentUseCase(
            equipmentRepository,
        ),
        SetEquipmentIncrementTiersUseCase(
            equipmentRepository,
        ),
        SetLiftEquipmentUseCase(liftRepository, equipmentRepository),
    )

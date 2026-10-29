package com.stratum.core.testing.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.application.lift.ForkEquipmentUseCase
import com.stratum.application.lift.ForkEquipmentWithTiersUseCase
import com.stratum.application.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.application.lift.SetLiftEquipmentUseCase

/**
 * Shared fork use-case graph for ViewModel tests: fresh validators, one
 * logger per use case. Keeps DI-graph construction in one place so a
 * constructor change touches one file instead of every test setup.
 */
fun testForkEquipmentWithTiers(
    equipmentRepository: FakeEquipmentRepository,
    liftRepository: FakeLiftRepository,
): ForkEquipmentWithTiersUseCase =
    ForkEquipmentWithTiersUseCase(
        ForkEquipmentUseCase(
            equipmentRepository,
            FakeLoggingPort(),
        ),
        SetEquipmentIncrementTiersUseCase(
            equipmentRepository,
            FakeLoggingPort(),
        ),
        SetLiftEquipmentUseCase(liftRepository, equipmentRepository, FakeLoggingPort()),
    )

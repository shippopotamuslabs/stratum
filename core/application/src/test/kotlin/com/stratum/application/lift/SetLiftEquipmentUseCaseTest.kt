package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds

class SetLiftEquipmentUseCaseTest {

    private val liftId = LiftId("cable-fly")
    private val cableId = EquipmentId("cable")

    @Test
    @DisplayName("Bodyweight equipment persists LoadType.Bodyweight on catalog lifts")
    fun bodyweightEquipmentSetsBodyweightLoadOnCatalog() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = false)))
        repo.equipmentNamesById = mapOf(SeedEquipmentIds.Bodyweight to "Bodyweight")
        val useCase = SetLiftEquipmentUseCase(repo, FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(liftId, SeedEquipmentIds.Bodyweight)

        assertEquals(SetLiftEquipmentResult.Success, result)
        val lift = repo.currentLifts.single()
        assertEquals(SeedEquipmentIds.Bodyweight, lift.equipmentId)
        assertEquals(LoadType.Bodyweight, lift.loadType)
    }

    @Test
    @DisplayName("Leaving bodyweight equipment restores Weighted")
    fun leavingBodyweightRestoresWeighted() = runTest {
        val repo =
            FakeLiftRepository(
                listOf(
                    sampleLift(
                        isCustom = true,
                        equipmentId = SeedEquipmentIds.Bodyweight,
                        loadType = LoadType.Bodyweight,
                    ),
                ),
            )
        repo.equipmentNamesById = mapOf(cableId to "Cables")
        val useCase = SetLiftEquipmentUseCase(repo, FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(liftId, cableId)

        assertEquals(SetLiftEquipmentResult.Success, result)
        val lift = repo.currentLifts.single()
        assertEquals(cableId, lift.equipmentId)
        assertEquals(LoadType.Weighted, lift.loadType)
    }

    @Test
    @DisplayName("Assisted is preserved when assigning non-bodyweight equipment")
    fun assistedPreservedOnWeightedEquipment() = runTest {
        val repo =
            FakeLiftRepository(
                listOf(sampleLift(isCustom = true, loadType = LoadType.Assisted)),
            )
        repo.equipmentNamesById = mapOf(cableId to "Cables")
        val useCase = SetLiftEquipmentUseCase(repo, FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(liftId, cableId)

        assertEquals(SetLiftEquipmentResult.Success, result)
        assertEquals(LoadType.Assisted, repo.currentLifts.single().loadType)
    }

    @Test
    @DisplayName("Clearing equipment leaves load type unchanged")
    fun clearingEquipmentLeavesLoadType() = runTest {
        val repo =
            FakeLiftRepository(
                listOf(sampleLift(isCustom = true, loadType = LoadType.Assisted)),
            )
        val useCase = SetLiftEquipmentUseCase(repo, FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(liftId, null)

        assertEquals(SetLiftEquipmentResult.Success, result)
        val lift = repo.currentLifts.single()
        assertEquals(null, lift.equipmentId)
        assertEquals(LoadType.Assisted, lift.loadType)
    }

    @Test
    @DisplayName("Tombstoned equipment cannot be persisted; live pick or clear succeeds")
    fun tombstonedEquipmentRejected() = runTest {
        val repo = FakeLiftRepository(listOf(sampleLift(isCustom = false, equipmentId = cableId)))
        val equipmentRepo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = emptyList(), isDeleted = true),
                Equipment(id = EquipmentId("live-eq"), name = "Live", incrementTiers = emptyList()),
            ),
        )
        val useCase = SetLiftEquipmentUseCase(repo, equipmentRepo, FakeLoggingPort())

        assertEquals(SetLiftEquipmentResult.EquipmentDeleted(cableId), useCase(liftId, cableId))
        assertEquals(cableId, repo.currentLifts.single().equipmentId)
        assertEquals(SetLiftEquipmentResult.Success, useCase(liftId, EquipmentId("live-eq")))
        assertEquals(SetLiftEquipmentResult.Success, useCase(liftId, null))
        assertEquals(null, repo.currentLifts.single().equipmentId)
    }

    @Test
    @DisplayName("Missing lift fails without writing")
    fun missingLiftFails() = runTest {
        val repo = FakeLiftRepository()
        val useCase = SetLiftEquipmentUseCase(repo, FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(liftId, cableId)

        assertEquals(SetLiftEquipmentResult.LiftNotFound(liftId), result)
        assertTrue(repo.currentLifts.isEmpty())
    }

    private fun sampleLift(
        isCustom: Boolean,
        equipmentId: EquipmentId? = null,
        loadType: LoadType = LoadType.Weighted,
    ): Lift =
        Lift(
            id = liftId,
            name = "My Cable Fly",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipmentId = equipmentId,
            enabled = true,
            isCustom = isCustom,
            loadType = loadType,
        )
}

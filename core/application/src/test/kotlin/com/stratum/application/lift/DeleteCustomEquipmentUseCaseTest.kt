package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.first
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

@DisplayName("DeleteCustomEquipmentUseCase")
class DeleteCustomEquipmentUseCaseTest {

    private val customId = EquipmentId("custom-eq")
    private val seedId = SeedEquipmentIds.Cable
    private val replacementId = EquipmentId("replacement-eq")

    private fun equipment(id: EquipmentId, name: String, isCustom: Boolean) =
        Equipment(id = id, name = name, incrementTiers = emptyList(), isCustom = isCustom)

    private fun lift(id: String, equipmentId: EquipmentId?) =
        Lift(
            id = LiftId(id),
            name = "Lift $id",
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            equipmentId = equipmentId,
            enabled = true,
            isCustom = true,
        )

    @Test
    @DisplayName("Tombstones custom equipment: row retained, hidden from the catalog")
    fun deletesCustomEquipmentAsTombstone() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(customId, "Custom Cable", true)))
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(customId)
        assertTrue(result.isSuccess)
        val retained = repo.current.single()
        assertEquals(customId, retained.id)
        assertTrue(retained.isDeleted)
        assertTrue(repo.observeEquipment().first().isEmpty())
    }

    @Test
    @DisplayName("Visible seed equipment now deletes successfully")
    fun deletesVisibleSeedEquipment() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(seedId, "Cables", false)))
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(seedId)
        assertTrue(result.isSuccess)
        assertTrue(repo.current.single().isDeleted)
    }

    @Test
    @DisplayName("Hidden system rows Default and Bodyweight cannot be deleted")
    fun failsWhenHiddenSystemRow() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                equipment(SeedEquipmentIds.Default, "Default", false),
                equipment(SeedEquipmentIds.Bodyweight, "Bodyweight", false),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        assertTrue(useCase(SeedEquipmentIds.Default).isFailure)
        assertTrue(useCase(SeedEquipmentIds.Bodyweight).isFailure)
        assertTrue(repo.current.none { it.isDeleted })
    }

    @Test
    @DisplayName("Fails when equipment is missing")
    fun failsWhenMissing() = runTest {
        val repo = FakeEquipmentRepository(initial = emptyList())
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        assertTrue(useCase(customId).isFailure)
    }

    @Test
    @DisplayName("Re-deleting tombstoned equipment succeeds idempotently")
    fun redeleteIsIdempotent() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(customId, "Custom Cable", true)))
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        assertTrue(useCase(customId).isSuccess)
        assertTrue(useCase(customId).isSuccess)
        assertTrue(repo.current.single().isDeleted)
    }

    @Test
    @DisplayName("Affected pointers with candidates but no replacement fails and changes nothing")
    fun missingReplacementFailsAtomically() = runTest {
        val liftRepo = FakeLiftRepository(listOf(lift("a", customId)))
        val repo = FakeEquipmentRepository(
            initial = listOf(
                equipment(customId, "Custom Cable", true),
                equipment(replacementId, "Replacement", true),
            ),
            liftRepository = liftRepo,
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(customId)
        assertTrue(result.isFailure)
        assertTrue(repo.current.none { it.isDeleted })
        assertEquals(customId, liftRepo.currentLifts.single().equipmentId)
    }

    @Test
    @DisplayName("Chosen replacement re-points every affected lift")
    fun replacementRepointsLifts() = runTest {
        val liftRepo = FakeLiftRepository(listOf(lift("a", customId), lift("b", customId), lift("c", replacementId)))
        val repo = FakeEquipmentRepository(
            initial = listOf(
                equipment(customId, "Custom Cable", true),
                equipment(replacementId, "Replacement", true),
            ),
            liftRepository = liftRepo,
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(customId, replacementId)
        assertTrue(result.isSuccess)
        assertTrue(liftRepo.currentLifts.all { it.equipmentId == replacementId })
        assertTrue(repo.current.first { it.id == customId }.isDeleted)
    }

    @Test
    @DisplayName("Tombstoned, hidden, or self replacement fails and changes nothing")
    fun invalidReplacementFails() = runTest {
        val liftRepo = FakeLiftRepository(listOf(lift("a", customId)))
        val tombstoned = equipment(EquipmentId("dead-eq"), "Dead", true).copy(isDeleted = true)
        val repo = FakeEquipmentRepository(
            initial = listOf(equipment(customId, "Custom Cable", true), tombstoned),
            liftRepository = liftRepo,
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        assertTrue(useCase(customId, EquipmentId("dead-eq")).isFailure)
        assertTrue(useCase(customId, SeedEquipmentIds.Bodyweight).isFailure)
        assertTrue(useCase(customId, customId).isFailure)
        assertTrue(repo.current.none { it.id == customId && it.isDeleted })
        assertEquals(customId, liftRepo.currentLifts.single().equipmentId)
    }

    @Test
    @DisplayName("No candidate falls back to Bodyweight and aligns load type")
    fun fallbackToBodyweight() = runTest {
        val liftRepo = FakeLiftRepository(listOf(lift("a", customId)))
        val repo = FakeEquipmentRepository(
            initial = listOf(equipment(customId, "Custom Cable", true)),
            liftRepository = liftRepo,
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(customId)
        assertTrue(result.isSuccess)
        val updated = liftRepo.currentLifts.single()
        assertEquals(SeedEquipmentIds.Bodyweight, updated.equipmentId)
        assertEquals(LoadType.Bodyweight, updated.loadType)
    }
}

package com.stratum.application.lift

import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.Equipment

@DisplayName("RenameEquipmentUseCase")
class RenameEquipmentUseCaseTest {

    private val equipmentId = EquipmentId("custom-eq")

    private fun equipment(id: EquipmentId, name: String) =
        Equipment(id = id, name = name, incrementTiers = emptyList(), isCustom = true)

    @Test
    @DisplayName("Valid name renames the row (trimmed) and returns Success")
    fun renamesWithTrimmedName() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(equipmentId, "Old Name")))
        val useCase = RenameEquipmentUseCase(repo)

        val result = useCase(equipmentId, "  New Name  ")

        assertEquals(RenameEquipmentResult.Success, result)
        assertEquals("New Name", repo.current.single().name)
    }

    @Test
    @DisplayName("Blank name returns InvalidName carrying the validation and writes nothing")
    fun rejectsBlankName() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(equipmentId, "Old Name")))
        val useCase = RenameEquipmentUseCase(repo)

        val result = useCase(equipmentId, "   ")

        assertTrue(result is RenameEquipmentResult.InvalidName)
        assertEquals(
            (CatalogNames.validateEquipmentName("   ") as CatalogNames.Validation.Invalid).message,
            (result as RenameEquipmentResult.InvalidName).validation.message,
        )
        assertEquals("Old Name", repo.current.single().name)
    }

    @Test
    @DisplayName("Repository write failure returns PersistenceFailed carrying the failure diagnostic")
    fun persistenceFailureOnWrite() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(equipmentId, "Old Name")))
        repo.failNextWrite = true
        val useCase = RenameEquipmentUseCase(repo)

        val result = useCase(equipmentId, "New Name")

        val failed = result as RenameEquipmentResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("RenameEquipment", diagnostic.tag)
        assertEquals(
            "Failed to rename equipment: Simulated equipment write failure",
            diagnostic.message,
        )
        assertTrue(diagnostic.cause is IllegalStateException)
        assertEquals("Old Name", repo.current.single().name)
    }
}

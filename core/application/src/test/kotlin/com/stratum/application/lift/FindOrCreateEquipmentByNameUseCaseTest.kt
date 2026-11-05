package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment

@DisplayName("FindOrCreateEquipmentByNameUseCase")
class FindOrCreateEquipmentByNameUseCaseTest {

    private val existingId = EquipmentId("cable-eq")

    private fun equipment(id: EquipmentId, name: String) =
        Equipment(id = id, name = name, incrementTiers = emptyList(), isCustom = true)

    @Test
    @DisplayName("Existing name resolves to the existing row without creating")
    fun findsExisting() = runTest {
        val repo = FakeEquipmentRepository(initial = listOf(equipment(existingId, "Cables")))
        val useCase = FindOrCreateEquipmentByNameUseCase(repo, FakeLoggingPort())

        val result = useCase("  Cables  ")

        assertEquals(FindOrCreateEquipmentResult.Success(existingId), result)
        assertEquals(1, repo.current.size)
    }

    @Test
    @DisplayName("Unknown name creates a custom row and returns its id")
    fun createsWhenMissing() = runTest {
        val repo = FakeEquipmentRepository(initial = emptyList())
        val useCase = FindOrCreateEquipmentByNameUseCase(repo, FakeLoggingPort())

        val result = useCase("Landmine")

        assertTrue(result is FindOrCreateEquipmentResult.Success)
        val created = repo.current.single()
        assertEquals(created.id, (result as FindOrCreateEquipmentResult.Success).equipmentId)
        assertEquals("Landmine", created.name)
        assertTrue(created.isCustom)
    }

    @Test
    @DisplayName("Blank name returns InvalidName and creates nothing")
    fun rejectsBlankName() = runTest {
        val repo = FakeEquipmentRepository(initial = emptyList())
        val useCase = FindOrCreateEquipmentByNameUseCase(repo, FakeLoggingPort())

        val result = useCase("   ")

        assertTrue(result is FindOrCreateEquipmentResult.InvalidName)
        assertTrue(repo.current.isEmpty())
    }

    @Test
    @DisplayName("Repository write failure returns PersistenceFailed with one error log")
    fun persistenceFailureOnCreate() = runTest {
        val repo = FakeEquipmentRepository(initial = emptyList())
        repo.failNextWrite = true
        val logging = FakeLoggingPort()
        val useCase = FindOrCreateEquipmentByNameUseCase(repo, logging)

        val result = useCase("Landmine")

        assertEquals(FindOrCreateEquipmentResult.PersistenceFailed, result)
        assertEquals(1, logging.errors.size)
        assertTrue(repo.current.isEmpty())
    }
}

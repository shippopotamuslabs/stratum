package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DeleteCustomEquipmentUseCase")
class DeleteCustomEquipmentUseCaseTest {

    private val customId = EquipmentId("custom-eq")
    private val seedId = EquipmentId("seed-eq")

    @Test
    @DisplayName("Tombstones custom equipment: row retained, hidden from the catalog")
    fun deletesCustomEquipmentAsTombstone() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = customId, name = "Custom Cable", incrementTiers = emptyList(), isCustom = true),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(customId)

        assertTrue(result.isSuccess)
        val retained = repo.current.single()
        assertEquals(customId, retained.id)
        assertEquals("Custom Cable", retained.name)
        assertTrue(retained.isDeleted)
        assertTrue(repo.observeEquipment().first().isEmpty())
    }

    @Test
    @DisplayName("Deletes referenced custom equipment without reassignment")
    fun deletesReferencedCustomEquipment() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = customId, name = "Custom Cable", incrementTiers = emptyList(), isCustom = true),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(customId)

        assertTrue(result.isSuccess)
        assertTrue(repo.current.single().isDeleted)
    }

    @Test
    @DisplayName("Fails when equipment is non-custom seed")
    fun failsWhenSeed() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = seedId, name = "Barbell", incrementTiers = emptyList(), isCustom = false),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(seedId)

        assertTrue(result.isFailure)
        assertEquals(1, repo.current.size)
    }

    @Test
    @DisplayName("Fails when equipment is missing")
    fun failsWhenMissing() = runTest {
        val repo = FakeEquipmentRepository(initial = emptyList())
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(customId)

        assertTrue(result.isFailure)
    }

    @Test
    @DisplayName("Re-deleting tombstoned equipment succeeds idempotently")
    fun redeleteIsIdempotent() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = customId, name = "Custom Cable", incrementTiers = emptyList(), isCustom = true),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        assertTrue(useCase(customId).isSuccess)
        assertTrue(useCase(customId).isSuccess)
        assertTrue(repo.current.single().isDeleted)
    }
}

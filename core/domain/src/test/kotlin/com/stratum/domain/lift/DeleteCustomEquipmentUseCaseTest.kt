package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
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
    @DisplayName("Deletes unreferenced custom equipment successfully")
    fun deletesUnreferencedCustomEquipment() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = customId, name = "Custom Cable", incrementTiers = emptyList(), isCustom = true),
            ),
        )
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(customId)

        assertTrue(result.isSuccess)
        assertTrue(repo.current.isEmpty())
    }

    @Test
    @DisplayName("Fails when equipment is referenced by lifts")
    fun failsWhenReferenced() = runTest {
        val repo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = customId, name = "Custom Cable", incrementTiers = emptyList(), isCustom = true),
            ),
        )
        repo.setLiftReferenceCount(customId, 2)
        val useCase = DeleteCustomEquipmentUseCase(repo, FakeLoggingPort())

        val result = useCase(customId)

        assertTrue(result.isFailure)
        assertEquals(1, repo.current.size)
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
}

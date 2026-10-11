package com.stratum.domain.lift

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@DisplayName("EquipmentPickResult.picksAfter")
class EquipmentPickResultTest {

    @Test
    @DisplayName("Given picks after the snapshot when collected then they are emitted")
    fun emitsNewerPicks() = runTest {
        val picks = EquipmentPickResult()
        picks.publish(EquipmentId("old-eq"))

        picks.picksAfter(picks.currentGeneration()).test {
            picks.publish(EquipmentId("new-eq"))
            assertEquals(EquipmentId("new-eq"), awaitItem().equipmentId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given only older picks when collected then nothing is emitted")
    fun ignoresOlderPicks() = runTest {
        val picks = EquipmentPickResult()
        picks.publish(EquipmentId("old-eq"))
        val snapshot = picks.currentGeneration()

        picks.picksAfter(snapshot).test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

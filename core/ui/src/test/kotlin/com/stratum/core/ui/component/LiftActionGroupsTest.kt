package com.stratum.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftActionGroupsTest {

    @Test
    @DisplayName("Given all entries when grouped then order matches the spec with remove last")
    fun fullSheetOrderMatchesSpec() {
        val groups =
            visibleLiftActionGroups(
                showEditEquipment = true,
                showRestTimes = true,
                showManageNotes = true,
                showPastNotes = true,
            )

        assertEquals(
            listOf(
                listOf(LiftActionItem.Swap, LiftActionItem.EditEquipment, LiftActionItem.Reorder),
                listOf(LiftActionItem.RestTimes, LiftActionItem.Superset),
                listOf(LiftActionItem.ManageNotes, LiftActionItem.PastNotes),
                listOf(LiftActionItem.Remove),
            ),
            groups,
        )
    }

    @Test
    @DisplayName("Given no optional entries when grouped then conditionals drop but remove stays last")
    fun legacySheetKeepsRemoveLast() {
        val groups =
            visibleLiftActionGroups(
                showEditEquipment = false,
                showRestTimes = false,
                showManageNotes = false,
                showPastNotes = false,
            )

        assertEquals(
            listOf(
                listOf(LiftActionItem.Swap, LiftActionItem.Reorder),
                listOf(LiftActionItem.Superset),
                listOf(LiftActionItem.Remove),
            ),
            groups,
        )
    }
}

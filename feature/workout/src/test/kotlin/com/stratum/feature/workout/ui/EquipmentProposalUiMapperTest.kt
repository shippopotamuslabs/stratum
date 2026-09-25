package com.stratum.feature.workout.ui

import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EquipmentProposalUiMapper")
class EquipmentProposalUiMapperTest {

    private val lb = Weight.WeightUnit.Lb
    private val tiers =
        listOf(IncrementTier(Weight(0.0, lb), Weight(50.0, lb), Weight(5.0, lb)))

    private fun proposal() =
        EquipmentIncrementProposal(
            id = "proposal-1",
            clusterEquipmentId = EquipmentId("pin"),
            liftIds = listOf(LiftId("lat-pulldown")),
            liftNames = listOf("Lat pulldown"),
            equipmentName = "Pin-Loaded Machine",
            sampleWeights = listOf(Weight(62.5, lb)),
            proposedTiers = tiers,
            primary =
                EquipmentIncrementRemedy.ForkCustomEquipment(
                    suggestedName = "Pin copy",
                    tiers = tiers,
                    assignLiftIds = listOf(LiftId("lat-pulldown")),
                ),
            alternatives = emptyList(),
            equipmentIsCustom = true,
            requiresBlastRadiusConfirmation = false,
            affectedInheritingLiftCount = 1,
        )

    @Test
    @DisplayName("Given baseline stops when mapped then the summary names the adder")
    fun adderStopsRenderInSummary() {
        val card =
            EquipmentProposalUiMapper.toCard(
                proposal(),
                baselineMicro = listOf(Weight(2.5, lb), Weight(5.0, lb)),
            )

        assertTrue(card.tierSummary.endsWith("+ adder 2.5/5"), card.tierSummary)
    }

    @Test
    @DisplayName("Given no stops when mapped then the summary is bands only")
    fun emptyMicroLeavesSummaryAlone() {
        val card = EquipmentProposalUiMapper.toCard(proposal(), baselineMicro = emptyList())

        assertEquals(
            EquipmentProposalUiMapper.formatTierSummary(tiers),
            card.tierSummary,
        )
    }

    @Test
    @DisplayName("Given stops when suffixed then the adder fragment appends")
    fun adderSuffixFormats() {
        assertEquals("", EquipmentProposalUiMapper.formatAdderSuffix(emptyList()))
        assertEquals(
            " + adder 2.5/5",
            EquipmentProposalUiMapper.formatAdderSuffix(
                listOf(Weight(2.5, lb), Weight(5.0, lb)),
            ),
        )
    }
}

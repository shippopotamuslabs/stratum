package com.stratum.feature.workout.ui

import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure-mapping tests for the post-workout "Edit steps" request: the editor target must
 * match the primary remedy's write destination and the drafts must carry the proposed
 * bands (UNIT_TESTING.md §1 — state-based, no mocks).
 */
@DisplayName("EquipmentProposalUiMapper.toEditRequest")
class EquipmentProposalEditRequestTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val latId = LiftId("lat-pulldown")
    private val proposed =
        listOf(
            IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
            IncrementTier(Weight(40.0, lb), null, Weight(5.0, lb)),
        )
    private val expectedArgs =
        listOf(
            ProposalTierArgs(0.0, 40.0, 2.5),
            ProposalTierArgs(40.0, null, 5.0),
        )

    @Test
    @DisplayName("Given amend primary when mapped then shared-equipment target with proposed tiers")
    fun amendMapsToSharedEquipmentEditor() {
        val proposal =
            proposal(
                primary =
                    EquipmentIncrementRemedy.AmendEquipmentTiers(
                        equipmentId = cableId,
                        tiers = proposed,
                    ),
            )

        val request = EquipmentProposalUiMapper.toEditRequest(proposal)

        assertEquals(cableId.value, request.target.equipmentId)
        assertEquals(expectedArgs, request.initialTiersLb)
        val context = checkNotNull(request.proposalContext as? ProposalEditContext.PostWorkout)
        assertEquals("test-proposal", context.proposalId)
        assertEquals(listOf(62.5), context.sampleWeightsLb)
        val destination = resolveEquipmentEditorDestination(request)
        assertTrue(destination is EquipmentEditorDestination.SharedEquipment)
        assertEquals(
            cableId.value,
            (destination as EquipmentEditorDestination.SharedEquipment).equipmentId,
        )
    }

    @Test
    @DisplayName("Given fork primary when mapped then shared equipment editor anchored on first assigned lift")
    fun forkMapsToSharedEquipmentAnchoredOnFirstAssignedLift() {
        val proposal =
            proposal(
                primary =
                    EquipmentIncrementRemedy.ForkCustomEquipment(
                        suggestedName = "Cables (custom)",
                        tiers = proposed,
                        assignLiftIds = listOf(latId),
                    ),
            )

        val request = EquipmentProposalUiMapper.toEditRequest(proposal)

        assertEquals(latId.value, request.liftId)
        assertEquals(cableId.value, request.target.equipmentId)
        assertEquals(expectedArgs, request.initialTiersLb)
        val destination = resolveEquipmentEditorDestination(request)
        assertTrue(destination is EquipmentEditorDestination.SharedEquipment)
        val shared = destination as EquipmentEditorDestination.SharedEquipment
        assertEquals(cableId.value, shared.equipmentId)
        assertEquals(latId.value, shared.sourceLiftId)
    }

    @Test
    @DisplayName("Given fork with no assigned lifts when mapped then falls back to first cluster lift")
    fun forkWithoutAssignLiftsFallsBackToClusterLift() {
        val proposal =
            proposal(
                primary =
                    EquipmentIncrementRemedy.ForkCustomEquipment(
                        suggestedName = "Cables (custom)",
                        tiers = proposed,
                        assignLiftIds = emptyList(),
                    ),
            )

        val request = EquipmentProposalUiMapper.toEditRequest(proposal)

        assertEquals(latId.value, request.liftId)
    }

    @Test
    @DisplayName("Given fork with no lifts anywhere when mapped then shared equipment editor")
    fun forkWithoutAnyLiftFallsBackToSharedEquipment() {
        val proposal =
            proposal(
                liftIds = emptyList(),
                liftNames = emptyList(),
                primary =
                    EquipmentIncrementRemedy.ForkCustomEquipment(
                        suggestedName = "Cables (custom)",
                        tiers = proposed,
                        assignLiftIds = emptyList(),
                    ),
            )

        val request = EquipmentProposalUiMapper.toEditRequest(proposal)

        val destination = resolveEquipmentEditorDestination(request)
        assertTrue(destination is EquipmentEditorDestination.SharedEquipment)
        assertEquals(
            cableId.value,
            (destination as EquipmentEditorDestination.SharedEquipment).equipmentId,
        )
        assertEquals(expectedArgs, request.initialTiersLb)
    }

    @Test
    @DisplayName("Given unassigned cluster when amend impossible then fork edit carries null equipment")
    fun forkWithoutEquipmentCarriesNullEquipmentId() {
        val proposal =
            proposal(
                clusterEquipmentId = null,
                primary =
                    EquipmentIncrementRemedy.ForkCustomEquipment(
                        suggestedName = "Custom",
                        tiers = proposed,
                        assignLiftIds = listOf(latId),
                    ),
            )

        val request = EquipmentProposalUiMapper.toEditRequest(proposal)

        assertNull(request.target.equipmentId)
        val destination =
            resolveEquipmentEditorDestination(request) as EquipmentEditorDestination.SharedEquipment
        assertNull(destination.equipmentId)
        assertEquals(latId.value, destination.sourceLiftId)
    }

    private fun proposal(
        primary: EquipmentIncrementRemedy,
        clusterEquipmentId: EquipmentId? = cableId,
        liftIds: List<LiftId> = listOf(latId),
        liftNames: List<String> = listOf("Lat pulldown"),
    ): EquipmentIncrementProposal =
        EquipmentIncrementProposal(
            id = "test-proposal",
            clusterEquipmentId = clusterEquipmentId,
            liftIds = liftIds,
            liftNames = liftNames,
            equipmentName = "Cables",
            sampleWeights = listOf(Weight(62.5, lb)),
            proposedTiers = proposed,
            primary = primary,
            alternatives = emptyList(),
            equipmentIsCustom = false,
            requiresBlastRadiusConfirmation = false,
            affectedInheritingLiftCount = 12,
        )
}

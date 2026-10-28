package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EquipmentIncrementProposalTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val customId = EquipmentId("custom-stack-1")
    private val latPulldown = LiftId("lat-pulldown")
    private val cableRow = LiftId("seated-cable-row")
    private val facePull = LiftId("face-pull")
    private val tricepPushdown = LiftId("tricep-pushdown")

    private val proposedTiers =
        listOf(
            IncrementTier(
                lowerBound = Weight(0.0, lb),
                upperBound = Weight(70.0, lb),
                increment = Weight(2.5, lb),
            ),
            IncrementTier(
                lowerBound = Weight(70.0, lb),
                upperBound = null,
                increment = Weight(5.0, lb),
            ),
        )

    private fun baseInput(
        equipmentId: EquipmentId? = cableId,
        equipmentName: String? = "Cables",
        isCustom: Boolean = false,
        inheritingCount: Int = 12,
        anomalousLiftIds: List<LiftId> = listOf(latPulldown),
        liftNamesById: Map<LiftId, String> =
            mapOf(
                latPulldown to "Lat pulldown",
                cableRow to "Seated cable row",
                facePull to "Face pull",
                tricepPushdown to "Tricep pushdown",
            ),
        sampleWeights: List<Weight> = listOf(Weight(62.5, lb), Weight(67.5, lb)),
        inferredStep: Double = 2.5,
        residualSetCount: Int = 2,
        distinctResidualAmounts: Int = 2,
        existingEquipmentNames: Collection<String> = emptyList(),
    ): EquipmentIncrementRankInput =
        EquipmentIncrementRankInput(
            equipmentId = equipmentId,
            equipmentName = equipmentName,
            isCustom = isCustom,
            inheritingCount = inheritingCount,
            anomalousLiftIds = anomalousLiftIds,
            liftNamesById = liftNamesById,
            sampleWeights = sampleWeights,
            proposedTiers = proposedTiers,
            inferredStep = inferredStep,
            residualSetCount = residualSetCount,
            distinctResidualAmounts = distinctResidualAmounts,
            existingEquipmentNames = existingEquipmentNames,
        )

    @Nested
    @DisplayName("rankEquipmentIncrementProposal")
    inner class Ranking {
        @Test
        @DisplayName(
            "Given seed with 12 inheritors when ranked then Fork primary, Amend alt, " +
                "no primary blast confirm (UI must confirm if user switches to Amend)",
        )
        fun seedManyInheritorsForks() {
            val proposal = rankEquipmentIncrementProposal(baseInput(inheritingCount = 12))
            assertTrue(proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment)
            val fork = proposal.primary as EquipmentIncrementRemedy.ForkCustomEquipment
            assertEquals("Lat pulldown stack", fork.suggestedName)
            assertEquals(listOf(latPulldown), fork.assignLiftIds)
            assertEquals(proposedTiers, fork.tiers)

            assertTrue(
                proposal.alternatives.any { it is EquipmentIncrementRemedy.AmendEquipmentTiers },
            )
            // Seed equipment: not custom. Primary is Fork → one-tap Accept needs no confirm.
            assertFalse(proposal.equipmentIsCustom)
            assertFalse(proposal.requiresBlastRadiusConfirmation)
            // UI contract: if the user picks Amend alternative, confirm because !equipmentIsCustom.
            assertTrue(
                !proposal.equipmentIsCustom &&
                    proposal.alternatives.any { it is EquipmentIncrementRemedy.AmendEquipmentTiers },
            )
            assertEquals(12, proposal.affectedInheritingLiftCount)
            assertEquals(cableId, proposal.clusterEquipmentId)
        }

        @Test
        @DisplayName("Given custom equipment when ranked then Amend is primary")
        fun customAmends() {
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(
                        equipmentId = customId,
                        equipmentName = "Life Fitness lat",
                        isCustom = true,
                        inheritingCount = 20,
                    ),
                )
            assertTrue(proposal.primary is EquipmentIncrementRemedy.AmendEquipmentTiers)
            val amend = proposal.primary as EquipmentIncrementRemedy.AmendEquipmentTiers
            assertEquals(customId, amend.equipmentId)
            assertEquals(proposedTiers, amend.tiers)
            assertTrue(
                proposal.alternatives.first() is EquipmentIncrementRemedy.ForkCustomEquipment,
            )
            assertTrue(proposal.equipmentIsCustom)
            assertFalse(proposal.requiresBlastRadiusConfirmation)
        }

        @Test
        @DisplayName("Given seed with inheritingCount equal to K when ranked then Amend is primary")
        fun seedAtKAmends() {
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(inheritingCount = EQUIPMENT_INCREMENT_FORK_THRESHOLD_K),
                )
            assertTrue(proposal.primary is EquipmentIncrementRemedy.AmendEquipmentTiers)
            assertFalse(proposal.equipmentIsCustom)
            assertTrue(proposal.requiresBlastRadiusConfirmation)
            assertEquals(EQUIPMENT_INCREMENT_FORK_THRESHOLD_K, proposal.affectedInheritingLiftCount)
        }

        @Test
        @DisplayName("Given seed with inheritingCount K+1 when ranked then Fork is primary")
        fun seedAboveKForks() {
            val kPlus = EQUIPMENT_INCREMENT_FORK_THRESHOLD_K + 1
            val proposal =
                rankEquipmentIncrementProposal(baseInput(inheritingCount = kPlus))
            assertTrue(proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment)
            assertFalse(proposal.equipmentIsCustom)
            assertFalse(proposal.requiresBlastRadiusConfirmation)
        }

        @Test
        @DisplayName("Given seed with inheritingCount 1 when ranked then Amend primary with blast radius")
        fun seedSingleInheritorAmends() {
            val proposal = rankEquipmentIncrementProposal(baseInput(inheritingCount = 1))
            assertTrue(proposal.primary is EquipmentIncrementRemedy.AmendEquipmentTiers)
            assertFalse(proposal.equipmentIsCustom)
            assertTrue(proposal.requiresBlastRadiusConfirmation)
        }

        @Test
        @DisplayName("Given multi-lift anomaly on seed with many inheritors when ranked then Fork names gym")
        fun multiLiftForkName() {
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(
                        inheritingCount = 12,
                        anomalousLiftIds = listOf(latPulldown, cableRow),
                    ),
                )
            val fork = proposal.primary as EquipmentIncrementRemedy.ForkCustomEquipment
            assertEquals("Cables (gym)", fork.suggestedName)
            assertEquals(2, fork.assignLiftIds.size)
        }

        @Test
        @DisplayName("Given no equipment id when ranked then Fork primary without Amend")
        fun noEquipmentForksOnly() {
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(
                        equipmentId = null,
                        equipmentName = null,
                        isCustom = false,
                        inheritingCount = 0,
                    ),
                )
            assertTrue(proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment)
            assertEquals("Lat pulldown stack", (proposal.primary as EquipmentIncrementRemedy.ForkCustomEquipment).suggestedName)
            assertTrue(
                proposal.alternatives.none { it is EquipmentIncrementRemedy.AmendEquipmentTiers },
            )
            // No equipment id → custom-ish (no seed amend); primary is Fork → no blast confirm.
            assertTrue(proposal.equipmentIsCustom)
            assertFalse(proposal.requiresBlastRadiusConfirmation)
        }

        @Test
        @DisplayName("Given empty anomalous lifts when ranked then throws")
        fun emptyLiftsThrows() {
            assertThrows<IllegalArgumentException> {
                rankEquipmentIncrementProposal(baseInput(anomalousLiftIds = emptyList()))
            }
        }

        @Test
        @DisplayName("requiresBlastRadius is true for Amend of non-custom equipment")
        fun requiresBlastRadiusHelper() {
            val proposal = rankEquipmentIncrementProposal(baseInput(inheritingCount = 1))
            val amend =
                proposal.primary as EquipmentIncrementRemedy.AmendEquipmentTiers
            assertTrue(proposal.requiresBlastRadius(amend))
            assertTrue(proposal.requiresBlastRadiusConfirmation)
            val fork =
                proposal.usefulAlternative() as EquipmentIncrementRemedy.ForkCustomEquipment
            assertFalse(proposal.requiresBlastRadius(fork))
        }

        @Test
        @DisplayName("score ranks stronger residual evidence higher")
        fun scoreOrdersByResidualStrength() {
            val weak =
                scoreEquipmentIncrementProposal(
                    residualSetCount = 1,
                    distinctResidualAmounts = 1,
                    primaryIsFork = false,
                    affectedInheritingLiftCount = 2,
                )
            val strong =
                scoreEquipmentIncrementProposal(
                    residualSetCount = 4,
                    distinctResidualAmounts = 2,
                    primaryIsFork = true,
                    affectedInheritingLiftCount = 12,
                )
            assertTrue(strong > weak)
        }

        @Test
        @DisplayName("Given colliding fork name when ranked then suggested name is unique")
        fun uniqueifiesCollidingForkName() {
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(
                        inheritingCount = 12,
                        existingEquipmentNames =
                            listOf("Cables", "Lat pulldown stack", "Lat pulldown stack (2)"),
                    ),
                )
            val fork = proposal.primary as EquipmentIncrementRemedy.ForkCustomEquipment
            assertEquals("Lat pulldown stack (1)", fork.suggestedName)
        }
    }

    @Nested
    @DisplayName("fingerprint and naming")
    inner class FingerprintAndNaming {
        @Test
        @DisplayName("Given cluster inputs when fingerprinted then stable sorted id")
        fun fingerprintStable() {
            val a =
                equipmentIncrementProposalFingerprint(
                    equipmentId = cableId,
                    liftIds = listOf(cableRow, latPulldown),
                    inferredStep = 2.5,
                )
            val b =
                equipmentIncrementProposalFingerprint(
                    equipmentId = cableId,
                    liftIds = listOf(latPulldown, cableRow),
                    inferredStep = 2.5,
                )
            assertEquals(a, b)
            assertEquals("cable|lat-pulldown,seated-cable-row|2.5", a)
        }

        @Test
        @DisplayName("Given integer step when fingerprinted then no trailing decimal")
        fun fingerprintIntegerStep() {
            val id =
                equipmentIncrementProposalFingerprint(
                    equipmentId = null,
                    liftIds = listOf(latPulldown),
                    inferredStep = 5.0,
                )
            assertEquals("none|lat-pulldown|5", id)
        }

        @Test
        @DisplayName("Given ranking when proposal id matches fingerprint")
        fun proposalIdMatchesFingerprint() {
            val input = baseInput()
            val proposal = rankEquipmentIncrementProposal(input)
            assertEquals(
                equipmentIncrementProposalFingerprint(
                    equipmentId = input.equipmentId,
                    liftIds = input.anomalousLiftIds,
                    inferredStep = input.inferredStep,
                ),
                proposal.id,
            )
        }

        @Test
        @DisplayName("Given multi-lift without equipment name when named then Custom stack")
        fun customStackFallback() {
            val name =
                suggestForkEquipmentName(
                    anomalousLiftIds = listOf(latPulldown, cableRow),
                    liftNamesById = emptyMap(),
                    equipmentName = null,
                )
            assertEquals("Custom stack", name)
        }

        @Test
        @DisplayName("Given normalizeKey collision when uniqueified then appends (1)")
        fun uniqueifyNormalizeKeyCollision() {
            val unique =
                uniqueifyForkEquipmentName(
                    suggestedName = "Lat pulldown stack",
                    existingNames = listOf("lat-pulldown stack"),
                )
            assertEquals("Lat pulldown stack (1)", unique)
        }

        @Test
        @DisplayName("Given base and (1) taken when uniqueified then picks the lowest free number (2)")
        fun uniqueifyPicksLowestFreeNumber() {
            val unique =
                uniqueifyForkEquipmentName(
                    suggestedName = "Lat pulldown stack",
                    existingNames = listOf("Lat pulldown stack", "Lat pulldown stack (1)"),
                )
            assertEquals("Lat pulldown stack (2)", unique)
        }

        @Test
        @DisplayName("Given only the (1) name taken when uniqueified then the free base is unchanged")
        fun uniqueifyBaseFreeDespiteNumberedSibling() {
            assertEquals(
                "Lat pulldown stack",
                uniqueifyForkEquipmentName(
                    suggestedName = "Lat pulldown stack",
                    existingNames = listOf("Lat pulldown stack (1)", "Cables"),
                ),
            )
        }

        @Test
        @DisplayName("Given free name when uniqueified then unchanged")
        fun uniqueifyNoCollision() {
            assertEquals(
                "Lat pulldown stack",
                uniqueifyForkEquipmentName(
                    suggestedName = "Lat pulldown stack",
                    existingNames = listOf("Cables", "Dumbbells"),
                ),
            )
        }
    }

    @Nested
    @DisplayName("proposed tiers validation")
    inner class ProposedTiersValid {
        @Test
        @DisplayName("Given ranked proposal tiers when validated then Valid")
        fun rankedTiersValid() {
            val proposal = rankEquipmentIncrementProposal(baseInput())
            assertEquals(
                IncrementTiersValidationResult.Valid,
                IncrementLattice.validateTiers(proposal.proposedTiers),
            )
        }

        @Test
        @DisplayName("Given end-to-end infer+rank when validated then Valid and on-lattice")
        fun endToEndInferAndRank() {
            val current =
                listOf(
                    IncrementTier(
                        lowerBound = Weight(0.0, lb),
                        upperBound = null,
                        increment = Weight(5.0, lb),
                    ),
                )
            val samples = listOf(Weight(62.5, lb), Weight(67.5, lb))
            val inferred = inferIncrementLattice(samples, current)
            assertTrue(inferred != null)
            val proposal =
                rankEquipmentIncrementProposal(
                    baseInput(
                        sampleWeights = samples,
                        inferredStep = inferred!!.step,
                        inheritingCount = 12,
                    ).copy(proposedTiers = inferred.tiers),
                )
            assertEquals(
                IncrementTiersValidationResult.Valid,
                IncrementLattice.validateTiers(proposal.proposedTiers),
            )
            assertTrue(proposal.proposedTiers.isOnLattice(Weight(62.5, lb)))
            assertTrue(proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment)
        }
    }
}

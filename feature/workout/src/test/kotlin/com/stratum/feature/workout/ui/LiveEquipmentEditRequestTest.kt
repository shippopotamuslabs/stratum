package com.stratum.feature.workout.ui

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.warnedIndexesForSamples
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure-mapping tests for the live "Adjust Equipment" pill's seeded editor request:
 * conclusive inference prefills + warns (warnings-only, no fingerprint), inconclusive
 * inference falls back to the unseeded request (UNIT_TESTING.md §1 — state-based).
 */
@DisplayName("liveEquipmentEditRequestFor")
class LiveEquipmentEditRequestTest {

    private val lb = Weight.WeightUnit.Lb
    private fun lb(amount: Double) = Weight(amount, lb)

    private fun tier(lower: Double, upper: Double?, step: Double) =
        IncrementTier(lb(lower), upper?.let { lb(it) }, lb(step))

    private val currentTiers =
        listOf(
            tier(0.0, 40.0, 2.5),
            tier(40.0, 100.0, 5.0),
            tier(100.0, 300.0, 10.0),
            tier(300.0, null, 25.0),
        )

    private fun base() =
        EquipmentEditRequest(
            liftId = "lift-1",
            target = EquipmentEditTarget(equipmentId = "eq-1"),
        )

    private fun domainTiersOf(request: EquipmentEditRequest): List<IncrementTier> =
        request.initialTiersLb.map { arg ->
            IncrementTier(
                lowerBound = lb(arg.lowerBoundLb),
                upperBound = arg.upperBoundLb?.let { lb(it) },
                increment = lb(arg.incrementLb),
            )
        }

    @Test
    @DisplayName("Given off-lattice logged weights when built then seeded tiers with warnings-only context")
    fun offLatticeSeedsTiersAndSamples() {
        val request =
            liveEquipmentEditRequestFor(
                base = base(),
                currentTiers = currentTiers,
                loadType = LoadType.Weighted,
                loggedWeights = listOf(lb(62.5)),
            )

        // Routing preserved from the base request.
        assertEquals("lift-1", request.liftId)
        assertEquals(base().target, request.target)
        // Warnings-only live context (no fingerprint to dismiss).
        val context = checkNotNull(request.proposalContext as? ProposalEditContext.Live)
        // Prefill covers the logged sets …
        assertTrue(request.initialTiersLb.isNotEmpty())
        assertTrue(request.initialTiersLb.all { it.incrementLb > 0.0 })
        val samples = context.sampleWeightsLb
        assertTrue(samples.contains(62.5))
        assertTrue(domainTiersOf(request).warnedIndexesForSamples(samples).isEmpty())
        // … while the current tiers miss them (the pill signal).
        assertTrue(currentTiers.warnedIndexesForSamples(samples).isNotEmpty())
    }

    @Test
    @DisplayName("Given on-lattice logged weights when built then falls back to the unseeded request")
    fun onLatticeFallsBack() {
        val request =
            liveEquipmentEditRequestFor(
                base = base(),
                currentTiers = currentTiers,
                loadType = LoadType.Weighted,
                loggedWeights = listOf(lb(65.0)),
            )

        assertEquals(base(), request)
    }

    @Test
    @DisplayName("Given no logged weights when built then falls back to the unseeded request")
    fun emptyWeightsFallBack() {
        val request =
            liveEquipmentEditRequestFor(
                base = base(),
                currentTiers = currentTiers,
                loadType = LoadType.Weighted,
                loggedWeights = emptyList(),
            )

        assertEquals(base(), request)
    }

    @Test
    @DisplayName("Given bodyweight load when built then falls back to the unseeded request")
    fun bodyweightFallsBack() {
        val request =
            liveEquipmentEditRequestFor(
                base = base(),
                currentTiers = currentTiers,
                loadType = LoadType.Bodyweight,
                loggedWeights = listOf(lb(62.5)),
            )

        assertEquals(base(), request)
    }

    @Test
    @DisplayName("Given assisted off-lattice weights when built then seeded with magnitude samples")
    fun assistedSeedsWithMagnitudes() {
        val request =
            liveEquipmentEditRequestFor(
                base = base(),
                currentTiers = listOf(tier(0.0, null, 5.0)),
                loadType = LoadType.Assisted,
                loggedWeights = listOf(Weight(-62.5, lb)),
            )

        assertTrue(request.initialTiersLb.isNotEmpty())
        val context = checkNotNull(request.proposalContext as? ProposalEditContext.Live)
        assertEquals(listOf(62.5), context.sampleWeightsLb)
    }
}

package com.stratum.domain.response.modeling

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.DefaultResponseObservationExtractor
import com.stratum.core.testing.response.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResponseModelingPipelineTest {
    private val extractor = DefaultResponseObservationExtractor()

    @Test
    @DisplayName("LMK-001 extraction produces persisted modeling landmarks")
    fun extractionProducesModelingLandmarks() {
        val input = ResponseFixtures.fixS7MultiEpoch()
        val output = extractor.extract(input)
        val modeling = output.modeling

        assertTrue(modeling != null)
        assertTrue(modeling!!.volumeSnapshots.isNotEmpty())
        assertTrue(modeling.diagnostics.isNotEmpty())
        assertTrue(modeling.outcomeIndices.any { it.muscle == MuscleGroup.Quad })
        assertTrue(output.volumeResponses.isNotEmpty())
    }

    @Test
    @DisplayName("LMK-002 proven anchor no longer skips volume responses")
    fun provenAnchorDoesNotSkipVolumeResponses() {
        val input = ResponseFixtures.fixS7MultiEpoch(provenLiftIds = setOf(ResponseFixtures.liftBench))
        val output = extractor.extract(input)

        assertTrue(output.volumeResponses.isNotEmpty())
    }
}

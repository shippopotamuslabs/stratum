package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResponseObservationExtractorTest {
    private val extractor: ResponseObservationExtractor = DefaultResponseObservationExtractor()

    @Test
    @DisplayName("RP-S7-001 end-to-end extract output")
    fun rpS7_001_multiEpochPassiveExtract() {
        val input = ResponseFixtures.fixS7MultiEpoch(provenLiftIds = setOf(ResponseFixtures.liftBench))
        val output = extractor.extract(input)

        assertEquals(2, output.epochs.size)
        assertTrue(output.volumeResponses.size >= 1)
        assertTrue(output.liftEffectiveness.size >= 1)
        assertEquals(1, output.deloadWeeksExcluded.size)
    }
}

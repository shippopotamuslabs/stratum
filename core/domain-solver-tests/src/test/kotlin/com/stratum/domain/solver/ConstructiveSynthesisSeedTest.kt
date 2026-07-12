@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.domain.program.generation.synthesis.SynthesisSessionDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ConstructiveSynthesisSeedTest {

    @Test
    @DisplayName("all session counts seed Full (matches solver mixed-day preference)")
    fun allDaysFull() {
        assertEquals(
            listOf(
                SynthesisSessionDomain.Full,
                SynthesisSessionDomain.Full,
                SynthesisSessionDomain.Full,
            ),
            ConstructiveSynthesisSeed.proposeDomains(3),
        )
        assertEquals(
            List(5) { SynthesisSessionDomain.Full },
            ConstructiveSynthesisSeed.proposeDomains(5),
        )
    }
}

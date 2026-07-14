package com.stratum.core.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class IntSetConverterTest {
    private val converter = IntSetConverter()

    @Test
    @DisplayName("empty set round-trips as empty string")
    fun emptyRoundTrip() {
        assertEquals("", converter.fromIntSet(emptySet()))
        assertEquals(emptySet<Int>(), converter.toIntSet(""))
        assertEquals(emptySet<Int>(), converter.toIntSet(null))
    }

    @Test
    @DisplayName("rest indices sort on encode and round-trip")
    fun restIndicesRoundTrip() {
        val encoded = converter.fromIntSet(setOf(7, 3))
        assertEquals("3,7", encoded)
        assertEquals(setOf(3, 7), converter.toIntSet(encoded))
    }
}

package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WeightUnitParseTest {
    @ParameterizedTest
    @CsvSource("lbs,Lb", "lb,Lb", "LB,Lb", "Lb,Lb", "pounds,Lb", "kg,Kg", "KG,Kg", "kilograms,Kg")
    fun `parse accepts common import variants`(raw: String, expected: Weight.WeightUnit) {
        assertEquals(expected, Weight.WeightUnit.parse(raw))
    }

    @Test
    fun `parse rejects unknown unit`() {
        assertThrows(IllegalArgumentException::class.java) {
            Weight.WeightUnit.parse("stone")
        }
    }
}

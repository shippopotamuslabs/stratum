package com.stratum.core.inference

import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Fixtures match the **callable response** shape returned by
 * `inferEquipmentIncrements` (`unit` + `tiers` + `microStops`), mirroring the
 * server `parseScannedTiers` contract.
 */
class IncrementScanResponseParserTest {
    @Test
    @DisplayName("parseResponse maps lb tiers with open-ended top band and micro stops")
    fun parsesLbTiersWithMicro() {
        val data =
            mapOf(
                "unit" to "lb",
                "tiers" to
                    listOf(
                        mapOf("lower" to 10.0, "upper" to 50.0, "step" to 10.0),
                        mapOf("lower" to 50.0, "upper" to 100.0, "step" to 5.0),
                        mapOf("lower" to 100.0, "upper" to null, "step" to 2.5),
                    ),
                "microStops" to listOf(1.25, 2.5),
            )
        val parsed = IncrementScanResponseParser.parseResponse(data)
        assertEquals(Weight.WeightUnit.Lb, parsed.unit)
        assertEquals(
            listOf(
                ScannedTierRow(lower = 10.0, upper = 50.0, step = 10.0),
                ScannedTierRow(lower = 50.0, upper = 100.0, step = 5.0),
                ScannedTierRow(lower = 100.0, upper = null, step = 2.5),
            ),
            parsed.tiers,
        )
        assertEquals(listOf(1.25, 2.5), parsed.microStops)
    }

    @Test
    @DisplayName("parseResponse maps kg unit and tolerates missing microStops")
    fun parsesKgUnitWithoutMicro() {
        val data =
            mapOf(
                "unit" to "kg",
                "tiers" to
                    listOf(
                        mapOf("lower" to 5.0, "upper" to 40.0, "step" to 5.0),
                        mapOf("lower" to 40.0, "upper" to null, "step" to 2.5),
                    ),
            )
        val parsed = IncrementScanResponseParser.parseResponse(data)
        assertEquals(Weight.WeightUnit.Kg, parsed.unit)
        assertEquals(2, parsed.tiers.size)
        assertEquals(emptyList<Double>(), parsed.microStops)
    }

    @Test
    @DisplayName("parseResponse falls back to Lb for unknown or missing unit")
    fun unknownUnitFallsBackToLb() {
        val missingUnit =
            mapOf(
                "tiers" to listOf(mapOf("lower" to 10.0, "upper" to null, "step" to 10.0)),
                "microStops" to emptyList<Double>(),
            )
        assertEquals(
            Weight.WeightUnit.Lb,
            IncrementScanResponseParser.parseResponse(missingUnit).unit,
        )
        val unknownUnit =
            mapOf(
                "unit" to "stone",
                "tiers" to listOf(mapOf("lower" to 10.0, "upper" to null, "step" to 10.0)),
                "microStops" to emptyList<Double>(),
            )
        assertEquals(
            Weight.WeightUnit.Lb,
            IncrementScanResponseParser.parseResponse(unknownUnit).unit,
        )
    }

    @Test
    @DisplayName("parseResponse drops malformed tier rows and keeps valid ones")
    fun dropsMalformedRows() {
        val data =
            mapOf(
                "unit" to "lb",
                "tiers" to
                    listOf(
                        mapOf("lower" to 10.0, "upper" to 50.0, "step" to 10.0),
                        mapOf("lower" to "ten", "upper" to 50.0, "step" to 10.0),
                        mapOf("lower" to 50.0, "upper" to 100.0, "step" to 0.0),
                        mapOf("lower" to 50.0, "upper" to 100.0, "step" to -5.0),
                        mapOf("lower" to 100.0, "upper" to 50.0, "step" to 5.0),
                        mapOf("lower" to 100.0, "upper" to "lots", "step" to 5.0),
                        mapOf("lower" to -10.0, "upper" to 50.0, "step" to 5.0),
                        mapOf("lower" to 200.0, "step" to 5.0),
                        "not-a-row",
                        null,
                    ),
                "microStops" to listOf(2.5, "junk", -1.0, 0.0),
            )
        val parsed = IncrementScanResponseParser.parseResponse(data)
        assertEquals(
            listOf(ScannedTierRow(lower = 10.0, upper = 50.0, step = 10.0)),
            parsed.tiers,
        )
        assertEquals(listOf(2.5), parsed.microStops)
    }

    @Test
    @DisplayName("parseResponse ignores the _meta version block (STR-48 forward compat)")
    fun ignoresMetaVersionBlock() {
        val data =
            mapOf(
                "unit" to "lb",
                "tiers" to
                    listOf(
                        mapOf("lower" to 10.0, "upper" to 50.0, "step" to 10.0),
                        mapOf("lower" to 50.0, "upper" to null, "step" to 5.0),
                    ),
                "microStops" to listOf(2.5),
                "_meta" to
                    mapOf(
                        "version" to 1,
                        "deprecation" to
                            mapOf(
                                "sunsetDate" to "2027-03-01",
                                "message" to "Superseded by inferEquipmentIncrements_v2.",
                            ),
                    ),
            )
        val parsed = IncrementScanResponseParser.parseResponse(data)
        assertEquals(Weight.WeightUnit.Lb, parsed.unit)
        assertEquals(
            listOf(
                ScannedTierRow(lower = 10.0, upper = 50.0, step = 10.0),
                ScannedTierRow(lower = 50.0, upper = null, step = 5.0),
            ),
            parsed.tiers,
        )
        assertEquals(listOf(2.5), parsed.microStops)
    }

    @Test
    @DisplayName("parseResponse fails on structurally malformed payloads")
    fun malformedPayloadsThrow() {
        assertThrows(IllegalStateException::class.java) {
            IncrementScanResponseParser.parseResponse(null)
        }
        assertThrows(IllegalStateException::class.java) {
            IncrementScanResponseParser.parseResponse("nope")
        }
        assertThrows(IllegalStateException::class.java) {
            IncrementScanResponseParser.parseResponse(mapOf("unit" to "lb"))
        }
        assertThrows(IllegalStateException::class.java) {
            IncrementScanResponseParser.parseResponse(mapOf("unit" to "lb", "tiers" to "nope"))
        }
    }
}

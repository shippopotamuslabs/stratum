package com.stratum.core.inference

import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ImportedHistoryJsonParser")
class ImportedHistoryJsonParserTest {

    @Test
    @DisplayName("missing weightUnit uses fallback")
    fun missingUnitUsesFallback() {
        val payload =
            """
            {
              "sessions": [
                {"id": "s1", "startedAt": "2025-01-01T10:00:00Z"}
              ],
              "sets": [
                {
                  "id": "set-1",
                  "sessionId": "s1",
                  "exerciseNameRaw": "Bench Press",
                  "setIndex": 0,
                  "weightAmount": 100.0,
                  "reps": 5,
                  "completedAt": "2025-01-01T10:05:00Z"
                }
              ]
            }
            """.trimIndent()

        val (_, sets) =
            ImportedHistoryJsonParser.parse(payload, fallbackWeightUnit = Weight.WeightUnit.Kg)

        assertEquals(1, sets.size)
        // Canonical storage: 100 kg → Lb
        assertEquals(Weight.WeightUnit.Lb, sets[0].weight.unit)
        assertEquals(100.0 * 1.0 / 0.45359237, sets[0].weight.amount, 1e-6)
    }

    @Test
    @DisplayName("explicit weightUnit wins over fallback")
    fun explicitUnitWins() {
        val payload =
            """
            {
              "sessions": [
                {"id": "s1", "startedAt": "2025-01-01T10:00:00Z"}
              ],
              "sets": [
                {
                  "id": "set-1",
                  "sessionId": "s1",
                  "exerciseNameRaw": "Bench Press",
                  "setIndex": 0,
                  "weightAmount": 135.0,
                  "weightUnit": "Lb",
                  "reps": 5,
                  "completedAt": "2025-01-01T10:05:00Z"
                }
              ]
            }
            """.trimIndent()

        val (_, sets) =
            ImportedHistoryJsonParser.parse(payload, fallbackWeightUnit = Weight.WeightUnit.Kg)

        // Explicit Lb stays Lb after canonical normalize.
        assertEquals(Weight.WeightUnit.Lb, sets[0].weight.unit)
        assertEquals(135.0, sets[0].weight.amount)
    }

    @Test
    @DisplayName("unrecognized weightUnit falls back to fallbackWeightUnit")
    fun unrecognizedUnitUsesFallback() {
        val payload =
            """
            {
              "sessions": [
                {"id": "s1", "startedAt": "2025-01-01T10:00:00Z"}
              ],
              "sets": [
                {
                  "id": "set-1",
                  "sessionId": "s1",
                  "exerciseNameRaw": "Bench Press",
                  "setIndex": 0,
                  "weightAmount": 100.0,
                  "weightUnit": "STONE",
                  "reps": 5,
                  "completedAt": "2025-01-01T10:05:00Z"
                }
              ]
            }
            """.trimIndent()

        val (_, sets) =
            ImportedHistoryJsonParser.parse(payload, fallbackWeightUnit = Weight.WeightUnit.Kg)

        assertEquals(1, sets.size)
        assertEquals(Weight.WeightUnit.Lb, sets[0].weight.unit)
        assertEquals(100.0 * 1.0 / 0.45359237, sets[0].weight.amount, 1e-6)
    }
}

package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonValue
import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.lift.Weight

/** Ensures [this] is a [JsonObject], or errors with [label]. */
internal fun JsonValue.requireAsObject(label: String): JsonObject =
    asObjectOrNull() ?: error("Expected object for $label")

/**
 * Runs [block] and rethrows parse failures as [ProgramSynthesisWireException].
 * Already-typed wire exceptions pass through unchanged.
 */
internal inline fun <T> wrapParse(kind: String, block: () -> T): T =
    try {
        block()
    } catch (e: ProgramSynthesisWireException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
    } catch (e: IllegalStateException) {
        throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
    } catch (e: IndexOutOfBoundsException) {
        throw ProgramSynthesisWireException("Invalid program synthesis $kind JSON: ${e.message}", e)
    }

internal fun encodeWeight(weight: Weight): String =
    jsonObject(
        "amount" to jsonNumber(weight.amount),
        "unit" to jsonString(weight.unit.name),
    )

internal fun encodeWeightOrNull(weight: Weight?): String =
    if (weight == null) "null" else encodeWeight(weight)

internal fun decodeWeight(obj: JsonObject): Weight =
    Weight(
        amount = obj.requireDouble("amount"),
        unit = enumValueOf<Weight.WeightUnit>(obj.requireString("unit")),
    )

internal fun decodeWeightOrNull(value: JsonValue?): Weight? {
    if (value == null || value.isNull()) return null
    return decodeWeight(value.requireAsObject("weight"))
}

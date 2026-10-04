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
 * Accepts a missing `modelVersion` as v1 and any `v1.x` minor; rejects unknown
 * majors or malformed values (`solver-contract/VERSIONING.md` §1–§2). Unknown
 * majors are never guessed — the caller fails the job instead.
 */
internal fun requireSupportedModelVersion(root: JsonObject, kind: String) {
    val version = root.optionalString("modelVersion") ?: return
    if (!MODEL_VERSION_PATTERN.matches(version)) {
        throw ProgramSynthesisWireException(
            "Unsupported program synthesis $kind modelVersion '$version' " +
                "(supported: ${ProgramSynthesisWire.MODEL_VERSION})",
        )
    }
}

private val MODEL_VERSION_PATTERN =
    Regex("^" + Regex.escape(ProgramSynthesisWire.MODEL_VERSION) + """(\.\d+)?$""")

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

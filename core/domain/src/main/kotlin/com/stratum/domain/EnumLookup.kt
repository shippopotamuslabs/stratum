package com.stratum.domain

/**
 * Exact-name enum lookup behind every enum companion's `parseOrNull`
 * (STR-240 chunk 1): returns the entry whose [Enum.name] equals [raw]
 * exactly, or null — no trim, case folding, or aliases. That exact-name
 * semantics lives in this one implementation; each enum keeps its own
 * companion signature and delegates here. (`WeightUnit.parseOrNull` is the
 * documented exception: it is alias-aware and keeps its own body.)
 */
internal inline fun <reified T : Enum<T>> enumParseOrNull(raw: String): T? =
    enumValues<T>().firstOrNull { it.name == raw }

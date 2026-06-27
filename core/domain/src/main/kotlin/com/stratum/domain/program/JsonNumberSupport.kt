package com.stratum.domain.program

/**
 * Returns a JSON numeric literal for finite values; non-finite values are omitted from payloads.
 */
internal fun Double?.toJsonNumber(): String? = this?.takeIf { it.isFinite() }?.toString()

internal fun Double?.toJsonField(fieldName: String): String =
    toJsonNumber()?.let { ""","$fieldName":$it""" } ?: ""

/**
 * Returns a finite numeric literal, falling back to 0.0 for null/non-finite values.
 * Used for non-nullable landmark estimates (MEV/MRV) where omission would break the schema.
 */
internal fun Double?.toJsonNumberOrZero(): String =
    this?.takeIf { it.isFinite() }?.toString() ?: "0.0"

/** Finite JSON numeric literal for non-nullable doubles; non-finite values become 0.0. */
internal fun Double.toJsonLiteral(): String = takeIf { it.isFinite() }?.toString() ?: "0.0"

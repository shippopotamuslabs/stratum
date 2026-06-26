package com.stratum.domain.program

/**
 * Returns a JSON numeric literal for finite values; non-finite values are omitted from payloads.
 */
internal fun Double?.toJsonNumber(): String? = this?.takeIf { it.isFinite() }?.toString()

internal fun Double?.toJsonField(fieldName: String): String =
    toJsonNumber()?.let { ""","$fieldName":$it""" } ?: ""

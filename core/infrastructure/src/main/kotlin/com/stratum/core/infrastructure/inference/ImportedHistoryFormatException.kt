package com.stratum.core.infrastructure.inference

/**
 * Thrown at the imported-history parser boundary when a timestamp value matches
 * none of the accepted import formats. Carries the offending [value]; the import
 * flow's existing Failed surfacing consumes it unchanged.
 */
class ImportedHistoryFormatException(
    val value: String,
) : IllegalArgumentException("Unsupported timestamp format: $value")

package com.stratum.domain.featureflag

/**
 * Contract for a type-safe feature flag.
 *
 * All flags evaluate to `false` by default (fail-closed, safe baseline).
 * Implement this interface (e.g. using an `enum class` or `data object`) when
 * declaring feature flags for a feature or domain area.
 *
 * @property key The key corresponding to the Firebase Remote Config parameter.
 * @property description Human-readable purpose of the flag.
 */
interface FeatureFlag {
    val key: String
    val description: String
}

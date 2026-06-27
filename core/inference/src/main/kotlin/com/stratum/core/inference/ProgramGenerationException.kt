package com.stratum.core.inference

/**
 * Callable failure with a user-readable [message]. [FirebaseProgramGenerator] wraps Firebase /
 * network errors so the onboarding Generate step can show feedback without depending on Firebase
 * types in the feature module.
 */
class ProgramGenerationException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

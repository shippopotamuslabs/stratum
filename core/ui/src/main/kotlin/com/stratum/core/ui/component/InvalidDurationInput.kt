package com.stratum.core.ui.component

/**
 * Classifies why a value typed into a [StratumDurationStepper] segment field was
 * rejected. The stepper owns the parse and range checks (it knows the configured
 * min/max minutes); callers map these to localized messages via the stepper's
 * `onInvalidInput` callback.
 */
enum class InvalidDurationInput {
    Blank,
    NotANumber,
    TooShort,
    TooLong,
}

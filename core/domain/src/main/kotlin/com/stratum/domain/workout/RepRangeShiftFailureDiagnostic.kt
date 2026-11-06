package com.stratum.domain.workout

/**
 * A rep-range shift operational failure recorded as data (STR-237 Route (i),
 * domain-owned outcome precedent).
 *
 * The domain outcome ([ApplyRepRangeShiftResult]) carries ONLY the real
 * throwable — the domain names no log tag or rendered message. The
 * presentation boundary renders the legacy tag and message from this cause
 * (plus the workout lift id it already holds) and records the diagnostic
 * exactly once through its module's DiagnosticDrain.
 */
data class RepRangeShiftFailureDiagnostic(
    val cause: Throwable,
)

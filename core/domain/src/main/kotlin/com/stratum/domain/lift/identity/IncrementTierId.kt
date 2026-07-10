package com.stratum.domain.lift.identity

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Typed id for a single weight-band increment tier row.
 *
 * Document ids are **deterministic** from [liftId] + lower bound so re-saving the same
 * band on two devices does not fork cloud documents. Format: `{liftId}_{fixedPoint}`.
 *
 * Bounds are encoded as millipounds (×1000, half-up) so binary float noise cannot fork ids.
 */
@JvmInline
value class IncrementTierId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "IncrementTierId cannot be blank" }
    }

    companion object {
        fun forBand(liftId: LiftId, lowerBound: Double): IncrementTierId =
            IncrementTierId("${liftId.value}_${formatBound(lowerBound)}")

        /** Stable fixed-point encoding: millipounds as decimal string without locale quirks. */
        private fun formatBound(bound: Double): String {
            val milli = (bound * 1000.0).roundToLong()
            val whole = milli / 1000
            val frac = kotlin.math.abs(milli % 1000)
            return if (frac == 0L) {
                whole.toString()
            } else {
                String.format(Locale.US, "%d.%03d", whole, frac).trimEnd('0').trimEnd('.')
            }
        }
    }
}

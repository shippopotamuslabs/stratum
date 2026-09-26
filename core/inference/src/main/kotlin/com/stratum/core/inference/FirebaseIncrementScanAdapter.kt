package com.stratum.core.inference

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.lift.IncrementScanPort
import com.stratum.domain.lift.ScanPhoto
import com.stratum.domain.lift.ScannedTierRow
import com.stratum.domain.lift.ScannedTiers
import com.stratum.domain.lift.Weight
import com.stratum.domain.logging.LoggingPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firebase Callable client for `inferEquipmentIncrements`.
 *
 * Sends the stack-label photo inline as base64 and decodes the server-sanitized
 * payload (`unit` + `tiers` + optional `microStops`). Parsing is tolerant: an
 * unknown unit falls back to Lb, malformed tier rows and micro stops are dropped,
 * but a structurally invalid payload fails the returned [Result] (never throws past it).
 */
@Singleton
class FirebaseIncrementScanAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : IncrementScanPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun scanTiers(photo: ScanPhoto): Result<ScannedTiers> =
        runCatching {
            requireAuth()
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val payload =
                mapOf(
                    "imageBase64" to Base64.encodeToString(photo.bytes, Base64.NO_WRAP),
                    "mimeType" to photo.mimeType,
                )
            val result = callable.call(payload).await()
            parseResponse(result.data)
        }.onFailure { error ->
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
        }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for increment scan." }
    }

    internal companion object {
        const val TAG = "StratumIncrementScan"
        const val CALLABLE_NAME = "inferEquipmentIncrements"
        const val CALLABLE_TIMEOUT_SECONDS = 120L

        internal fun parseResponse(data: Any?): ScannedTiers {
            val map = data as? Map<*, *> ?: error("Invalid inferEquipmentIncrements response.")
            val tiersRaw =
                map["tiers"] as? List<*>
                    ?: error("Missing tiers in inferEquipmentIncrements response.")
            return ScannedTiers(
                unit = parseUnit(map["unit"]),
                tiers = tiersRaw.mapNotNull { entry -> parseTierRow(entry as? Map<*, *>) },
                microStops = parseMicroStops(map["microStops"]),
            )
        }

        private fun parseUnit(raw: Any?): Weight.WeightUnit =
            (raw as? String)?.let(Weight.WeightUnit::parseOrNull) ?: Weight.WeightUnit.Lb

        private fun parseTierRow(entry: Map<*, *>?): ScannedTierRow? {
            if (entry == null) return null
            val lower = parseLower(entry["lower"]) ?: return null
            val step = parsePositive(entry["step"]) ?: return null
            val upper = parseUpper(entry, lower) ?: return null
            return ScannedTierRow(lower = lower, upper = upper.value, step = step)
        }

        private fun parseLower(raw: Any?): Double? {
            val value = parseNumber(raw)
            if (value == null || !value.isFinite() || value < 0.0) return null
            return value
        }

        private fun parsePositive(raw: Any?): Double? {
            val value = parseNumber(raw)
            if (value == null || !value.isFinite() || value <= 0.0) return null
            return value
        }

        /**
         * Null wire upper means open-ended ([UpperBound.Open]); a missing key or an
         * invalid (non-numeric, non-finite, inverted) upper fails the row (null).
         */
        private fun parseUpper(entry: Map<*, *>, lower: Double): UpperBound? {
            if (!entry.containsKey("upper")) return null
            val raw = entry["upper"] ?: return UpperBound.Open
            val upper = parseNumber(raw)
            if (upper == null || !upper.isFinite() || upper <= lower) return null
            return UpperBound.Bounded(upper)
        }

        private sealed interface UpperBound {
            val value: Double?

            data object Open : UpperBound {
                override val value: Double? = null
            }

            data class Bounded(override val value: Double) : UpperBound
        }

        private fun parseMicroStops(raw: Any?): List<Double> {
            val stops = raw as? List<*> ?: return emptyList()
            return stops.mapNotNull { entry ->
                val value = parseNumber(entry) ?: return@mapNotNull null
                if (!value.isFinite() || value <= 0.0) null else value
            }
        }

        private fun parseNumber(raw: Any?): Double? =
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }
    }
}

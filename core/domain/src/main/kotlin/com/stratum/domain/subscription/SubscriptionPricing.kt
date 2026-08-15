package com.stratum.domain.subscription

import kotlin.math.roundToInt

private val ISO_8601_PERIOD = Regex("^P(\\d+)([DWMY])$")

private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val MONTHS_PER_YEAR = 12L

fun iso8601PeriodToDays(period: String): Int {
    val match = ISO_8601_PERIOD.matchEntire(period.trim().uppercase()) ?: return 0
    val count = match.groupValues[1].toIntOrNull() ?: return 0
    if (count <= 0) return 0
    return when (match.groupValues[2]) {
        "D" -> count
        "W" -> count * DAYS_PER_WEEK
        "M" -> count * DAYS_PER_MONTH
        "Y" -> count * DAYS_PER_YEAR
        else -> 0
    }
}

fun trialDaysFromPhase(billingPeriodIso: String, billingCycleCount: Int): Int {
    if (billingCycleCount <= 0) return 0
    return iso8601PeriodToDays(billingPeriodIso) * billingCycleCount
}

fun monthlyEquivalentMicros(priceAmountMicros: Long, billingPeriodIso: String): Long? {
    if (priceAmountMicros < 0L) return null
    val match = ISO_8601_PERIOD.matchEntire(billingPeriodIso.trim().uppercase()) ?: return null
    val count = match.groupValues[1].toIntOrNull() ?: return null
    if (count <= 0) return null
    return when (match.groupValues[2]) {
        "Y" -> priceAmountMicros / (count * MONTHS_PER_YEAR)
        "M" -> priceAmountMicros / count.toLong()
        else -> null
    }
}

fun annualSavingsPercent(
    annualMonthlyEquivalentMicros: Long,
    monthlyPriceMicros: Long,
): Int? {
    if (monthlyPriceMicros <= 0L || annualMonthlyEquivalentMicros < 0L) return null
    val saved = monthlyPriceMicros - annualMonthlyEquivalentMicros
    if (saved <= 0L) return 0
    return ((saved.toDouble() / monthlyPriceMicros.toDouble()) * 100.0).roundToInt()
}

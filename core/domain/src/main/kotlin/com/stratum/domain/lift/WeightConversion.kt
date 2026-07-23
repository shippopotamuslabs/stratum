package com.stratum.domain.lift

import kotlin.math.abs

/**
 * Converts between [Weight.WeightUnit] systems.
 *
 * **Canonical storage for training loads (logged sets, increment tiers, e1RM magnitudes used
 * in PR/progression compare):** **Lb**. UI display/input uses [WeightDisplayPreferences];
 * convert at the presentation boundary only.
 *
 * Analytics weekly series may still be kg-normalized separately (response modeling).
 */
object WeightConversion {
    const val LB_TO_KG = 0.45359237
    const val KG_TO_LB = 1.0 / LB_TO_KG

    /** Equality / lattice epsilon for converted load amounts in Lb. */
    const val LOAD_AMOUNT_EPS = 1e-9

    fun convertAmount(
        amount: Double,
        from: Weight.WeightUnit,
        to: Weight.WeightUnit,
    ): Double {
        if (from == to) return amount
        return when (from to to) {
            Weight.WeightUnit.Lb to Weight.WeightUnit.Kg -> amount * LB_TO_KG
            Weight.WeightUnit.Kg to Weight.WeightUnit.Lb -> amount * KG_TO_LB
            else -> amount
        }
    }

    /** Magnitude in pounds (sign preserved — assisted loads stay negative). */
    fun amountInLb(weight: Weight): Double =
        convertAmount(weight.amount, weight.unit, Weight.WeightUnit.Lb)

    /** Magnitude in kilograms (sign preserved). */
    fun amountInKg(weight: Weight): Double =
        convertAmount(weight.amount, weight.unit, Weight.WeightUnit.Kg)

    /**
     * Absolute load magnitude in **Lb** for e1RM / PR / insight peaks.
     * Assisted sets store negative assistance; e1RM formulas require a positive load.
     */
    fun loadMagnitudeLb(weight: Weight): Double = abs(amountInLb(weight))

    fun nearlyEqualLb(a: Double, b: Double, eps: Double = LOAD_AMOUNT_EPS): Boolean =
        abs(a - b) <= eps
}

/** Returns a [Weight] expressed in [target]. Same-unit values are returned as-is (no copy). */
fun Weight.convertTo(target: Weight.WeightUnit): Weight {
    if (unit == target) return this
    return Weight(
        amount = WeightConversion.convertAmount(amount, unit, target),
        unit = target,
    )
}

/** Canonical storage form for training loads. */
fun Weight.toStorageLb(): Weight = convertTo(Weight.WeightUnit.Lb)

/**
 * Maps a UI-typed display magnitude into canonical storage [Weight] (**Lb**).
 *
 * - [LoadType.Bodyweight] → `0 lb`
 * - [LoadType.Assisted] → negative Lb (assistance amount)
 * - [LoadType.Weighted] → positive Lb
 *
 * [displayAmount] is the absolute value the lifter typed (or the draft field); sign is
 * applied from [loadType], not from the input.
 */
fun storageWeightFromDisplay(
    displayAmount: Double,
    displayUnit: Weight.WeightUnit,
    loadType: LoadType,
): Weight {
    when (loadType) {
        LoadType.Bodyweight -> return Weight(0.0, Weight.WeightUnit.Lb)
        LoadType.Assisted -> {
            val magnitudeLb =
                WeightConversion.convertAmount(
                    abs(displayAmount),
                    displayUnit,
                    Weight.WeightUnit.Lb,
                )
            return Weight(
                amount = if (magnitudeLb != 0.0) -magnitudeLb else 0.0,
                unit = Weight.WeightUnit.Lb,
            )
        }
        LoadType.Weighted -> {
            val magnitudeLb =
                WeightConversion.convertAmount(
                    abs(displayAmount),
                    displayUnit,
                    Weight.WeightUnit.Lb,
                )
            return Weight(magnitudeLb, Weight.WeightUnit.Lb)
        }
    }
}

/** Short display label: `"lb"` / `"kg"`. */
fun Weight.WeightUnit.shortLabel(): String =
    when (this) {
        Weight.WeightUnit.Lb -> "lb"
        Weight.WeightUnit.Kg -> "kg"
    }

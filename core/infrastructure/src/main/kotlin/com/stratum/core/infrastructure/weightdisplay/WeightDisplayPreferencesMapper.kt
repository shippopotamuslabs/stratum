package com.stratum.core.infrastructure.weightdisplay

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences

internal fun WeightDisplayPreferencesRecord.toDomain(): WeightDisplayPreferences =
    WeightDisplayPreferences(displayUnit = displayUnit.toWeightUnit())

internal fun WeightDisplayPreferences.toRecord(): WeightDisplayPreferencesRecord =
    WeightDisplayPreferencesRecord(displayUnit = displayUnit.toStorageName())

private fun String.toWeightUnit(): Weight.WeightUnit =
    when (this) {
        WeightUnitNames.KG -> Weight.WeightUnit.Kg
        else -> Weight.WeightUnit.Lb
    }

private fun Weight.WeightUnit.toStorageName(): String =
    when (this) {
        Weight.WeightUnit.Lb -> WeightUnitNames.LB
        Weight.WeightUnit.Kg -> WeightUnitNames.KG
    }

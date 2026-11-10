package com.stratum.application.settings

import com.stratum.application.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.lift.ObserveEquipmentProposalsEnabledUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.subscription.ObserveEntitlementUseCase
import com.stratum.application.theme.ObserveThemePreferencesUseCase
import com.stratum.application.workout.ObserveRepRangeShiftProposalsEnabledUseCase
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemePreferences
import com.stratum.domain.theme.ThemeFamily
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Application-layer read model for the settings screen (STR-294).
 *
 * The durable preference facts behind the settings UI in one stream, so the
 * ViewModel can map presentation state from a single durable source per
 * §12.4. The entitlement stays a domain type here; the ViewModel maps it to
 * the `isEntitled` / `isComplimentary` presentation flags at its boundary.
 */
data class SettingsSnapshot(
    val colorMode: ColorMode = ColorMode.System,
    val themeFamily: ThemeFamily = ThemeFamily.Tonal,
    val checkInsEnabled: Boolean = true,
    val equipmentProposalsEnabled: Boolean = true,
    val repRangeProposalsEnabled: Boolean = true,
    val displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    val healthConnectEnabled: Boolean = false,
    val entitlement: EntitlementState = EntitlementState.NotEntitled(),
)

/**
 * Owns the settings multi-source composition (STR-294).
 *
 * Combines the seven preference/entitlement streams the Settings screen
 * renders into one [SettingsSnapshot]. Resolution semantics are unchanged
 * from the former ViewModel pipeline: current value of each source, one
 * flat combine.
 */
class ObserveSettingsStateUseCase @Inject constructor(
    private val observeThemePreferences: ObserveThemePreferencesUseCase,
    private val observeCheckInPreferences: ObserveCheckInPreferencesUseCase,
    private val observeEquipmentProposalsEnabled: ObserveEquipmentProposalsEnabledUseCase,
    private val observeRepRangeShiftProposalsEnabled: ObserveRepRangeShiftProposalsEnabledUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val observeHealthConnectPreferences: ObserveHealthConnectPreferencesUseCase,
    private val observeEntitlement: ObserveEntitlementUseCase,
) {
    // One flat combine over all seven sources. The typed combine overloads
    // stop at five flows, so the seven-source form resolves to the vararg
    // overload and the values arrive as an Array; each is bound to a typed
    // name here, in source order, before the snapshot is built.
    operator fun invoke(): Flow<SettingsSnapshot> =
        combine(
            observeThemePreferences(),
            observeCheckInPreferences(),
            observeEquipmentProposalsEnabled(),
            observeRepRangeShiftProposalsEnabled(),
            observeWeightDisplayPreferences(),
            observeHealthConnectPreferences(),
            observeEntitlement(),
        ) { values ->
            val theme = values[0] as ThemePreferences
            val checkIn = values[1] as CheckInPreferences
            val equipmentEnabled = values[2] as Boolean
            val repRangeEnabled = values[3] as Boolean
            val weightDisplay = values[4] as WeightDisplayPreferences
            val healthConnect = values[5] as HealthConnectPreferences
            val entitlement = values[6] as EntitlementState
            SettingsSnapshot(
                colorMode = theme.colorMode,
                themeFamily = theme.family,
                checkInsEnabled = checkIn.enabled,
                equipmentProposalsEnabled = equipmentEnabled,
                repRangeProposalsEnabled = repRangeEnabled,
                displayUnit = weightDisplay.displayUnit,
                healthConnectEnabled = healthConnect.enabled,
                entitlement = entitlement,
            )
        }
}

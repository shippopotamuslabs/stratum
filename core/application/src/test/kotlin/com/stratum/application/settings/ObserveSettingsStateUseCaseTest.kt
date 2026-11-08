package com.stratum.application.settings

import app.cash.turbine.test
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.core.testing.workout.FakeRepRangeShiftProposalPreferences
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
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.theme.ColorMode
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveSettingsStateUseCase] composition tests (STR-294): the 7-source
 * preference/entitlement combine moved out of `SettingsViewModel` must
 * preserve the ViewModel's prior resolution — current value of each source
 * in a single snapshot stream.
 */
class ObserveSettingsStateUseCaseTest {

    private val fakeThemePreferences = FakeThemePreferencesRepository()
    private val fakeCheckInPreferences = FakeCheckInPreferencesRepository()
    private val fakeEquipmentProposals = FakeEquipmentProposalPreferences()
    private val fakeRepRangeProposals = FakeRepRangeShiftProposalPreferences()
    private val fakeWeightDisplayPreferences = FakeWeightDisplayPreferencesRepository()
    private val fakeHealthConnectPreferences = FakeHealthConnectPreferencesRepository()
    private val fakeEntitlementRepository = FakeEntitlementRepository()

    private val subject =
        ObserveSettingsStateUseCase(
            ObserveThemePreferencesUseCase(fakeThemePreferences),
            ObserveCheckInPreferencesUseCase(fakeCheckInPreferences),
            ObserveEquipmentProposalsEnabledUseCase(fakeEquipmentProposals),
            ObserveRepRangeShiftProposalsEnabledUseCase(fakeRepRangeProposals),
            ObserveWeightDisplayPreferencesUseCase(fakeWeightDisplayPreferences),
            ObserveHealthConnectPreferencesUseCase(fakeHealthConnectPreferences),
            ObserveEntitlementUseCase(fakeEntitlementRepository),
        )

    @Test
    @DisplayName("Given default prefs when observed then snapshot carries the defaults")
    fun defaultSnapshot() = runTest {
        subject().test {
            assertEquals(
                SettingsSnapshot(
                    colorMode = ColorMode.System,
                    themeFamily = ThemeFamily.Tonal,
                    checkInsEnabled = true,
                    equipmentProposalsEnabled = true,
                    repRangeProposalsEnabled = true,
                    displayUnit = Weight.WeightUnit.Lb,
                    healthConnectEnabled = false,
                    entitlement = EntitlementState.NotEntitled(),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given seeded prefs when observed then snapshot maps every source")
    fun seededPrefsMapped() = runTest {
        fakeThemePreferences.seed(
            ThemePreferences(
                family = ThemeFamily.NeoBrutalist,
                colorMode = ColorMode.Dark,
            ),
        )
        fakeCheckInPreferences.seed(CheckInPreferences(enabled = false))
        fakeEquipmentProposals.seedEnabled(false)
        fakeRepRangeProposals.seedEnabled(false)
        fakeWeightDisplayPreferences.seed(
            WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg),
        )
        fakeHealthConnectPreferences.setPreferences(HealthConnectPreferences(enabled = true))
        fakeEntitlementRepository.setState(
            EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
        )

        subject().test {
            val snapshot = awaitItem()
            assertEquals(ColorMode.Dark, snapshot.colorMode)
            assertEquals(ThemeFamily.NeoBrutalist, snapshot.themeFamily)
            assertFalse(snapshot.checkInsEnabled)
            assertFalse(snapshot.equipmentProposalsEnabled)
            assertFalse(snapshot.repRangeProposalsEnabled)
            assertEquals(Weight.WeightUnit.Kg, snapshot.displayUnit)
            assertTrue(snapshot.healthConnectEnabled)
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess),
                snapshot.entitlement,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given pref change when observed then snapshot re-emits with the new value")
    fun prefChangeReEmits() = runTest {
        subject().test {
            assertTrue(awaitItem().checkInsEnabled)

            fakeCheckInPreferences.seed(CheckInPreferences(enabled = false))
            assertFalse(awaitItem().checkInsEnabled)

            fakeEntitlementRepository.setState(
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            )
            assertEquals(
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
                awaitItem().entitlement,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

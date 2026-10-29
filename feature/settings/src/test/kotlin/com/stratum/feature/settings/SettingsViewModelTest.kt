package com.stratum.feature.settings

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAccountDeletionPort
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.checkin.FakeCheckInPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.theme.FakeThemePreferencesRepository
import com.stratum.core.testing.workout.FakeRepRangeShiftProposalPreferences
import com.stratum.domain.auth.AuthState
import com.stratum.application.auth.DeleteAccountUseCase
import com.stratum.application.auth.LogoutUseCase
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.checkin.CheckInPreferences
import com.stratum.application.checkin.MarkCheckInPendingUseCase
import com.stratum.application.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.application.checkin.SetCheckInEnabledUseCase
import com.stratum.application.lift.ObserveEquipmentProposalsEnabledUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.lift.SetEquipmentProposalsEnabledUseCase
import com.stratum.application.lift.SetWeightDisplayUnitUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.application.sync.EndSessionAndWipeUseCase
import com.stratum.application.sync.StartCloudSyncUseCase
import com.stratum.application.sync.StopCloudSyncUseCase
import com.stratum.domain.theme.ColorMode
import com.stratum.application.theme.ObserveThemePreferencesUseCase
import com.stratum.application.theme.SetColorModeUseCase
import com.stratum.application.theme.SetThemeFamilyUseCase
import com.stratum.domain.theme.ThemeFamily
import com.stratum.domain.theme.ThemePreferences
import com.stratum.application.training.ForceRefreshDerivedTrainingData
import com.stratum.application.workout.ObserveRepRangeShiftProposalsEnabledUseCase
import com.stratum.application.workout.SetRepRangeShiftProposalsEnabledUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectSyncStatusPort
import com.stratum.core.testing.subscription.FakeEntitlementRepository
import com.stratum.application.healthconnect.ClearHealthConnectSyncErrorUseCase
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectSyncErrorsUseCase
import com.stratum.application.healthconnect.SetHealthConnectEnabledUseCase
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.ObserveEntitlementUseCase

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelTest {

    private val fakeThemePreferences = FakeThemePreferencesRepository()
    private val fakeCheckInPreferences = FakeCheckInPreferencesRepository()
    private val fakeEquipmentProposals = FakeEquipmentProposalPreferences()
    private val fakeRepRangeProposals = FakeRepRangeShiftProposalPreferences()
    private val fakeWeightDisplayPreferences = FakeWeightDisplayPreferencesRepository()
    private val fakeHealthConnectPreferences = FakeHealthConnectPreferencesRepository()
    private val fakeHealthConnectSync = FakeHealthConnectSyncPort()
    private val fakeHealthConnectSyncStatus = FakeHealthConnectSyncStatusPort()
    private val fakeEntitlementRepository = FakeEntitlementRepository()
    private lateinit var fakeAuth: FakeAuthManager
    private lateinit var fakeCloudSync: FakeCloudSyncPort
    private lateinit var fakeAccountDeletion: FakeAccountDeletionPort
    private lateinit var subject: SettingsViewModel
    private var forceRefreshCalls = 0
    private var forceRefreshResult: Result<Unit> = Result.success(Unit)
    private var forceRefreshGate: CompletableDeferred<Unit>? = null

    @BeforeEach
    fun setup() {
        forceRefreshCalls = 0
        forceRefreshResult = Result.success(Unit)
        forceRefreshGate = null
        fakeThemePreferences.seed(ThemePreferences())
        fakeCheckInPreferences.seed(CheckInPreferences())
        fakeEquipmentProposals.seedEnabled(true)
        fakeRepRangeProposals.seedEnabled(true)
        fakeWeightDisplayPreferences.seed(WeightDisplayPreferences())
        fakeHealthConnectPreferences.setPreferences(HealthConnectPreferences())
        fakeHealthConnectSync.isAvailable = true
        fakeHealthConnectSync.hasPermission = true
        fakeHealthConnectSync.granted = emptySet()
        fakeHealthConnectSync.grantedPermissionsFailure = null
        fakeEntitlementRepository.setState(EntitlementState.NotEntitled())
        fakeAuth = FakeAuthManager(
            AuthState.Authenticated(UserProfile("uid-1", "Test", "t@example.com", null)),
        )
        fakeCloudSync = FakeCloudSyncPort()
        fakeAccountDeletion = FakeAccountDeletionPort()
        subject = buildViewModel()
    }

    private fun buildViewModel(
        forceRefresh: ForceRefreshDerivedTrainingData =
            ForceRefreshDerivedTrainingData {
                forceRefreshCalls++
                forceRefreshGate?.await()
                forceRefreshResult
            },
    ): SettingsViewModel =
        SettingsViewModel(
            observeThemePreferences = ObserveThemePreferencesUseCase(fakeThemePreferences),
            observeCheckInPreferences = ObserveCheckInPreferencesUseCase(fakeCheckInPreferences),
            observeEquipmentProposalsEnabled =
                ObserveEquipmentProposalsEnabledUseCase(fakeEquipmentProposals),
            observeRepRangeShiftProposalsEnabled =
                ObserveRepRangeShiftProposalsEnabledUseCase(fakeRepRangeProposals),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(fakeWeightDisplayPreferences),
            observeHealthConnectPreferences =
                ObserveHealthConnectPreferencesUseCase(fakeHealthConnectPreferences),
            observeHealthConnectSyncErrors =
                ObserveHealthConnectSyncErrorsUseCase(fakeHealthConnectSyncStatus),
            observeEntitlement =
                ObserveEntitlementUseCase(fakeEntitlementRepository),
            setThemeFamily = SetThemeFamilyUseCase(fakeThemePreferences, FakeLoggingPort()),
            setColorMode = SetColorModeUseCase(fakeThemePreferences, FakeLoggingPort()),
            setCheckInEnabled = SetCheckInEnabledUseCase(fakeCheckInPreferences, FakeLoggingPort()),
            setEquipmentProposalsEnabled =
                SetEquipmentProposalsEnabledUseCase(fakeEquipmentProposals, FakeLoggingPort()),
            setRepRangeShiftProposalsEnabled =
                SetRepRangeShiftProposalsEnabledUseCase(fakeRepRangeProposals, FakeLoggingPort()),
            setWeightDisplayUnit =
                SetWeightDisplayUnitUseCase(fakeWeightDisplayPreferences, FakeLoggingPort()),
            setHealthConnectEnabled =
                SetHealthConnectEnabledUseCase(fakeHealthConnectPreferences, fakeHealthConnectSync),
            clearHealthConnectSyncError =
                ClearHealthConnectSyncErrorUseCase(fakeHealthConnectSyncStatus),
            markCheckInPending = MarkCheckInPendingUseCase(fakeCheckInPreferences, FakeLoggingPort()),
            forceRefreshDerived = forceRefresh,
            logout = LogoutUseCase(
                authManager = fakeAuth,
                endSessionAndWipe = EndSessionAndWipeUseCase(fakeCloudSync),
                stopCloudSync = StopCloudSyncUseCase(fakeCloudSync),
                startCloudSync = StartCloudSyncUseCase(fakeCloudSync),
            ),
            deleteAccount = DeleteAccountUseCase(
                authManager = fakeAuth,
                accountDeletionPort = fakeAccountDeletion,
                stopCloudSync = StopCloudSyncUseCase(fakeCloudSync),
                startCloudSync = StartCloudSyncUseCase(fakeCloudSync),
                endSessionAndWipe = EndSessionAndWipeUseCase(fakeCloudSync),
            ),
        )

    @Test
    @DisplayName("Given default prefs when observed then state is System + Stratum + features on")
    fun defaultState() = runTest {
        subject.uiState.test {
            val state = awaitItem()
            assertEquals(AppearanceUi.System, state.appearance)
            assertEquals(ThemeFamilyUi.Stratum, state.themeFamily)
            assertEquals(WeightUnitUi.Lbs, state.weightUnit)
            assertTrue(state.checkInsEnabled)
            assertTrue(state.equipmentProposalsEnabled)
            assertTrue(state.repRangeProposalsEnabled)
            // showDebugTools follows BuildConfig.DEBUG of the test variant (true for debug unit tests).
            assertEquals(BuildConfig.DEBUG, state.showDebugTools)
            assertFalse(state.showLogoutConfirm)
            assertFalse(state.isLoggingOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given rep-range toggle when disabled then uiState reflects off")
    fun repRangeProposalsToggle() = runTest {
        subject.uiState.test {
            skipItems(1)
            subject.onPostWorkoutSuggestionEnabledChange(
                PostWorkoutSuggestionUi.RepRangeShift,
                false,
            )
            advanceUntilIdle()
            assertFalse(awaitItem().repRangeProposalsEnabled)
            assertFalse(fakeRepRangeProposals.isEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given equipment toggle when disabled then uiState reflects off")
    fun equipmentProposalsToggle() = runTest {
        subject.uiState.test {
            skipItems(1)
            subject.onPostWorkoutSuggestionEnabledChange(
                PostWorkoutSuggestionUi.EquipmentSteps,
                false,
            )
            advanceUntilIdle()
            assertFalse(awaitItem().equipmentProposalsEnabled)
            assertFalse(fakeEquipmentProposals.isEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given seeded Neo + Dark when observed then presentation enums match")
    fun mapsSeededPreferences() = runTest {
        fakeThemePreferences.seed(
            ThemePreferences(
                family = ThemeFamily.NeoBrutalist,
                colorMode = ColorMode.Dark,
            ),
        )
        subject = buildViewModel()

        subject.uiState.test {
            assertEquals(
                SettingsUiState(
                    appearance = AppearanceUi.Dark,
                    themeFamily = ThemeFamilyUi.NeoBrutalist,
                    checkInsEnabled = true,
                    showDebugTools = BuildConfig.DEBUG,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When appearance selected then repository stores matching ColorMode")
    fun appearanceSelectionPersists() = runTest {
        subject.onAppearanceSelected(AppearanceUi.Light)
        advanceUntilIdle()

        assertEquals(ColorMode.Light, fakeThemePreferences.current.colorMode)
        subject.uiState.test {
            assertEquals(AppearanceUi.Light, awaitItem().appearance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When theme family selected then repository stores NeoBrutalist")
    fun themeFamilySelectionPersists() = runTest {
        subject.onThemeFamilySelected(ThemeFamilyUi.NeoBrutalist)
        advanceUntilIdle()

        assertEquals(ThemeFamily.NeoBrutalist, fakeThemePreferences.current.family)
        subject.uiState.test {
            assertEquals(ThemeFamilyUi.NeoBrutalist, awaitItem().themeFamily)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When check-ins disabled then repository stores enabled=false")
    fun checkInsTogglePersists() = runTest {
        subject.onCheckInsEnabledChange(false)
        advanceUntilIdle()

        assertFalse(fakeCheckInPreferences.current.enabled)
        subject.uiState.test {
            assertFalse(awaitItem().checkInsEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When weight unit selected then repository stores Kg")
    fun weightUnitSelectionPersists() = runTest {
        subject.onWeightUnitSelected(WeightUnitUi.Kg)
        advanceUntilIdle()

        assertEquals(Weight.WeightUnit.Kg, fakeWeightDisplayPreferences.current.displayUnit)
        subject.uiState.test {
            assertEquals(WeightUnitUi.Kg, awaitItem().weightUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("forceCheckInPending enables check-ins and marks pending in debug")
    fun forceCheckInPendingMarksPending() = runTest {
        fakeCheckInPreferences.seed(CheckInPreferences(enabled = false))
        subject = buildViewModel()

        subject.forceCheckInPending()
        advanceUntilIdle()

        if (BuildConfig.DEBUG) {
            assertTrue(fakeCheckInPreferences.current.enabled)
            assertNotNull(fakeCheckInPreferences.current.pendingSinceExtractedAt)
            subject.uiState.test {
                val state = awaitItem()
                assertTrue(state.checkInsEnabled)
                assertNotNull(state.debugStatusMessage)
                cancelAndIgnoreRemainingEvents()
            }
        } else {
            assertFalse(fakeCheckInPreferences.current.enabled)
            assertEquals(null, fakeCheckInPreferences.current.pendingSinceExtractedAt)
        }
    }

    @Test
    @DisplayName("rebuild progress confirm runs force refresh and sets cooldown")
    fun rebuildProgressSuccess() = runTest {
        subject.uiState.test {
            skipItems(1)
            subject.onRebuildProgressClick()
            assertTrue(awaitItem().showRebuildProgressConfirm)

            subject.onRebuildProgressConfirmed()
            // running then settled with cooldown
            val running = awaitItem()
            assertTrue(running.isForceRefreshingDerived || running.isForceRefreshCooldown)
            val settled =
                if (running.isForceRefreshCooldown && !running.isForceRefreshingDerived) {
                    running
                } else {
                    awaitItem()
                }
            assertEquals(1, forceRefreshCalls)
            assertTrue(settled.isForceRefreshCooldown)
            assertFalse(settled.isForceRefreshingDerived)
            assertFalse(settled.showRebuildProgressConfirm)
            assertNotNull(subject.userMessage.value)
            assertTrue(subject.userMessage.value!!.contains("Volume charts", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("rebuild progress failure surfaces message without cooldown")
    fun rebuildProgressFailure() = runTest {
        forceRefreshResult = Result.failure(IllegalStateException("boom"))
        subject = buildViewModel()

        subject.uiState.test {
            skipItems(1)
            subject.onRebuildProgressClick()
            assertTrue(awaitItem().showRebuildProgressConfirm)
            subject.onRebuildProgressConfirmed()
            // Consume running / failed emissions until settled
            var settled = awaitItem()
            if (settled.isForceRefreshingDerived) {
                settled = awaitItem()
            }
            assertEquals(1, forceRefreshCalls)
            assertTrue(subject.userMessage.value!!.contains("Couldn't rebuild", ignoreCase = true))
            assertFalse(settled.isForceRefreshCooldown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("double confirm while running is ignored")
    fun rebuildProgressIgnoresReentrancy() = runTest {
        val gate = CompletableDeferred<Unit>()
        forceRefreshGate = gate
        subject = buildViewModel()

        subject.uiState.test {
            skipItems(1)
            subject.onRebuildProgressClick()
            assertTrue(awaitItem().showRebuildProgressConfirm)
            subject.onRebuildProgressConfirmed()
            assertTrue(awaitItem().isForceRefreshingDerived)

            subject.onRebuildProgressConfirmed()
            expectNoEvents()
            assertEquals(1, forceRefreshCalls)

            gate.complete(Unit)
            val settled = awaitItem()
            assertEquals(1, forceRefreshCalls)
            assertTrue(settled.isForceRefreshCooldown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When logout clicked then confirm dialog is shown without wiping yet")
    fun logoutClickShowsConfirm() = runTest {
        subject.onLogoutClick()
        advanceUntilIdle()

        subject.uiState.test {
            assertTrue(awaitItem().showLogoutConfirm)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList<String>(), fakeCloudSync.callOrder)
        assertEquals(0, fakeAuth.signOutCount)
    }

    @Test
    @DisplayName("When logout confirmed then wipe and sign out run")
    fun logoutConfirmedInvokesUseCase() = runTest {
        subject.onLogoutClick()
        subject.onLogoutConfirmed()
        advanceUntilIdle()

        assertEquals(listOf("wipe"), fakeCloudSync.callOrder)
        assertEquals("uid-1", fakeCloudSync.lastWipedUserId)
        assertEquals(1, fakeAuth.signOutCount)
        assertEquals(AuthState.Unauthenticated, fakeAuth.authState.value)
        assertNull(subject.userMessage.value)
        assertFalse(subject.uiState.value.isLoggingOut)
        assertFalse(subject.uiState.value.showLogoutConfirm)
    }

    @Test
    @DisplayName("When logout fails then userMessage is set and in-flight clears")
    fun logoutFailureSurfacesMessage() = runTest {
        fakeCloudSync.wipeResult = Result.failure(IllegalStateException("wipe failed"))
        subject = buildViewModel()

        subject.onLogoutClick()
        subject.onLogoutConfirmed()
        advanceUntilIdle()

        // Wipe fails → recovery start; sign-out not called
        assertEquals(0, fakeAuth.signOutCount)
        assertEquals("Couldn't sign out. Try again.", subject.userMessage.value)
        assertFalse(subject.uiState.value.isLoggingOut)
    }

    @Test
    @DisplayName("When logout confirm dismissed then dialog closes")
    fun logoutConfirmDismiss() = runTest {
        subject.onLogoutClick()
        advanceUntilIdle()
        subject.onLogoutConfirmDismiss()
        advanceUntilIdle()

        assertFalse(subject.uiState.value.showLogoutConfirm)
        assertEquals(emptyList<String>(), fakeCloudSync.callOrder)
    }

    @Test
    @DisplayName("When delete account clicked then confirm dialog is shown without deleting yet")
    fun deleteAccountClickShowsConfirm() = runTest {
        subject.onDeleteAccountClick()
        advanceUntilIdle()

        subject.uiState.test {
            assertTrue(awaitItem().showDeleteConfirm)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, fakeAccountDeletion.deleteCount)
        assertEquals(emptyList<String>(), fakeCloudSync.callOrder)
        assertEquals(0, fakeAuth.signOutCount)
    }

    @Test
    @DisplayName("When delete account confirmed then stop, remote data, wipe, auth, and sign out run")
    fun deleteAccountConfirmedInvokesUseCase() = runTest {
        subject.onDeleteAccountClick()
        subject.onDeleteAccountConfirmed()
        advanceUntilIdle()

        assertEquals(1, fakeAccountDeletion.deleteDataCount)
        assertEquals(1, fakeAccountDeletion.deleteAuthCount)
        assertEquals(listOf("stop", "wipe"), fakeCloudSync.callOrder)
        assertEquals("uid-1", fakeCloudSync.lastWipedUserId)
        assertEquals(1, fakeAuth.signOutCount)
        assertEquals(AuthState.Unauthenticated, fakeAuth.authState.value)
        assertNull(subject.userMessage.value)
        assertFalse(subject.uiState.value.isDeletingAccount)
        assertFalse(subject.uiState.value.showDeleteConfirm)
    }

    @Test
    @DisplayName("When logout confirm is open then delete click does not open second dialog")
    fun exclusiveAccountConfirmDialogs() = runTest {
        subject.uiState.test {
            // Collect so stateIn stays active.
            skipItems(1)
            subject.onLogoutClick()
            val afterLogout = awaitItem()
            assertTrue(afterLogout.showLogoutConfirm)
            assertFalse(afterLogout.showDeleteConfirm)

            subject.onDeleteAccountClick()
            expectNoEvents()

            assertTrue(subject.uiState.value.showLogoutConfirm)
            assertFalse(subject.uiState.value.showDeleteConfirm)
            assertEquals(0, fakeAccountDeletion.deleteCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When auth delete fails after wipe then distinct finish-account message")
    fun deleteAccountAuthFailureSurfacesGuidance() = runTest {
        fakeAccountDeletion.deleteAuthResult =
            Result.failure(IllegalStateException("auth delete failed"))
        subject = buildViewModel()

        subject.onDeleteAccountClick()
        subject.onDeleteAccountConfirmed()
        advanceUntilIdle()

        assertEquals(1, fakeAccountDeletion.deleteDataCount)
        assertEquals(1, fakeAccountDeletion.deleteAuthCount)
        assertEquals(0, fakeAuth.signOutCount)
        assertTrue(
            subject.userMessage.value?.contains("fully closed") == true ||
                subject.userMessage.value?.contains("Try Delete account again") == true,
        )
        assertFalse(subject.uiState.value.isDeletingAccount)
    }

    @Test
    @DisplayName("When remote delete fails then generic userMessage is set")
    fun deleteAccountFailureSurfacesMessage() = runTest {
        fakeAccountDeletion.deleteDataResult = Result.failure(IllegalStateException("cloud failed"))
        subject = buildViewModel()

        subject.onDeleteAccountClick()
        subject.onDeleteAccountConfirmed()
        advanceUntilIdle()

        assertEquals(1, fakeAccountDeletion.deleteDataCount)
        assertEquals(0, fakeAccountDeletion.deleteAuthCount)
        assertEquals(0, fakeAuth.signOutCount)
        assertEquals("Couldn't delete account. Try again.", subject.userMessage.value)
        assertFalse(subject.uiState.value.isDeletingAccount)
    }

    @Test
    @DisplayName("When local wipe fails after cloud data delete then local-cleanup guidance snackbar")
    fun deleteAccountLocalCleanupFailureSurfacesGuidance() = runTest {
        fakeCloudSync.wipeResult = Result.failure(IllegalStateException("wipe failed"))
        subject = buildViewModel()

        subject.onDeleteAccountClick()
        subject.onDeleteAccountConfirmed()
        advanceUntilIdle()

        assertEquals(1, fakeAccountDeletion.deleteDataCount)
        assertEquals(0, fakeAccountDeletion.deleteAuthCount)
        assertEquals(0, fakeAuth.signOutCount)
        assertTrue(fakeAuth.authState.value is AuthState.Authenticated)
        assertTrue(
            subject.userMessage.value?.contains("clear the app") == true ||
                subject.userMessage.value?.contains("Uninstall") == true,
        )
        assertFalse(subject.uiState.value.isDeletingAccount)
    }

    @Test
    @DisplayName("When delete account confirm dismissed then dialog closes")
    fun deleteAccountConfirmDismiss() = runTest {
        subject.onDeleteAccountClick()
        advanceUntilIdle()
        subject.onDeleteAccountConfirmDismiss()
        advanceUntilIdle()

        assertFalse(subject.uiState.value.showDeleteConfirm)
        assertEquals(0, fakeAccountDeletion.deleteCount)
    }

    @Test
    @DisplayName("When Health Connect toggle changed then state updates")
    fun healthConnectToggleUpdatesState() = runTest {
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectEnabledChange(true)
            advanceUntilIdle()
            assertTrue(awaitItem().healthConnectEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Health Connect enable succeeds when available even without write permission")
    fun healthConnectEnableSucceedsWithoutWrite() = runTest {
        fakeHealthConnectSync.hasPermission = false
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectEnabledChange(true)
            advanceUntilIdle()
            assertTrue(awaitItem().healthConnectEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Health Connect disable still works after a dashboard-only opt-in")
    fun healthConnectDisableStillWorks() = runTest {
        fakeHealthConnectSync.hasPermission = false
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectEnabledChange(true)
            advanceUntilIdle()
            assertTrue(awaitItem().healthConnectEnabled)
            subject.onHealthConnectEnabledChange(false)
            advanceUntilIdle()
            assertFalse(awaitItem().healthConnectEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("permission sheet uses current grants so cancel does not disable")
    fun permissionSheetCancelKeepsEnabledWhenGrantsRemain() = runTest {
        fakeHealthConnectSync.granted = setOf(HealthConnectLaunchPermissions.writeExercise)
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectEnabledChange(true)
            advanceUntilIdle()
            assertTrue(awaitItem().healthConnectEnabled)

            subject.onHealthConnectPermissionSheetClosed()
            advanceUntilIdle()
            val afterSheet = expectMostRecentItem()
            assertTrue(afterSheet.healthConnectEnabled)
            assertTrue(afterSheet.healthConnectWriteGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("permission sheet enables from read-only grants")
    fun permissionSheetEnablesFromReads() = runTest {
        fakeHealthConnectSync.hasPermission = false
        fakeHealthConnectSync.granted = setOf(HealthConnectLaunchPermissions.readSteps)
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectPermissionSheetClosed()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.healthConnectEnabled)
            assertTrue(state.healthConnectReadsGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("permission sheet query failure leaves the current enabled state")
    fun permissionSheetQueryFailureKeepsEnabled() = runTest {
        subject.uiState.test {
            skipItems(1)
            subject.onHealthConnectEnabledChange(true)
            advanceUntilIdle()
            assertTrue(awaitItem().healthConnectEnabled)

            fakeHealthConnectSync.grantedPermissionsFailure = IllegalStateException("sdk blip")
            subject.onHealthConnectPermissionSheetClosed()
            advanceUntilIdle()
            expectNoEvents()
            assertTrue(subject.uiState.value.healthConnectEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("row tap requests only permissions Health Connect has not granted")
    fun requestPermissionsEmitsMissingOnly() = runTest {
        fakeHealthConnectSync.granted = setOf(HealthConnectLaunchPermissions.writeExercise)
        subject.healthConnectPermissionAction.test {
            subject.onRequestHealthConnectPermissions()
            advanceUntilIdle()
            val action = awaitItem() as HealthConnectPermissionAction.Request
            assertEquals(
                HealthConnectLaunchPermissions.all -
                    setOf(HealthConnectLaunchPermissions.writeExercise),
                action.permissions,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("write row requests only the workout write permission when it is missing")
    fun requestWriteFocusEmitsWriteOnly() = runTest {
        fakeHealthConnectSync.granted = setOf(HealthConnectLaunchPermissions.readSteps)
        subject.healthConnectPermissionAction.test {
            subject.onRequestHealthConnectPermissions(HealthConnectPermissionFocus.Write)
            advanceUntilIdle()
            val action = awaitItem() as HealthConnectPermissionAction.Request
            assertEquals(setOf(HealthConnectLaunchPermissions.writeExercise), action.permissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("dashboard row requests only missing read permissions")
    fun requestReadsFocusEmitsMissingReads() = runTest {
        fakeHealthConnectSync.granted = setOf(
            HealthConnectLaunchPermissions.writeExercise,
            HealthConnectLaunchPermissions.readSteps,
        )
        subject.healthConnectPermissionAction.test {
            subject.onRequestHealthConnectPermissions(HealthConnectPermissionFocus.Reads)
            advanceUntilIdle()
            val action = awaitItem() as HealthConnectPermissionAction.Request
            assertEquals(
                HealthConnectLaunchPermissions.reads -
                    setOf(HealthConnectLaunchPermissions.readSteps),
                action.permissions,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("grant snapshot maps write vs dashboard reads into settings state")
    fun refreshGrantsMapsWriteAndReads() = runTest {
        fakeHealthConnectSync.granted = setOf(HealthConnectLaunchPermissions.writeExercise)
        subject.uiState.test {
            skipItems(1)
            subject.refreshHealthConnectGrants()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.healthConnectWriteGranted)
            assertFalse(state.healthConnectReadsGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("row tap opens Health Connect settings and opts in when every permission is granted")
    fun requestPermissionsOpensSettingsWhenNothingMissing() = runTest {
        fakeHealthConnectSync.granted = HealthConnectLaunchPermissions.all
        subject.healthConnectPermissionAction.test {
            subject.onRequestHealthConnectPermissions()
            advanceUntilIdle()
            assertEquals(HealthConnectPermissionAction.OpenSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakeHealthConnectPreferences.getPreferences().enabled)
    }

    @Test
    @DisplayName("row tap requests the full set when the grant query fails")
    fun requestPermissionsFallsBackToAllOnQueryFailure() = runTest {
        fakeHealthConnectSync.grantedPermissionsFailure = IllegalStateException("sdk blip")
        subject.healthConnectPermissionAction.test {
            subject.onRequestHealthConnectPermissions()
            advanceUntilIdle()
            val action = awaitItem() as HealthConnectPermissionAction.Request
            assertEquals(HealthConnectLaunchPermissions.all, action.permissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Health Connect settings unavailable surfaces a user message")
    fun healthConnectSettingsUnavailableSurfacesMessage() = runTest {
        subject.userMessage.test {
            assertEquals(null, awaitItem())
            subject.onHealthConnectSettingsUnavailable()
            assertEquals(
                "Couldn't open Health Connect. Is it available on this device?",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("When sticky Health Connect sync error is reported then userMessage surfaces it")
    fun healthConnectSyncErrorSurfacesInUserMessage() = runTest {
        subject.userMessage.test {
            assertEquals(null, awaitItem())
            fakeHealthConnectSyncStatus.reportSyncError("Health Connect write failed")
            advanceUntilIdle()
            assertEquals("Health Connect write failed", awaitItem())
            assertEquals(null, fakeHealthConnectSyncStatus.currentPendingError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Entitlement status updates isEntitled and isComplimentary in uiState")
    fun entitlementStatusUpdatesUiState() = runTest {
        fakeEntitlementRepository.setState(EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess))

        subject.uiState.test {
            val state = awaitItem()
            assertTrue(state.isEntitled)
            assertTrue(state.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Play entitled maps isEntitled true and isComplimentary false")
    fun playEntitledMapsMembershipFlags() = runTest {
        fakeEntitlementRepository.setState(
            EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
        )
        subject = buildViewModel()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.isEntitled)
            assertFalse(state.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("NotEntitled maps isEntitled and isComplimentary false")
    fun notEntitledMapsMembershipFlags() = runTest {
        fakeEntitlementRepository.setState(EntitlementState.NotEntitled())
        subject = buildViewModel()

        subject.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isEntitled)
            assertFalse(state.isComplimentary)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

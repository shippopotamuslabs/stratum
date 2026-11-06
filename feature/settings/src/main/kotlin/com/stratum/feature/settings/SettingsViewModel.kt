package com.stratum.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.auth.DeleteAccountResult
import com.stratum.application.auth.DeleteAccountUseCase
import com.stratum.application.auth.LogoutResult
import com.stratum.application.auth.LogoutUseCase
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.checkin.CheckInPreferenceResult
import com.stratum.application.checkin.MarkCheckInPendingUseCase
import com.stratum.application.checkin.ObserveCheckInPreferencesUseCase
import com.stratum.application.checkin.SetCheckInEnabledUseCase
import com.stratum.application.healthconnect.ClearHealthConnectSyncErrorResult
import com.stratum.application.healthconnect.ClearHealthConnectSyncErrorUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectSyncErrorsUseCase
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.healthconnect.ApplyGrantedPermissionsDiagnostic
import com.stratum.application.healthconnect.ApplyGrantedPermissionsOutcome
import com.stratum.application.healthconnect.HealthConnectPermissionsQueryResult
import com.stratum.application.healthconnect.SetHealthConnectEnabledResult
import com.stratum.application.healthconnect.SetHealthConnectEnabledUseCase
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.ObserveEntitlementUseCase
import com.stratum.application.lift.ObserveEquipmentProposalsEnabledUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.lift.EquipmentProposalPreferenceResult
import com.stratum.application.lift.SetEquipmentProposalsEnabledUseCase
import com.stratum.application.lift.SetWeightDisplayUnitUseCase
import com.stratum.application.lift.WeightDisplayPreferenceResult
import com.stratum.domain.lift.Weight
import com.stratum.domain.theme.ColorMode
import com.stratum.application.theme.ObserveThemePreferencesUseCase
import com.stratum.application.theme.SetColorModeUseCase
import com.stratum.application.theme.SetThemeFamilyUseCase
import com.stratum.application.theme.ThemePreferenceResult
import com.stratum.domain.theme.ThemeFamily
import com.stratum.application.training.ForceRefreshDerivedTrainingData
import com.stratum.application.training.ForceRefreshDerivedTrainingDataResult
import com.stratum.application.workout.ObserveRepRangeShiftProposalsEnabledUseCase
import com.stratum.application.workout.RepRangeShiftPreferenceResult
import com.stratum.application.workout.SetRepRangeShiftProposalsEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemePreferences: ObserveThemePreferencesUseCase,
    observeCheckInPreferences: ObserveCheckInPreferencesUseCase,
    observeEquipmentProposalsEnabled: ObserveEquipmentProposalsEnabledUseCase,
    observeRepRangeShiftProposalsEnabled: ObserveRepRangeShiftProposalsEnabledUseCase,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    observeHealthConnectPreferences: ObserveHealthConnectPreferencesUseCase,
    observeHealthConnectSyncErrors: ObserveHealthConnectSyncErrorsUseCase,
    observeEntitlement: ObserveEntitlementUseCase,
    private val setThemeFamily: SetThemeFamilyUseCase,
    private val setColorMode: SetColorModeUseCase,
    private val setCheckInEnabled: SetCheckInEnabledUseCase,
    private val setEquipmentProposalsEnabled: SetEquipmentProposalsEnabledUseCase,
    private val setRepRangeShiftProposalsEnabled: SetRepRangeShiftProposalsEnabledUseCase,
    private val setWeightDisplayUnit: SetWeightDisplayUnitUseCase,
    private val setHealthConnectEnabled: SetHealthConnectEnabledUseCase,
    private val diagnosticDrain: DiagnosticDrain,
    private val clearHealthConnectSyncError: ClearHealthConnectSyncErrorUseCase,
    private val markCheckInPending: MarkCheckInPendingUseCase,
    private val forceRefreshDerived: ForceRefreshDerivedTrainingData,
    private val logout: LogoutUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val debugStatusMessage = MutableStateFlow<String?>(null)
    private val rebuildProgressUi = MutableStateFlow(RebuildProgressUi())
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _healthConnectPermissionAction =
        MutableSharedFlow<HealthConnectPermissionAction>(extraBufferCapacity = 1)
    val healthConnectPermissionAction: SharedFlow<HealthConnectPermissionAction> =
        _healthConnectPermissionAction.asSharedFlow()

    private val healthConnectGranted = MutableStateFlow<Set<String>>(emptySet())

    /** Exclusive account lifecycle UI — only one dialog or in-flight op at a time. */
    private val accountUi = MutableStateFlow<AccountUi>(AccountUi.Idle)

    init {
        observeHealthConnectSyncErrors()
            .onEach { kind ->
                _userMessage.value = kind.toUserMessage()
                val clearResult = clearHealthConnectSyncError()
                if (clearResult is ClearHealthConnectSyncErrorResult.PersistenceFailed) {
                    diagnosticDrain.drain(clearResult.diagnostics)
                }
            }
            .launchIn(viewModelScope)
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            combine(
                combine(
                    observeThemePreferences(),
                    observeCheckInPreferences(),
                    observeEquipmentProposalsEnabled(),
                ) { theme, checkIn, equipment -> Triple(theme, checkIn, equipment) },
                combine(
                    observeRepRangeShiftProposalsEnabled(),
                    observeWeightDisplayPreferences(),
                    observeHealthConnectPreferences(),
                ) { repRange, weightDisplay, healthConnect ->
                    Triple(repRange, weightDisplay, healthConnect)
                },
                observeEntitlement(),
            ) { appearance, training, entitlement ->
                val (theme, checkIn, equipmentEnabled) = appearance
                val (repRangeEnabled, weightDisplay, healthConnect) = training
                PreferenceSnapshot(
                    appearance = theme.colorMode.toAppearanceUi(),
                    themeFamily = theme.family.toThemeFamilyUi(),
                    checkInsEnabled = checkIn.enabled,
                    equipmentProposalsEnabled = equipmentEnabled,
                    repRangeProposalsEnabled = repRangeEnabled,
                    weightUnit = weightDisplay.displayUnit.toWeightUnitUi(),
                    healthConnectEnabled = healthConnect.enabled,
                    isEntitled = entitlement is EntitlementState.Entitled,
                    isComplimentary = entitlement is EntitlementState.Entitled &&
                        entitlement.source == EntitlementSource.ComplimentaryAccess,
                )
            },
            debugStatusMessage,
            accountUi,
            rebuildProgressUi,
            healthConnectGranted,
        ) { prefs, debugStatus, account, rebuild, granted ->
            SettingsUiState(
                appearance = prefs.appearance,
                themeFamily = prefs.themeFamily,
                checkInsEnabled = prefs.checkInsEnabled,
                equipmentProposalsEnabled = prefs.equipmentProposalsEnabled,
                repRangeProposalsEnabled = prefs.repRangeProposalsEnabled,
                weightUnit = prefs.weightUnit,
                healthConnectEnabled = prefs.healthConnectEnabled,
                healthConnectWriteGranted = healthConnectWriteGranted(granted),
                healthConnectReadsGranted = healthConnectReadsGranted(granted),
                isEntitled = prefs.isEntitled,
                isComplimentary = prefs.isComplimentary,
                showDebugTools = BuildConfig.DEBUG,
                debugStatusMessage = debugStatus,
                isForceRefreshingDerived = rebuild.isRunning,
                isForceRefreshCooldown = rebuild.isOnCooldown(),
                showRebuildProgressConfirm = rebuild.showConfirm,
                showLogoutConfirm = account is AccountUi.ConfirmLogout,
                isLoggingOut = account is AccountUi.LoggingOut,
                showDeleteConfirm = account is AccountUi.ConfirmDelete,
                isDeletingAccount = account is AccountUi.DeletingAccount,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(showDebugTools = BuildConfig.DEBUG),
        )

    fun onHealthConnectEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = setHealthConnectEnabled(enabled)) {
                SetHealthConnectEnabledResult.Success -> Unit
                SetHealthConnectEnabledResult.SdkUnavailable ->
                    _userMessage.value = MSG_HEALTH_SYNC_UNAVAILABLE
                is SetHealthConnectEnabledResult.PersistenceFailed -> {
                    diagnosticDrain.drain(result.diagnostics)
                    _userMessage.value = MSG_PREFS_FAILED
                }
            }
        }
    }

    /**
     * Permission sheet closed. Opt-in from the SDK grant set, not the activity result, so Back /
     * cancel cannot turn an already-enabled switch off.
     */
    fun onHealthConnectPermissionSheetClosed() {
        viewModelScope.launch {
            handleApplyGrantedPermissions(
                setHealthConnectEnabled.applyGrantedPermissions(HealthConnectLaunchPermissions.all),
            )
            loadHealthConnectGrants()
        }
    }

    private fun handleApplyGrantedPermissions(outcome: ApplyGrantedPermissionsOutcome) {
        when (outcome) {
            is ApplyGrantedPermissionsOutcome.Applied ->
                logPermissionDiagnostics(outcome.diagnostics)
            ApplyGrantedPermissionsOutcome.SdkUnavailable ->
                _userMessage.value = MSG_HEALTH_SYNC_UNAVAILABLE
            is ApplyGrantedPermissionsOutcome.PersistenceFailed -> {
                diagnosticDrain.drain(outcome.diagnostics)
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    /**
     * Row tap or switch-on: request only types Health Connect has not granted for [focus].
     * If that subset is already granted, opt in from the current grant set and open Health Connect.
     */
    fun onRequestHealthConnectPermissions(
        focus: HealthConnectPermissionFocus = HealthConnectPermissionFocus.All,
    ) {
        viewModelScope.launch {
            loadHealthConnectGrants()
            val requested = focus.requestedPermissions()
            val missing = when (
                val query = setHealthConnectEnabled.permissionsStillNeeded(requested)
            ) {
                is HealthConnectPermissionsQueryResult.Success -> query.permissions
                HealthConnectPermissionsQueryResult.QueryFailed -> requested
            }
            if (missing.isEmpty()) {
                handleApplyGrantedPermissions(
                    setHealthConnectEnabled.applyGrantedPermissions(
                        HealthConnectLaunchPermissions.all,
                    ),
                )
                _healthConnectPermissionAction.emit(HealthConnectPermissionAction.OpenSettings)
            } else {
                _healthConnectPermissionAction.emit(HealthConnectPermissionAction.Request(missing))
            }
        }
    }

    fun refreshHealthConnectGrants() {
        viewModelScope.launch { loadHealthConnectGrants() }
    }

    fun onHealthConnectSettingsUnavailable() {
        _userMessage.value = MSG_HEALTH_CONNECT_SETTINGS
    }

    private suspend fun loadHealthConnectGrants() {
        val query = setHealthConnectEnabled.grantedPermissions()
        if (query is HealthConnectPermissionsQueryResult.Success) {
            healthConnectGranted.value = query.permissions
        }
    }

    fun onAppearanceSelected(appearance: AppearanceUi) {
        viewModelScope.launch {
            val result = setColorMode(appearance.toDomain())
            if (result is ThemePreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onThemeFamilySelected(family: ThemeFamilyUi) {
        viewModelScope.launch {
            val result = setThemeFamily(family.toDomain())
            if (result is ThemePreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onCheckInsEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            val result = setCheckInEnabled(enabled)
            if (result is CheckInPreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onWeightUnitSelected(unit: WeightUnitUi) {
        viewModelScope.launch {
            val result = setWeightDisplayUnit(unit.toDomain())
            if (result is WeightDisplayPreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    fun onPostWorkoutSuggestionEnabledChange(
        kind: PostWorkoutSuggestionUi,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val failed =
                when (kind) {
                    PostWorkoutSuggestionUi.EquipmentSteps -> {
                        val result = setEquipmentProposalsEnabled(enabled)
                        if (result is EquipmentProposalPreferenceResult.PersistenceFailed) {
                            diagnosticDrain.drain(result.diagnostics)
                            true
                        } else {
                            false
                        }
                    }
                    PostWorkoutSuggestionUi.RepRangeShift -> {
                        val result = setRepRangeShiftProposalsEnabled(enabled)
                        if (result is RepRangeShiftPreferenceResult.PersistenceFailed) {
                            diagnosticDrain.drain(result.diagnostics)
                            true
                        } else {
                            false
                        }
                    }
                }
            if (failed) {
                _userMessage.value = MSG_PREFS_FAILED
            }
        }
    }

    /**
     * Debug-only: ensure check-ins are enabled and mark a pending coach check-in so the Programs
     * banner appears without waiting for the 20-workout re-extract gate.
     */
    fun forceCheckInPending() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val enable = setCheckInEnabled(true)
            val pending = markCheckInPending(Instant.now())
            listOf(enable, pending).forEach { result ->
                if (result is CheckInPreferenceResult.PersistenceFailed) {
                    diagnosticDrain.drain(result.diagnostics)
                }
            }
            if (
                enable is CheckInPreferenceResult.PersistenceFailed ||
                pending is CheckInPreferenceResult.PersistenceFailed
            ) {
                _userMessage.value = MSG_PREFS_FAILED
                return@launch
            }
            debugStatusMessage.value = "Check-in pending. Open Programs."
        }
    }

    /** Opens the confirm dialog for a full local progress rebuild. */
    fun onRebuildProgressClick() {
        val current = rebuildProgressUi.value
        if (current.isRunning || current.isOnCooldown() || current.showConfirm) return
        rebuildProgressUi.update { it.copy(showConfirm = true) }
    }

    fun onRebuildProgressConfirmDismiss() {
        rebuildProgressUi.update { it.copy(showConfirm = false) }
    }

    /**
     * Rebuilds local insights + weekly series and force-queues the full dose-response extract
     * (bypasses the 20-workout gate). Recovery for missing Dashboard progress after import.
     */
    fun onRebuildProgressConfirmed() {
        val current = rebuildProgressUi.value
        if (current.isRunning || current.isOnCooldown()) {
            rebuildProgressUi.update { it.copy(showConfirm = false) }
            return
        }
        rebuildProgressUi.update { it.copy(showConfirm = false, isRunning = true) }
        viewModelScope.launch {
            val forceRefreshOutcome = forceRefreshDerived()
            when (forceRefreshOutcome) {
                ForceRefreshDerivedTrainingDataResult.Success -> {
                    rebuildProgressUi.update {
                        it.copy(
                            isRunning = false,
                            cooldownUntilEpochMs =
                                System.currentTimeMillis() + FORCE_REFRESH_COOLDOWN_MS,
                        )
                    }
                    _userMessage.value = MSG_FORCE_REFRESH_QUEUED
                }
                is ForceRefreshDerivedTrainingDataResult.PersistenceFailed -> {
                    diagnosticDrain.drain(forceRefreshOutcome.diagnostics)
                    rebuildProgressUi.update { it.copy(isRunning = false) }
                    _userMessage.value = MSG_FORCE_REFRESH_FAILED
                }
            }
        }
    }

    fun onLogoutClick() {
        accountUi.update { current ->
            if (current is AccountUi.Idle) AccountUi.ConfirmLogout else current
        }
    }

    fun onLogoutConfirmDismiss() {
        accountUi.update { current ->
            if (current is AccountUi.ConfirmLogout) AccountUi.Idle else current
        }
    }

    fun onLogoutConfirmed() {
        var claimed = false
        accountUi.update { current ->
            if (current is AccountUi.ConfirmLogout) {
                claimed = true
                AccountUi.LoggingOut
            } else {
                current
            }
        }
        if (!claimed) return
        viewModelScope.launch {
            try {
                when (val result = logout()) {
                    LogoutResult.Success -> Unit
                    is LogoutResult.WipeFailed -> {
                        // STR-238: the primary wipe failure drains here too,
                        // exactly once, alongside the compensating restart's.
                        drainSyncDiagnostics(result.wipeDiagnostics, "endSessionAndWipe failed")
                        drainRestartDiagnostics(
                            result.restartDiagnostics,
                            MSG_SYNC_RESTART_FAILED_LOGOUT,
                        )
                        _userMessage.value = MSG_LOGOUT_FAILED
                    }
                    is LogoutResult.SignOutFailed -> {
                        drainRestartDiagnostics(
                            result.restartDiagnostics,
                            MSG_SYNC_RESTART_FAILED_LOGOUT,
                        )
                        _userMessage.value = MSG_LOGOUT_FAILED
                    }
                }
            } finally {
                accountUi.value = AccountUi.Idle
            }
        }
    }

    /**
     * STR-238: drains a sync operation's failure diagnostics exactly once,
     * each rendered with the sync adapter's tag/message shape for that
     * operation ([operation] is "endSessionAndWipe failed") so the record
     * matches the adapter's legacy log line for the same failure.
     */
    private fun drainSyncDiagnostics(
        diagnostics: List<CloudSyncDiagnostic>,
        operation: String,
    ) {
        diagnostics.forEach { diagnostic ->
            when (diagnostic) {
                is CloudSyncDiagnostic.OperationFailed ->
                    diagnosticDrain.drain(
                        OperationalDiagnostic(
                            tag = TAG_SYNC_ADAPTER,
                            message = "$operation: ${diagnostic.cause.message}",
                            cause = diagnostic.cause,
                        ),
                    )
            }
        }
    }

    /** STR-256: a failed compensating sync restart is drained here exactly once. */
    private fun drainRestartDiagnostics(
        diagnostics: List<CloudSyncDiagnostic>,
        message: String,
    ) {
        diagnostics.forEach { diagnostic ->
            when (diagnostic) {
                is CloudSyncDiagnostic.OperationFailed ->
                    diagnosticDrain.drain(
                        OperationalDiagnostic(
                            tag = TAG_AUTH,
                            message = message,
                            cause = diagnostic.cause,
                        ),
                    )
            }
        }
    }

    fun onDeleteAccountClick() {
        accountUi.update { current ->
            if (current is AccountUi.Idle) AccountUi.ConfirmDelete else current
        }
    }

    fun onDeleteAccountConfirmDismiss() {
        accountUi.update { current ->
            if (current is AccountUi.ConfirmDelete) AccountUi.Idle else current
        }
    }

    fun onDeleteAccountConfirmed() {
        var claimed = false
        accountUi.update { current ->
            if (current is AccountUi.ConfirmDelete) {
                claimed = true
                AccountUi.DeletingAccount
            } else {
                current
            }
        }
        if (!claimed) return
        viewModelScope.launch {
            try {
                when (val result = deleteAccount()) {
                    DeleteAccountResult.Success -> Unit
                    DeleteAccountResult.NotSignedIn ->
                        _userMessage.value = MSG_DELETE_FAILED
                    is DeleteAccountResult.RemoteDataFailed -> {
                        // STR-256: a failed compensating sync restart is drained here
                        // exactly once.
                        drainRestartDiagnostics(
                            result.restartDiagnostics,
                            MSG_SYNC_RESTART_FAILED_DELETE,
                        )
                        _userMessage.value = MSG_DELETE_FAILED
                    }
                    is DeleteAccountResult.LocalCleanupFailed -> {
                        // STR-238: the primary wipe failure is drained here
                        // exactly once.
                        drainSyncDiagnostics(result.wipeDiagnostics, "endSessionAndWipe failed")
                        _userMessage.value = MSG_DELETE_LOCAL_CLEANUP_FAILED
                    }
                    DeleteAccountResult.AuthDeleteFailed ->
                        _userMessage.value = MSG_DELETE_AUTH_FAILED
                }
            } finally {
                accountUi.value = AccountUi.Idle
            }
        }
    }

    private data class PreferenceSnapshot(
        val appearance: AppearanceUi,
        val themeFamily: ThemeFamilyUi,
        val checkInsEnabled: Boolean,
        val equipmentProposalsEnabled: Boolean,
        val repRangeProposalsEnabled: Boolean,
        val weightUnit: WeightUnitUi,
        val healthConnectEnabled: Boolean,
        val isEntitled: Boolean,
        val isComplimentary: Boolean,
    )

    private data class RebuildProgressUi(
        val showConfirm: Boolean = false,
        val isRunning: Boolean = false,
        val cooldownUntilEpochMs: Long = 0L,
    ) {
        fun isOnCooldown(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs < cooldownUntilEpochMs
    }

    private sealed class AccountUi {
        data object Idle : AccountUi()
        data object ConfirmLogout : AccountUi()
        data object ConfirmDelete : AccountUi()
        data object LoggingOut : AccountUi()
        data object DeletingAccount : AccountUi()
    }

    /**
     * Translates the sticky sync-error kind to the existing shipped user text (STR-268).
     * The strings are the Health Connect classification wordings — no new copy.
     */
    private fun HealthFailureKind.toUserMessage(): String =
        when (this) {
            HealthFailureKind.Unavailable -> MSG_HEALTH_SYNC_UNAVAILABLE
            HealthFailureKind.PermissionDenied -> MSG_HEALTH_SYNC_PERMISSION
            HealthFailureKind.Unknown -> MSG_HEALTH_SYNC_FAILED
        }

    /**
     * STR-257: reports each permission-query diagnostic exactly once through the screen's
     * error-logging wiring. Diagnostics are log-only — never surfaced in the UI. Error
     * level (not warning) so the bound failure travels attached: `LoggingPort.warning`
     * carries no throwable and ViewModels must not inject `LoggingPort` directly.
     */
    internal fun logPermissionDiagnostics(diagnostics: List<ApplyGrantedPermissionsDiagnostic>) {
        for (diagnostic in diagnostics) {
            when (diagnostic) {
                is ApplyGrantedPermissionsDiagnostic.PermissionQueryFailed ->
                    diagnosticDrain.drain(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Health Connect permission query failed — preference left unchanged.",
                            cause = diagnostic.cause,
                        ),
                    )
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val TAG_AUTH = "SettingsAuth"

        /** The sync adapter's log tag — sync-failure records keep its shape. */
        const val TAG_SYNC_ADAPTER = "FirestoreCloudSync"
        const val MSG_SYNC_RESTART_FAILED_LOGOUT =
            "Cloud sync restart failed after sign-out failure."
        const val MSG_SYNC_RESTART_FAILED_DELETE =
            "Cloud sync restart failed after account deletion failure."
        const val FORCE_REFRESH_COOLDOWN_MS = 120_000L
        const val MSG_PREFS_FAILED = "Couldn't save that setting. Try again."
        const val MSG_HEALTH_SYNC_UNAVAILABLE =
            "Health Connect SDK is not available on this device"
        const val MSG_HEALTH_SYNC_PERMISSION =
            "Health Connect write permission is not granted"
        const val MSG_HEALTH_SYNC_FAILED = "Failed to sync workout to Health Connect"
        const val MSG_HEALTH_CONNECT_SETTINGS =
            "Couldn't open Health Connect. Is it available on this device?"
        const val MSG_FORCE_REFRESH_QUEUED =
            "Volume charts updated. Progress line refreshes in the background when battery isn’t low."
        const val MSG_FORCE_REFRESH_FAILED = "Couldn't rebuild progress data. Try again."
        const val MSG_LOGOUT_FAILED = "Couldn't sign out. Try again."
        const val MSG_DELETE_FAILED = "Couldn't delete account. Try again."
        const val MSG_DELETE_AUTH_FAILED =
            "Your training data was removed, but the account could not be fully closed. " +
                "Try Delete account again, or use Log out and contact support."
        const val MSG_DELETE_LOCAL_CLEANUP_FAILED =
            "Your cloud data was deleted, but this device could not clear local data. " +
                "Uninstall Stratum or clear the app’s storage in system settings."
    }
}

/** Presentation-only settings state — no domain types in composables. */
data class SettingsUiState(
    val appearance: AppearanceUi = AppearanceUi.System,
    val themeFamily: ThemeFamilyUi = ThemeFamilyUi.Stratum,
    val checkInsEnabled: Boolean = true,
    val equipmentProposalsEnabled: Boolean = true,
    val repRangeProposalsEnabled: Boolean = true,
    /** Display weight unit; defaults to lbs. */
    val weightUnit: WeightUnitUi = WeightUnitUi.Lbs,
    val healthConnectEnabled: Boolean = false,
    val healthConnectWriteGranted: Boolean = false,
    val healthConnectReadsGranted: Boolean = false,
    val isEntitled: Boolean = false,
    val isComplimentary: Boolean = false,
    /** True only in debug builds; release UI never shows developer tools. */
    val showDebugTools: Boolean = false,
    val debugStatusMessage: String? = null,
    /** True while insights + weekly series refresh is in flight. */
    val isForceRefreshingDerived: Boolean = false,
    /** True briefly after a successful queue so re-taps do not thrash WorkManager. */
    val isForceRefreshCooldown: Boolean = false,
    val showRebuildProgressConfirm: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val isLoggingOut: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeletingAccount: Boolean = false,
) {
    /** Row is not tappable while running, confirming, or in post-queue cooldown. */
    val isRebuildProgressLocked: Boolean
        get() = isForceRefreshingDerived || isForceRefreshCooldown || showRebuildProgressConfirm
}

/** Which post-workout suggestion switch the user toggled. */
enum class PostWorkoutSuggestionUi {
    EquipmentSteps,
    RepRangeShift,
}

/**
 * Account lifecycle callbacks for [com.stratum.feature.settings.ui.SettingsScreen].
 * Grouped so the composable stays under Detekt [LongParameterList].
 */
data class SettingsAccountActions(
    val onMembershipClick: () -> Unit = {},
    val onLogoutClick: () -> Unit = {},
    val onLogoutConfirmDismiss: () -> Unit = {},
    val onLogoutConfirmed: () -> Unit = {},
    val onDeleteAccountClick: () -> Unit = {},
    val onDeleteAccountConfirmDismiss: () -> Unit = {},
    val onDeleteAccountConfirmed: () -> Unit = {},
)

/**
 * Import / export / rebuild callbacks for [com.stratum.feature.settings.ui.SettingsScreen].
 * Grouped so the composable stays under Detekt [LongParameterList].
 */
data class SettingsDataActions(
    val onImportDataClick: () -> Unit = {},
    val onExportDataClick: () -> Unit = {},
    val onRebuildProgressClick: () -> Unit = {},
    val onRebuildProgressConfirmDismiss: () -> Unit = {},
    val onRebuildProgressConfirmed: () -> Unit = {},
)

/** Light / Dark / System (device) appearance policy. */
enum class AppearanceUi {
    System,
    Light,
    Dark,
}

/** Preferred weight display unit shown in Settings and used across the app. */
enum class WeightUnitUi {
    Lbs,
    Kg,
}

/** Product chrome family labels shown in Settings. */
enum class ThemeFamilyUi {
    /** Stratum Tonal — default ship target. */
    Stratum,

    /** Neo Brutalist iron/concrete family. */
    NeoBrutalist,
}

private fun ColorMode.toAppearanceUi(): AppearanceUi =
    when (this) {
        ColorMode.System -> AppearanceUi.System
        ColorMode.Light -> AppearanceUi.Light
        ColorMode.Dark -> AppearanceUi.Dark
    }

private fun AppearanceUi.toDomain(): ColorMode =
    when (this) {
        AppearanceUi.System -> ColorMode.System
        AppearanceUi.Light -> ColorMode.Light
        AppearanceUi.Dark -> ColorMode.Dark
    }

internal fun Weight.WeightUnit.toWeightUnitUi(): WeightUnitUi =
    when (this) {
        Weight.WeightUnit.Lb -> WeightUnitUi.Lbs
        Weight.WeightUnit.Kg -> WeightUnitUi.Kg
    }

internal fun WeightUnitUi.toDomain(): Weight.WeightUnit =
    when (this) {
        WeightUnitUi.Lbs -> Weight.WeightUnit.Lb
        WeightUnitUi.Kg -> Weight.WeightUnit.Kg
    }

/** Alias used by import flows. */
internal fun WeightUnitUi.toDomainWeightUnit(): Weight.WeightUnit = toDomain()

private fun ThemeFamily.toThemeFamilyUi(): ThemeFamilyUi =
    when (this) {
        ThemeFamily.Tonal -> ThemeFamilyUi.Stratum
        ThemeFamily.NeoBrutalist -> ThemeFamilyUi.NeoBrutalist
    }

private fun ThemeFamilyUi.toDomain(): ThemeFamily =
    when (this) {
        ThemeFamilyUi.Stratum -> ThemeFamily.Tonal
        ThemeFamilyUi.NeoBrutalist -> ThemeFamily.NeoBrutalist
    }


package com.stratum.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.feature.onboarding.navigation.OnboardingKey
import com.stratum.feature.onboarding.navigation.onboardingEntry
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.program.navigation.createProgramEntry
import com.stratum.feature.program.navigation.EditWorkoutKey
import com.stratum.feature.program.navigation.editWorkoutEntry
import com.stratum.feature.program.navigation.LiftSwapCommitMode
import com.stratum.feature.program.navigation.LiftSwapKey
import com.stratum.feature.program.navigation.liftSwapEntry
import com.stratum.feature.program.navigation.ProgramLibraryKey
import com.stratum.feature.program.navigation.programLibraryEntry
import com.stratum.feature.program.navigation.ReorderLiftsKey
import com.stratum.feature.program.navigation.reorderLiftsEntry
import com.stratum.feature.program.navigation.ReorderWorkoutsKey
import com.stratum.feature.program.navigation.reorderWorkoutsEntry
import com.stratum.feature.history.navigation.EditHistoricalWorkoutKey
import com.stratum.feature.history.navigation.HistoryDaySessionsKey
import com.stratum.feature.history.navigation.WorkoutHistoryKey
import com.stratum.feature.history.navigation.WorkoutSessionDetailKey
import com.stratum.feature.history.navigation.editHistoricalWorkoutEntry
import com.stratum.feature.history.navigation.historyDaySessionsEntry
import com.stratum.feature.history.navigation.workoutHistoryEntry
import com.stratum.feature.history.navigation.workoutSessionDetailEntry
import com.stratum.feature.settings.navigation.ExportDataKey
import com.stratum.feature.settings.navigation.ExportLogsKey
import com.stratum.feature.settings.navigation.ExportProgramsKey
import com.stratum.feature.settings.navigation.ImportDataKey
import com.stratum.feature.settings.navigation.ImportLogsKey
import com.stratum.feature.settings.navigation.ImportProgramKey
import com.stratum.feature.settings.navigation.LegalKey
import com.stratum.feature.settings.navigation.SettingsKey
import com.stratum.feature.settings.navigation.exportDataEntry
import com.stratum.feature.settings.navigation.exportLogsEntry
import com.stratum.feature.settings.navigation.exportProgramsEntry
import com.stratum.feature.settings.navigation.importDataEntry
import com.stratum.feature.settings.navigation.importLogsEntry
import com.stratum.feature.settings.navigation.importProgramEntry
import com.stratum.feature.settings.navigation.legalEntry
import com.stratum.feature.settings.navigation.settingsEntry
import com.stratum.feature.subscription.navigation.PaywallKey
import com.stratum.feature.subscription.navigation.paywallEntry
import com.stratum.feature.workout.navigation.WorkoutKey
import com.stratum.feature.workout.navigation.WorkoutCompleteKey
import com.stratum.feature.workout.navigation.workoutEntry
import com.stratum.feature.workout.navigation.workoutCompleteEntry
import com.stratum.feature.workout.ui.EquipmentEditRequest
import com.stratum.feature.workout.ui.EquipmentEditorDestination
import com.stratum.feature.workout.ui.resolveEquipmentEditorDestination
import com.stratum.liftconfig.CreateLiftKey
import com.stratum.liftconfig.EquipmentIncrementsKey
import com.stratum.liftconfig.LiftIncrementsKey
import com.stratum.liftconfig.LiftInformationKey
import com.stratum.liftconfig.ManageEquipmentKey
import com.stratum.liftconfig.createLiftEntry
import com.stratum.liftconfig.equipmentIncrementsEntry
import com.stratum.liftconfig.liftIncrementsEntry
import com.stratum.liftconfig.liftInformationEntry
import com.stratum.liftconfig.manageEquipmentEntry

@Composable
fun StratumNavHost(
    pendingWorkoutId: String? = null,
    onPendingWorkoutConsumed: () -> Unit = {},
    onScreenChanged: (screenName: String) -> Unit = {},
) {
    val statusViewModel: OnboardingStatusViewModel = hiltViewModel()
    val gateViewModel: EntitlementGateViewModel = hiltViewModel()
    val destination by gateViewModel.destination.collectAsState()
    var lastContentName by rememberSaveable { mutableStateOf<String?>(null) }
    val lastContentDestination =
        lastContentName?.let(AppGateDestination::valueOf)
    val mountNav = shouldMountAppGateNavDisplay(destination, lastContentDestination != null)
    SideEffect {
        if (destination.isContentDestination()) {
            lastContentName = destination.name
        }
    }
    if (!mountNav) {
        if (destination == AppGateDestination.Failed) {
            StratumScaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    StratumFeatureErrorFallback(onRetry = gateViewModel::retry)
                }
            }
            return
        }
        StratumScaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }
    val startDestination =
        if (destination.isContentDestination()) {
            destination
        } else {
            checkNotNull(lastContentDestination)
        }
    val startKey: NavKey = remember { startDestination.toStartNavKey() }
    val backStack = rememberNavBackStack(startKey)
    val onNavigateBack =
        remember(backStack) {
            {
                if (shouldPopAppGateBackStack(backStack)) {
                    // Prefer removeAt over removeLast(): with compileSdk 35+, Kotlin can resolve
                    // to java.util.List.removeLast() (API 35+ only) and crash on Android 14.
                    backStack.removeAt(backStack.lastIndex)
                }
            }
        }

    LaunchedEffect(destination, pendingWorkoutId) {
        reconcileAppGateBackStack(backStack, destination)
        val workoutId = pendingWorkoutId ?: return@LaunchedEffect
        if (destination != AppGateDestination.Main) return@LaunchedEffect
        val top = backStack.lastOrNull()
        if (top is WorkoutKey && top.workoutId == workoutId) {
            onPendingWorkoutConsumed()
            return@LaunchedEffect
        }
        // Avoid stacking duplicate workout routes for the same session.
        backStack.removeAll { it is WorkoutKey && it.workoutId == workoutId }
        backStack.add(WorkoutKey(workoutId))
        onPendingWorkoutConsumed()
    }

    val currentKey = backStack.lastOrNull()
    LaunchedEffect(currentKey) {
        if (currentKey != null && currentKey !is MainTabKey) {
            val screenName = currentKey::class.simpleName ?: "Unknown"
            onScreenChanged(screenName)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
        backStack = backStack,
        onBack = onNavigateBack,
        // Match forward/pop fade for predictive back. Library default predictive pop uses
        // scaleOut(0.7f), which shrinks the outgoing screen into a rectangle on gesture back.
        transitionSpec = { stratumNavFadeTransform() },
        popTransitionSpec = { stratumNavFadeTransform() },
        predictivePopTransitionSpec = { stratumNavFadeTransform() },
        // Scope ViewModels (and saveable state) to each NavEntry. Without the ViewModelStore
        // decorator, hiltViewModel() resolves to the Activity and CreateProgram retains
        // showDiscardConfirm / entryConfigured across visits — discard sheet on library edit open.
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = entryProvider {
            onboardingEntry(
                onComplete = {
                    backStack.removeAll { it is OnboardingKey || it is CreateProgramKey }
                    if (backStack.none { it is MainTabKey }) {
                        backStack.add(MainTabKey(isPreviewMode = true))
                    }
                },
                onBuildFromScratch = {
                    backStack.add(
                        CreateProgramKey(
                            startInScratch = true,
                            finishOnboarding = true,
                        ),
                    )
                },
            )
            entry<MainTabKey> { key ->
                MainTabShell(
                    isPreviewMode = key.isPreviewMode,
                    callbacks =
                        MainTabShellCallbacks(
                            onDiscardProgramClick = {
                                statusViewModel.discardProgramAndReopenOnboarding()
                                backStack.removeAll { it is MainTabKey }
                                backStack.add(OnboardingKey)
                            },
                            onKeepProgramClick = {
                                backStack.removeAll { it is MainTabKey }
                                backStack.add(MainTabKey(isPreviewMode = false))
                            },
                            onNavigateToSwap = { workoutId, workoutLiftId, id, name ->
                                backStack.add(
                                    LiftSwapKey(
                                        workoutId = workoutId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = id,
                                        liftName = name,
                                        commitMode = LiftSwapCommitMode.PersistToProgram,
                                    ),
                                )
                            },
                            onNavigateToEditWorkout = { workoutId ->
                                backStack.add(EditWorkoutKey(workoutId))
                            },
                            onNavigateToWorkout = { workoutId ->
                                backStack.add(WorkoutKey(workoutId))
                            },
                            onNavigateToSettings = {
                                backStack.add(SettingsKey)
                            },
                            onNavigateToHistory = {
                                backStack.add(WorkoutHistoryKey)
                            },
                            onCreateNewLift = {
                                backStack.add(CreateLiftKey)
                            },
                            onManageEquipment = {
                                backStack.add(ManageEquipmentKey(selectionMode = false))
                            },
                            onNavigateToLiftIncrements = { liftId ->
                                backStack.add(LiftIncrementsKey(liftId))
                            },
                            onNavigateToLiftInformation = { liftId ->
                                backStack.add(LiftInformationKey(liftId))
                            },
                            onCreateProgramClick = {
                                backStack.add(CreateProgramKey())
                            },
                            onEditProgramClick = { programId ->
                                backStack.add(CreateProgramKey(programId = programId))
                            },
                            onReorderWorkoutsClick = { programId ->
                                backStack.add(ReorderWorkoutsKey(programId = programId))
                            },
                            onProgramLibraryClick = {
                                backStack.add(ProgramLibraryKey)
                            },
                        ),
                    onScreenChanged = onScreenChanged,
                )
            }
            liftIncrementsEntry(onBackClick = onNavigateBack)
            liftInformationEntry(
                onBackClick = onNavigateBack,
                onChooseEquipment = {
                    backStack.add(ManageEquipmentKey(selectionMode = true))
                },
            )
            createLiftEntry(
                onBackClick = onNavigateBack,
                onChooseEquipment = {
                    backStack.add(ManageEquipmentKey(selectionMode = true))
                },
            )
            manageEquipmentEntry(
                onBackClick = onNavigateBack,
                onOpenIncrements = { equipmentId ->
                    backStack.add(EquipmentIncrementsKey(equipmentId))
                },
            )
            equipmentIncrementsEntry(
                onBackClick = onNavigateBack,
                onNavigateToLift = { liftId ->
                    backStack.add(LiftInformationKey(liftId))
                },
                onChooseEquipmentForLift = {
                    backStack.add(ManageEquipmentKey(selectionMode = true))
                },
            )
            settingsEntry(
                onBackClick = onNavigateBack,
                onNavigateToLegal = { backStack.add(LegalKey) },
                onNavigateToImportData = { backStack.add(ImportDataKey) },
                onNavigateToExportData = { backStack.add(ExportDataKey) },
                onNavigateToPaywall = { backStack.add(PaywallKey(isDismissable = true)) },
            )
            paywallEntry(
                onDismiss = {
                    val top = backStack.lastOrNull() as? PaywallKey
                    if (top?.isDismissable == true) {
                        onNavigateBack()
                    }
                },
                onNavigateToTerms = { backStack.add(LegalKey) },
                onNavigateToPrivacy = { backStack.add(LegalKey) },
            )
            legalEntry(onBackClick = onNavigateBack)
            importDataEntry(
                onBackClick = onNavigateBack,
                onNavigateToImportLogs = { backStack.add(ImportLogsKey) },
                onNavigateToImportProgram = { backStack.add(ImportProgramKey) },
            )
            importLogsEntry(onBackClick = onNavigateBack)
            importProgramEntry(onBackClick = onNavigateBack)
            exportDataEntry(
                onBackClick = onNavigateBack,
                onNavigateToExportLogs = { backStack.add(ExportLogsKey) },
                onNavigateToExportPrograms = { backStack.add(ExportProgramsKey) },
            )
            exportLogsEntry(onBackClick = onNavigateBack)
            exportProgramsEntry(onBackClick = onNavigateBack)
            workoutHistoryEntry(
                onBackClick = onNavigateBack,
                onDayClick = { day ->
                    when (day.sessionIds.size) {
                        0 -> Unit
                        1 -> {
                            val key = WorkoutSessionDetailKey(day.sessionIds.single())
                            if (backStack.lastOrNull() != key) backStack.add(key)
                        }
                        else -> {
                            val key = HistoryDaySessionsKey(dateIso = day.date.toString())
                            if (backStack.lastOrNull() != key) backStack.add(key)
                        }
                    }
                },
            )
            historyDaySessionsEntry(
                onBackClick = onNavigateBack,
                onSessionClick = { sessionId ->
                    val key = WorkoutSessionDetailKey(sessionId)
                    if (backStack.lastOrNull() != key) backStack.add(key)
                },
            )
            workoutSessionDetailEntry(
                onBackClick = onNavigateBack,
                onEditWorkoutClick = { sessionId ->
                    val key = EditHistoricalWorkoutKey(sessionId)
                    if (backStack.lastOrNull() != key) backStack.add(key)
                },
            )
            editHistoricalWorkoutEntry(
                onBackClick = onNavigateBack,
            )
            programLibraryEntry(
                onBackClick = onNavigateBack,
                onProgramClick = { programId ->
                    backStack.add(CreateProgramKey(programId = programId))
                },
                onWorkoutClick = { programId, workoutId ->
                    backStack.add(EditWorkoutKey(workoutId = workoutId, programId = programId))
                },
            )
            createProgramEntry(
                onBackClick = onNavigateBack,
                onFinished = {
                    // Onboarding scratch: CompleteOnboarding already ran in the VM; land on
                    // preview main tabs (same as generate). Guard against double-push if
                    // OnboardingRoute.onComplete also fires when isComplete flips.
                    val key = backStack.lastOrNull() as? CreateProgramKey
                    if (key?.finishOnboarding == true) {
                        backStack.removeAll { it is OnboardingKey || it is CreateProgramKey }
                        if (backStack.none { it is MainTabKey }) {
                            backStack.add(MainTabKey(isPreviewMode = true))
                        }
                    } else {
                        onNavigateBack()
                    }
                },
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
            )
            liftSwapEntry(
                onBackClick = onNavigateBack,
                onCreateNewLift = {
                    backStack.add(CreateLiftKey)
                },
            )
            editWorkoutEntry(
                onBackClick = onNavigateBack,
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
                onNavigateToWorkout = { workoutId ->
                    // Leave the template editor; avoid stacking Edit under live workout.
                    backStack.removeAll { it is EditWorkoutKey }
                    backStack.removeAll { it is WorkoutKey && it.workoutId == workoutId }
                    backStack.add(WorkoutKey(workoutId))
                },
            )
            workoutEntry(
                onNavigateBack = onNavigateBack,
                onNavigateToFinalize = { sessionId ->
                    backStack.removeAll { it is WorkoutKey }
                    backStack.add(WorkoutCompleteKey(sessionId))
                },
                onSwapLiftClick = { workoutId, workoutLiftId, liftId, liftName ->
                    backStack.add(LiftSwapKey(workoutId, workoutLiftId, liftId, liftName))
                },
                onEditEquipmentClick = { request -> pushEquipmentEditor(backStack, request) },
                onReorderLiftsClick = { workoutId ->
                    backStack.add(ReorderLiftsKey(workoutId))
                },
            )
            workoutCompleteEntry(
                onCloseClick = onNavigateBack,
                onEditEquipmentClick = { request -> pushEquipmentEditor(backStack, request) },
            )
            reorderLiftsEntry(
                onBackClick = onNavigateBack,
            )
            reorderWorkoutsEntry(
                onBackClick = onNavigateBack,
            )
        },
        )
        if (destination == AppGateDestination.Failed) {
            StratumScaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    StratumFeatureErrorFallback(onRetry = gateViewModel::retry)
                }
            }
        }
    }
}

/**
 * Shared "Edit steps" navigation for the live-workout and complete entries. The request
 * travels intact into the editor key (no unpack-repack of its proposal fields).
 */
private fun pushEquipmentEditor(
    backStack: MutableList<NavKey>,
    request: EquipmentEditRequest,
) {
    backStack.add(equipmentEditorKey(request))
}

/**
 * Maps an equipment-editing decision to the editor key (keeps `StratumNavHost` lean).
 * Blank lift ids (proposal clusters without a lift, e.g. Amend with no anomalous lift
 * captured) normalize to null so Save As New never reassigns a `LiftId("")`.
 */
private fun equipmentEditorKey(request: EquipmentEditRequest): NavKey =
    when (val destination = resolveEquipmentEditorDestination(request)) {
        is EquipmentEditorDestination.LiftOverride ->
            LiftIncrementsKey(
                liftId = destination.liftId,
                initialTiersLb = request.initialTiersLb,
                proposalContext = request.proposalContext,
            )
        is EquipmentEditorDestination.SharedEquipment ->
            EquipmentIncrementsKey(
                equipmentId = destination.equipmentId,
                sourceLiftId = destination.sourceLiftId?.takeIf { it.isNotBlank() },
                initialTiersLb = request.initialTiersLb,
                proposalContext = request.proposalContext,
            )
    }

/** Matches navigation3 default forward/pop duration; fade only (no predictive scale-out). */
private fun stratumNavFadeTransform(): ContentTransform =
    fadeIn(animationSpec = tween(STRATUM_NAV_TRANSITION_MS)) togetherWith
        fadeOut(animationSpec = tween(STRATUM_NAV_TRANSITION_MS))

private const val STRATUM_NAV_TRANSITION_MS = 700

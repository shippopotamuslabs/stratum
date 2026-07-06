package com.stratum.feature.workout

import com.stratum.domain.logging.LogErrorUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SnapshotRequest
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiMapper
import com.stratum.feature.workout.ui.WorkoutUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the overlay layer (`MutableStateFlow<WorkoutOverlay>`) as in-memory session state
 * (D2, D9). Consumes use cases only (no repository ports directly — ARCHITECTURE.md
 * §4.4). Maps the merged rendered workout → presentation model.
 *
 * The snapshot is computed once per [startSession] from history; overlay mutations
 * (add/delete/undo/log) re-merge against the immutable snapshot and re-render. Live
 * recommendations for upcoming unlogged sets are recomputed on each merge and stored in
 * the overlay's `recommendations` map (overlay-only — never writes back to the snapshot).
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val generateSnapshot: GenerateProgressionSnapshotUseCase,
    private val mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    private val projectStructure: ProjectWorkoutStructureUseCase,
    private val recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    private val estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    private val deleteLoggedSet: DeleteLoggedSetUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    private val logErrorUseCase: LogErrorUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkoutUiEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutUiEvent> = _events.receiveAsFlow()

    private var activeWorkoutId: WorkoutId? = null
    private var cachedSessionId: com.stratum.domain.training.identity.TrainingSessionId? = null
    private var sessionStartedAt: java.time.Instant? = null
    private var structure: WorkoutStructureProjection? = null
    private var snapshotBySlot: Map<SetSlotId, SlotRecommendation> = emptyMap()
    private var overlay: WorkoutOverlay = WorkoutOverlay()
    private var sessionObservationJob: Job? = null
    private var liftNamesById: Map<LiftId, String> = emptyMap()
    private var liftsById: Map<LiftId, com.stratum.domain.lift.Lift> = emptyMap()
    private var fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap()
    private var snapshotLoaded = false

    fun startSession(workoutId: WorkoutId) {
        if (activeWorkoutId == workoutId && snapshotLoaded) return
        activeWorkoutId = workoutId
        snapshotLoaded = false
        overlay = WorkoutOverlay()
        snapshotBySlot = emptyMap()
        structure = null
        _uiState.value = WorkoutUiState.Loading
        sessionObservationJob?.cancel()
        sessionObservationJob =
            viewModelScope.launch {
                liftNamesById =
                    observeLiftCatalog().first().associate { lift -> lift.id to lift.name }
                liftsById =
                    observeLiftCatalog().first().associateBy { lift -> lift.id }
                fatigueByLiftId = observeLiftTrainingInsights()
                observeCycleProgress().collect { progress ->
                    val program = progress.program ?: return@collect
                    val session = progress.activeSession ?: return@collect
                    cachedSessionId = session.id
                    sessionStartedAt = session.startedAt
                    val workout = program.workouts.firstOrNull { it.id == workoutId } ?: return@collect
                    if (!snapshotLoaded) {
                        val projected = projectStructure(workout, liftNamesById)
                        structure = projected
                        val snapshotResult = generateSnapshot(
                            SnapshotRequest(
                                programId = program.id,
                                workoutId = workoutId,
                                lifts = workout.workoutLifts,
                            ),
                        )
                        val snapshot = snapshotResult.getOrDefault(
                            com.stratum.domain.workout.ProgressionSnapshot(workoutId, emptyMap()),
                        )
                        snapshotBySlot = snapshot.bySlot
                        
                        // Load already logged sets for the session from database (resume session support)
                        val dbSets = getLoggedSetsForSession(session.id)
                        val resumedLoggedSets = mutableMapOf<SetSlotId, LoggedSetValue>()
                        for (dbSet in dbSets) {
                            val slot = projected.lifts.flatMap { it.slots }.firstOrNull {
                                it.workoutLiftId == dbSet.liftContext.workoutLiftId && it.position == dbSet.setIndex
                            }
                            if (slot != null) {
                                resumedLoggedSets[slot.slotId] = LoggedSetValue(
                                    slotId = slot.slotId,
                                    weight = dbSet.weight,
                                    reps = dbSet.reps,
                                    rir = dbSet.rir,
                                )
                            }
                        }
                        overlay = overlay.copy(loggedSets = resumedLoggedSets)
                        snapshotLoaded = true
                    }
                    recomputeRecommendations()
                    render()
                }
            }
    }

    fun logSet(slotKey: String, weight: Weight, reps: Int, rir: Int?) {
        val session = currentSessionId() ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        viewModelScope.launch {
            try {
                logSetUseCase(
                    LogSetCommand(
                        sessionId = session,
                        liftId = slot.liftId,
                        workoutLiftId = slot.workoutLiftId,
                        setIndex = slot.position,
                        weight = weight,
                        reps = reps,
                        rir = rir,
                        completedAt = Instant.now(),
                    ),
                )
                overlay = overlay.copy(
                    loggedSets = overlay.loggedSets + (slotId to LoggedSetValue(slotId, weight, reps, rir)),
                )
                recomputeRecommendations()
                render()
            } catch (e: Exception) {
                logErrorUseCase(TAG, "Failed to log set", e)
                _events.send(WorkoutUiEvent.ShowMessage("Failed to log set: ${e.message}"))
            }
        }
    }

    fun unlogSet(slotKey: String) {
        val session = currentSessionId() ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        viewModelScope.launch {
            try {
                val dbSets = getLoggedSetsForSession(session)
                val dbSet = dbSets.firstOrNull {
                    it.liftContext.workoutLiftId == slot.workoutLiftId && it.setIndex == slot.position
                }
                if (dbSet != null) {
                    deleteLoggedSet(dbSet.id)
                }
                overlay = overlay.copy(
                    loggedSets = overlay.loggedSets - slotId,
                )
                recomputeRecommendations()
                render()
            } catch (e: Exception) {
                logErrorUseCase(TAG, "Failed to unlog set", e)
                _events.send(WorkoutUiEvent.ShowMessage("Failed to unlog set: ${e.message}"))
            }
        }
    }

    fun addSet(afterSlotId: SetSlotId) {
        val anchor = resolveSlot(afterSlotId) ?: return
        val newPosition = anchor.position + 1
        val added = SetSlot(
            slotId = SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("added-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = newPosition,
            setType = anchor.setType,
            isTopSet = false,
            repMin = anchor.repMin,
            repMax = anchor.repMax,
            rirGoal = anchor.rirGoal,
            restSeconds = anchor.restSeconds,
        )
        val baseline = snapshotBySlot[afterSlotId] ?: anchorRecommendation(afterSlotId)
        val fatigue = fatigueByLiftId[anchor.liftId] ?: FatigueDecay(null, null)
        val estimatedReps = estimateAddedSetReps(
            previousRecommendation = baseline ?: SlotRecommendation(afterSlotId, zeroWeight(), anchor.repMin),
            nextSetIndex = newPosition,
            perLiftSlope = fatigue.slope,
            perLiftIntercept = fatigue.intercept,
        )
        val addedWeight = baseline?.recommendedWeight ?: zeroWeight()
        val addedRecommendation = SlotRecommendation(added.slotId, addedWeight, estimatedReps)
        overlay = overlay.apply(StructuralMutation.AddSet(afterSlotId = afterSlotId, newSlot = added))
            .copy(dynamicBaselines = overlay.dynamicBaselines + (added.slotId to addedRecommendation))
        recomputeRecommendations()
        render()
    }

    fun deleteSet(slotId: SetSlotId) {
        overlay = overlay.apply(StructuralMutation.DeleteSet(slotId))
        recomputeRecommendations()
        render()
    }

    fun undo() {
        overlay = overlay.undo()
        recomputeRecommendations()
        render()
    }

    fun generateMyoRepMiniSet(activationSlotId: SetSlotId) {
        val anchor = resolveSlot(activationSlotId) ?: return
        val mini = SetSlot(
            slotId = SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("mini-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = anchor.position + 1,
            setType = com.stratum.domain.program.SetType.MyoRep,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = 15,
        )
        overlay = overlay.apply(
            StructuralMutation.GenerateMyoRepMiniSet(activationSlotId = activationSlotId, newSlot = mini),
        )
        recomputeRecommendations()
        render()
    }

    fun addDropSet(afterSlotId: SetSlotId) {
        val anchor = resolveSlot(afterSlotId) ?: return
        val drop = SetSlot(
            slotId = SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("drop-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = anchor.position + 1,
            setType = com.stratum.domain.program.SetType.DropSet,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = 60,
        )
        overlay = overlay.apply(StructuralMutation.AddDropSet(afterSlotId = afterSlotId, newSlot = drop))
        recomputeRecommendations()
        render()
    }

    @Suppress("UnusedParameter")
    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId) {
        // Exercise swap recomputes the snapshot — out of scope for the Slice 7 skeleton.
        // Signature retained for UI wiring; implementation deferred.
    }

    fun finalizeSession() {
        val session = currentSessionId() ?: return
        viewModelScope.launch { _events.send(WorkoutUiEvent.NavigateToFinalize(session.value)) }
    }

    fun abandonSession() {
        val session = currentSessionId()
        if (session != null) {
            viewModelScope.launch {
                abandonTrainingSession(session)
                sessionObservationJob?.cancel()
                sessionObservationJob = null
                activeWorkoutId = null
                snapshotLoaded = false
                structure = null
                snapshotBySlot = emptyMap()
                fatigueByLiftId = emptyMap()
                overlay = WorkoutOverlay()
                _uiState.value = WorkoutUiState.NoActiveSession
            }
        } else {
            sessionObservationJob?.cancel()
            sessionObservationJob = null
            activeWorkoutId = null
            snapshotLoaded = false
            structure = null
            snapshotBySlot = emptyMap()
            fatigueByLiftId = emptyMap()
            overlay = WorkoutOverlay()
            _uiState.value = WorkoutUiState.NoActiveSession
        }
    }

    /**
     * Recomputes overlay-only recommendations for upcoming unlogged sets from the most
     * recent performed set per lift (D4). Delegates the pipeline to
     * [RecomputeRecommendationsUseCase]; never writes back into the snapshot.
     */
    private fun recomputeRecommendations() {
        try {
            val recommendations = recomputeRecommendationsUseCase(merge(), liftsById, fatigueByLiftId)
            overlay = overlay.copy(recommendations = recommendations)
        } catch (e: Exception) {
            logErrorUseCase(TAG, "Failed to recompute recommendations", e)
        }
    }

    private fun merge(): RenderedWorkout {
        val base = structure ?: return RenderedWorkout(
            com.stratum.domain.program.identity.WorkoutId(""),
            "",
            emptyList(),
        )
        // Fold overlay structural mutations into the projection: append added slots to
        // their owning lift; the merge use case then removes deleted slots via its
        // deletedSlotIds check.
        val withAdded = base.copy(
            lifts = base.lifts.map { lift ->
                val appended = overlay.addedSlots.filter { it.workoutLiftId == lift.workoutLiftId }
                if (appended.isEmpty()) lift else lift.copy(slots = lift.slots + appended)
            },
        )
        val snapshot = com.stratum.domain.workout.ProgressionSnapshot(withAdded.workoutId, snapshotBySlot)
        return mergeRenderedWorkout(withAdded, snapshot, overlay)
    }

    private fun render() {
        val rendered = merge()
        val startedAtMilli = sessionStartedAt?.toEpochMilli() ?: java.time.Instant.now().toEpochMilli()
        _uiState.value = WorkoutUiMapper.map(rendered, startedAtMilli)
    }

    private fun resolveSlot(slotId: SetSlotId): SetSlot? {
        val struct = structure ?: return null
        return struct.lifts.flatMap { it.slots }.firstOrNull { it.slotId == slotId }
            ?: overlay.addedSlots.firstOrNull { it.slotId == slotId }
    }

    private fun anchorRecommendation(slotId: SetSlotId): SlotRecommendation? = snapshotBySlot[slotId]

    private fun currentSessionId(): com.stratum.domain.training.identity.TrainingSessionId? = cachedSessionId

    private fun zeroWeight(): Weight = Weight(0.0, Weight.WeightUnit.Lb)

    private fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }

    private fun resolveSlotByKey(slotKey: String): SetSlot? {
        val struct = structure ?: return null
        return struct.lifts.flatMap { it.slots }.firstOrNull { slotKey(it.slotId) == slotKey }
            ?: overlay.addedSlots.firstOrNull { slotKey(it.slotId) == slotKey }
    }

    private companion object {
        const val TAG = "WorkoutViewModel"
    }
}

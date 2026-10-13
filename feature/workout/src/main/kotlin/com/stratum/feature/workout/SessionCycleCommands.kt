package com.stratum.feature.workout

import com.stratum.core.ui.component.SupersetLinkableLiftState
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.isDoubleSidedPlateLoad
import com.stratum.domain.lift.isPlateLoaded
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.lift.storageWeightFromDisplay
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.coerceForCompound
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.FinalizeTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SessionPinnedNoteLists
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SnapshotRequest
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiMapper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Cycle/catalog apply + overlay restore for [LiveWorkoutSessionRuntime]. */

/** Re-render when a session is loaded (display-unit flips, catalog updates). */
internal fun LiveWorkoutSessionRuntime.handleRenderOnly() {
    if (structure != null) render()
}

internal suspend fun LiveWorkoutSessionRuntime.handleApplyCycleSnapshot(cmd: SessionCommand.ApplyCycleSnapshot) {
    liftNamesById = cmd.catalog.associate { lift -> lift.id to lift.name }
    liftsById = cmd.catalog.associateBy { lift -> lift.id }
    equipmentById = cmd.equipment.associateBy { equipment -> equipment.id }
    tombstonedEquipmentIds = cmd.equipment.filter { it.isDeleted }.map { it.id }.toSet()
    tombstonedLiftIds = cmd.catalog.filter { it.isDeleted }.map { it.id }.toSet()

    val program = cmd.program
    val session = cmd.session
    if (program == null || session == null || !sessionMatchesRoute(session, cmd.routeWorkoutId)) {
        clearToNoActiveSession()
        return
    }
    val workout = cmd.workout
    if (workout == null) {
        // Program template deleted mid-session: VM may have already abandoned the orphan.
        // Keep session id so we do not re-abandon the same orphan every collect.
        clearToNoActiveSession(retainSessionId = session.id)
        return
    }

    cachedProgramId = program.id
    cachedSessionId = session.id
    sessionStartedAt = session.startedAt
    baselineWorkout = workout
    // Kind is source of truth — never save synthetic AD_HOC baselines as program days.
    canSaveTemplateChanges = session.kind == TrainingSessionKind.PROGRAM_WORKOUT
    val fingerprint = templateStructureFingerprint(workout)
    val needsStructureRebuild =
        !snapshotLoaded || fingerprint != templateLiftFingerprint
    // Room log writes re-emit cycle progress. Recomputing on every emission re-anchors
    // live fatigue without the pre-log "shown" prescriptions and undoes exact-match
    // freeze (e.g. set 4 reps 7→6). Only recompute when structure/snapshot/overlay
    // actually changed; handleLogSet already recomputes after each log.
    var shouldRecompute = false
    if (needsStructureRebuild) {
        val isInitialLoad = !snapshotLoaded
        // Stored multi-note lists (authoritative over the legacy single-note mirrors).
        // Best-effort: a repo failure must not block the session from loading.
        sessionPinnedNotes =
            runCatching { getSessionPinnedNoteLists(workout) }
                .getOrDefault(SessionPinnedNoteLists())
        val projected =
            projectStructure(
                workout,
                liftNamesById,
                loadTypesById = liftsById.mapValues { it.value.loadType },
                isCompoundById = liftsById.mapValues { it.value.isCompound },
                liftPinnedNotesById = liftsById.mapValues { it.value.pinnedNote },
                isPlateLoadedById = liftsById.mapValues { it.value.isPlateLoaded },
                isDoubleSidedById = liftsById.mapValues { it.value.isDoubleSidedPlateLoad },
                workoutPinnedNoteListsById = sessionPinnedNotes.byWorkoutLiftId,
                liftPinnedNoteListsById = sessionPinnedNotes.byLiftId,
            )
        structure = projected
        val snapshotResult =
            generateSnapshot(
                SnapshotRequest(
                    programId = program.id,
                    workoutId = workout.id,
                    lifts = workout.workoutLifts,
                    loadTypesById = liftsById.mapValues { it.value.loadType },
                    incrementTiersById =
                        liftsById.mapValues { (_, lift) -> lift.incrementTiers },
                    equipmentIdByWorkoutLiftId = sessionEquipmentByWorkoutLiftId(workout.workoutLifts),
                ),
            )
        val snapshot =
            snapshotResult.getOrDefault(
                com.stratum.domain.workout.ProgressionSnapshot(workout.id, emptyMap()),
            )
        snapshotBySlot = snapshot.bySlot

        if (isInitialLoad) {
            installRestoredOverlay(
                restoreOverlayOnInitialLoad(
                    sessionId = session.id,
                    projected = projected,
                ),
            )
        } else {
            val persisted = manageWorkoutOverlay.load(session.id)
            if (persisted != null && shouldAdoptPersistedOverlay(persisted)) {
                installRestoredOverlay(persisted)
            }
        }
        // In-session swaps (or restored ones) rebind previous/recs to the
        // performed catalog lift and re-scope pair history — template snapshot
        // alone is pre-swap.
        refreshSnapshotForSwaps(workout)
        templateLiftFingerprint = fingerprint
        snapshotLoaded = true
        shouldRecompute = true
    } else {
        // External overlay remaps (e.g. lift merge) — adopt only when memory
        // has no unflushed work and the store signature actually changed.
        val persisted = manageWorkoutOverlay.load(session.id)
        if (
            persisted != null &&
            catalogIdentitySignature(persisted) !=
            catalogIdentitySignature(overlay) &&
            shouldAdoptPersistedOverlay(persisted)
        ) {
            installRestoredOverlay(persisted)
            refreshSnapshotForSwaps(workout)
            shouldRecompute = true
        }
    }
    applyPinnedNotesFromTemplate()
    if (shouldRecompute) {
        recomputeRecommendations()
        // Persist post-restore recs (and any established-block freeze) so the next
        // minimize/reopen does not re-enter adaptive Reduce without priors.
        flushOverlay()
    }
    render()
}

internal suspend fun LiveWorkoutSessionRuntime.restoreOverlayOnInitialLoad(
    sessionId: TrainingSessionId,
    projected: WorkoutStructureProjection,
): WorkoutOverlay {
    val dbSets = getLoggedSetsForSession(sessionId)
    val templateWorkoutLiftIds = projected.lifts.map { it.workoutLiftId }.toSet()
    val resumedLoggedSets = mutableMapOf<SetSlotId, LoggedSetValue>()
    for (dbSet in dbSets) {
        val slot =
            projected.lifts.flatMap { it.slots }.firstOrNull {
                it.workoutLiftId == dbSet.liftContext.workoutLiftId &&
                    it.position == dbSet.setIndex
            }
        if (slot != null) {
            resumedLoggedSets[slot.slotId] =
                LoggedSetValue(
                    slotId = slot.slotId,
                    weight = dbSet.weight,
                    reps = dbSet.reps,
                    rir = dbSet.rir,
                    // Stamp Room id so amend/unlog survive later position renumber.
                    persistedLoggedSetId = dbSet.id.value,
                    partialReps = dbSet.partialReps,
                )
        }
    }
    val persisted = manageWorkoutOverlay.load(sessionId)
    if (persisted != null) return persisted

    val orphanSets =
        dbSets.filter { set ->
            val wlId = set.liftContext.workoutLiftId
            wlId != null &&
                set.liftId != null &&
                wlId !in templateWorkoutLiftIds
        }
    val rebuilt =
        if (orphanSets.isNotEmpty()) {
            LiveWorkoutOverlayOps.rebuildFromSessionLoggedSets(
                dbSets = orphanSets,
                zeroWeight = Weight(0.0, Weight.WeightUnit.Lb),
            )
        } else {
            null
        }
    return when {
        rebuilt != null ->
            rebuilt.copy(loggedSets = rebuilt.loggedSets + resumedLoggedSets)
        else -> WorkoutOverlay(loggedSets = resumedLoggedSets)
    }
}


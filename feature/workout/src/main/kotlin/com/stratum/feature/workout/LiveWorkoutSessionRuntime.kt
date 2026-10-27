package com.stratum.feature.workout

import com.stratum.core.ui.component.SupersetLinkableLiftState
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.EffectiveSteps
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.shouldShowLiveEquipmentAdjust
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.storageWeightFromDisplay
import com.stratum.domain.program.GetProgramByIdUseCase
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
import com.stratum.domain.workout.ComputeTemplateChangeSummaryUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.GetSessionPinnedNoteListsUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.SessionPinnedNoteLists
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SnapshotRequest
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.resolveSessionEquipmentId
import com.stratum.domain.workout.templateEquipmentIdForPair
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.intensifier.RestDefaultForTransition
import com.stratum.feature.workout.ui.TemplateChangeSummaryMapper
import com.stratum.feature.workout.ui.TemplateChangeSummaryUi
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiMapper
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Single-writer session memory + domain deps for live workout commands.
 *
 * Owned only by [LiveWorkoutSessionActor]. Command handlers live as package-level
 * extensions in Session*Commands.kt files.
 */

@Suppress("TooManyFunctions", "LongParameterList")
internal class LiveWorkoutSessionRuntime(
    val effects: SessionEffects,
    val generateSnapshot: GenerateProgressionSnapshotUseCase,
    val mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    val projectStructure: ProjectWorkoutStructureUseCase,
    val recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    val estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    val logSetUseCase: LogSetUseCase,
    val updateLoggedSet: UpdateLoggedSetUseCase,
    val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    val deleteLoggedSet: DeleteLoggedSetUseCase,
    val finalizeTrainingSession: FinalizeTrainingSessionUseCase,
    val applyOverlayToWorkout: ApplyOverlayToWorkoutUseCase,
    val computeTemplateChangeSummary: ComputeTemplateChangeSummaryUseCase,
    val saveWorkoutUseCase: SaveWorkoutUseCase,
    val getProgramById: GetProgramByIdUseCase,
    val observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    val getSessionPinnedNoteLists: GetSessionPinnedNoteListsUseCase,
) {
    var activeWorkoutId: WorkoutId? = null
    var cachedProgramId: ProgramId? = null
    var cachedSessionId: TrainingSessionId? = null
    var sessionStartedAt: Instant? = null
    var structure: WorkoutStructureProjection? = null
    var snapshotBySlot: Map<SetSlotId, SlotRecommendation> = emptyMap()
    var overlay: WorkoutOverlay = WorkoutOverlay()
    var liftNamesById: Map<LiftId, String> = emptyMap()
    var liftsById: Map<LiftId, Lift> = emptyMap()

    /**
     * Equipment catalog as of the latest cycle snapshot (STR-128): delivered
     * atomically with the lift catalog in [SessionCommand.ApplyCycleSnapshot] and
     * cleared with session memory like [liftsById]. The adjust chip resolves
     * tiers by the equipment in use from this map.
     */
    var equipmentById: Map<EquipmentId, Equipment> = emptyMap()

    /** Tombstoned equipment retained for this session (STR-105 grandfathering). */
    var tombstonedEquipmentIds: Set<EquipmentId> = emptySet()

    /** Tombstoned lifts retained for this session (STR-131 grandfathering). */
    var tombstonedLiftIds: Set<LiftId> = emptySet()
    var fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap()
    var snapshotLoaded: Boolean = false
    var templateLiftFingerprint: List<String> = emptyList()
    var baselineWorkout: Workout? = null
    /** Stored multi-note lists by slot (authoritative template source for card pins). */
    var sessionPinnedNotes: SessionPinnedNoteLists = SessionPinnedNoteLists()
    var canSaveTemplateChanges: Boolean = false
    var templateSavedThisComplete: Boolean = false
    var overlayRevision: Long = 0L
    var lastPersistedRevision: Long = 0L
    var sessionTerminal: Boolean = false
    var abandonInFlight: Boolean = false
    /**
     * Session bookkeeping for the "Adjust Equipment" chip (dismissed + tier-save
     * resolution). Internal — mutated only through [EquipmentAdjustTracker].
     * Same-package command handlers touch it, so it cannot be private.
     */
    internal val equipmentAdjustTracker = EquipmentAdjustTracker()

    internal fun clearToNoActiveSession(retainSessionId: TrainingSessionId? = null) {
        cachedProgramId = null
        cachedSessionId = retainSessionId
        sessionStartedAt = null
        activeWorkoutId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        structure = null
        snapshotBySlot = emptyMap()
        liftsById = emptyMap()
        equipmentById = emptyMap()
        liftNamesById = emptyMap()
        resetOverlay(WorkoutOverlay())
        baselineWorkout = null
        sessionPinnedNotes = SessionPinnedNoteLists()
        canSaveTemplateChanges = false
        templateSavedThisComplete = false
        equipmentAdjustTracker.clear()
        effects.clearLiveSurfaces()
        publishNoActiveSessionIfNeeded()
    }

    internal fun clearSessionMemory(publishEmpty: Boolean) {
        activeWorkoutId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        structure = null
        snapshotBySlot = emptyMap()
        fatigueByLiftId = emptyMap()
        liftsById = emptyMap()
        equipmentById = emptyMap()
        liftNamesById = emptyMap()
        resetOverlay(WorkoutOverlay())
        cachedProgramId = null
        cachedSessionId = null
        sessionStartedAt = null
        baselineWorkout = null
        sessionPinnedNotes = SessionPinnedNoteLists()
        canSaveTemplateChanges = false
        templateSavedThisComplete = false
        equipmentAdjustTracker.clear()
        if (publishEmpty) {
            publishNoActiveSessionIfNeeded()
        }
    }

    internal fun publishNoActiveSessionIfNeeded() {
        if (effects.isCompleting() || effects.shouldRetainLoadedUi()) return
        effects.publishUi(com.stratum.feature.workout.ui.WorkoutUiState.NoActiveSession)
    }

    internal fun catalogIdentitySignature(value: WorkoutOverlay): List<String> =
        buildList {
            value.swappedLifts.forEach { (workoutLiftId, liftId) ->
                add("swap:${workoutLiftId.value}:${liftId.value}")
            }
            value.addedLifts.forEach { added ->
                add("add:${added.workoutLiftId.value}:${added.liftId.value}")
            }
            value.addedSlots.forEach { slot ->
                add("slot:${LiveWorkoutOverlayOps.slotKey(slot.slotId)}:${slot.liftId.value}")
            }
        }.sorted()

    internal fun commitOverlay(next: WorkoutOverlay) {
        overlay = next
        overlayRevision += 1
    }

    internal fun installRestoredOverlay(next: WorkoutOverlay) {
        overlay = next
        overlayRevision += 1
        lastPersistedRevision = overlayRevision
    }

    internal fun resetOverlay(next: WorkoutOverlay) {
        overlay = next
        overlayRevision = 0L
        lastPersistedRevision = 0L
    }

    internal fun shouldAdoptPersistedOverlay(persisted: WorkoutOverlay): Boolean {
        if (overlayRevision > lastPersistedRevision) return false
        if (memoryHasRicherCatalogStructure(memory = overlay, persisted = persisted)) {
            return false
        }
        return catalogIdentitySignature(persisted) != catalogIdentitySignature(overlay)
    }

    internal fun memoryHasRicherCatalogStructure(
        memory: WorkoutOverlay,
        persisted: WorkoutOverlay,
    ): Boolean {
        val memSlots =
            memory.addedSlots.map { LiveWorkoutOverlayOps.slotKey(it.slotId) }.toSet()
        val storeSlots =
            persisted.addedSlots.map { LiveWorkoutOverlayOps.slotKey(it.slotId) }.toSet()
        if (!storeSlots.containsAll(memSlots)) return true
        val memLifts = memory.addedLifts.map { it.workoutLiftId.value }.toSet()
        val storeLifts = persisted.addedLifts.map { it.workoutLiftId.value }.toSet()
        if (!storeLifts.containsAll(memLifts)) return true
        val memSwaps = memory.swappedLifts.keys.map { it.value }.toSet()
        val storeSwaps = persisted.swappedLifts.keys.map { it.value }.toSet()
        if (!storeSwaps.containsAll(memSwaps)) return true
        return false
    }

    internal suspend fun flushOverlay() {
        val id = cachedSessionId ?: return
        val rev = overlayRevision
        manageWorkoutOverlay.save(id, overlay)
        lastPersistedRevision = rev
    }

    internal fun recomputeRecommendations() {
        // Best-effort: keep the last good recommendations if recompute throws.
        runCatching {
            val priorRecommendations = overlay.recommendations
            // Snapshot-only merge so Hold/Reduce does not ratchet on prior live weights.
            val snapshotOnlyOverlay = overlay.copy(recommendations = emptyMap())
            val renderedForRecs =
                LiveWorkoutOverlayOps.merge(
                    structure = structure,
                    overlay = snapshotOnlyOverlay,
                    snapshotBySlot = snapshotBySlot,
                    liftsById = liftsById,
                    liftNamesById = liftNamesById,
                    mergeRenderedWorkout = mergeRenderedWorkout,
                    // Same catalog-pin source as render-path merge; pins don't feed recs,
                    // but the two call sites must not disagree by construction.
                    liftPinnedNoteListsById = sessionPinnedNotes.byLiftId,
                )
            recomputeRecommendationsUseCase(
                rendered = renderedForRecs,
                liftsById = liftsById,
                priorRecommendations = priorRecommendations,
                fatigueByLiftId = fatigueByLiftId,
            )
        }.onSuccess { recommendations ->
            // Recs are derived presentation — do not bump overlayRevision (not structural).
            overlay = overlay.copy(recommendations = recommendations)
        }
    }

    internal fun templateStructureFingerprint(workout: Workout): List<String> =
        workout.workoutLifts.flatMap { lift ->
            listOf("lift:${lift.id.value}:${lift.liftId.value}") +
                lift.sets.map { set ->
                    "set:${set.id.value}:${set.position}:${set.setType.name}:" +
                        "${set.myoRepKind?.name.orEmpty()}:${set.isTopSet}"
                }
        }

    /**
     * Copies stored note lists onto the current projection without rebuilding slots
     * (pin edits must not recompute live recs). Overlay entries win at merge time;
     * this only refreshes the template side from [sessionPinnedNotes].
     */
    internal fun applyPinnedNotesFromTemplate() {
        val current = structure ?: return
        structure =
            current.copy(
                lifts =
                    current.lifts.map { lift ->
                        lift.copy(
                            workoutPinnedNotes =
                                sessionPinnedNotes.byWorkoutLiftId[lift.workoutLiftId]
                                    ?: lift.workoutPinnedNotes,
                            liftPinnedNotes =
                                sessionPinnedNotes.byLiftId[lift.liftId]
                                    ?: lift.liftPinnedNotes,
                        )
                    },
            )
    }

    internal fun merge(): RenderedWorkout =
        LiveWorkoutOverlayOps.merge(
            structure = structure,
            overlay = overlay,
            snapshotBySlot = snapshotBySlot,
            liftsById = liftsById,
            liftNamesById = liftNamesById,
            mergeRenderedWorkout = mergeRenderedWorkout,
            liftPinnedNoteListsById = sessionPinnedNotes.byLiftId,
        )

    internal fun visibleSlotsForLift(workoutLiftId: WorkoutLiftId): List<SetSlot> =
        LiveWorkoutOverlayOps.visibleSlotsForLift(structure, overlay, workoutLiftId)

    internal fun render() {
        val rendered = merge()
        val startedAtMilli =
            sessionStartedAt?.toEpochMilli() ?: Instant.now().toEpochMilli()
        val hasIncompleteLifts =
            rendered.lifts.any { lift ->
                lift.sets.any { it.loggedValue == null }
            }
        // The save offer follows the change summary, not the raw overlay latch:
        // edits that net to zero against the baseline (equipment swapped forth
        // and back, a reverted rest override, an added-then-deleted set) leave
        // the overlay non-empty but write nothing, so they must not pop the
        // sheet (STR-136). Ad-hoc never offers template save
        // (canSaveTemplateChanges == false).
        val templateChanges =
            resolveTemplateChanges(overlay.isDirty && canSaveTemplateChanges)
                ?.takeUnless { it.isEmpty }
        val hasDirtyEdits = templateChanges != null
        val imageUrlByLiftId =
            liftsById.mapValues { (_, lift) -> lift.imageUris.firstOrNull() }
                .mapKeys { (id, _) -> id.value }
        val defaultRestSecondsByLiftId =
            liftsById.mapKeys { (id, _) -> id.value }.mapValues { (_, lift) ->
                lift.resolvedDefaultRestSeconds()
            }
        val equipmentIdByLiftId =
            liftsById.mapKeys { (id, _) -> id.value }.mapValues { (_, lift) ->
                lift.equipmentId?.value
            }
        // One steps value per lift, resolved by the equipment in use (STR-128):
        // tiers and micro share a single lookup so the chip signal and the tracker
        // can never observe different lattices for the same card.
        val stepsByWorkoutLiftId =
            rendered.lifts.associate { lift ->
                lift.workoutLiftId to effectiveStepsFor(lift)
            }
        // Live off-lattice signal per lift card, from sets logged so far this session.
        // Logged weights persist in canonical Lb, matching equipment tier units.
        // Tier edits after the chip showed resolve it; untouched editor visits keep it.
        val rawAdjustIds =
            rendered.lifts.mapNotNull { lift ->
                val catalog = liftsById[lift.liftId] ?: return@mapNotNull null
                val loggedWeights = lift.sets.mapNotNull { it.loggedValue?.weight }
                val steps = stepsByWorkoutLiftId[lift.workoutLiftId] ?: return@mapNotNull null
                if (shouldShowLiveEquipmentAdjust(loggedWeights, steps, catalog.loadType)) {
                    lift.workoutLiftId
                } else {
                    null
                }
            }.toSet()
        val adjustEquipmentWorkoutLiftIds =
            equipmentAdjustTracker.update(
                rawIds = rawAdjustIds,
                stepsByWorkoutLiftId = stepsByWorkoutLiftId,
                presentIds = rendered.lifts.map { it.workoutLiftId }.toSet(),
            )
        val mapped =
            WorkoutUiMapper.map(
                rendered,
                startedAtMilli,
                hasIncompleteLifts = hasIncompleteLifts,
                hasDirtyEdits = hasDirtyEdits,
                imageUrlByLiftId = imageUrlByLiftId,
                displayUnit = effects.displayUnit(),
                displayUnitEpoch = effects.displayUnitEpoch(),
                defaultRestSecondsByLiftId = defaultRestSecondsByLiftId,
                equipmentIdByLiftId = equipmentIdByLiftId,
                adjustEquipmentWorkoutLiftIds = adjustEquipmentWorkoutLiftIds,
                templateChanges = templateChanges,
            )
        // Active-workout notification snapshot is derived from domain session by
        // DomainActiveSessionSurfaceSync — do not dual-publish from render().
        effects.publishUi(mapped)
    }

    /**
     * End-of-workout change summary (STR-125): diffs the baseline against the applied
     * workout — the same artifact "Update Workout Template" persists — so the sheet
     * shows exactly what update will write. Computed only when dirty; clean and ad-hoc
     * sessions (or a missing baseline) carry null.
     */
    private fun resolveTemplateChanges(hasDirtyEdits: Boolean): TemplateChangeSummaryUi? {
        if (!hasDirtyEdits) return null
        val baseline = baselineWorkout ?: return null
        val applied = applyOverlayToWorkout(baseline, overlay, tombstonedEquipmentIds, tombstonedLiftIds)
        val summary = computeTemplateChangeSummary(baseline, applied, liftNamesById)
        return TemplateChangeSummaryMapper.map(summary)
    }

    /**
     * Quiets the chip for one card (X affordance). UI-state only — no overlay write,
     * so dismissing never dirties the session or persists anything.
     */
    internal fun handleDismissEquipmentAdjust(workoutLiftId: WorkoutLiftId) {
        equipmentAdjustTracker.dismiss(workoutLiftId)
        if (structure != null) render()
    }

    /**
     * Effective steps for one rendered card, resolved by the equipment in use
     * (STR-128): the merge-resolved equipment owns the lattice, so after an
     * equipment swap the chip, tracker, and editor seed all read the swapped-to
     * piece's tiers — not the catalog lift's linked (pre-swap) equipment. The
     * catalog lift's steps stay the answer when the resolved equipment is the
     * lift's own linked equipment (its tiers already carry the fallback
     * resolution) or when the resolved piece is not in the catalog mirror yet.
     */
    internal fun effectiveStepsFor(renderedLift: RenderedWorkoutLift): EffectiveSteps {
        val catalog = liftsById[renderedLift.liftId]
        val catalogSteps =
            catalog?.let { EffectiveSteps.from(it) }
                ?: EffectiveSteps(tiers = emptyList(), microIncrements = emptyList())
        val resolvedId = renderedLift.equipmentId ?: return catalogSteps
        // The lift's linked equipment is the one case the catalog steps must win:
        // a Lift's tiers are *defined* as that equipment's tiers plus the global
        // fallback resolution (see LiftMapper), which an equipment-direct read
        // would lose when the linked equipment's tiers are cleared. Every other
        // resolved piece (a live swap) owns its lattice outright.
        if (resolvedId == catalog?.equipmentId) return catalogSteps
        val equipment = equipmentById[resolvedId] ?: return catalogSteps
        return EffectiveSteps.from(equipment)
    }

    /**
     * Tap-time inference inputs for one lift card's editor request: the sets logged so
     * far this session (canonical Lb) with the in-use equipment's effective tiers. Pure
     * domain data, no navigation DTOs — the UI layer owns request routing (same join
     * as the pill signal in [render]). Null when the card or its catalog lift is
     * unknown.
     */
    internal fun liveEquipmentSeed(workoutLiftId: WorkoutLiftId): LiveEquipmentSeed? {
        val renderedLift = merge().lifts.firstOrNull { it.workoutLiftId == workoutLiftId } ?: return null
        val catalog = liftsById[renderedLift.liftId] ?: return null
        return LiveEquipmentSeed(
            currentTiers = effectiveStepsFor(renderedLift).tiers,
            loadType = catalog.loadType,
            loggedWeights = renderedLift.sets.mapNotNull { it.loggedValue?.weight },
        )
    }

    internal fun resolveSlot(slotId: SetSlotId): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlot(structure, overlay, slotId)

    internal fun resolveSlotByKey(slotKey: String): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlotByKey(structure, overlay, slotKey)

    internal fun progressionSchemeForLift(workoutLiftId: WorkoutLiftId): ProgressionScheme {
        // Structure projection already coerces isolation Linear → Double (D8).
        structure?.lifts?.firstOrNull { it.workoutLiftId == workoutLiftId }?.progressionScheme
            ?.let { return it }
        val added = overlay.addedLifts.firstOrNull { it.workoutLiftId == workoutLiftId }
        if (added != null) {
            val isCompound = liftsById[added.liftId]?.isCompound ?: false
            return added.progressionScheme.coerceForCompound(isCompound)
        }
        return ProgressionScheme.Double
    }

    /**
     * Re-scopes snapshots for in-session swaps (restored or external): a lift swap
     * rebinds the history pool's lift, an equipment swap re-scopes pair history.
     * One merged request covers lifts carrying both swaps (identity lift swaps — no
     * pool change — are skipped unless paired with an equipment swap).
     */
    internal suspend fun refreshSnapshotForSwaps(workout: Workout) {
        val effective =
            workout.workoutLifts.mapNotNull { lift ->
                val swappedId = overlay.swappedLifts[lift.id]
                val hasLiftSwap = swappedId != null && swappedId != lift.liftId
                if (!hasLiftSwap && !overlay.swappedEquipment.containsKey(lift.id)) {
                    return@mapNotNull null
                }
                // A lift swap on the same card rebinds the history pool's lift first.
                lift.copy(liftId = swappedId ?: lift.liftId)
            }
        if (effective.isEmpty()) return
        mergeSnapshotForLifts(effective)
    }

    /**
     * In-use equipment per template lift for snapshot scoping (STR-123): live-swap
     * override, else template assignment, else the effective lift's default — the same
     * priority [LiveWorkoutOverlayOps.merge] renders. Unassigned lifts are omitted.
     */
    internal fun sessionEquipmentByWorkoutLiftId(
        lifts: List<WorkoutLift>,
    ): Map<WorkoutLiftId, EquipmentId> =
        lifts.mapNotNull { lift ->
            resolveSessionEquipmentId(
                swapOverrideId = overlay.swappedEquipment[lift.id],
                // STR-127: same pair rule as render (see templateEquipmentIdForPair).
                templateEquipmentId =
                    templateEquipmentIdForPair(
                        swappedLiftId = overlay.swappedLifts[lift.id],
                        templateLiftId = lift.liftId,
                        hasSessionEquipmentOverride =
                            overlay.swappedEquipment.containsKey(lift.id),
                        templateEquipmentId = lift.equipmentId,
                    ),
                liftEquipmentId =
                    liftsById[overlay.swappedLifts[lift.id] ?: lift.liftId]?.equipmentId,
            )?.let { lift.id to it }
        }.toMap()

    internal suspend fun mergeSnapshotForLifts(lifts: List<WorkoutLift>) {
        if (lifts.isEmpty()) return
        val programId = cachedProgramId ?: return
        val workoutId = activeWorkoutId ?: return
        // One steps value per lift, projected into the request's parallel maps so
        // both facets share a single iteration and missing-key handling.
        val stepsByLiftId =
            liftsById.mapValues { (_, lift) ->
                EffectiveSteps(
                    tiers = lift.incrementTiers,
                    microIncrements = lift.microIncrements,
                )
            }
        generateSnapshot(
            SnapshotRequest(
                programId = programId,
                workoutId = workoutId,
                lifts = lifts,
                loadTypesById = liftsById.mapValues { it.value.loadType },
                incrementTiersById = stepsByLiftId.mapValues { it.value.tiers },
                microIncrementsById = stepsByLiftId.mapValues { it.value.microIncrements },
                equipmentIdByWorkoutLiftId = sessionEquipmentByWorkoutLiftId(lifts),
            ),
        ).onSuccess { snapshot ->
            if (snapshot.bySlot.isNotEmpty()) {
                snapshotBySlot = snapshotBySlot + snapshot.bySlot
            }
        }
    }


    companion object {

        internal const val DEFAULT_LIFT_REST_SECONDS = 120
        internal const val MSG_LOG_SET_FAILED = "Couldn't save that set. Try again."
        internal const val MSG_UNLOG_SET_FAILED = "Couldn't remove that set. Try again."
        internal const val MSG_COMPLETE_FAILED = "Couldn't finish the workout. Try again."
        internal const val MSG_COMPLETE_FINALIZE_AFTER_TEMPLATE =
            "Template saved, but finishing the session failed. Tap Complete again."    }
}

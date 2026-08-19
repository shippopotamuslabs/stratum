package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetUi
import com.stratum.core.ui.component.workoutlog.canCompleteSetLogFromFields
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-set ephemeral field text while the lifter is typing (not yet / just being committed). */
data class SetFieldDraft(
    val weightText: String = "",
    val repsText: String = "",
    val rir: Int = 2,
)

/** Resolved payload for [com.stratum.feature.workout.WorkoutViewModel.logSet]. */
data class SetLogCommit(
    val slotKey: String,
    /** UI/display magnitude (not yet converted to storage Lb). */
    val weightAmountDisplay: Double,
    val reps: Int,
    val rir: Int?,
)

/** Durable logged labels used as the seed throttle key. */
data class LoggedFieldSnapshot(
    val weightLabel: String,
    val repsLabel: String,
    val rir: Int,
)

/**
 * Result of seeding drafts from rendered sets. Pure — no Compose.
 *
 * @param pendingCommittedSlots slots that optimistically materialised a commit and are
 *   waiting for durable labels to catch up (or a failure rollback).
 * @param userEditedRirSlots slots where the lifter explicitly changed RIR this session.
 *   Incomplete drafts on other slots always track durable [RenderedSetUi.rirGoal] so a
 *   Standard→Drop type change (rirGoal → 0) cannot leave a stale RIR 1 that jumps the
 *   next drop rec after "log as shown".
 */
data class LiveSetLogSeedState(
    val drafts: Map<String, SetFieldDraft> = emptyMap(),
    val lastSyncedLogged: Map<String, LoggedFieldSnapshot> = emptyMap(),
    val pendingCommittedSlots: Set<String> = emptySet(),
    val userEditedRirSlots: Set<String> = emptySet(),
)

private fun RenderedSetUi.durableSnapshot(): LoggedFieldSnapshot =
    LoggedFieldSnapshot(
        weightLabel = loggedWeightLabel.orEmpty(),
        repsLabel = loggedRepsLabel.orEmpty(),
        rir = loggedRir ?: rirGoal,
    )

private fun RenderedSetUi.draftFromDurable(): SetFieldDraft =
    SetFieldDraft(
        weightText = loggedWeightLabel.orEmpty(),
        repsText = loggedRepsLabel.orEmpty(),
        rir = loggedRir ?: rirGoal,
    )

private fun draftMatchesDurable(
    draft: SetFieldDraft,
    durable: LoggedFieldSnapshot,
): Boolean =
    draft.weightText == durable.weightLabel &&
        draft.repsText == durable.repsLabel &&
        draft.rir == durable.rir

/**
 * Seeds / re-seeds drafts from durable presentation sets.
 *
 * - Incomplete: seed once when the slot appears; keep draft RIR on [RenderedSetUi.rirGoal]
 *   unless the lifter explicitly edited RIR (see [LiveSetLogSeedState.userEditedRirSlots]).
 * - Complete + keyboard focused: leave drafts alone.
 * - Complete + pending commit: keep materialised draft until durable matches (or failure).
 * - Complete + unfocused + not pending: always reseed from durable (discards uncommitted edits).
 */
internal fun seedLiveSetLogDrafts(
    previous: LiveSetLogSeedState,
    sets: List<RenderedSetUi>,
    focusedSetKey: String?,
): LiveSetLogSeedState {
    val drafts = previous.drafts.toMutableMap()
    val lastSynced = previous.lastSyncedLogged.toMutableMap()
    val pending = previous.pendingCommittedSlots.toMutableSet()
    val userEditedRir = previous.userEditedRirSlots.toMutableSet()

    for (set in sets) {
        applySeedForSet(
            set = set,
            focusedSetKey = focusedSetKey,
            drafts = drafts,
            lastSynced = lastSynced,
            pending = pending,
            userEditedRir = userEditedRir,
        )
    }

    val liveKeys = sets.map { it.slotKey }.toSet()
    drafts.keys.retainAll(liveKeys)
    lastSynced.keys.retainAll(liveKeys)
    pending.retainAll(liveKeys)
    userEditedRir.retainAll(liveKeys)

    return LiveSetLogSeedState(
        drafts = drafts,
        lastSyncedLogged = lastSynced,
        pendingCommittedSlots = pending,
        userEditedRirSlots = userEditedRir,
    )
}

private fun applySeedForSet(
    set: RenderedSetUi,
    focusedSetKey: String?,
    drafts: MutableMap<String, SetFieldDraft>,
    lastSynced: MutableMap<String, LoggedFieldSnapshot>,
    pending: MutableSet<String>,
    userEditedRir: MutableSet<String>,
) {
    val durable = set.durableSnapshot()
    if (!set.isComplete) {
        lastSynced.remove(set.slotKey)
        pending.remove(set.slotKey)
        val existing = drafts[set.slotKey]
        if (existing == null) {
            drafts[set.slotKey] = set.draftFromDurable()
            userEditedRir.remove(set.slotKey)
        } else if (set.slotKey !in userEditedRir && existing.rir != set.rirGoal) {
            // Set-type / template rirGoal changed (Standard→Drop → 0). Keep projection,
            // badge, and checkbox log on the same RIR so chain drops do not jump after
            // logging the prior drop exactly as shown.
            drafts[set.slotKey] = existing.copy(rir = set.rirGoal)
        }
        return
    }
    if (set.slotKey == focusedSetKey) return

    if (set.slotKey in pending) {
        val draft = drafts[set.slotKey]
        if (draft != null && draftMatchesDurable(draft, durable)) {
            lastSynced[set.slotKey] = durable
            pending.remove(set.slotKey)
        }
        return
    }

    // Unfocused, not pending: durable wins (blur discards dirty complete edits).
    drafts[set.slotKey] = set.draftFromDurable()
    lastSynced[set.slotKey] = durable
    userEditedRir.remove(set.slotKey)
}

/**
 * Merges durable [lift] presentation with ephemeral [drafts] into the shared set-log panel model.
 *
 * [allLifts] gates the superset chip: singleton orphan group ids are not shown (matches edit
 * workout's [com.stratum.core.ui.component.isSupersetPairMember] rule).
 */
internal fun mergeLiveLogLift(
    lift: RenderedLiftUi,
    drafts: Map<String, SetFieldDraft>,
    allLifts: List<RenderedLiftUi> = emptyList(),
): WorkoutLogLiftUi =
    WorkoutLogLiftUi(
        key = lift.workoutLiftId,
        liftName = lift.liftName,
        showWeightColumn = lift.showWeightColumn,
        weightHeaderLabel = lift.weightHeaderLabel,
        supersetGroupId =
            lift.supersetGroupId?.takeIf {
                allLifts.isEmpty() ||
                    com.stratum.core.ui.component.isSupersetPairMember(lift, allLifts)
            },
        isPlateLoaded = lift.isPlateLoaded,
        isDoubleSided = lift.isDoubleSided,
        unitLabel = lift.unitLabel,
        sets =
            lift.sets.map { set ->
                val draft = drafts[set.slotKey] ?: set.draftFromDurable()
                val canComplete =
                    canCompleteSetLogFromFields(
                        showWeightInput = set.showWeightInput,
                        weightText = draft.weightText,
                        weightPlaceholder = set.recommendedWeightLabel,
                        recommendedWeightAmount = set.recommendedWeightAmount,
                        assistMode = set.assistMode,
                        repsText = draft.repsText,
                        repsPlaceholder = set.recommendedRepsLabel,
                        recommendedReps = set.recommendedReps,
                    )
                WorkoutLogSetUi(
                    setKey = set.slotKey,
                    positionLabel = set.positionLabel,
                    setTypeLabel = set.setTypeLabel,
                    previousLabel = set.previousWeightLabel ?: "—",
                    previousRirLabel = set.previousRirLabel,
                    weightText = draft.weightText,
                    weightPlaceholder = set.recommendedWeightLabel,
                    repsText = draft.repsText,
                    repsPlaceholder = set.recommendedRepsLabel,
                    rir = draft.rir,
                    isComplete = set.isComplete,
                    fieldsEnabled = true,
                    showWeightInput = set.showWeightInput,
                    showCheckbox = true,
                    assistMode = set.assistMode,
                    recommendedWeightAmount = set.recommendedWeightAmount,
                    recommendedReps = set.recommendedReps,
                    repMin = set.repMin,
                    canComplete = canComplete,
                    isPlateLoaded = set.isPlateLoaded,
                    isDoubleSided = set.isDoubleSided,
                    unitLabel = set.unitLabel,
                )
            },
    )

internal fun resolveLiveSetLogCommit(
    source: RenderedSetUi,
    draft: SetFieldDraft,
    emptyFieldFallback: EmptyFieldFallback = EmptyFieldFallback.AcceptPlaceholders,
): SetLogCommit {
    val (weight, reps) =
        resolveCommitLogValues(
            emptyFieldFallback = emptyFieldFallback,
            enteredWeight = draft.weightText,
            enteredReps = draft.repsText,
            showWeightInput = source.showWeightInput,
            recommendedWeightAmount = source.recommendedWeightAmount,
            recommendedReps = source.recommendedReps,
            repMin = source.repMin,
            previousLoggedWeight = source.loggedWeightAmount,
            previousLoggedReps = source.loggedReps,
        )
    return SetLogCommit(
        slotKey = source.slotKey,
        weightAmountDisplay = weight,
        reps = reps,
        rir = draft.rir,
    )
}

internal fun draftFromCommit(
    commit: SetLogCommit,
    assistMode: Boolean,
    showWeightInput: Boolean,
): SetFieldDraft =
    SetFieldDraft(
        weightText =
            if (!showWeightInput) {
                ""
            } else {
                formatWeightAmount(commit.weightAmountDisplay, assistMode = assistMode)
            },
        repsText = commit.reps.toString(),
        rir = commit.rir ?: 2,
    )

/**
 * Holds ephemeral set-log drafts for one live lift panel.
 * Compose observes [revision] and re-merges.
 */
class LiveSetLogDraftStore {
    private var seedState = LiveSetLogSeedState()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun snapshot(): Map<String, SetFieldDraft> = seedState.drafts

    fun draftFor(slotKey: String): SetFieldDraft =
        seedState.drafts[slotKey] ?: SetFieldDraft()

    /**
     * Clears all ephemeral drafts/pending commits when the display unit flips so typed
     * magnitudes are not re-stamped under a different unit.
     */
    fun clearForDisplayUnitChange() {
        seedState = LiveSetLogSeedState()
        bump()
    }

    fun syncFromRendered(
        sets: List<RenderedSetUi>,
        focusedSetKey: String?,
    ) {
        val next = seedLiveSetLogDrafts(seedState, sets, focusedSetKey)
        if (next != seedState) {
            seedState = next
            bump()
        }
    }

    fun setWeight(
        slotKey: String,
        text: String,
    ) {
        updateDraft(slotKey) { it.copy(weightText = text) }
    }

    fun setReps(
        slotKey: String,
        text: String,
    ) {
        updateDraft(slotKey) { it.copy(repsText = text) }
    }

    fun setRir(
        slotKey: String,
        rir: Int,
    ) {
        val current = draftFor(slotKey)
        if (current.rir == rir && slotKey in seedState.userEditedRirSlots) return
        seedState =
            seedState.copy(
                drafts = seedState.drafts + (slotKey to current.copy(rir = rir)),
                userEditedRirSlots = seedState.userEditedRirSlots + slotKey,
            )
        bump()
    }

    fun resolveCommit(
        source: RenderedSetUi,
        emptyFieldFallback: EmptyFieldFallback = EmptyFieldFallback.AcceptPlaceholders,
    ): SetLogCommit =
        resolveLiveSetLogCommit(
            source = source,
            draft = draftFor(source.slotKey),
            emptyFieldFallback = emptyFieldFallback,
        )

    /**
     * Optimistic materialise after resolve; marks slot pending until durable labels match
     * or [forceReseedSlot] on persist failure.
     */
    fun applyResolvedCommit(
        source: RenderedSetUi,
        commit: SetLogCommit,
    ) {
        val draft =
            draftFromCommit(
                commit = commit,
                assistMode = source.assistMode,
                showWeightInput = source.showWeightInput,
            )
        seedState =
            seedState.copy(
                drafts = seedState.drafts + (source.slotKey to draft),
                lastSyncedLogged =
                    seedState.lastSyncedLogged + (source.slotKey to source.durableSnapshot()),
                pendingCommittedSlots = seedState.pendingCommittedSlots + source.slotKey,
            )
        bump()
    }

    /** Persist failed — restore drafts from durable labels and drop pending. */
    fun forceReseedSlot(source: RenderedSetUi) {
        val durable = source.durableSnapshot()
        seedState =
            seedState.copy(
                drafts = seedState.drafts + (source.slotKey to source.draftFromDurable()),
                lastSyncedLogged = seedState.lastSyncedLogged + (source.slotKey to durable),
                pendingCommittedSlots = seedState.pendingCommittedSlots - source.slotKey,
                userEditedRirSlots = seedState.userEditedRirSlots - source.slotKey,
            )
        bump()
    }

    private fun updateDraft(
        slotKey: String,
        transform: (SetFieldDraft) -> SetFieldDraft,
    ) {
        val current = draftFor(slotKey)
        val next = transform(current)
        if (next == current) return
        seedState =
            seedState.copy(
                drafts = seedState.drafts + (slotKey to next),
            )
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }
}

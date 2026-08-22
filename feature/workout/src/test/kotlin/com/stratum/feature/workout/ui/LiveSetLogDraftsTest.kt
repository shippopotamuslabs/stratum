package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LiveSetLogDraftsTest {

    private fun set(
        slotKey: String,
        isComplete: Boolean = false,
        loggedWeight: String? = null,
        loggedReps: String? = null,
        loggedWeightAmount: Double? = null,
        loggedRepsInt: Int? = null,
        loggedRir: Int? = null,
        recommendedWeight: Double = 135.0,
        recommendedReps: Int = 8,
        recommendedWeightLabel: String = "135",
        recommendedRepsLabel: String = "8",
        showWeightInput: Boolean = true,
        rirGoal: Int = 2,
        repMin: Int = 4,
    ): RenderedSetUi =
        RenderedSetUi(
            slotKey = slotKey,
            positionLabel = "1",
            setTypeLabel = "Working",
            repRangeLabel = "4–6",
            rirLabel = "RIR $rirGoal",
            recommendedWeightLabel = recommendedWeightLabel,
            recommendedRepsLabel = recommendedRepsLabel,
            loggedWeightLabel = loggedWeight,
            loggedRepsLabel = loggedReps,
            loggedWeightAmount = loggedWeightAmount,
            loggedReps = loggedRepsInt,
            isComplete = isComplete,
            rirGoal = rirGoal,
            repMin = repMin,
            repMax = 6,
            recommendedWeightAmount = recommendedWeight,
            recommendedReps = recommendedReps,
            loggedRir = loggedRir,
            showWeightInput = showWeightInput,
        )

    private fun lift(vararg sets: RenderedSetUi): RenderedLiftUi =
        RenderedLiftUi(
            workoutLiftId = "wl-1",
            liftId = "L-1",
            liftName = "Squat",
            sets = sets.toList(),
        )

    @Nested
    @DisplayName("seedLiveSetLogDrafts")
    inner class Seed {
        @Test
        @DisplayName("seeds incomplete sets once from empty/logged labels")
        fun seedsIncompleteOnce() {
            val sets = listOf(set("s1", isComplete = false))
            val first = seedLiveSetLogDrafts(LiveSetLogSeedState(), sets, focusedSetKey = null)
            assertEquals("", first.drafts.getValue("s1").weightText)
            assertEquals(2, first.drafts.getValue("s1").rir)

            val typed =
                first.copy(
                    drafts =
                        first.drafts +
                            ("s1" to SetFieldDraft(weightText = "140", repsText = "5", rir = 1)),
                    // Explicit RIR edit — seed must not force back to rirGoal.
                    userEditedRirSlots = setOf("s1"),
                )
            val second = seedLiveSetLogDrafts(typed, sets, focusedSetKey = null)
            assertEquals("140", second.drafts.getValue("s1").weightText)
            assertEquals("5", second.drafts.getValue("s1").repsText)
            assertEquals(1, second.drafts.getValue("s1").rir)
        }

        @Test
        @DisplayName("incomplete draft RIR tracks rirGoal after set-type change unless user edited RIR")
        fun incompleteRirTracksRirGoalUnlessUserEdited() {
            val asStandard = listOf(set("s1", isComplete = false, rirGoal = 1))
            var state = seedLiveSetLogDrafts(LiveSetLogSeedState(), asStandard, focusedSetKey = null)
            assertEquals(1, state.drafts.getValue("s1").rir)

            // Live Standard → Drop forces rirGoal 0; weight/reps drafts may already exist.
            state =
                state.copy(
                    drafts =
                        state.drafts +
                            ("s1" to SetFieldDraft(weightText = "", repsText = "", rir = 1)),
                )
            val asDrop = listOf(set("s1", isComplete = false, rirGoal = 0))
            state = seedLiveSetLogDrafts(state, asDrop, focusedSetKey = null)
            assertEquals(0, state.drafts.getValue("s1").rir, "stale RIR 1 must clear for Drop")

            // Explicit override sticks across further rirGoal-stable seeds.
            state =
                state.copy(
                    drafts =
                        state.drafts +
                            ("s1" to state.drafts.getValue("s1").copy(rir = 2)),
                    userEditedRirSlots = setOf("s1"),
                )
            state = seedLiveSetLogDrafts(state, asDrop, focusedSetKey = null)
            assertEquals(2, state.drafts.getValue("s1").rir)
        }

        @Test
        @DisplayName("LiveSetLogDraftStore.setRir marks slot user-edited")
        fun setRirMarksUserEdited() {
            val store = LiveSetLogDraftStore()
            val sets = listOf(set("s1", isComplete = false, rirGoal = 1))
            store.syncFromRendered(sets, focusedSetKey = null)
            assertEquals(1, store.draftFor("s1").rir)

            store.setRir("s1", 2)
            store.syncFromRendered(listOf(set("s1", isComplete = false, rirGoal = 0)), null)
            assertEquals(2, store.draftFor("s1").rir, "user RIR must survive rirGoal change")
        }

        @Test
        @DisplayName("unfocused complete always reseeds from durable (discards dirty edits)")
        fun blurDiscardsDirtyComplete() {
            val completed =
                listOf(
                    set(
                        "s1",
                        isComplete = true,
                        loggedWeight = "140",
                        loggedReps = "5",
                        loggedWeightAmount = 140.0,
                        loggedRepsInt = 5,
                        loggedRir = 1,
                    ),
                )
            var state = seedLiveSetLogDrafts(LiveSetLogSeedState(), completed, null)
            state =
                state.copy(
                    drafts =
                        state.drafts +
                            ("s1" to SetFieldDraft(weightText = "99", repsText = "1", rir = 3)),
                )
            // Unfocused → dirty discarded.
            state = seedLiveSetLogDrafts(state, completed, focusedSetKey = null)
            assertEquals("140", state.drafts.getValue("s1").weightText)
            assertEquals("5", state.drafts.getValue("s1").repsText)
            assertEquals(1, state.drafts.getValue("s1").rir)
        }

        @Test
        @DisplayName("does not reseed complete set while keyboard focused on that slot")
        fun skipsReseedWhileFocused() {
            val completed =
                listOf(
                    set(
                        "s1",
                        isComplete = true,
                        loggedWeight = "140",
                        loggedReps = "5",
                        loggedWeightAmount = 140.0,
                        loggedRepsInt = 5,
                        loggedRir = 1,
                    ),
                )
            var state = seedLiveSetLogDrafts(LiveSetLogSeedState(), completed, null)
            state =
                state.copy(
                    drafts =
                        state.drafts +
                            ("s1" to SetFieldDraft(weightText = "99", repsText = "1", rir = 3)),
                )
            state = seedLiveSetLogDrafts(state, completed, focusedSetKey = "s1")
            assertEquals("99", state.drafts.getValue("s1").weightText)
            assertEquals("1", state.drafts.getValue("s1").repsText)
        }

        @Test
        @DisplayName("pending commit keeps materialised draft until durable matches")
        fun pendingKeepsMaterialise() {
            val old =
                set(
                    "s1",
                    isComplete = true,
                    loggedWeight = "275",
                    loggedReps = "12",
                    loggedWeightAmount = 275.0,
                    loggedRepsInt = 12,
                    loggedRir = 1,
                )
            var state = seedLiveSetLogDrafts(LiveSetLogSeedState(), listOf(old), null)
            state =
                state.copy(
                    drafts =
                        state.drafts +
                            ("s1" to SetFieldDraft(weightText = "135", repsText = "8", rir = 2)),
                    pendingCommittedSlots = setOf("s1"),
                )
            // Durable still old — keep materialised.
            state = seedLiveSetLogDrafts(state, listOf(old), focusedSetKey = null)
            assertEquals("135", state.drafts.getValue("s1").weightText)
            assertTrue(state.pendingCommittedSlots.contains("s1"))

            val updated =
                set(
                    "s1",
                    isComplete = true,
                    loggedWeight = "135",
                    loggedReps = "8",
                    loggedWeightAmount = 135.0,
                    loggedRepsInt = 8,
                    loggedRir = 2,
                )
            state = seedLiveSetLogDrafts(state, listOf(updated), focusedSetKey = null)
            assertEquals("135", state.drafts.getValue("s1").weightText)
            assertFalse(state.pendingCommittedSlots.contains("s1"))
        }

        @Test
        @DisplayName("drops drafts for deleted slots")
        fun dropsDeletedSlots() {
            val two = listOf(set("s1"), set("s2"))
            var state = seedLiveSetLogDrafts(LiveSetLogSeedState(), two, null)
            assertTrue(state.drafts.containsKey("s1"))
            assertTrue(state.drafts.containsKey("s2"))
            state = seedLiveSetLogDrafts(state, listOf(set("s1")), null)
            assertTrue(state.drafts.containsKey("s1"))
            assertFalse(state.drafts.containsKey("s2"))
        }
    }

    @Nested
    @DisplayName("mergeLiveLogLift")
    inner class Merge {
        @Test
        @DisplayName("merges drafts and computes canComplete")
        fun mergesDrafts() {
            val source =
                set(
                    "s1",
                    recommendedWeight = 135.0,
                    recommendedReps = 8,
                    recommendedWeightLabel = "135",
                    recommendedRepsLabel = "8",
                )
            val logLift =
                mergeLiveLogLift(
                    lift(source),
                    mapOf("s1" to SetFieldDraft(weightText = "140", repsText = "5", rir = 1)),
                )
            val row = logLift.sets.single()
            assertEquals("140", row.weightText)
            assertEquals("5", row.repsText)
            assertEquals(1, row.rir)
            assertTrue(row.canComplete)
            assertTrue(row.fieldsEnabled)
        }

        @Test
        @DisplayName("canComplete false for junk non-empty text without recs")
        fun canCompleteFalseForJunk() {
            val source =
                set(
                    "s1",
                    recommendedWeight = 0.0,
                    recommendedReps = 0,
                    recommendedWeightLabel = "",
                    recommendedRepsLabel = "",
                )
            val logLift =
                mergeLiveLogLift(
                    lift(source),
                    mapOf("s1" to SetFieldDraft(weightText = "abc", repsText = "xyz", rir = 2)),
                )
            assertFalse(logLift.sets.single().canComplete)
        }

        @Test
        @DisplayName("bodyweight canComplete with reps rec and no weight text")
        fun bodyweightCanCompleteWithoutWeight() {
            val source =
                set(
                    "s1",
                    recommendedWeight = 0.0,
                    recommendedReps = 10,
                    recommendedWeightLabel = "",
                    recommendedRepsLabel = "10",
                    showWeightInput = false,
                )
            val logLift =
                mergeLiveLogLift(
                    lift(source).copy(showWeightColumn = false, weightHeaderLabel = ""),
                    mapOf("s1" to SetFieldDraft(weightText = "", repsText = "", rir = 2)),
                )
            val row = logLift.sets.single()
            assertFalse(logLift.showWeightColumn)
            assertFalse(row.showWeightInput)
            assertTrue(row.canComplete)
        }
    }

    @Nested
    @DisplayName("resolveLiveSetLogCommit")
    inner class Commit {
        @Test
        @DisplayName("first complete uses recommendations for empty drafts")
        fun firstCompleteRecs() {
            val source =
                set(
                    "s1",
                    isComplete = false,
                    recommendedWeight = 135.0,
                    recommendedReps = 8,
                    repMin = 4,
                )
            val commit =
                resolveLiveSetLogCommit(
                    source,
                    SetFieldDraft(weightText = "", repsText = "", rir = 2),
                )
            assertEquals("s1", commit.slotKey)
            assertEquals(135.0, commit.weightAmountDisplay)
            assertEquals(8, commit.reps)
            assertEquals(2, commit.rir)
        }

        @Test
        @DisplayName("RIR-only mode empty drafts keep previous logged not recommendations")
        fun rirOnlyKeepsLogged() {
            val source =
                set(
                    "s1",
                    isComplete = true,
                    loggedWeight = "275",
                    loggedReps = "12",
                    loggedWeightAmount = 275.0,
                    loggedRepsInt = 12,
                    loggedRir = 1,
                    recommendedWeight = 999.0,
                    recommendedReps = 99,
                )
            val commit =
                resolveLiveSetLogCommit(
                    source,
                    SetFieldDraft(weightText = "", repsText = "", rir = 0),
                    emptyFieldFallback = EmptyFieldFallback.KeepPreviousLogged,
                )
            assertEquals(275.0, commit.weightAmountDisplay)
            assertEquals(12, commit.reps)
            assertEquals(0, commit.rir)
        }

        @Test
        @DisplayName("explicit complete empty drafts accept placeholders")
        fun explicitAcceptsPlaceholders() {
            val source =
                set(
                    "s1",
                    isComplete = true,
                    loggedWeight = "275",
                    loggedReps = "12",
                    loggedWeightAmount = 275.0,
                    loggedRepsInt = 12,
                    recommendedWeight = 135.0,
                    recommendedReps = 8,
                )
            val commit =
                resolveLiveSetLogCommit(
                    source,
                    SetFieldDraft(weightText = "", repsText = "", rir = 2),
                    emptyFieldFallback = EmptyFieldFallback.AcceptPlaceholders,
                )
            assertEquals(135.0, commit.weightAmountDisplay)
            assertEquals(8, commit.reps)
        }
    }

    @Nested
    @DisplayName("LiveSetLogDraftStore")
    inner class Store {
        @Test
        @DisplayName("mutations bump revision and update snapshot")
        fun mutations() {
            val store = LiveSetLogDraftStore()
            val sets = listOf(set("s1"))
            store.syncFromRendered(sets, null)
            val rev0 = store.revision.value
            store.setWeight("s1", "100")
            assertTrue(store.revision.value > rev0)
            assertEquals("100", store.draftFor("s1").weightText)
            store.setReps("s1", "6")
            store.setRir("s1", 1)
            val commit = store.resolveCommit(sets.single())
            assertEquals(100.0, commit.weightAmountDisplay)
            assertEquals(6, commit.reps)
            assertEquals(1, commit.rir)
        }

        @Test
        @DisplayName("applyResolvedCommit materializes placeholder; forceReseed restores durable")
        fun applyAndForceReseed() {
            val store = LiveSetLogDraftStore()
            val source =
                set(
                    "s1",
                    isComplete = true,
                    loggedWeight = "275",
                    loggedReps = "12",
                    loggedWeightAmount = 275.0,
                    loggedRepsInt = 12,
                    loggedRir = 1,
                    recommendedWeight = 135.0,
                    recommendedReps = 8,
                    recommendedWeightLabel = "135",
                    recommendedRepsLabel = "8",
                )
            store.syncFromRendered(listOf(source), null)
            store.setWeight("s1", "")
            store.setReps("s1", "")

            val commit =
                store.resolveCommit(source, EmptyFieldFallback.AcceptPlaceholders)
            store.applyResolvedCommit(source, commit)
            assertEquals("135", store.draftFor("s1").weightText)
            assertEquals("8", store.draftFor("s1").repsText)

            // Blur with pending keeps materialise (durable still 275).
            store.syncFromRendered(listOf(source), focusedSetKey = null)
            assertEquals("135", store.draftFor("s1").weightText)

            // Failure rollback.
            store.forceReseedSlot(source)
            assertEquals("275", store.draftFor("s1").weightText)
            assertEquals("12", store.draftFor("s1").repsText)
        }
    }
}

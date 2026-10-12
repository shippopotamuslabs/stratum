package com.stratum.domain.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PinnedNoteResolutionTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")

    @Test
    @DisplayName("overlay workout pin wins over template")
    fun overlayWins() {
        assertEquals(
            "overlay",
            resolvedWorkoutPinnedNote(
                overlayNotes = mapOf(workoutLiftId to "overlay"),
                workoutLiftId = workoutLiftId,
                templateNote = "template",
            ),
        )
    }

    @Test
    @DisplayName("missing overlay key keeps template pin")
    fun templateWhenOverlayAbsent() {
        assertEquals(
            "template",
            resolvedWorkoutPinnedNote(
                overlayNotes = emptyMap(),
                workoutLiftId = workoutLiftId,
                templateNote = "template",
            ),
        )
    }

    @Test
    @DisplayName("catalog pin is authoritative including a cleared null")
    fun catalogClearIsAuthoritative() {
        val catalog =
            Lift(
                id = LiftId("lift-1"),
                name = "Squat",
                movementPatterns = emptySet(),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                pinnedNote = null,
            )
        assertNull(resolvedLiftPinnedNote(catalog, projectedNote = "stale"))
    }

    @Test
    @DisplayName("missing catalog falls back to the projection")
    fun missingCatalogFallsBack() {
        assertEquals("projected", resolvedLiftPinnedNote(catalog = null, projectedNote = "projected"))
    }

    @Test
    @DisplayName("overlay workout note list wins over template list")
    fun overlayListWins() {
        assertEquals(
            listOf("overlay-a", "overlay-b"),
            resolvedWorkoutPinnedNotes(
                overlayNotes = mapOf(workoutLiftId to listOf("overlay-a", "overlay-b")),
                workoutLiftId = workoutLiftId,
                templateNotes = listOf("template"),
            ),
        )
    }

    @Test
    @DisplayName("missing overlay key keeps template note list")
    fun templateListWhenOverlayAbsent() {
        assertEquals(
            listOf("template"),
            resolvedWorkoutPinnedNotes(
                overlayNotes = emptyMap(),
                workoutLiftId = workoutLiftId,
                templateNotes = listOf("template"),
            ),
        )
    }

    @Test
    @DisplayName("present catalog list is authoritative including an explicit clear")
    fun catalogListClearIsAuthoritative() {
        assertEquals(
            emptyList<String>(),
            resolvedLiftPinnedNotes(catalogNotes = emptyList(), projectedNotes = listOf("stale")),
        )
        assertEquals(
            listOf("catalog-a", "catalog-b"),
            resolvedLiftPinnedNotes(
                catalogNotes = listOf("catalog-a", "catalog-b"),
                projectedNotes = listOf("stale"),
            ),
        )
    }

    @Test
    @DisplayName("missing catalog list falls back to the projected list")
    fun missingCatalogListFallsBack() {
        assertEquals(
            listOf("projected"),
            resolvedLiftPinnedNotes(catalogNotes = null, projectedNotes = listOf("projected")),
        )
    }

    @Test
    @DisplayName("overlay list first entry wins for display")
    fun overlayListFirstWins() {
        val overlay =
            WorkoutOverlay(
                workoutLiftPinnedNoteLists = mapOf(workoutLiftId to listOf("first", "second")),
                workoutLiftPinnedNotes = mapOf(workoutLiftId to "stale single"),
            )
        assertEquals("first", resolvedWorkoutPinnedNoteFirst(overlay, workoutLiftId, "template"))
    }

    @Test
    @DisplayName("display falls back to single map then template")
    fun displayFallbackChain() {
        val singleOnly =
            WorkoutOverlay(workoutLiftPinnedNotes = mapOf(workoutLiftId to "single"))
        assertEquals("single", resolvedWorkoutPinnedNoteFirst(singleOnly, workoutLiftId, "template"))
        assertEquals(
            "template",
            resolvedWorkoutPinnedNoteFirst(WorkoutOverlay(), workoutLiftId, "template"),
        )
        assertNull(resolvedWorkoutPinnedNoteFirst(WorkoutOverlay(), workoutLiftId, null))
    }

    // ---- STR-127 pair-aware resolution ------------------------------------

    private val templateLiftId = LiftId("lift-template")
    private val swappedLiftId = LiftId("lift-swapped")

    @Test
    @DisplayName("pair-aware: unswapped slot keeps the template list")
    fun pairUnswappedKeepsTemplate() {
        assertEquals(
            listOf("template note"),
            resolvedWorkoutPinnedNotes(
                overlay = WorkoutOverlay(),
                workoutLiftId = workoutLiftId,
                templateLiftId = templateLiftId,
                templateNotes = listOf("template note"),
            ),
        )
    }

    @Test
    @DisplayName("pair-aware: swapped slot without a stash never shows the template lift's notes")
    fun pairSwappedWithoutStashIsEmpty() {
        val overlay =
            WorkoutOverlay(swappedLifts = mapOf(workoutLiftId to swappedLiftId))
        assertEquals(
            emptyList<String>(),
            resolvedWorkoutPinnedNotes(
                overlay = overlay,
                workoutLiftId = workoutLiftId,
                templateLiftId = templateLiftId,
                templateNotes = listOf("template note"),
            ),
        )
    }

    @Test
    @DisplayName("pair-aware: swapped slot reads its own pair's stash")
    fun pairSwappedReadsStash() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(workoutLiftId to swappedLiftId),
                pinnedNoteStash =
                    mapOf(
                        SlotLiftPair(workoutLiftId, swappedLiftId) to listOf("swapped pair note"),
                        SlotLiftPair(workoutLiftId, templateLiftId) to listOf("template note"),
                    ),
            )
        assertEquals(
            listOf("swapped pair note"),
            resolvedWorkoutPinnedNotes(
                overlay = overlay,
                workoutLiftId = workoutLiftId,
                templateLiftId = templateLiftId,
                templateNotes = listOf("template note"),
            ),
        )
    }

    @Test
    @DisplayName("pair-aware: swap back to the template lift restores its stashed notes")
    fun pairSwapBackRestoresStash() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(workoutLiftId to templateLiftId),
                pinnedNoteStash =
                    mapOf(
                        SlotLiftPair(workoutLiftId, templateLiftId) to listOf("stashed template note"),
                    ),
            )
        assertEquals(
            listOf("stashed template note"),
            resolvedWorkoutPinnedNotes(
                overlay = overlay,
                workoutLiftId = workoutLiftId,
                templateLiftId = templateLiftId,
                templateNotes = listOf("template note"),
            ),
        )
    }

    @Test
    @DisplayName("pair-aware: explicit overlay pins still win on a swapped slot")
    fun pairOverlayPinsWin() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(workoutLiftId to swappedLiftId),
                workoutLiftPinnedNoteLists = mapOf(workoutLiftId to listOf("edited in session")),
            )
        assertEquals(
            listOf("edited in session"),
            resolvedWorkoutPinnedNotes(
                overlay = overlay,
                workoutLiftId = workoutLiftId,
                templateLiftId = templateLiftId,
                templateNotes = listOf("template note"),
            ),
        )
    }
}

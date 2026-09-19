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
}

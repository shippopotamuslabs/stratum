package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LibraryProgramsTest {

    @Test
    @DisplayName("Active program leads, library follows newest-first")
    fun activeFirstThenLibraryNewestFirst() {
        val active = program(id = "active", createdAt = "2026-01-01T00:00:00Z", isActive = true)
        val library =
            listOf(
                program(id = "old", createdAt = "2026-01-02T00:00:00Z", isActive = false),
                program(id = "new", createdAt = "2026-02-01T00:00:00Z", isActive = false),
            )

        val merged = mergeActiveAndLibraryPrograms(active, library)

        assertEquals(listOf("active", "new", "old"), merged.map { it.id.value })
    }

    @Test
    @DisplayName("Stale library row matching the active id is dropped")
    fun dropsLibraryRowMatchingActiveId() {
        val active = program(id = "shared", name = "Shared", isActive = true)
        // Stale library emission still includes the program that is now (or still) active.
        val library =
            listOf(
                program(id = "shared", name = "Shared stale", isActive = false),
                program(id = "other", name = "Other", isActive = false),
            )

        val merged = mergeActiveAndLibraryPrograms(active, library)

        assertEquals(listOf("shared", "other"), merged.map { it.id.value })
        assertTrue(merged.first().isActive)
        assertEquals(1, merged.count { it.id.value == "shared" })
    }

    @Test
    @DisplayName("Null active program yields library newest-first")
    fun nullActiveYieldsLibrarySorted() {
        val library =
            listOf(
                program(id = "old", createdAt = "2026-01-02T00:00:00Z", isActive = false),
                program(id = "new", createdAt = "2026-02-01T00:00:00Z", isActive = false),
            )

        val merged = mergeActiveAndLibraryPrograms(null, library)

        assertEquals(listOf("new", "old"), merged.map { it.id.value })
    }

    @Test
    @DisplayName("Null active and empty library yields empty list")
    fun nullActiveAndEmptyLibraryYieldsEmpty() {
        assertEquals(emptyList<Program>(), mergeActiveAndLibraryPrograms(null, emptyList()))
    }

    private fun program(
        id: String,
        name: String = id,
        createdAt: String = "2026-01-01T00:00:00Z",
        isActive: Boolean = false,
    ): Program =
        Program(
            id = ProgramId(id),
            name = name,
            createdAt = Instant.parse(createdAt),
            cycleLength = 1,
            workouts = emptyList(),
            isActive = isActive,
        )
}

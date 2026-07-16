package com.stratum.domain.export

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.export.ExportTestFixtures.CLOCK_INSTANT
import com.stratum.domain.export.ExportTestFixtures.programActive
import com.stratum.domain.export.ExportTestFixtures.programLib
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonReader
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExportProgramsUseCaseTest {

    private lateinit var programRepo: FakeProgramRepository
    private lateinit var liftRepo: FakeLiftRepository
    private val clock: () -> Instant = { CLOCK_INSTANT }

    @BeforeEach
    fun setup() {
        programRepo = FakeProgramRepository()
        liftRepo = FakeLiftRepository()
        seedDisplayNameLifts()
    }

    private fun useCase() =
        ExportProgramsUseCase(
            programRepository = programRepo,
            liftRepository = liftRepo,
            clock = clock,
        )

    private suspend fun seedActiveAndLib() {
        programRepo.saveProgram(programActive)
        programRepo.saveToLibrary(programLib)
    }

    /** Lifts matching [ExportTestFixtures.minimalProgram] ids with human-readable names. */
    private fun seedDisplayNameLifts() {
        liftRepo.seedLifts(
            listOf(
                catalogLift(LiftId("lift-squat"), "Squat"),
                catalogLift(LiftId("lift-bench"), "Bench"),
            ),
        )
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private fun decodeRoot(bytes: ByteArray): JsonObject =
        JsonReader(bytes.decodeToString()).readRootObject()

    @Test
    @DisplayName("IE-S2-002 Export with empty programIds fails")
    fun ieS2_002_emptySelection() = runTest {
        val result = useCase()(emptyList())
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Select at least one program to export.", ex!!.message)
    }

    @Test
    @DisplayName("IE-S2-003 Multi-program export includes stable ids")
    fun ieS2_003_multiProgramIds() = runTest {
        seedActiveAndLib()
        val result = useCase()(listOf(ProgramId("prog-active"), ProgramId("prog-lib")))
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals("stratum.export.programs", root.requireString("schema"))
        assertEquals(1, root.requireInt("schemaVersion"))
        assertEquals("stratum", root.requireString("source"))
        val programs = root.requireArray("programs")
        assertEquals(2, programs.values.size)
        val ids =
            programs.values.map { (it as JsonObject).requireString("id") }.toSet()
        assertEquals(setOf("prog-active", "prog-lib"), ids)
        programs.values.forEach { item ->
            val template = (item as JsonObject).requireObject("template")
            assertTrue(template.requireArray("workouts").values.isNotEmpty())
        }
    }

    @Test
    @DisplayName("IE-S2-004 Export rejects import-history program id")
    fun ieS2_004_rejectImportHistory() = runTest {
        programRepo.saveToLibrary(ExportTestFixtures.programImportHistory)
        val result = useCase()(listOf(ProgramId("import-history")))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("One or more programs could not be exported.", ex!!.message)
    }

    @Test
    @DisplayName("IE-S2-005 Program export mime and filename")
    fun ieS2_005_mimeAndFilename() = runTest {
        programRepo.saveProgram(programActive)
        val result = useCase()(listOf(ProgramId("prog-active")))
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("application/json", file.mimeType)
        assertEquals("stratum-programs-20260716-150000.json", file.fileName)
    }

    @Test
    @DisplayName("IE-S2-M001 Mutation defense program export must include programs[].id")
    fun ieS2_m001_programIdsPresent() = runTest {
        programRepo.saveProgram(programActive)
        val result = useCase()(listOf(ProgramId("prog-active")))
        assertTrue(result.isSuccess)
        val programs = decodeRoot(result.getOrThrow().bytes).requireArray("programs")
        val first = programs.values[0] as JsonObject
        assertEquals("prog-active", first.requireString("id"))
        assertTrue(first.entries.containsKey("id"))
    }

    @Test
    @DisplayName("IE-S2-006 Export includes lift display names, not raw lift ids")
    fun ieS2_006_liftDisplayNames() = runTest {
        seedActiveAndLib()
        val result = useCase()(listOf(ProgramId("prog-active"), ProgramId("prog-lib")))
        assertTrue(result.isSuccess)
        val json = result.getOrThrow().bytes.decodeToString()
        assertTrue(json.contains("\"name\":\"Squat\""), "expected Squat display name in JSON: $json")
        assertTrue(json.contains("\"name\":\"Bench\""), "expected Bench display name in JSON: $json")
        assertFalse(json.contains("lift-squat"), "must not export raw lift id slug: $json")
        assertFalse(json.contains("lift-bench"), "must not export raw lift id slug: $json")

        val programs = decodeRoot(result.getOrThrow().bytes).requireArray("programs")
        val namesByProgramId =
            programs.values.associate { item ->
                val obj = item as JsonObject
                val id = obj.requireString("id")
                val workouts = obj.requireObject("template").requireArray("workouts")
                val firstTraining =
                    workouts.values
                        .map { it as JsonObject }
                        .first { !it.requireBoolean("restDay") }
                val liftName =
                    firstTraining
                        .requireArray("lifts")
                        .values
                        .first()
                        .let { (it as JsonObject).requireString("name") }
                id to liftName
            }
        assertEquals("Squat", namesByProgramId["prog-active"])
        assertEquals("Bench", namesByProgramId["prog-lib"])
    }
}

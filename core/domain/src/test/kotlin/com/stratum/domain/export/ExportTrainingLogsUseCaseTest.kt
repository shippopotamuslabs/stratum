package com.stratum.domain.export

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.export.ExportTestFixtures.CLOCK_INSTANT
import com.stratum.domain.export.ExportTestFixtures.ZONE_NY
import com.stratum.domain.export.ExportTestFixtures.session
import com.stratum.domain.export.ExportTestFixtures.sessionA
import com.stratum.domain.export.ExportTestFixtures.sessionB
import com.stratum.domain.export.ExportTestFixtures.sessionLive
import com.stratum.domain.export.ExportTestFixtures.sessionOld
import com.stratum.domain.export.ExportTestFixtures.set
import com.stratum.domain.export.ExportTestFixtures.setA1
import com.stratum.domain.export.ExportTestFixtures.setB1
import com.stratum.domain.export.ExportTestFixtures.setLive1
import com.stratum.domain.export.ExportTestFixtures.setOld1
import com.stratum.domain.json.JsonObject
import com.stratum.domain.json.JsonReader
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExportTrainingLogsUseCaseTest {

    private val clock: () -> Instant = { CLOCK_INSTANT }

    private fun useCase(repo: FakeTrainingLogRepository) =
        ExportTrainingLogsUseCase(
            trainingLogRepository = repo,
            clock = clock,
            loggingPort = FakeLoggingPort(),
        )

    private fun decodeRoot(bytes: ByteArray): JsonObject =
        JsonReader(bytes.decodeToString()).readRootObject()

    private fun sessionIds(root: JsonObject): List<String> {
        val history = root.requireObject("history")
        val sessions = history.requireArray("sessions")
        return sessions.values.map { (it as JsonObject).requireString("id") }
    }

    private fun setIds(root: JsonObject): List<String> {
        val history = root.requireObject("history")
        val sets = history.requireArray("sets")
        return sets.values.map { (it as JsonObject).requireString("id") }
    }

    @Test
    @DisplayName("IE-S1-001 Export AllTime includes every session and set")
    fun ieS1_001_exportAllTime() = runTest {
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA, sessionB, sessionLive),
            initialSets = listOf(setA1, setB1, setLive1),
        )
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals(setOf("sess-a", "sess-b", "sess-live"), sessionIds(root).toSet())
        assertEquals(setOf("set-a1", "set-b1", "set-live1"), setIds(root).toSet())
    }

    @Test
    @DisplayName("IE-S1-002 Export LastDays 30 uses device-local inclusive window")
    fun ieS1_002_exportLastDays30() = runTest {
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA, sessionOld),
            initialSets = listOf(setA1, setOld1),
        )
        val result = useCase(repo)(ExportLogRange.LastDays(days = 30, zone = ZONE_NY))
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals(listOf("sess-a"), sessionIds(root))
        assertEquals(listOf("set-a1"), setIds(root))
        val range = root.requireObject("range")
        assertEquals("last_days", range.requireString("kind"))
        assertEquals(30, range.requireInt("days"))
    }

    @Test
    @DisplayName("IE-S1-003 Export LastDays 90 includes session 60 days ago and excludes 120 days ago")
    fun ieS1_003_exportLastDays90() = runTest {
        val sess60 = session("sess-60", Instant.parse("2026-05-17T14:00:00Z"))
        val sess120 = session("sess-120", Instant.parse("2026-03-18T14:00:00Z"))
        val set60 = set("set-60", "sess-60", completedAt = Instant.parse("2026-05-17T14:05:00Z"))
        val set120 = set("set-120", "sess-120", completedAt = Instant.parse("2026-03-18T14:05:00Z"))
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sess60, sess120),
            initialSets = listOf(set60, set120),
        )
        val result = useCase(repo)(ExportLogRange.LastDays(days = 90, zone = ZONE_NY))
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals(listOf("sess-60"), sessionIds(root))
        assertEquals(listOf("set-60"), setIds(root))
    }

    @Test
    @DisplayName("IE-S1-004 Export YearToDate from January 1 through today in zone")
    fun ieS1_004_exportYearToDate() = runTest {
        val sessYtd = session("sess-ytd", Instant.parse("2026-02-01T15:00:00Z"))
        val sessPrev = session("sess-prev", Instant.parse("2025-12-31T15:00:00Z"))
        val setYtd = set("set-ytd", "sess-ytd", completedAt = Instant.parse("2026-02-01T15:05:00Z"))
        val setPrev = set("set-prev", "sess-prev", completedAt = Instant.parse("2025-12-31T15:05:00Z"))
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessYtd, sessPrev),
            initialSets = listOf(setYtd, setPrev),
        )
        val result = useCase(repo)(ExportLogRange.YearToDate(zone = ZONE_NY))
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals(listOf("sess-ytd"), sessionIds(root))
        assertEquals(listOf("set-ytd"), setIds(root))
        assertEquals("year_to_date", root.requireObject("range").requireString("kind"))
    }

    @Test
    @DisplayName("IE-S1-005 Export Custom inclusive device-local calendar days")
    fun ieS1_005_exportCustom() = runTest {
        val sessMid = session("sess-mid", Instant.parse("2026-06-15T16:00:00Z"))
        val sessOut = session("sess-out", Instant.parse("2026-05-01T16:00:00Z"))
        val setMid = set("set-mid", "sess-mid", completedAt = Instant.parse("2026-06-15T16:05:00Z"))
        val setOut = set("set-out", "sess-out", completedAt = Instant.parse("2026-05-01T16:05:00Z"))
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessMid, sessOut),
            initialSets = listOf(setMid, setOut),
        )
        val result =
            useCase(repo)(
                ExportLogRange.Custom(
                    startDate = LocalDate.of(2026, 6, 1),
                    endDateInclusive = LocalDate.of(2026, 6, 30),
                    zone = ZONE_NY,
                ),
            )
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals(listOf("sess-mid"), sessionIds(root))
        assertEquals(listOf("set-mid"), setIds(root))
        assertEquals("custom", root.requireObject("range").requireString("kind"))
    }

    @Test
    @DisplayName("IE-S1-006 Export Custom with start after end fails")
    fun ieS1_006_exportCustomInverted() = runTest {
        val repo = FakeTrainingLogRepository()
        val result =
            useCase(repo)(
                ExportLogRange.Custom(
                    startDate = LocalDate.of(2026, 7, 10),
                    endDateInclusive = LocalDate.of(2026, 7, 1),
                    zone = ZONE_NY,
                ),
            )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Start date must be on or before end date.", ex!!.message)
    }

    @Test
    @DisplayName("IE-S1-007 Exported logs JSON has required schema markers")
    fun ieS1_007_schemaMarkers() = runTest {
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA),
            initialSets = listOf(setA1),
        )
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        assertEquals("stratum.export.logs", root.requireString("schema"))
        assertEquals(1, root.requireInt("schemaVersion"))
        assertEquals("stratum", root.requireString("source"))
        assertEquals("2026-07-16T15:00:00Z", root.requireString("exportedAt"))
    }

    @Test
    @DisplayName("Export always emits weightUnit Lb and converts kg-tagged amounts")
    fun exportAlwaysLb() = runTest {
        // setB1 fixture is 60 kg; export must re-encode as Lb.
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionB),
            initialSets = listOf(setB1),
        )
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        val sets = root.requireObject("history").requireArray("sets")
        assertEquals(1, sets.values.size)
        val setObj = sets.values.first() as JsonObject
        assertEquals("Lb", setObj.requireString("weightUnit"))
        val expectedLb =
            com.stratum.domain.lift.WeightConversion.amountInLb(
                com.stratum.domain.lift.Weight(60.0, com.stratum.domain.lift.Weight.WeightUnit.Kg),
            )
        assertEquals(expectedLb, setObj.requireDouble("weightAmount"), 1e-6)
    }

    @Test
    @DisplayName("Export emits equipmentId for sets performed with known equipment")
    fun exportEmitsSetEquipmentId() = runTest {
        val withEquipment =
            setA1.copy(
                equipmentId = com.stratum.domain.lift.identity.EquipmentId("eq-cable"),
            )
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA),
            initialSets = listOf(withEquipment),
        )
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        val sets = root.requireObject("history").requireArray("sets")
        val setObj = sets.values.first() as JsonObject
        assertEquals("eq-cable", setObj.requireString("equipmentId"))
    }

    @Test
    @DisplayName("IE-S1-008 Exported session and set ids match domain ids")
    fun ieS1_008_stableIds() = runTest {
        val sess = session("stable-sess-9", Instant.parse("2026-07-01T14:00:00Z"))
        val logged = set("stable-set-9", "stable-sess-9")
        val repo = FakeTrainingLogRepository(listOf(sess), listOf(logged))
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        val history = root.requireObject("history")
        val sessions = history.requireArray("sessions")
        val sets = history.requireArray("sets")
        val sessionObj = sessions.values[0] as JsonObject
        val setObj = sets.values[0] as JsonObject
        assertEquals("stable-sess-9", sessionObj.requireString("id"))
        assertEquals("stable-set-9", setObj.requireString("id"))
        assertEquals("stable-sess-9", setObj.requireString("sessionId"))
    }

    @Test
    @DisplayName("IE-S1-009 Empty selection fails without file result")
    fun ieS1_009_emptyRange() = runTest {
        val logging = FakeLoggingPort()
        val repo = FakeTrainingLogRepository()
        val result =
            ExportTrainingLogsUseCase(
                trainingLogRepository = repo,
                clock = clock,
                loggingPort = logging,
            )(ExportLogRange.LastDays(30, ZONE_NY))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("No training logs in the selected range.", ex!!.message)
        assertTrue(logging.errors.isEmpty())
    }

    @Test
    @DisplayName("Repository read failure is logged and returned")
    fun repositoryReadFailureIsLogged() = runTest {
        val logging = FakeLoggingPort()
        val repo = FakeTrainingLogRepository()
        repo.getAllSessionsError = IllegalStateException("room down")
        val result =
            ExportTrainingLogsUseCase(
                trainingLogRepository = repo,
                clock = clock,
                loggingPort = logging,
            )(ExportLogRange.AllTime)
        assertTrue(result.isFailure)
        assertEquals("room down", result.exceptionOrNull()?.message)
        assertEquals(1, logging.errors.size)
        assertEquals("ExportTrainingLogs", logging.errors.single().tag)
    }

    @Test
    @DisplayName("IE-S1-011 Export file mime and name")
    fun ieS1_011_mimeAndFilename() = runTest {
        val repo = FakeTrainingLogRepository(listOf(sessionA), listOf(setA1))
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals("application/json", file.mimeType)
        assertEquals("stratum-logs-20260716-150000.json", file.fileName)
    }

    @Test
    @DisplayName("IE-S1-M001 Mutation defense export must include session ids")
    fun ieS1_m001_sessionIdsPresent() = runTest {
        val sess = session("must-export-id", Instant.parse("2026-07-01T14:00:00Z"))
        val logged = set("must-export-set", "must-export-id")
        val repo = FakeTrainingLogRepository(listOf(sess), listOf(logged))
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val root = decodeRoot(result.getOrThrow().bytes)
        val sessionObj = root.requireObject("history").requireArray("sessions").values[0] as JsonObject
        assertEquals("must-export-id", sessionObj.requireString("id"))
        assertTrue(sessionObj.entries.containsKey("id"))
    }

    @Test
    @DisplayName("partialReps is omitted when none logged and written when present")
    fun partialRepsOptionalOnExport() = runTest {
        val none = set("set-none", "sess-a")
        val withPartials = set("set-partial", "sess-a").copy(partialReps = 2)
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA),
            initialSets = listOf(none, withPartials),
        )
        val result = useCase(repo)(ExportLogRange.AllTime)
        assertTrue(result.isSuccess)
        val sets = decodeRoot(result.getOrThrow().bytes)
            .requireObject("history")
            .requireArray("sets")
            .values
            .map { it as JsonObject }
            .associateBy { it.requireString("id") }
        assertNull(sets.getValue("set-none").optionalInt("partialReps"))
        assertEquals(false, sets.getValue("set-none").entries.containsKey("partialReps"))
        assertEquals(2, sets.getValue("set-partial").requireInt("partialReps"))
    }
}

package com.stratum.domain.importing

import com.stratum.domain.export.ExportTestFixtures.ZONE_NY
import com.stratum.domain.export.ExportTestFixtures.session
import com.stratum.domain.export.ExportTestFixtures.sessionLive
import com.stratum.domain.export.ExportTestFixtures.set
import com.stratum.domain.export.ExportTestFixtures.setLive1
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.TrainingSessionOrigin
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportHistoryDedupeTest {

    @Test
    @DisplayName("IE-S4-001 Merge skips existing Stratum session id and inserts new")
    fun ieS4_001_stratumIdSkip() {
        val existingSess = session("sess-export-1", Instant.parse("2026-07-01T14:00:00Z"))
        val existingSet = set("set-export-1", "sess-export-1")
        val incoming1 = session("sess-export-1", Instant.parse("2026-07-01T14:00:00Z"))
        val incoming2 = session("sess-export-2", Instant.parse("2026-07-02T14:00:00Z"))
        val set1 = set("set-export-1", "sess-export-1")
        val set2 = set("set-export-2", "sess-export-2")
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = listOf(incoming1, incoming2),
                incomingSets = listOf(set1, set2),
                existingSessions = listOf(existingSess),
                existingSets = listOf(existingSet),
                zone = ZONE_NY,
                sourceKind = ImportDedupeSourceKind.StratumStableIds,
            )
        assertEquals(listOf("sess-export-2"), filtered.sessions.map { it.id.value })
        assertEquals(listOf("set-export-2"), filtered.sets.map { it.id.value })
        assertEquals(1, filtered.skippedSessionCount)
        assertEquals(1, filtered.skippedSetCount)
    }

    @Test
    @DisplayName(
        "IE-S4-002 Third-party same day different structure imports (no day occupancy)",
    )
    fun ieS4_002_thirdPartySameDayDifferentStructureKeeps() {
        // LIVE Deadlift on 2026-07-10 (NY). Incoming same local day with different sets
        // (default Squat) must import — multi-session days are allowed.
        val incoming =
            session(
                "tp-1",
                Instant.parse("2026-07-10T20:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val incomingSet =
            set(
                "tp-set-1",
                "tp-1",
                completedAt = Instant.parse("2026-07-10T20:05:00Z"),
            )
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = listOf(incoming),
                incomingSets = listOf(incomingSet),
                existingSessions = listOf(sessionLive),
                existingSets = listOf(setLive1),
                zone = ZONE_NY,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
            )
        assertEquals(listOf("tp-1"), filtered.sessions.map { it.id.value })
        assertEquals(listOf("tp-set-1"), filtered.sets.map { it.id.value })
        assertEquals(0, filtered.skippedSessionCount)
        assertEquals(0, filtered.skippedSetCount)
    }

    @Test
    @DisplayName("IE-S4-002b Third-party same day identical fingerprint skips")
    fun ieS4_002b_thirdPartySameDayIdenticalFingerprintSkips() {
        val existing =
            session("exist-day", Instant.parse("2026-07-05T14:00:00Z"))
        val existingSet =
            set(
                "exist-set",
                "exist-day",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T14:05:00Z"),
            )
        val incoming =
            session(
                "tp-same-day",
                Instant.parse("2026-07-05T18:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val incomingSet =
            set(
                "tp-same-set",
                "tp-same-day",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T18:05:00Z"),
            )
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = listOf(incoming),
                incomingSets = listOf(incomingSet),
                existingSessions = listOf(existing),
                existingSets = listOf(existingSet),
                zone = ZONE_NY,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
            )
        assertTrue(filtered.sessions.isEmpty())
        assertEquals(1, filtered.skippedSessionCount)
        assertEquals(1, filtered.skippedSetCount)
    }

    @Test
    @DisplayName(
        "IE-S4-004 Third-party same structure different day imports (date in fingerprint)",
    )
    fun ieS4_004_contentMatchDifferentDayKeeps() {
        // Plateau week: identical set multiset on a different local calendar day must import.
        val existing =
            session("exist-day", Instant.parse("2026-07-05T14:00:00Z"))
        val existingSet =
            set(
                "exist-set",
                "exist-day",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T14:05:00Z"),
            )
        val incoming =
            session(
                "tp-free-day",
                Instant.parse("2026-07-12T16:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val incomingSet =
            set(
                "tp-free-set",
                "tp-free-day",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-12T16:05:00Z"),
            )
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = listOf(incoming),
                incomingSets = listOf(incomingSet),
                existingSessions = listOf(existing),
                existingSets = listOf(existingSet),
                zone = ZONE_NY,
                sourceKind = ImportDedupeSourceKind.ThirdParty,
            )
        assertEquals(listOf("tp-free-day"), filtered.sessions.map { it.id.value })
        assertEquals(listOf("tp-free-set"), filtered.sets.map { it.id.value })
        assertEquals(0, filtered.skippedSessionCount)
        assertEquals(0, filtered.skippedSetCount)
    }

    @Test
    @DisplayName(
        "IE-S4-008 Mixed batch: Stratum id collision skips; third-party same day different " +
            "content imports; third-party same fingerprint skips",
    )
    fun ieS4_008_mixedBatchPerSessionSourceKind() {
        // Existing: Stratum session by stable id + a third-party fingerprint on 2026-07-05.
        val existingStratum = session("sess-export-1", Instant.parse("2026-07-01T14:00:00Z"))
        val existingStratumSet = set("set-export-1", "sess-export-1")
        val existingFpDay = session("exist-day", Instant.parse("2026-07-05T14:00:00Z"))
        val existingFpSet =
            set(
                "exist-set",
                "exist-day",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T14:05:00Z"),
            )

        // Incoming Stratum: same stable id → id-only skip (must not require fingerprint match).
        val incomingStratum = session("sess-export-1", Instant.parse("2026-07-01T16:00:00Z"))
        val incomingStratumSet =
            set(
                "set-export-1b",
                "sess-export-1",
                exercise = "Bench",
                weightAmount = 80.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 8,
                rir = 1,
                completedAt = Instant.parse("2026-07-01T16:05:00Z"),
            )

        // Incoming third-party: same local day, different content → import.
        val incomingDifferent =
            session(
                "f1::tp-diff",
                Instant.parse("2026-07-05T20:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val incomingDifferentSet =
            set(
                "f1::tp-diff-set",
                "f1::tp-diff",
                exercise = "Deadlift",
                weightAmount = 140.0,
                weightUnit = Weight.WeightUnit.Kg,
                reps = 3,
                rir = null,
                completedAt = Instant.parse("2026-07-05T20:05:00Z"),
            )

        // Incoming third-party: same day + identical fingerprint → content skip.
        val incomingSameFp =
            session(
                "f1::tp-same",
                Instant.parse("2026-07-05T18:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val incomingSameFpSet =
            set(
                "f1::tp-same-set",
                "f1::tp-same",
                exercise = "Squat",
                weightAmount = 100.0,
                weightUnit = Weight.WeightUnit.Lb,
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2026-07-05T18:05:00Z"),
            )

        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions =
                    listOf(incomingStratum, incomingDifferent, incomingSameFp),
                incomingSets =
                    listOf(incomingStratumSet, incomingDifferentSet, incomingSameFpSet),
                existingSessions = listOf(existingStratum, existingFpDay),
                existingSets = listOf(existingStratumSet, existingFpSet),
                zone = ZONE_NY,
                // Global ThirdParty would wrongly fingerprint the Stratum row; per-session set
                // keeps Stratum on id-only while third-party rows still fingerprint.
                sourceKind = ImportDedupeSourceKind.ThirdParty,
                stableIdSessionIds = setOf("sess-export-1"),
            )

        assertEquals(listOf("f1::tp-diff"), filtered.sessions.map { it.id.value })
        assertEquals(listOf("f1::tp-diff-set"), filtered.sets.map { it.id.value })
        // Stratum id collision + same-fingerprint third-party.
        assertEquals(2, filtered.skippedSessionCount)
        assertEquals(2, filtered.skippedSetCount)
    }

    @Test
    @DisplayName(
        "IE-S4-010 Set-id collision drops all sets for a session — ghost empty session is skipped",
    )
    fun ieS4_010_setIdCollisionDropsEmptySession() {
        // Existing set id collides with the only set on an otherwise-new session.
        val existingSess = session("sess-old", Instant.parse("2026-07-01T14:00:00Z"))
        val existingSet = set("set-shared-id", "sess-old")
        val incoming =
            session(
                "sess-new",
                Instant.parse("2026-07-02T14:00:00Z"),
                origin = TrainingSessionOrigin.IMPORT,
            )
        val collidingSet = set("set-shared-id", "sess-new")
        val filtered =
            ImportHistoryDedupe.filterForMerge(
                incomingSessions = listOf(incoming),
                incomingSets = listOf(collidingSet),
                existingSessions = listOf(existingSess),
                existingSets = listOf(existingSet),
                zone = ZONE_NY,
                sourceKind = ImportDedupeSourceKind.StratumStableIds,
            )
        assertTrue(filtered.sessions.isEmpty())
        assertTrue(filtered.sets.isEmpty())
        assertEquals(1, filtered.skippedSessionCount)
        assertEquals(1, filtered.skippedSetCount)
    }
}

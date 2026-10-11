package com.stratum.domain.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PersonalRecordScoringTest {

    private val liftId = LiftId("lift-pullup")
    private val sessionId = TrainingSessionId("session-1")
    private val earlier = Instant.parse("2026-01-01T10:00:00Z")
    private val later = Instant.parse("2026-02-01T10:00:00Z")

    @Test
    @DisplayName("Bodyweight 12 effective reps beats 8")
    fun bodyweightHigherRepsWins() {
        val eight = createSet("s8", weight = 0.0, reps = 8, at = earlier)
        val twelve = createSet("s12", weight = 0.0, reps = 12, at = earlier)
        assertEquals("s12", PersonalRecordScoring.bestPersonalRecordSet(listOf(eight, twelve))?.id?.value)
    }

    @Test
    @DisplayName("Bodyweight RIR is included in effective reps")
    fun bodyweightRirCounts() {
        val tenAtTwo = createSet("s10", weight = 0.0, reps = 10, at = earlier).copy(rir = 2)
        val elevenAtZero = createSet("s11", weight = 0.0, reps = 11, at = later).copy(rir = 0)
        assertEquals(
            "s10",
            PersonalRecordScoring.bestPersonalRecordSet(listOf(tenAtTwo, elevenAtZero))?.id?.value,
        )
        assertEquals(
            PersonalRecordScore.BodyweightReps(12),
            PersonalRecordScoring.score(tenAtTwo),
        )
    }

    @Test
    @DisplayName("Zero-load never writes an e1RM")
    fun zeroLoadEstimatedOneRmIsNull() {
        assertNull(PersonalRecordScoring.estimatedOneRmLb(zeroLb(), 12, rir = 1))
        val score = PersonalRecordScoring.score(zeroLb(), 12, rir = 1)
        assertTrue(score is PersonalRecordScore.BodyweightReps)
    }

    @Test
    @DisplayName("Weighted path still uses e1RM")
    fun weightedUsesE1rm() {
        val heavier = createSet("h", weight = 110.0, reps = 3, at = earlier)
        val lighter = createSet("l", weight = 100.0, reps = 5, at = later)
        assertEquals("h", PersonalRecordScoring.bestPersonalRecordSet(listOf(lighter, heavier))?.id?.value)
        val expected = EstimatedOneRm.calculate(110.0, 3)
        assertEquals(expected, PersonalRecordScoring.estimatedOneRmLb(heavier.weight, 3, null))
    }

    @Test
    @DisplayName("Loaded working set beats a later bodyweight backoff")
    fun loadedBeatsLaterBodyweightBackoff() {
        val working = createSet("work", weight = 45.0, reps = 5, at = earlier)
        val backoff = createSet("bw", weight = 0.0, reps = 8, at = later)
        assertEquals(
            "work",
            PersonalRecordScoring.bestPersonalRecordSet(listOf(working, backoff))?.id?.value,
        )
    }

    @Test
    @DisplayName("Equal bodyweight reps prefers the later set")
    fun equalBodyweightRepsPrefersLater() {
        val first = createSet("a", weight = 0.0, reps = 10, at = earlier).copy(rir = 2)
        val second = createSet("b", weight = 0.0, reps = 12, at = later).copy(rir = 0)
        assertEquals("b", PersonalRecordScoring.bestPersonalRecordSet(listOf(first, second))?.id?.value)
    }

    @Test
    @DisplayName("Ineligible sets (0 reps or null lift) have no score")
    fun ineligibleHasNoScore() {
        assertNull(PersonalRecordScoring.score(zeroLb(), 0, null))
        assertNull(
            PersonalRecordScoring.score(
                createSet(id = "no-lift", weight = 100.0, reps = 5).copy(liftId = null),
            ),
        )
    }

    @Test
    @DisplayName("bestPersonalRecordSet picks max bodyweight reps")
    fun bestSetPicksMaxBodyweightReps() {
        val eight = createSet("s8", weight = 0.0, reps = 8, at = earlier)
        val twelve = createSet("s12", weight = 0.0, reps = 12, at = earlier)
        val six = createSet("s6", weight = 0.0, reps = 6, at = later)
        val best = PersonalRecordScoring.bestPersonalRecordSet(listOf(eight, twelve, six))
        assertEquals("s12", best?.id?.value)
    }

    @Test
    @DisplayName("withPersonalRecords flags exactly one loaded max as PR")
    fun withPersonalRecordsFlagsLoadedMax() {
        val working = createSet("work", weight = 45.0, reps = 5, at = earlier)
        val backoff = createSet("bw", weight = 0.0, reps = 12, at = later)
        val flagged = PersonalRecordScoring.withPersonalRecords(listOf(working, backoff))
        assertTrue(flagged.first { it.id.value == "work" }.isPersonalRecord)
        assertFalse(flagged.first { it.id.value == "bw" }.isPersonalRecord)
        assertNull(flagged.first { it.id.value == "bw" }.estimatedOneRm)
    }

    private fun zeroLb(): Weight = Weight(0.0, Weight.WeightUnit.Lb)

    private fun createSet(
        id: String,
        weight: Double,
        reps: Int,
        at: Instant = earlier,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Pull-up",
            liftId = liftId,
            setIndex = 1,
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = reps,
            rir = null,
            completedAt = at,
            liftContext = LoggedLiftContext(liftName = "Pull-up"),
        )

    @Test
    @DisplayName("STR-123: each (lift, equipment) pool crowns its own PR")
    fun perPairWinners() {
        val barbellOld = createSet("bb-old", weight = 100.0, reps = 5, at = earlier)
            .copy(equipmentId = com.stratum.domain.lift.identity.EquipmentId("barbell"))
        val barbellNew = createSet("bb-new", weight = 110.0, reps = 5, at = later)
            .copy(equipmentId = com.stratum.domain.lift.identity.EquipmentId("barbell"))
        val ssbOnly = createSet("ssb", weight = 60.0, reps = 5, at = later)
            .copy(equipmentId = com.stratum.domain.lift.identity.EquipmentId("ssb"))

        val flagged =
            PersonalRecordScoring.withPersonalRecordsPerPair(
                listOf(barbellOld, barbellNew, ssbOnly),
            ).associateBy { it.id.value }

        assertFalse(flagged.getValue("bb-old").isPersonalRecord)
        assertTrue(flagged.getValue("bb-new").isPersonalRecord)
        assertTrue(flagged.getValue("ssb").isPersonalRecord)
    }

    @Test
    @DisplayName("STR-123: per-pair flagging preserves input order for interleaved pairs")
    fun perPairPreservesInputOrder() {
        val barbellOne = createSet("bb1", weight = 100.0, reps = 5, at = earlier)
            .copy(equipmentId = EquipmentId("barbell"))
        val ssbOne = createSet("ssb1", weight = 60.0, reps = 5, at = earlier)
            .copy(equipmentId = EquipmentId("ssb"))
        val barbellTwo = createSet("bb2", weight = 110.0, reps = 5, at = later)
            .copy(equipmentId = EquipmentId("barbell"))
        val stray = createSet("stray", weight = 200.0, reps = 5, at = later)
            .copy(liftId = null)

        val flagged =
            PersonalRecordScoring.withPersonalRecordsPerPair(
                listOf(barbellOne, ssbOne, barbellTwo, stray),
            )

        assertEquals(listOf("bb1", "ssb1", "bb2", "stray"), flagged.map { it.id.value })
        assertFalse(flagged[0].isPersonalRecord)
        assertTrue(flagged[1].isPersonalRecord)
        assertTrue(flagged[2].isPersonalRecord)
        assertFalse(flagged[3].isPersonalRecord)
        assertNull(flagged[3].estimatedOneRm)
    }

    @Test
    @DisplayName("STR-123: headline best-per-lift still spans equipment pools")
    fun headlineSpansPools() {
        val barbell = createSet("bb", weight = 110.0, reps = 5, at = earlier)
            .copy(equipmentId = com.stratum.domain.lift.identity.EquipmentId("barbell"))
        val ssb = createSet("ssb", weight = 60.0, reps = 5, at = later)
            .copy(equipmentId = com.stratum.domain.lift.identity.EquipmentId("ssb"))

        assertEquals(
            "bb",
            PersonalRecordScoring.bestPersonalRecordSet(listOf(barbell, ssb))?.id?.value,
        )
    }
}

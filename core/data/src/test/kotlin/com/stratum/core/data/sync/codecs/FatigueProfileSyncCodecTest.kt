package com.stratum.core.data.sync.codecs

import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.LoggedSetEntity
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FPR-S2-009 … FPR-S2-012 — sync wire key fatigue_profile only; fallbacks Low/Medium.
 */
class FatigueProfileSyncCodecTest {

    private val encoders = SyncEntityEncoders()
    private val decoders = SyncEntityDecoders()

    @Test
    @DisplayName("FPR-S2-009 lift and logged-set sync encode fatigue_profile")
    fun fprS2009_encodeKeyFatigueProfile() {
        val lift =
            LiftEntity(
                id = LiftId("lift-high"),
                name = "High Lift",
                movementPatternsBitmask = 0,
                primaryMusclesBitmask = 0,
                secondaryMusclesBitmask = 0,
                presetTags = "",
                isCustom = false,
                enabled = true,
                fatigueProfile = FatigueProfile.High,
            )
        val liftMap = encoders.lift(lift)
        assertTrue("fatigue_profile" in liftMap, "must encode fatigue_profile key")
        assertEquals("High", liftMap["fatigue_profile"])
        assertFalse("kinematic_profile" in liftMap, "must not encode kinematic_profile")

        val logged =
            LoggedSetEntity(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("session-1"),
                exerciseNameRaw = "Curl",
                liftId = null,
                setIndex = 0,
                weightAmount = 20.0,
                weightUnit = "Lb",
                reps = 10,
                rir = 2,
                completedAtEpochMillis = 0L,
                fatigueProfile = FatigueProfile.Medium,
            )
        val setMap = encoders.loggedSet(logged)
        assertTrue("fatigue_profile" in setMap, "must encode fatigue_profile key")
        assertEquals("Medium", setMap["fatigue_profile"])
        assertFalse("kinematic_profile" in setMap, "must not encode kinematic_profile")
    }

    @Test
    @DisplayName("logged-set sync encodes partial_reps and decodes a missing key as null")
    fun loggedSetPartialRepsOptional() {
        val withPartials =
            LoggedSetEntity(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("session-1"),
                exerciseNameRaw = "Curl",
                liftId = null,
                setIndex = 0,
                weightAmount = 20.0,
                weightUnit = "Lb",
                reps = 10,
                rir = 2,
                partialReps = 2,
                completedAtEpochMillis = 0L,
                fatigueProfile = FatigueProfile.Medium,
            )
        val encoded = encoders.loggedSet(withPartials)
        assertEquals(2, encoded["partial_reps"])
        val roundTrip = requireNotNull(decoders.loggedSet("set-1", encoded))
        assertEquals(2, roundTrip.partialReps)

        val missing = requireNotNull(decoders.loggedSet("set-1", baseLoggedSetDoc()))
        assertEquals(null, missing.partialReps)
    }

    @Test
    @DisplayName("FPR-S2-010 lift sync decode unknown or missing fatigue_profile yields Low")
    fun fprS2010_liftDecodeFallbackLow() {
        val missing = baseLiftDoc()
        val missingDecoded = requireNotNull(decoders.lift("lift-1", missing))
        assertEquals("Low", profileName(missingDecoded))

        val garbage = baseLiftDoc() + ("fatigue_profile" to "GarbageValue")
        val garbageDecoded = requireNotNull(decoders.lift("lift-1", garbage))
        assertEquals("Low", profileName(garbageDecoded))
    }

    @Test
    @DisplayName("FPR-S2-011 logged set sync decode unknown or missing fatigue_profile yields Medium")
    fun fprS2011_loggedSetDecodeFallbackMedium() {
        val missing = baseLoggedSetDoc()
        val missingDecoded = requireNotNull(decoders.loggedSet("set-1", missing))
        assertEquals("Medium", profileName(missingDecoded))

        val garbage = baseLoggedSetDoc() + ("fatigue_profile" to "GarbageValue")
        val garbageDecoded = requireNotNull(decoders.loggedSet("set-1", garbage))
        assertEquals("Medium", profileName(garbageDecoded))
    }

    @Test
    @DisplayName("FPR-S2-012 kinematic_profile key is ignored when fatigue_profile missing")
    fun fprS2012_noKinematicDualRead() {
        val onlyLegacy =
            baseLiftDoc() + ("kinematic_profile" to "AxialCompound")
        val decoded = requireNotNull(decoders.lift("lift-1", onlyLegacy))
        // Must fall back to Low — never promote from legacy kinematic_profile key.
        assertEquals("Low", profileName(decoded))
    }

    private fun baseLiftDoc(): Map<String, Any?> =
        mapOf(
            "name" to "Sample Lift",
            "movement_patterns" to listOf("ElbowFlexion"),
            "primary_muscles" to listOf("Bicep"),
            "secondary_muscles" to emptyList<String>(),
            "preset_tags" to emptyList<String>(),
            "is_custom" to false,
            "enabled" to true,
            "muscle_length_bias" to "MidRange",
            "mainstream" to false,
            "load_type" to "Weighted",
            "updated_at_epoch_millis" to 0L,
        )

    private fun baseLoggedSetDoc(): Map<String, Any?> =
        mapOf(
            "session_id" to "session-1",
            "exercise_name_raw" to "Curl",
            "set_index" to 0,
            "weight_amount" to 20.0,
            "weight_unit" to "Lb",
            "reps" to 10,
            "completed_at_epoch_millis" to 0L,
            "lift_name" to "Curl",
            "movement_patterns_bitmask" to 0,
            "primary_muscles_bitmask" to 0,
            "secondary_muscles_bitmask" to 0,
            "muscle_length_bias" to "MidRange",
            "updated_at_epoch_millis" to 0L,
        )

    private fun profileName(entity: Any): String {
        val field =
            entity::class.java.declaredFields.first { it.name == "fatigueProfile" }
        field.isAccessible = true
        return field.get(entity)?.toString()?.substringAfterLast('.') ?: "null"
    }
}

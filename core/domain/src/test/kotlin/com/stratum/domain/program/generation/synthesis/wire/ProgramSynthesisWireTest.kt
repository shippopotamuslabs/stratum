package com.stratum.domain.program.generation.synthesis.wire

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.LiftEffectivenessEntry
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.prescription.SynthesisObjectiveBreakdown
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.split.ResolvedSessionPlan
import com.stratum.domain.program.generation.slots.SessionComposition
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramSynthesisWireTest {

    @Test
    @DisplayName("FPR-S1-020 context wire encodes fatigueProfile as High Medium Low names")
    fun fprS1020_contextWireEncodesFatigueProfile() {
        val context = minimalContext()
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), context)
        assertTrue(
            encoded.contains("\"fatigueProfile\""),
            "JSON must contain key fatigueProfile",
        )
        assertTrue(
            encoded.contains("\"High\""),
            "JSON fatigueProfile value must be High for Axial/High lift fixture",
        )
        assertFalse(
            encoded.contains("\"kinematicProfile\""),
            "JSON must not contain legacy key kinematicProfile",
        )
    }


    @Test
    fun `request round-trip preserves minimal realistic context and targets`() {
        val weeklyTargets =
            mapOf(
                "Chest" to WeeklyVolumeTarget(min = 8.0, aim = 12.0, max = 18.0),
                "Back" to WeeklyVolumeTarget(min = 6.0, aim = 10.0, max = 16.0),
            )
        val context = minimalContext()

        val encoded = ProgramSynthesisWire.encodeRequest(weeklyTargets, context)
        val decoded = ProgramSynthesisWire.decodeRequest(encoded)

        assertEquals(weeklyTargets, decoded.weeklyTargets)
        assertEquals(context, decoded.context)
    }

    @Test
    fun `result round-trip preserves one session plan and objective`() {
        val result = minimalResult()

        val encoded = ProgramSynthesisWire.encodeResult(result)
        val decoded = ProgramSynthesisWire.decodeResult(encoded)

        assertEquals(result.sessions, decoded.sessions)
        assertEquals(result.plans.size, decoded.plans.size)
        assertEquals(result.plans[0].sessionType, decoded.plans[0].sessionType)
        assertEquals(result.plans[0].name, decoded.plans[0].name)
        assertEquals(result.plans[0].siblingIndex, decoded.plans[0].siblingIndex)
        assertEquals(result.plans[0].position, decoded.plans[0].position)
        assertEquals(result.plans[0].slots, decoded.plans[0].slots)
        assertEquals(result.plans[0].filledSlots, decoded.plans[0].filledSlots)
        assertEquals(result.cycleSlotIndexByPosition, decoded.cycleSlotIndexByPosition)
        assertEquals(result.objective, decoded.objective)
        assertEquals(result.activeSlotIdsBySession, decoded.activeSlotIdsBySession)
        assertEquals(result.regionFilterBySession, decoded.regionFilterBySession)
    }

    @Test
    fun `decodeResult without regionFilterBySession infers from sessionType labels`() {
        val result = minimalResult()
        val encoded = ProgramSynthesisWire.encodeResult(result)
        // Strip the new field to simulate a pre-regionFilter cloud result.
        val legacyJson =
            encoded.replace(
                Regex(""",\s*"regionFilterBySession"\s*:\s*\[[^\]]*\]"""),
                "",
            )
        assertTrue(!legacyJson.contains("regionFilterBySession"), "precondition: field stripped")

        val decoded = ProgramSynthesisWire.decodeResult(legacyJson)

        assertEquals(listOf(SessionRegionFilter.UpperOnly), decoded.regionFilterBySession)
        assertEquals(result.sessions, decoded.sessions)
    }

    @Test
    fun `sessionTypeIdToRegionFilter maps legacy labels`() {
        assertEquals(
            SessionRegionFilter.UpperOnly,
            sessionTypeIdToRegionFilter(SessionTypeId.Upper),
        )
        assertEquals(
            SessionRegionFilter.LowerOnly,
            sessionTypeIdToRegionFilter(SessionTypeId.Lower),
        )
        assertEquals(
            SessionRegionFilter.Unrestricted,
            sessionTypeIdToRegionFilter(SessionTypeId.FullBody),
        )
    }

    @Test
    fun `encodeRequest emits modelVersion v1`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        assertTrue(
            encoded.contains("\"modelVersion\":\"v1\""),
            "request JSON must carry modelVersion v1, got: $encoded".take(200),
        )
    }

    @Test
    fun `decodeRequest tolerates missing modelVersion as v1`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        val legacy = encoded.replace("\"modelVersion\":\"v1\",", "")
        assertFalse(legacy.contains("modelVersion"), "precondition: version stripped")

        val decoded = ProgramSynthesisWire.decodeRequest(legacy)

        assertEquals(emptyMap<String, WeeklyVolumeTarget>(), decoded.weeklyTargets)
        assertEquals(minimalContext(), decoded.context)
    }

    @Test
    fun `decodeRequest accepts v1 minor versions`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        val minor = encoded.replace("\"modelVersion\":\"v1\"", "\"modelVersion\":\"v1.4\"")

        val decoded = ProgramSynthesisWire.decodeRequest(minor)

        assertEquals(minimalContext(), decoded.context)
    }

    @Test
    fun `decodeRequest rejects unknown major modelVersion`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        val v2 = encoded.replace("\"modelVersion\":\"v1\"", "\"modelVersion\":\"v2\"")

        val ex =
            assertThrows(ProgramSynthesisWireException::class.java) {
                ProgramSynthesisWire.decodeRequest(v2)
            }
        assertTrue(ex.message!!.contains("modelVersion"), "message must name the field: ${ex.message}")
    }

    @Test
    fun `decodeResult tolerates missing modelVersion as v1`() {
        val result = minimalResult()
        val encoded = ProgramSynthesisWire.encodeResult(result)
        assertFalse(encoded.contains("modelVersion"), "precondition: encoder emits no version")

        val decoded = ProgramSynthesisWire.decodeResult(encoded)

        assertEquals(result.sessions, decoded.sessions)
        assertEquals(result.objective, decoded.objective)
    }

    @Test
    fun `decodeResult accepts v1 minor versions`() {
        val encoded = ProgramSynthesisWire.encodeResult(minimalResult())
        val minor = encoded.replaceFirst("{", "{\"modelVersion\":\"v1.2\",")

        val decoded = ProgramSynthesisWire.decodeResult(minor)

        assertEquals(minimalResult().sessions, decoded.sessions)
    }

    @Test
    fun `decodeResult rejects unknown major modelVersion`() {
        val encoded = ProgramSynthesisWire.encodeResult(minimalResult())
        val v2 = encoded.replaceFirst("{", "{\"modelVersion\":\"v2\",")

        val ex =
            assertThrows(ProgramSynthesisWireException::class.java) {
                ProgramSynthesisWire.decodeResult(v2)
            }
        assertTrue(ex.message!!.contains("modelVersion"), "message must name the field: ${ex.message}")
    }

    @Test
    fun `stampModelVersionV1 leaves versioned payloads byte-identical`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())

        assertSame(encoded, ProgramSynthesisWire.stampModelVersionV1(encoded))
    }

    @Test
    fun `stampModelVersionV1 splices v1 onto legacy payloads without touching other bytes`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        val legacy = encoded.replace("\"modelVersion\":\"v1\",", "")
        assertFalse(legacy.contains("modelVersion"), "precondition: version stripped")

        val stamped = ProgramSynthesisWire.stampModelVersionV1(legacy)

        val brace = legacy.indexOf('{')
        assertEquals(
            legacy.substring(0, brace + 1) + "\"modelVersion\":\"v1\"," + legacy.substring(brace + 1),
            stamped,
        )
        // The stamped payload validates as v1.
        val decoded = ProgramSynthesisWire.decodeRequest(stamped)
        assertEquals(minimalContext(), decoded.context)
    }

    @Test
    fun `stampModelVersionV1 keeps explicit minor versions untouched`() {
        val encoded = ProgramSynthesisWire.encodeRequest(emptyMap(), minimalContext())
        val minor = encoded.replace("\"modelVersion\":\"v1\"", "\"modelVersion\":\"v1.4\"")

        assertEquals(minor, ProgramSynthesisWire.stampModelVersionV1(minor))
    }

    @Test
    fun `stampModelVersionV1 rejects non-object JSON`() {
        assertThrows(ProgramSynthesisWireException::class.java) {
            ProgramSynthesisWire.stampModelVersionV1("{not-json")
        }
    }

    @Test
    fun `invalid request JSON throws ProgramSynthesisWireException`() {
        val ex =
            assertThrows(ProgramSynthesisWireException::class.java) {
                ProgramSynthesisWire.decodeRequest("{not-json")
            }
        assertTrue(ex.message!!.contains("Invalid program synthesis request JSON"))
    }

    @Test
    fun `invalid result JSON throws ProgramSynthesisWireException`() {
        val ex =
            assertThrows(ProgramSynthesisWireException::class.java) {
                ProgramSynthesisWire.decodeResult("""{"sessions":[]}""")
            }
        assertTrue(ex.message!!.contains("Invalid program synthesis result JSON"))
    }

    @Test
    fun `missing required result field throws clear exception`() {
        val ex =
            assertThrows(ProgramSynthesisWireException::class.java) {
                ProgramSynthesisWire.decodeResult("{}")
            }
        assertTrue(ex.message!!.contains("Invalid program synthesis result JSON"))
    }

    private fun minimalContext(): ProgramGenerationContext {
        val bench =
            LiftContext(
                id = "bench-press",
                name = "Barbell Bench Press",
                movementPattern = MovementPattern.HorizontalPress,
                movementPatterns = setOf(MovementPattern.HorizontalPress),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
                mainstream = true,
                progressTrend = "up",
                proven = true,
                sessionCount = 12,
                effectivenessRank = 1,
                effectivenessEvidence = "personal",
                fatigueDecaySlope = -0.15,
                fatigueDecayIntercept = 1.0,
                muscleLengthBias = MuscleLengthBias.MidRange,
                equipment = "barbell",
                fatigueProfile = FatigueProfile.High,
                incrementTiers =
                    listOf(
                        IncrementTier(
                            lowerBound = Weight(0.0, Weight.WeightUnit.Lb),
                            upperBound = Weight(100.0, Weight.WeightUnit.Lb),
                            increment = Weight(5.0, Weight.WeightUnit.Lb),
                        ),
                        IncrementTier(
                            lowerBound = Weight(100.0, Weight.WeightUnit.Lb),
                            upperBound = null,
                            increment = Weight(10.0, Weight.WeightUnit.Lb),
                        ),
                    ),
                estimatedOneRm = Weight(225.0, Weight.WeightUnit.Lb),
            )
        return ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = 60,
            trainingDaysPerWeek = 4,
            specialization =
                mapOf(
                    MuscleGroup.Chest to SpecializationLevel.Specialize,
                    MuscleGroup.Back to SpecializationLevel.Maintenance,
                ),
            liftsById = mapOf(bench.id to bench),
            muscleVolumeSnapshotsByMuscle =
                mapOf(
                    "Chest" to
                        MuscleVolumeSnapshotContext(
                            muscle = "Chest",
                            retentionFloorMv = 6.0,
                            paretoVolumePv = 12.0,
                            recoverabilityCeilingRc = 20.0,
                            growthEntryFloor = 8.0,
                            maintenanceCeiling = 16.0,
                            posture = "Productive",
                            observedDoseMax = 14.0,
                            observedDoseMedian = 10.0,
                            source = VolumeSnapshotSource.Personal,
                        ),
                ),
            volumeCorrelationsByMuscle =
                mapOf(
                    "Chest" to
                        VolumeCorrelationContext(
                            muscle = "Chest",
                            correlationSlope = 0.42,
                            evidence = "moderate",
                        ),
                ),
            enabledLiftIds = setOf("bench-press"),
            movementPatternByLiftId =
                mapOf(
                    "bench-press" to MovementPattern.HorizontalPress,
                    "custom-iso" to null,
                ),
            liftEffectivenessByLiftId =
                mapOf(
                    "bench-press" to
                        LiftEffectivenessEntry(
                            liftId = "bench-press",
                            pattern = MovementPattern.HorizontalPress,
                            rank = 1,
                            effectivenessScore = 0.91,
                            evidence = "personal",
                        ),
                ),
        )
    }

    private fun minimalResult(): ProgramSynthesisResult {
        val slotTemplate =
            WorkoutSlotTemplate(
                slotId = "h-press",
                role = SlotRole.Anchor,
                pattern = MovementPattern.HorizontalPress,
                primaryMuscle = MuscleGroup.Chest,
                repProfile = RpRepProfile.LowerMachineOrUpperPress,
                lengthBiasPreference = MuscleLengthBiasPreference.Any,
                siblingVariant = SiblingVariant.A,
                isCrossDomainTouch = false,
                allowedFatigueProfiles =
                    setOf(FatigueProfile.High, FatigueProfile.Medium),
            )
        val filled =
            FilledSlot(
                slotId = "h-press",
                role = SlotRole.Anchor,
                pattern = MovementPattern.HorizontalPress,
                primaryMuscle = MuscleGroup.Chest,
                repProfile = RpRepProfile.LowerMachineOrUpperPress,
                lengthBiasPreference = MuscleLengthBiasPreference.Any,
                siblingVariant = SiblingVariant.A,
                isCrossDomainTouch = false,
                setCount = 3,
            )
        val session =
            ResolvedSessionPlan(
                sessionType = SessionTypeId.Upper,
                name = "Upper A",
                slots = listOf(slotTemplate),
                siblingIndex = 0,
                position = 0,
                composition = SessionComposition.UpperBalanced,
            )
        val plan =
            SessionSlotPlan(
                sessionType = SessionTypeId.Upper,
                name = "Upper A",
                slots = listOf(slotTemplate),
                siblingIndex = 0,
                position = 0,
                filledSlots = mutableListOf(filled),
            )
        return ProgramSynthesisResult(
            sessions = listOf(session),
            plans = listOf(plan),
            cycleSlotIndexByPosition = listOf(0),
            objective =
                SynthesisObjectiveBreakdown(
                    total = 1_000L,
                    recoveryPenalty = 10L,
                    specializedMevShortfall = 0L,
                    coSpecializationImbalance = 5L,
                    specializeAimDeviation = 20L,
                    maintenanceAimDeviation = 30L,
                    minimumVolumeAimDeviation = 0L,
                    roleMinimumShortfall = 0L,
                    pushPullImbalance = 2L,
                    sessionFillShortfall = 0L,
                ),
            activeSlotIdsBySession = listOf(setOf("h-press")),
            regionFilterBySession = listOf(SessionRegionFilter.UpperOnly),
        )
    }
}

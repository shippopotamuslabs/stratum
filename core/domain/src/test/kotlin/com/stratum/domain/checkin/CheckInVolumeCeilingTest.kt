package com.stratum.domain.checkin

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.OutcomeModelType
import com.stratum.domain.response.modeling.VolumePosture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CheckInVolumeCeilingTest {

    private val literatureCeiling = GlobalVolumePriors.maintenanceCeiling()

    @Test
    @DisplayName("A1: no snapshot, volume 10 → not at ceiling")
    fun underLiteratureCeiling() {
        assertFalse(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to 10.0),
                snapshotsByMuscle = emptyMap(),
            ),
        )
    }

    @Test
    @DisplayName("A2: no snapshot, volume above literature maintenance → at ceiling")
    fun overLiteratureCeiling() {
        assertTrue(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to literatureCeiling + 0.5),
                snapshotsByMuscle = emptyMap(),
            ),
        )
    }

    @Test
    @DisplayName("A3: snapshot PV/RC → maintenance ceiling; volume at ceiling")
    fun snapshotMaintenanceCeiling() {
        val snapshot = snapshot(pv = 12.0, rc = 18.0, posture = VolumePosture.Holding)
        val ceiling = GlobalVolumePriors.maintenanceCeiling(12.0, 18.0)
        assertEquals(15.0, ceiling)
        assertTrue(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to ceiling),
                snapshotsByMuscle = mapOf(MuscleGroup.Chest to snapshot),
            ),
        )
    }

    @Test
    @DisplayName("A4: Saturated posture + volume ≥ 50% ceiling → at ceiling")
    fun saturatedPostureWithEnoughVolume() {
        val snapshot = snapshot(pv = 12.0, rc = 18.0, posture = VolumePosture.Saturated)
        val ceiling = CheckInVolumeCeiling.resolveCeiling(snapshot)
        // Below 50% of ceiling: Saturated alone does not freeze +1
        assertFalse(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to ceiling * 0.4),
                snapshotsByMuscle = mapOf(MuscleGroup.Chest to snapshot),
            ),
        )
        // At 50%+: Saturated forces ceiling
        assertTrue(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to ceiling * 0.5),
                snapshotsByMuscle = mapOf(MuscleGroup.Chest to snapshot),
            ),
        )
    }

    @Test
    @DisplayName("A5: multi-primary — any primary over ceiling counts")
    fun multiPrimaryAnyOver() {
        assertTrue(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                programVolumeByMuscle =
                    mapOf(
                        MuscleGroup.Chest to 8.0,
                        MuscleGroup.Tricep to literatureCeiling + 1.0,
                    ),
                snapshotsByMuscle = emptyMap(),
            ),
        )
    }

    @Test
    @DisplayName("A6: empty primary set → not at ceiling")
    fun emptyPrimary() {
        assertFalse(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = emptySet(),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to 99.0),
                snapshotsByMuscle = emptyMap(),
            ),
        )
    }

    @Test
    @DisplayName("A7/A8: exactly at ceiling is at; just under is not")
    fun boundary() {
        assertFalse(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to literatureCeiling - 0.1),
                snapshotsByMuscle = emptyMap(),
            ),
        )
        assertTrue(
            CheckInVolumeCeiling.isAtCeiling(
                primaryMuscles = setOf(MuscleGroup.Chest),
                programVolumeByMuscle = mapOf(MuscleGroup.Chest to literatureCeiling),
                snapshotsByMuscle = emptyMap(),
            ),
        )
    }

    @Test
    @DisplayName("resolveCeiling: null snapshot uses literature maintenance")
    fun resolveNullUsesLiterature() {
        assertEquals(literatureCeiling, CheckInVolumeCeiling.resolveCeiling(null))
    }

    private fun snapshot(
        pv: Double,
        rc: Double,
        posture: VolumePosture,
    ): MuscleVolumeResponseSnapshot =
        MuscleVolumeResponseSnapshot(
            muscle = MuscleGroup.Chest,
            retentionFloorMv = 4.0,
            paretoVolumePv = pv,
            recoverabilityCeilingRc = rc,
            growthEntryFloor = 6.0,
            observedDoseMin = 4.0,
            observedDoseMax = 16.0,
            observedDoseMedian = 10.0,
            productiveBandMin = 8.0,
            productiveBandMax = 14.0,
            posture = posture,
            pvInterval = LandmarkConfidenceInterval.Unavailable,
            confidenceTier = LandmarkConfidenceTier.Medium,
            outcomeModelType = OutcomeModelType.MuscleIndex,
            diagnosticAnchorLiftId = LiftId("anchor"),
        )
}

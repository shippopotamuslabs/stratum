package com.stratum.feature.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LiveSetTypeOverlayOps")
class LiveSetTypeOverlayOpsTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("bench")
    private val activationId = SetSlotId.Template(LiftSetId("ls-act"))
    private val standardId = SetSlotId.Template(LiftSetId("ls-std"))
    private val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-act-1-aaaaaaaa"))

    private fun slot(
        id: SetSlotId,
        position: Int,
        type: SetType,
        isTopSet: Boolean = position == 0,
        myoKind: MyoRepKind? = if (type == SetType.MyoRep) MyoRepKind.RepFloor else null,
        repMin: Int = 8,
        repMax: Int = 12,
        rir: Int = 1,
    ) = SetSlot(
        slotId = id,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = type,
        isTopSet = isTopSet,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rir,
        restSeconds = 120,
        myoRepKind = myoKind,
    )

    private fun structure(slots: List<SetSlot>) =
        WorkoutStructureProjection(
            workoutId = WorkoutId("w-1"),
            workoutName = "Test",
            lifts =
                listOf(
                    WorkoutLiftProjection(
                        workoutLiftId = workoutLiftId,
                        liftId = liftId,
                        liftName = "Bench",
                        progressionScheme = ProgressionScheme.Double,
                        slots = slots,
                    ),
                ),
        )

    @Test
    @DisplayName("auto-generated mini cannot change type")
    fun miniBlocked() {
        val activation = slot(activationId, 0, SetType.MyoRep, isTopSet = true)
        val mini = slot(miniId, 1, SetType.MyoRep, isTopSet = false)
        val slots = listOf(activation, mini)
        assertFalse(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots = slots,
                setIndex = 1,
                kind = LiveSetKind.Standard,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
            ),
        )
        val result =
            LiveSetTypeOverlayOps.applySetKind(
                structure = structure(listOf(activation)),
                overlay = WorkoutOverlay(addedSlots = listOf(mini)),
                slotId = miniId,
                newKind = LiveSetKind.Standard,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
                liftDefaultRestSeconds = 120,
            )
        assertNull(result)
    }

    @Test
    @DisplayName("demoting myo activation discards minis and keeps activation log")
    fun demoteMyoDiscardsMinis() {
        val activation = slot(activationId, 0, SetType.MyoRep, isTopSet = true)
        val mini = slot(miniId, 1, SetType.MyoRep, isTopSet = false)
        val std = slot(standardId, 2, SetType.Standard, isTopSet = false)
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(mini),
                loggedSets =
                    mapOf(
                        activationId to
                            LoggedSetValue(
                                activationId,
                                Weight(100.0, Weight.WeightUnit.Lb),
                                reps = 12,
                                rir = 1,
                            ),
                        miniId to
                            LoggedSetValue(
                                miniId,
                                Weight(100.0, Weight.WeightUnit.Lb),
                                reps = 5,
                                rir = 0,
                            ),
                    ),
            )
        assertTrue(
            LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
                structure = structure(listOf(activation, std)),
                overlay = overlay,
                slotId = activationId,
                newKind = LiveSetKind.Standard,
            ),
        )
        val next =
            LiveSetTypeOverlayOps.applySetKind(
                structure = structure(listOf(activation, std)),
                overlay = overlay,
                slotId = activationId,
                newKind = LiveSetKind.Standard,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
                liftDefaultRestSeconds = 120,
            )
        assertNotNull(next)
        requireNotNull(next)
        assertTrue(miniId in next.deletedSlotIds)
        assertFalse(miniId in next.loggedSets)
        assertTrue(activationId in next.loggedSets)
        val shape = next.slotShapeOverrides[activationId]
        assertEquals(SetType.Standard, shape?.setType)
    }

    @Test
    @DisplayName("floor to match discards floor minis and materializes match backoffs")
    fun floorToMatchDiscardsMinisAndAddsBackoffs() {
        val activation = slot(activationId, 0, SetType.MyoRep, isTopSet = true)
        val mini = slot(miniId, 1, SetType.MyoRep, isTopSet = false)
        val overlay = WorkoutOverlay(addedSlots = listOf(mini))
        val next =
            LiveSetTypeOverlayOps.applySetKind(
                structure = structure(listOf(activation)),
                overlay = overlay,
                slotId = activationId,
                newKind = LiveSetKind.MyoRepMatch,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
                liftDefaultRestSeconds = 120,
            )
        assertNotNull(next)
        requireNotNull(next)
        assertTrue(miniId in next.deletedSlotIds, "floor minis must not become match minis")
        assertEquals(MyoRepKind.RepMatching, next.slotShapeOverrides[activationId]?.myoRepKind)
        // Default 2 match backoffs as overlay adds (mini removed).
        val matchBackoffs =
            next.addedSlots.filter {
                it.myoRepKind == MyoRepKind.RepMatching && !it.slotId.let { id ->
                    id is SetSlotId.Overlay && id.instanceId.value.startsWith("mini-")
                }
            }
        assertEquals(2, matchBackoffs.size, "default match backoffs materialize")
        // Floor → Match discards floor minis (confirm when open minis exist).
        assertTrue(
            LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
                structure = structure(listOf(activation)),
                overlay = overlay,
                slotId = activationId,
                newKind = LiveSetKind.MyoRepMatch,
            ),
        )
        assertEquals(
            listOf(miniId),
            LiveSetTypeOverlayOps.slotsDiscardedOnSetKindChange(
                structure = structure(listOf(activation)),
                overlay = overlay,
                slotId = activationId,
                newKind = LiveSetKind.MyoRepMatch,
            ),
        )
    }

    @Test
    @DisplayName("enter Match with trailing Standard keeps backoffs contiguous after activation")
    fun enterMatchWithTrailingStandardInsertsBackoffsBeforeStandard() {
        val activation = slot(activationId, 0, SetType.Standard, isTopSet = true)
        val trailing = slot(standardId, 1, SetType.Standard, isTopSet = false)
        val next =
            LiveSetTypeOverlayOps.applySetKind(
                structure = structure(listOf(activation, trailing)),
                overlay = WorkoutOverlay(),
                slotId = activationId,
                newKind = LiveSetKind.MyoRepMatch,
                scheme = ProgressionScheme.Double,
                loadType = LoadType.Weighted,
                liftDefaultRestSeconds = 120,
            )
        assertNotNull(next)
        requireNotNull(next)
        val ordered =
            LiveWorkoutOverlayOps.visibleSlotsForLift(
                structure(listOf(activation, trailing)),
                next,
                workoutLiftId,
            )
        assertEquals(4, ordered.size, "act + 2 backoffs + trailing standard")
        assertEquals(activationId, ordered[0].slotId)
        assertTrue(
            ordered[1].slotId is SetSlotId.Overlay &&
                (ordered[1].slotId as SetSlotId.Overlay).instanceId.value.startsWith("match-bo-"),
        )
        assertTrue(
            ordered[2].slotId is SetSlotId.Overlay &&
                (ordered[2].slotId as SetSlotId.Overlay).instanceId.value.startsWith("match-bo-"),
        )
        assertEquals(standardId, ordered[3].slotId)
        assertEquals(
            2,
            com.stratum.domain.workout.myorep.MyoMatchStructure.matchBackoffCount(ordered, 0),
        )
    }

    @Test
    @DisplayName("drop set not allowed on first set, after myo, or linear")
    fun dropPlacementRules() {
        val s0 = slot(activationId, 0, SetType.Standard, isTopSet = true)
        val s1 = slot(standardId, 1, SetType.Standard, isTopSet = false)
        val slots = listOf(s0, s1)
        assertFalse(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots, 0, LiveSetKind.DropSet,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
        assertTrue(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots, 1, LiveSetKind.DropSet,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
        assertFalse(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots, 1, LiveSetKind.DropSet,
                ProgressionScheme.Linear, LoadType.Weighted,
            ),
        )

        val myoThenStd =
            listOf(
                slot(activationId, 0, SetType.MyoRep, isTopSet = true),
                slot(standardId, 1, SetType.Standard, isTopSet = false),
            )
        assertFalse(
            LiveSetTypeOverlayOps.canSelectSetKind(
                myoThenStd, 1, LiveSetKind.DropSet,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
    }

    @Test
    @DisplayName("second myo activation allowed after existing myo")
    fun multipleMyoAllowed() {
        val slots =
            listOf(
                slot(activationId, 0, SetType.MyoRep, isTopSet = true),
                slot(standardId, 1, SetType.Standard, isTopSet = false),
            )
        assertTrue(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots, 1, LiveSetKind.MyoRepFloor,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
        assertTrue(
            LiveSetTypeOverlayOps.canSelectSetKind(
                slots, 1, LiveSetKind.MyoRepMatch,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
    }

    @Test
    @DisplayName("drop chain after standard drop is allowed; drop after myo is not")
    fun dropChainAndMyoPredecessor() {
        val afterDrop =
            listOf(
                slot(activationId, 0, SetType.Standard, isTopSet = true),
                slot(standardId, 1, SetType.DropSet, isTopSet = false, repMin = 0, repMax = 0, rir = 0),
                slot(SetSlotId.Template(LiftSetId("ls-3")), 2, SetType.Standard, isTopSet = false),
            )
        assertTrue(
            LiveSetTypeOverlayOps.canSelectSetKind(
                afterDrop, 2, LiveSetKind.DropSet,
                ProgressionScheme.Double, LoadType.Weighted,
            ),
        )
    }
}

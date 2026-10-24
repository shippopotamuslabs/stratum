package com.stratum.core.data.healthconnect

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HealthConnectExerciseMapperTest {

    private val sessionStart = Instant.parse("2026-08-10T10:00:00Z")
    private val sessionEnd = Instant.parse("2026-08-10T11:00:00Z")

    @Nested
    @DisplayName("mapSegmentType")
    inner class MapSegmentType {
        @Test
        fun mapsCommonStrengthNames() {
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
                HealthConnectExerciseMapper.mapSegmentType("Barbell Bench Press"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
                HealthConnectExerciseMapper.mapSegmentType("Conventional Deadlift"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
                HealthConnectExerciseMapper.mapSegmentType("Back Squat"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
                HealthConnectExerciseMapper.mapSegmentType("Weighted Pull-Up"),
            )
        }

        @Test
        fun mapsSpecificityOrder() {
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS,
                HealthConnectExerciseMapper.mapSegmentType("Barbell Overhead Press"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
                HealthConnectExerciseMapper.mapSegmentType("Dumbbell Shoulder Press"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
                HealthConnectExerciseMapper.mapSegmentType("Lying Leg Curl"),
            )
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
                HealthConnectExerciseMapper.mapSegmentType("Ez-Bar Curl"),
            )
        }

        @Test
        fun rowingMachineFallsBackToCompatibleWeightlifting() {
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
                HealthConnectExerciseMapper.mapSegmentType("Rowing Machine"),
            )
        }

        @Test
        fun mappedTypesAreCompatibleWithWeightliftingSession() {
            val sampleNames = listOf(
                "Barbell Bench Press",
                "Conventional Deadlift",
                "Back Squat",
                "Barbell Overhead Press",
                "Dumbbell Shoulder Press",
                "Lat Pulldown",
                "Leg Press",
                "Lying Leg Curl",
                "Leg Extension",
                "Hanging Leg Raise",
                "Hip Thrust",
                "Weighted Pull-Up",
                "Walking Lunge",
                "Plank",
                "Crunch",
                "Sit-Up",
                "Back Extension",
                "Kettlebell Swing",
                "Dumbbell Front Raise",
                "Front Raise",
                "Dumbbell Lateral Raise",
                "Lateral Raise",
                "Dumbbell Row",
                "Ez-Bar Curl",
                "Triceps Extension",
                "Burpee",
                "Cable Face Pull",
                "Rowing Machine",
            )
            sampleNames.forEach { name ->
                val type = HealthConnectExerciseMapper.mapSegmentType(name)
                assertTrue(
                    ExerciseSegment.isSegmentTypeCompatibleWithSessionType(
                        segmentType = type,
                        sessionType = HealthConnectExerciseMapper.SESSION_EXERCISE_TYPE,
                    ),
                    "incompatible segment for '$name' -> $type",
                )
            }
        }

        @Test
        fun unknownNamesFallBackToWeightlifting() {
            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
                HealthConnectExerciseMapper.mapSegmentType("Cable Face Pull"),
            )
        }
    }

    @Nested
    @DisplayName("rirToRpe")
    inner class RirToRpe {
        @Test
        fun convertsRirToBorgScale() {
            assertEquals(8f, HealthConnectExerciseMapper.rirToRpe(2))
            assertEquals(10f, HealthConnectExerciseMapper.rirToRpe(0))
            assertEquals(0f, HealthConnectExerciseMapper.rirToRpe(12))
        }

        @Test
        fun nullRirOmitsRpe() {
            assertNull(HealthConnectExerciseMapper.rirToRpe(null))
        }
    }

    @Nested
    @DisplayName("toMass")
    inner class ToMass {
        @Test
        fun convertsLbAndKg() {
            val lb = HealthConnectExerciseMapper.toMass(Weight(225.0, Weight.WeightUnit.Lb))
            val kg = HealthConnectExerciseMapper.toMass(Weight(100.0, Weight.WeightUnit.Kg))
            assertEquals(225.0, lb.inPounds, 0.001)
            assertEquals(100.0, kg.inKilograms, 0.001)
        }
    }

    @Nested
    @DisplayName("buildSegments")
    inner class BuildSegments {
        @Test
        @DisplayName("builds one segment per set with reps, weight, setIndex, RPE, and type")
        fun buildsPerSetSegments() {
            val session = completedSession()
            val sets = listOf(
                loggedSet(
                    id = "set-1",
                    exercise = "Bench Press",
                    setIndex = 0,
                    reps = 8,
                    weightLb = 205.0,
                    rir = 2,
                    completedAt = Instant.parse("2026-08-10T10:15:00Z"),
                ),
                loggedSet(
                    id = "set-2",
                    exercise = "Bench Press",
                    setIndex = 1,
                    reps = 7,
                    weightLb = 205.0,
                    rir = 1,
                    completedAt = Instant.parse("2026-08-10T10:18:00Z"),
                ),
                loggedSet(
                    id = "set-3",
                    exercise = "Cable Face Pull",
                    setIndex = 0,
                    reps = 15,
                    weightLb = 40.0,
                    rir = null,
                    completedAt = Instant.parse("2026-08-10T10:40:00Z"),
                ),
            )

            val segments = HealthConnectExerciseMapper.buildSegments(
                session,
                sets,
                sessionEndFallback = sessionEnd,
            )

            assertEquals(3, segments.size)
            assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, segments[0].segmentType)
            assertEquals(8, segments[0].repetitions)
            assertEquals(0, segments[0].setIndex)
            assertEquals(8f, segments[0].rateOfPerceivedExertion)
            assertEquals(205.0, segments[0].weight!!.inPounds, 0.001)

            assertEquals(1, segments[1].setIndex)
            assertEquals(9f, segments[1].rateOfPerceivedExertion)

            assertEquals(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
                segments[2].segmentType,
            )
            assertNull(segments[2].rateOfPerceivedExertion)
        }

        @Test
        @DisplayName("segments stay inside session bounds with start before end and no overlap")
        fun segmentsAreOrderedInsideSession() {
            val session = completedSession()
            val sets = listOf(
                loggedSet(
                    id = "a",
                    exercise = "Squat",
                    setIndex = 0,
                    reps = 5,
                    weightLb = 315.0,
                    completedAt = Instant.parse("2026-08-10T10:10:00Z"),
                ),
                loggedSet(
                    id = "b",
                    exercise = "Squat",
                    setIndex = 1,
                    reps = 5,
                    weightLb = 315.0,
                    // Same completion instant forces collision handling
                    completedAt = Instant.parse("2026-08-10T10:10:00Z"),
                ),
                loggedSet(
                    id = "c",
                    exercise = "Deadlift",
                    setIndex = 0,
                    reps = 3,
                    weightLb = 405.0,
                    completedAt = Instant.parse("2026-08-10T10:10:05Z"),
                ),
            )

            val segments = HealthConnectExerciseMapper.buildSegments(
                session,
                sets,
                sessionEndFallback = sessionEnd,
            )

            assertEquals(3, segments.size)
            segments.forEach { segment ->
                assertTrue(!segment.startTime.isBefore(sessionStart))
                assertTrue(!segment.endTime.isAfter(sessionEnd))
                assertTrue(segment.startTime.isBefore(segment.endTime))
            }
            for (index in 1 until segments.size) {
                assertTrue(
                    !segments[index].startTime.isBefore(segments[index - 1].endTime),
                    "segment $index starts before previous end",
                )
            }
        }

        @Test
        @DisplayName("prefers 30s ending at completedAt when space allows")
        fun prefersDefaultDurationEndingAtCompletedAt() {
            val session = completedSession()
            val completedAt = Instant.parse("2026-08-10T10:15:00Z")
            val sets = listOf(
                loggedSet(
                    id = "solo",
                    exercise = "Squat",
                    setIndex = 0,
                    reps = 5,
                    weightLb = 225.0,
                    completedAt = completedAt,
                ),
            )

            val segments = HealthConnectExerciseMapper.buildSegments(
                session,
                sets,
                sessionEndFallback = sessionEnd,
            )

            assertEquals(1, segments.size)
            assertEquals(completedAt.minusSeconds(30), segments[0].startTime)
            assertEquals(completedAt, segments[0].endTime)
        }

        @Test
        fun invertedSessionBoundsFailFast() {
            // STR-166: the inverted shape is now unrepresentable — TrainingSession's
            // constructor invariant rejects endedAt before startedAt, so the fail-fast
            // happens at construction (the mapper's own require stays as a second guard).
            assertThrows<IllegalArgumentException> {
                val inverted = completedSession().copy(
                    startedAt = sessionEnd,
                    endedAt = sessionStart,
                )
                HealthConnectExerciseMapper.buildSegments(
                    inverted,
                    listOf(
                        loggedSet(
                            id = "x",
                            exercise = "Squat",
                            setIndex = 0,
                            reps = 1,
                            weightLb = 135.0,
                            completedAt = sessionStart,
                        ),
                    ),
                    sessionEndFallback = sessionStart,
                )
            }
        }

        @Test
        @DisplayName("many sets at session end stay non-overlapping and build a valid record")
        fun denseTimestampsAtSessionEndAreValid() {
            val session = completedSession()
            val sets = (0 until 40).map { index ->
                loggedSet(
                    id = "dense-$index",
                    exercise = "Squat",
                    setIndex = index,
                    reps = 5,
                    weightLb = 315.0,
                    completedAt = sessionEnd,
                )
            }

            val segments = HealthConnectExerciseMapper.buildSegments(
                session,
                sets,
                sessionEndFallback = sessionEnd,
            )

            assertTrue(segments.isNotEmpty())
            segments.forEach { segment ->
                assertTrue(segment.startTime.isBefore(segment.endTime))
                assertTrue(!segment.startTime.isBefore(sessionStart))
                assertTrue(!segment.endTime.isAfter(sessionEnd))
            }
            for (index in 1 until segments.size) {
                assertTrue(
                    !segments[index].startTime.isBefore(segments[index - 1].endTime),
                    "overlap at $index",
                )
            }

            // Constructing the SDK record is the real validation boundary.
            ExerciseSessionRecord(
                startTime = sessionStart,
                startZoneOffset = ZoneOffset.UTC,
                endTime = sessionEnd,
                endZoneOffset = ZoneOffset.UTC,
                exerciseType = HealthConnectExerciseMapper.SESSION_EXERCISE_TYPE,
                title = "Dense",
                notes = "notes",
                segments = segments,
                metadata = Metadata.activelyRecorded(
                    device = Device(type = Device.TYPE_PHONE),
                    clientRecordId = "dense-session",
                ),
            )
        }

        @Test
        fun emptySetsYieldNoSegments() {
            val segments = HealthConnectExerciseMapper.buildSegments(
                completedSession(),
                emptyList(),
                sessionEndFallback = sessionEnd,
            )
            assertTrue(segments.isEmpty())
        }
    }

    private fun completedSession(): TrainingSession =
        TrainingSession(
            id = TrainingSessionId("session-1"),
            programId = null,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = sessionStart,
            endedAt = sessionEnd,
            sourceApp = null,
            workoutNameSnapshot = "Push Workout A",
        )

    private fun loggedSet(
        id: String,
        exercise: String,
        setIndex: Int,
        reps: Int,
        weightLb: Double,
        completedAt: Instant,
        rir: Int? = null,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId("session-1"),
            exerciseNameRaw = exercise,
            liftId = LiftId("lift-$id"),
            setIndex = setIndex,
            weight = Weight(weightLb, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            completedAt = completedAt,
            liftContext = LoggedLiftContext(exercise),
            isPersonalRecord = false,
        )
}

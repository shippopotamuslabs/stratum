package com.stratum.core.data.healthconnect

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Mass
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import java.time.Duration
import java.time.Instant

/**
 * Maps Stratum training logs onto Health Connect [ExerciseSegment] fields.
 * Pure helpers — no [androidx.health.connect.client.HealthConnectClient] I/O.
 */
object HealthConnectExerciseMapper {

    /** Session type all synced workouts use — segment types must be compatible with this. */
    const val SESSION_EXERCISE_TYPE: Int = ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING

    private val defaultSegmentDuration: Duration = Duration.ofSeconds(30)
    private val minSegmentDuration: Duration = Duration.ofMillis(1)

    fun buildSegments(
        session: TrainingSession,
        sets: List<LoggedSet>,
        sessionEndFallback: Instant,
    ): List<ExerciseSegment> {
        if (sets.isEmpty()) return emptyList()

        val sessionStart = session.startedAt
        val sessionEnd = session.endedAt ?: sessionEndFallback
        require(sessionStart.isBefore(sessionEnd)) {
            "session startTime must be before endTime for Health Connect segments"
        }

        val ordered = sets.sortedWith(compareBy({ it.completedAt }, { it.setIndex }))
        val windows = allocateSegmentWindows(ordered, sessionStart, sessionEnd)
        // If the session cannot fit every set, keep as many leading segments as possible.
        return ordered.take(windows.size).zip(windows) { set, window ->
            ExerciseSegment(
                startTime = window.start,
                endTime = window.end,
                segmentType = mapSegmentType(set.exerciseNameRaw),
                repetitions = set.reps,
                weight = toMass(set.weight),
                setIndex = set.setIndex,
                rateOfPerceivedExertion = rirToRpe(set.rir),
            )
        }
    }

    fun mapSegmentType(exerciseNameRaw: String): Int {
        val name = exerciseNameRaw.lowercase()
        val mapped =
            SEGMENT_TYPE_RULES
                .firstOrNull { rule -> rule.matches(name) }
                ?.segmentType
                ?: ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        return if (
            ExerciseSegment.isSegmentTypeCompatibleWithSessionType(
                segmentType = mapped,
                sessionType = SESSION_EXERCISE_TYPE,
            )
        ) {
            mapped
        } else {
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        }
    }

    fun toMass(weight: Weight): Mass =
        when (weight.unit) {
            Weight.WeightUnit.Kg -> Mass.kilograms(weight.amount)
            Weight.WeightUnit.Lb -> Mass.pounds(weight.amount)
        }

    fun rirToRpe(rir: Int?): Float? {
        if (rir == null) return null
        return (10 - rir).toFloat().coerceIn(0f, 10f)
    }

    /**
     * Single-pass windows for ordered sets. Prefers ~[defaultSegmentDuration] ending at
     * [LoggedSet.completedAt], never starting before the previous segment's end, never outside
     * the session. Stops early if the session has no room for another legal window.
     */
    internal fun allocateSegmentWindows(
        orderedSets: List<LoggedSet>,
        sessionStart: Instant,
        sessionEnd: Instant,
    ): List<SegmentWindow> {
        if (orderedSets.isEmpty()) return emptyList()

        val windows = ArrayList<SegmentWindow>(orderedSets.size)
        var cursor = sessionStart
        for (set in orderedSets) {
            val window = nextSegmentWindow(cursor, set.completedAt, sessionEnd) ?: break
            windows.add(window)
            cursor = window.end
        }
        return windows
    }

    private fun nextSegmentWindow(
        cursor: Instant,
        completedAt: Instant,
        sessionEnd: Instant,
    ): SegmentWindow? {
        val earliestEnd = cursor.plus(minSegmentDuration)
        if (earliestEnd.isAfter(sessionEnd)) return null

        val preferredEnd = clamp(completedAt, earliestEnd, sessionEnd)
        var end = preferredEnd
        var start = maxInstant(cursor, end.minus(defaultSegmentDuration))
        if (!start.isBefore(end)) {
            start = cursor
            end = earliestEnd
        }
        if (!start.isBefore(end) || end.isAfter(sessionEnd)) return null
        return SegmentWindow(start = start, end = end)
    }

    private fun maxInstant(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

    private fun clamp(value: Instant, min: Instant, max: Instant): Instant =
        when {
            value.isBefore(min) -> min
            value.isAfter(max) -> max
            else -> value
        }

    data class SegmentWindow(
        val start: Instant,
        val end: Instant,
    )

    private data class SegmentTypeRule(
        val segmentType: Int,
        val predicate: (String) -> Boolean,
    ) {
        fun matches(name: String): Boolean = predicate(name)
    }

    private fun containsAny(name: String, vararg needles: String): Boolean =
        needles.any { name.contains(it) }

    /**
     * First match wins — put more specific rules (e.g. barbell shoulder press) before generic ones.
     */
    private val SEGMENT_TYPE_RULES: List<SegmentTypeRule> =
        listOf(
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS) {
                containsAny(it, "bench press", "benchpress")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT) {
                containsAny(it, "deadlift")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT) {
                containsAny(it, "squat")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS) {
                containsAny(it, "barbell shoulder press", "overhead press", "ohp") ||
                    (it.contains("shoulder press") && it.contains("barbell"))
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS) {
                containsAny(it, "shoulder press", "military press")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN) {
                containsAny(it, "lat pulldown", "lat pull-down", "lat pull down", "pulldown")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS) {
                containsAny(it, "leg press")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL) {
                containsAny(it, "leg curl", "hamstring curl")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION) {
                containsAny(it, "leg extension")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE) {
                containsAny(it, "leg raise")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST) {
                containsAny(it, "hip thrust")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP) {
                containsAny(it, "pull-up", "pull up", "pullup", "chin-up", "chin up")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE) {
                containsAny(it, "lunge")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK) {
                containsAny(it, "plank")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH) {
                containsAny(it, "crunch")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP) {
                containsAny(it, "sit-up", "sit up", "situp")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION) {
                containsAny(it, "back extension", "hyperextension")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING) {
                containsAny(it, "kettlebell swing")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE) {
                containsAny(it, "front raise") && it.contains("dumbbell")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE) {
                containsAny(it, "front raise")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE) {
                containsAny(it, "lateral raise") && it.contains("dumbbell")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE) {
                containsAny(it, "lateral raise", "side raise")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW) {
                containsAny(it, "dumbbell row", "db row")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL) {
                containsAny(it, "arm curl", "bicep curl", "biceps curl") ||
                    (it.contains("curl") && !it.contains("leg"))
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION) {
                containsAny(it, "tricep", "triceps") && it.contains("extension")
            },
            SegmentTypeRule(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE) {
                containsAny(it, "burpee")
            },
        )
}

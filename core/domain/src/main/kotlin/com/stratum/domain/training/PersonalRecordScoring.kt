package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion

/**
 * Comparable PR metric for a logged set.
 *
 * Weighted and assisted sets (positive load magnitude) use RIR-adjusted e1RM.
 * Zero-load (bodyweight) sets use effective reps because e1RM of 0 is always 0.
 */
sealed interface PersonalRecordScore {
    data class LoadedE1rm(val lb: Double) : PersonalRecordScore
    data class BodyweightReps(val effectiveReps: Int) : PersonalRecordScore
}

/**
 * Single PR / session-best ranking. When any loaded set exists, the max e1RM wins so a
 * bodyweight warmup or backoff cannot steal the record. Otherwise max effective reps.
 */
object PersonalRecordScoring {
    fun estimatedOneRmLb(weight: Weight, reps: Int, rir: Int?): Double? =
        when (val scored = score(weight, reps, rir)) {
            is PersonalRecordScore.LoadedE1rm -> scored.lb
            is PersonalRecordScore.BodyweightReps, null -> null
        }

    fun score(weight: Weight, reps: Int, rir: Int?): PersonalRecordScore? {
        if (reps <= 0) return null
        val effectiveReps = reps + (rir ?: 0)
        if (effectiveReps <= 0) return null
        val loadMagnitudeLb = WeightConversion.loadMagnitudeLb(weight)
        return if (loadMagnitudeLb > 0.0) {
            PersonalRecordScore.LoadedE1rm(
                EstimatedOneRm.calculate(loadMagnitudeLb, effectiveReps),
            )
        } else {
            PersonalRecordScore.BodyweightReps(effectiveReps)
        }
    }

    fun score(set: LoggedSet): PersonalRecordScore? =
        if (set.liftId == null) null else score(set.weight, set.reps, set.rir)

    fun withComputedEstimatedOneRm(set: LoggedSet): LoggedSet {
        val e1Rm =
            if (set.liftId != null && set.reps > 0) {
                estimatedOneRmLb(set.weight, set.reps, set.rir)
            } else {
                null
            }
        return set.copy(estimatedOneRm = e1Rm)
    }

    /**
     * Stamps e1RM and marks exactly one PR in [sets] (loaded max e1RM if any loaded set
     * exists, else max bodyweight effective reps; later [LoggedSet.completedAt] on ties).
     */
    fun withPersonalRecords(sets: List<LoggedSet>): List<LoggedSet> {
        val stamped =
            sets.map { set ->
                withComputedEstimatedOneRm(set).copy(isPersonalRecord = false)
            }
        val winnerId = bestPersonalRecordSet(stamped)?.id
        return stamped.map { set ->
            set.copy(isPersonalRecord = set.id == winnerId)
        }
    }

    /**
     * Per-pair PR assignment (STR-123): PR identity is lift + equipment, so each
     * (liftId, equipmentId) pool gets its own winner via [withPersonalRecords].
     * Legacy null-equipment sets compete in their own `(liftId, null)` pool. Sets
     * without a liftId keep flags down and e1RM null (never PR-eligible).
     * Headline lift PRs stay "max across equipment" — [bestPersonalRecordSet] over a
     * lift's full history is unaffected by how flags partition into pairs.
     */
    fun withPersonalRecordsPerPair(sets: List<LoggedSet>): List<LoggedSet> {
        val (unidentified, identified) = sets.partition { it.liftId == null }
        return identified
            .groupBy { it.liftId to it.equipmentId }
            .values
            .flatMap(::withPersonalRecords) +
            unidentified.map { it.copy(isPersonalRecord = false, estimatedOneRm = null) }
    }

    /**
     * Best set in [sets]. Null if none are eligible.
     *
     * Loaded family wins whenever any positive-load set is present so a bodyweight
     * warmup/backoff cannot outrank weighted work. All-zero-load lists use max
     * effective reps. Ties go to the later [LoggedSet.completedAt].
     */
    fun bestPersonalRecordSet(sets: List<LoggedSet>): LoggedSet? {
        val scored = sets.mapNotNull { set -> score(set)?.let { set to it } }
        if (scored.isEmpty()) return null
        val loaded = scored.filter { it.second is PersonalRecordScore.LoadedE1rm }
        val pool = loaded.ifEmpty { scored }
        return pool.maxWithOrNull(
            compareBy<Pair<LoggedSet, PersonalRecordScore>> { metricValue(it.second) }
                .thenBy { it.first.completedAt },
        )?.first
    }

    private fun metricValue(score: PersonalRecordScore): Double =
        when (score) {
            is PersonalRecordScore.LoadedE1rm -> score.lb
            is PersonalRecordScore.BodyweightReps -> score.effectiveReps.toDouble()
        }
}

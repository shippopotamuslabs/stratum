package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.temporal.ChronoUnit
import com.stratum.domain.program.generation.selection.scoreLift

object AnchorLiftSelector {
    fun select(
        muscle: MuscleGroup,
        epochs: List<Epoch>,
        input: ResponseExtractionInput,
    ): LiftId? {
        if (epochs.isEmpty()) return null

        val cleanSets = TrainingSignalCleaner.clean(input)
        val candidateLifts =
            input.lifts.filter { muscle in it.primaryMuscles }.map { it.id }.toSet()
        if (candidateLifts.isEmpty()) return null

        val referenceInstant = epochs.maxOf { it.end }
        val scores =
            candidateLifts.mapNotNull { liftId ->
                scoreLift(
                    liftId = liftId,
                    muscle = muscle,
                    epochs = epochs,
                    cleanSets = cleanSets,
                    input = input,
                    referenceInstant = referenceInstant,
                )
            }
        if (scores.isEmpty()) return null

        val maxSessions = scores.maxOf { it.sessionCount }
        val sessionLeaders = scores.filter { it.sessionCount == maxSessions }
        if (muscle in input.specializeMuscles && sessionLeaders.size > 1) {
            sessionLeaders.filter { it.liftId in input.provenLiftIds }
                .maxWithOrNull(anchorLiftComparator(input, muscle))
                ?.liftId
                ?.let { return it }
        }
        return scores.maxWithOrNull(anchorLiftComparator(input, muscle))?.liftId
    }

    private data class AnchorLiftScore(
        val liftId: LiftId,
        val sessionCount: Int,
        val weekCount: Int,
        val score: Double,
        val provenPriority: Boolean,
    )

    private fun scoreLift(
        liftId: LiftId,
        muscle: MuscleGroup,
        epochs: List<Epoch>,
        cleanSets: List<CleanTrainingSet>,
        input: ResponseExtractionInput,
        referenceInstant: java.time.Instant,
    ): AnchorLiftScore? {
        val workingSets =
            epochs.flatMap { epoch ->
                WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, liftId)
            }
        if (workingSets.isEmpty()) return null

        val sessionIds = workingSets.map { it.sourceSet.sessionId }.distinct()
        if (sessionIds.size < MIN_ANCHOR_SESSIONS) return null

        val weekStarts =
            workingSets.map { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) }.distinct()
        if (weekStarts.size < MIN_ANCHOR_WEEKS) return null

        val recencyWeight =
            sessionIds.sumOf { sessionId ->
                val sessionInstant =
                    input.sessions.firstOrNull { it.id == sessionId }?.startedAt
                        ?: workingSets.first { it.sourceSet.sessionId == sessionId }.sourceSet.completedAt
                val daysAgo = ChronoUnit.DAYS.between(sessionInstant, referenceInstant).coerceAtLeast(0)
                1.0 / (1.0 + daysAgo / RE_EXTRACT_MIN_INTERVAL_DAYS.toDouble())
            }
        val provenPriority = muscle in input.specializeMuscles && liftId in input.provenLiftIds
        return AnchorLiftScore(
            liftId = liftId,
            sessionCount = sessionIds.size,
            weekCount = weekStarts.size,
            score = sessionIds.size * recencyWeight,
            provenPriority = provenPriority,
        )
    }

    private fun anchorLiftComparator(
        input: ResponseExtractionInput,
        muscle: MuscleGroup,
    ): Comparator<AnchorLiftScore> =
        compareByDescending<AnchorLiftScore> { it.sessionCount }
            .thenByDescending { it.weekCount }
            .thenByDescending { it.score }
            .thenByDescending { it.liftId in input.provenLiftIds && muscle in input.specializeMuscles }
            .thenBy { it.liftId.value }
}

package com.stratum.domain.importing

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

data class ImportedHistoryChunk(
    val sourceLabel: String,
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
)

/**
 * Combines per-file normalized history into one export, namespacing session and set ids so
 * collisions across independent exports (e.g. both using id `"1"`) cannot clobber each other.
 */
class MergeImportedHistoryUseCase @Inject constructor() {
    operator fun invoke(
        chunks: List<ImportedHistoryChunk>,
    ): Pair<List<TrainingSession>, List<LoggedSet>> {
        val mergedSessions = mutableListOf<TrainingSession>()
        val mergedSets = mutableListOf<LoggedSet>()
        chunks.forEachIndexed { index, chunk ->
            if (chunk.sets.isEmpty()) {
                return@forEachIndexed
            }
            val slug = fileSlug(index)
            val sessionIdMap =
                chunk.sessions.associate { session ->
                    session.id.value to namespacedId(slug, session.id.value)
                }
            mergedSessions +=
                chunk.sessions.map { session ->
                    session.copy(
                        id = TrainingSessionId(sessionIdMap.getValue(session.id.value)),
                    )
                }
            mergedSets +=
                chunk.sets.map { set ->
                    val namespacedSessionId =
                        sessionIdMap[set.sessionId.value]
                            ?: namespacedId(slug, set.sessionId.value)
                    set.copy(
                        id = LoggedSetId(namespacedId(slug, set.id.value)),
                        sessionId = TrainingSessionId(namespacedSessionId),
                    )
                }
        }
        val dedupedSets = dedupeOverlappingSets(mergedSets)
        require(dedupedSets.isNotEmpty()) {
            "Import contained no valid training sets."
        }
        return mergedSessions to dedupedSets
    }

    /**
     * Drops duplicate working sets that appear in multiple exports (e.g. Strong + MacroFactor
     * covering the same sessions). Keeps the first occurrence per fingerprint.
     */
    internal fun dedupeOverlappingSets(sets: List<LoggedSet>): List<LoggedSet> {
        val seen = linkedSetOf<String>()
        return sets.filter { set ->
            seen.add(setFingerprint(set))
        }
    }

    private fun setFingerprint(set: LoggedSet): String {
        val minuteBucket = ChronoUnit.MINUTES.between(Instant.EPOCH, set.completedAt)
        val weightKg =
            when (set.weight.unit) {
                Weight.WeightUnit.Kg -> set.weight.amount
                Weight.WeightUnit.Lb -> set.weight.amount * LB_TO_KG
            }
        val weightKey = (weightKg * 100).roundToInt()
        val exerciseKey = ImportExerciseNameNormalizer.normalizeForMatching(set.exerciseNameRaw)
        return "$minuteBucket|$exerciseKey|$weightKey|${set.reps}"
    }

    private fun fileSlug(index: Int): String = "f$index"

    private fun namespacedId(slug: String, originalId: String): String = "$slug::$originalId"

    private companion object {
        private const val LB_TO_KG = 0.45359237
    }
}

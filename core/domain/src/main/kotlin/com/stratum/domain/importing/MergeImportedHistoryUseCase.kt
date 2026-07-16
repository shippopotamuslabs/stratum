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
    /**
     * When true (Stratum export), keep session/set ids verbatim instead of `fN::` namespacing.
     * Later preserve-stable chunks that collide with earlier ids are namespaced only for the
     * colliding rows.
     */
    val preserveStableIds: Boolean = false,
)

/**
 * Result of combining per-file normalized history.
 *
 * [stableIdSessionIds] are post-merge session ids that originated from chunks with
 * [ImportedHistoryChunk.preserveStableIds] (Stratum). Callers use this for per-session
 * merge dedupe (id-only for Stratum, content fingerprint for third-party).
 */
data class MergedImportedHistory(
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    val stableIdSessionIds: Set<String> = emptySet(),
)

/**
 * Combines per-file normalized history into one export, namespacing session and set ids so
 * collisions across independent exports (e.g. both using id `"1"`) cannot clobber each other.
 */
class MergeImportedHistoryUseCase @Inject constructor() {
    operator fun invoke(
        chunks: List<ImportedHistoryChunk>,
    ): MergedImportedHistory {
        val mergedSessions = mutableListOf<TrainingSession>()
        val mergedSets = mutableListOf<LoggedSet>()
        val usedSessionIds = mutableSetOf<String>()
        val usedSetIds = mutableSetOf<String>()
        val stableIdSessionIds = mutableSetOf<String>()
        chunks.forEachIndexed { index, chunk ->
            if (chunk.sets.isEmpty()) {
                return@forEachIndexed
            }
            val slug = fileSlug(index)
            if (chunk.preserveStableIds) {
                val sessionIdMap = mutableMapOf<String, String>()
                for (session in chunk.sessions) {
                    val original = session.id.value
                    val resolved =
                        if (original in usedSessionIds) {
                            namespacedId(slug, original)
                        } else {
                            original
                        }
                    sessionIdMap[original] = resolved
                    usedSessionIds += resolved
                    stableIdSessionIds += resolved
                }
                mergedSessions +=
                    chunk.sessions.map { session ->
                        session.copy(
                            id = TrainingSessionId(sessionIdMap.getValue(session.id.value)),
                        )
                    }
                mergedSets +=
                    chunk.sets.map { set ->
                        val sessionId =
                            sessionIdMap[set.sessionId.value]
                                ?: set.sessionId.value
                        val setId =
                            if (set.id.value in usedSetIds) {
                                namespacedId(slug, set.id.value)
                            } else {
                                set.id.value
                            }
                        usedSetIds += setId
                        set.copy(
                            id = LoggedSetId(setId),
                            sessionId = TrainingSessionId(sessionId),
                        )
                    }
            } else {
                val sessionIdMap =
                    chunk.sessions.associate { session ->
                        session.id.value to namespacedId(slug, session.id.value)
                    }
                mergedSessions +=
                    chunk.sessions.map { session ->
                        val newId = sessionIdMap.getValue(session.id.value)
                        usedSessionIds += newId
                        session.copy(id = TrainingSessionId(newId))
                    }
                mergedSets +=
                    chunk.sets.map { set ->
                        val namespacedSessionId =
                            sessionIdMap[set.sessionId.value]
                                ?: namespacedId(slug, set.sessionId.value)
                        val newSetId = namespacedId(slug, set.id.value)
                        usedSetIds += newSetId
                        set.copy(
                            id = LoggedSetId(newSetId),
                            sessionId = TrainingSessionId(namespacedSessionId),
                        )
                    }
            }
        }
        val dedupedSets = dedupeOverlappingSets(mergedSets)
        require(dedupedSets.isNotEmpty()) {
            "Import contained no valid training sets."
        }
        return MergedImportedHistory(
            sessions = mergedSessions,
            sets = dedupedSets,
            stableIdSessionIds = stableIdSessionIds,
        )
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

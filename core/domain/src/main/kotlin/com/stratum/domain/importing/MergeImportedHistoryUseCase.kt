package com.stratum.domain.importing

import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import javax.inject.Inject

data class ImportedHistoryChunk(
    val sourceLabel: String,
    val sessions: List<HistoricalSession>,
    val sets: List<LoggedSet>,
)

/**
 * Combines per-file normalized history into one export, namespacing session and set ids so
 * collisions across independent exports (e.g. both using id `"1"`) cannot clobber each other.
 */
class MergeImportedHistoryUseCase @Inject constructor() {
    operator fun invoke(
        chunks: List<ImportedHistoryChunk>,
    ): Pair<List<HistoricalSession>, List<LoggedSet>> {
        val mergedSessions = mutableListOf<HistoricalSession>()
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
                        id = HistoricalSessionId(sessionIdMap.getValue(session.id.value)),
                    )
                }
            mergedSets +=
                chunk.sets.map { set ->
                    val namespacedSessionId =
                        sessionIdMap[set.sessionId.value]
                            ?: namespacedId(slug, set.sessionId.value)
                    set.copy(
                        id = LoggedSetId(namespacedId(slug, set.id.value)),
                        sessionId = HistoricalSessionId(namespacedSessionId),
                    )
                }
        }
        require(mergedSets.isNotEmpty()) {
            "Import contained no valid training sets."
        }
        return mergedSessions to mergedSets
    }

    private fun fileSlug(index: Int): String = "f$index"

    private fun namespacedId(slug: String, originalId: String): String = "$slug::$originalId"
}

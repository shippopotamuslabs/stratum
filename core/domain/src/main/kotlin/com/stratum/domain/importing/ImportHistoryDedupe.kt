package com.stratum.domain.importing

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import java.time.ZoneId

internal object ImportHistoryDedupe {
    /**
     * Filters incoming sessions/sets for merge-skip-duplicates.
     *
     * Always drops exact session/set id collisions first.
     *
     * Then, per-session content rules:
     * - Session ids in [stableIdSessionIds] (Stratum chunks): id-only skip — no fingerprint.
     * - Remaining sessions: ThirdParty date+content fingerprint skip.
     *
     * When [stableIdSessionIds] is empty and [sourceKind] is [ImportDedupeSourceKind.StratumStableIds],
     * all id-kept sessions use id-only skip (legacy pure-Stratum path).
     */
    fun filterForMerge(
        incomingSessions: List<TrainingSession>,
        incomingSets: List<LoggedSet>,
        existingSessions: List<TrainingSession>,
        existingSets: List<LoggedSet>,
        zone: ZoneId,
        sourceKind: ImportDedupeSourceKind,
        stableIdSessionIds: Set<String> = emptySet(),
    ): FilteredImportHistory {
        val existingSessionIds = existingSessions.map { it.id.value }.toSet()
        val existingSetIds = existingSets.map { it.id.value }.toSet()

        // Always drop exact id collisions first.
        val idKeptSessions = incomingSessions.filter { it.id.value !in existingSessionIds }
        val idDroppedSessionIds =
            incomingSessions.map { it.id.value }.toSet() - idKeptSessions.map { it.id.value }.toSet()
        val idKeptSets =
            incomingSets.filter { set ->
                set.id.value !in existingSetIds && set.sessionId.value !in idDroppedSessionIds
            }

        // Pure Stratum batch with no per-session set: id-only for everything.
        val pureStratumFallback =
            sourceKind == ImportDedupeSourceKind.StratumStableIds && stableIdSessionIds.isEmpty()
        if (pureStratumFallback) {
            return dropEmptySessions(
                incomingSessions = incomingSessions,
                incomingSets = incomingSets,
                keptSessions = idKeptSessions,
                keptSets = idKeptSets,
            )
        }

        // Mixed / third-party: fingerprint only non-stable sessions.
        // Sessions in stableIdSessionIds keep id-only treatment after the id pass above.
        val existingFingerprints =
            existingSessions
                .mapNotNull { session ->
                    fingerprintForSession(
                        session = session,
                        sets = existingSets,
                        zone = zone,
                    )
                }
                .toSet()

        val contentKeptSessions = mutableListOf<TrainingSession>()
        val contentDroppedSessionIds = mutableSetOf<String>()
        for (session in idKeptSessions) {
            if (session.id.value in stableIdSessionIds) {
                contentKeptSessions += session
                continue
            }
            val fp =
                fingerprintForSession(
                    session = session,
                    sets = idKeptSets,
                    zone = zone,
                )
            if (fp != null && fp in existingFingerprints) {
                contentDroppedSessionIds += session.id.value
            } else {
                contentKeptSessions += session
            }
        }
        val finalSets =
            idKeptSets.filter { set -> set.sessionId.value !in contentDroppedSessionIds }

        return dropEmptySessions(
            incomingSessions = incomingSessions,
            incomingSets = incomingSets,
            keptSessions = contentKeptSessions,
            keptSets = finalSets,
        )
    }

    /**
     * After set-id filtering, sessions may retain zero sets (ghost empty sessions).
     * Drop those and count them as skipped so merge never inserts empty session shells.
     */
    private fun dropEmptySessions(
        incomingSessions: List<TrainingSession>,
        incomingSets: List<LoggedSet>,
        keptSessions: List<TrainingSession>,
        keptSets: List<LoggedSet>,
    ): FilteredImportHistory {
        val sessionIdsWithSets = keptSets.map { it.sessionId.value }.toSet()
        val nonEmptySessions = keptSessions.filter { it.id.value in sessionIdsWithSets }
        return FilteredImportHistory(
            sessions = nonEmptySessions,
            sets = keptSets,
            skippedSessionCount = incomingSessions.size - nonEmptySessions.size,
            skippedSetCount = incomingSets.size - keptSets.size,
        )
    }

    /**
     * Full content fingerprint for third-party merge:
     * device-local calendar date of [session].startedAt in [zone], plus multiset of
     * `(normalize(exerciseNameRaw), weight.amount, weight.unit, reps, rir)`.
     *
     * Returns null when the session has no sets (cannot content-match).
     */
    private fun fingerprintForSession(
        session: TrainingSession,
        sets: List<LoggedSet>,
        zone: ZoneId,
    ): String? {
        val setKeys =
            sets
                .filter { it.sessionId.value == session.id.value }
                .map { set ->
                    val exercise =
                        ImportExerciseNameNormalizer.normalizeForMatching(set.exerciseNameRaw)
                    val rirKey = set.rir?.toString() ?: "null"
                    "$exercise|${set.weight.amount}|${set.weight.unit.name}|${set.reps}|$rirKey"
                }
                .sorted()
        if (setKeys.isEmpty()) return null
        val localDate = session.startedAt.atZone(zone).toLocalDate()
        return "$localDate|${setKeys.joinToString(";")}"
    }
}

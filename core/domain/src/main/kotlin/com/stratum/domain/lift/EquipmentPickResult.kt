package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

/**
 * Process-scoped one-shot result channel for Manage Equipment selection mode.
 *
 * Callers mark awaiting and snapshot [currentGeneration], then collect [result] only when
 * a published [Pick.generation] is greater than that snapshot. Singleton (not
 * activity-retained): generations only move forward, so a stale pick from a previous
 * activity can never satisfy a freshly-armed consumer.
 */
@Singleton
class EquipmentPickResult @Inject constructor() {
    data class Pick(
        val generation: Long,
        val equipmentId: EquipmentId?,
    )

    private val generationCounter = AtomicLong(0)
    private val _result = MutableStateFlow<Pick?>(null)
    val result: StateFlow<Pick?> = _result.asStateFlow()

    /** Monotonic generation of the latest published pick (0 when none). */
    fun currentGeneration(): Long = _result.value?.generation ?: 0L

    fun publish(equipmentId: EquipmentId?) {
        val gen = generationCounter.incrementAndGet()
        _result.value = Pick(generation = gen, equipmentId = equipmentId)
    }

    /**
     * Returns the latest pick when its generation is strictly greater than [lastSeenGen],
     * otherwise null.
     */
    fun consumeIfNewer(lastSeenGen: Long): Pick? {
        val pick = _result.value ?: return null
        return if (pick.generation > lastSeenGen) pick else null
    }

    /**
     * Picks published strictly after [generation], for selection flows that arm-then-navigate.
     * Collectors get one-shot semantics without reimplementing the generation guard.
     */
    fun picksAfter(generation: Long): Flow<Pick> =
        result.filterNotNull().filter { it.generation > generation }
}

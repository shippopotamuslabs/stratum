package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.domain.lift.EquipmentRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [EquipmentRepository] for domain and ViewModel tests.
 *
 * Optional [liftRepository] keeps [createCustomEquipmentAndAssignLifts] state-based: fork
 * tests can assert lift equipment ids without a separate Room transaction.
 */
class FakeEquipmentRepository(
    initial: List<Equipment> = emptyList(),
    private val liftRepository: FakeLiftRepository? = null,
) : EquipmentRepository {
    private val rows = MutableStateFlow(initial)
    private val liftRefs = mutableMapOf<EquipmentId, Int>()

    /** Lift ids assigned via [createCustomEquipmentAndAssignLifts], keyed by new equipment. */
    val assignedLiftsByEquipment: Map<EquipmentId, List<LiftId>>
        get() = assignedLifts.toMap()

    private val assignedLifts = mutableMapOf<EquipmentId, List<LiftId>>()

    /**
     * When true, the next write path throws once (then clears). Used to exercise apply-failure
     * restore on the workout-complete proposal session.
     */
    var failNextWrite: Boolean = false

    /** Test hook: mark how many lifts reference an equipment id (for delete rules). */
    fun setLiftReferenceCount(id: EquipmentId, count: Int) {
        liftRefs[id] = count
    }

    val current: List<Equipment> get() = rows.value

    fun seed(equipment: List<Equipment>) {
        rows.value = equipment
    }

    override fun observeEquipment(): Flow<List<Equipment>> = rows

    override suspend fun getEquipment(id: EquipmentId): Equipment? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun addCustomEquipment(
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    ): EquipmentId {
        throwIfFailNextWrite()
        val id = EquipmentId(UUID.randomUUID().toString())
        val resolvedTiers = tiers.ifEmpty { defaultSingleTiers() }
        rows.value = rows.value + Equipment(
            id = id,
            name = name,
            microIncrements = micro,
            incrementTiers = resolvedTiers,
            isCustom = true,
        )
        return id
    }

    override suspend fun createCustomEquipmentAndAssignLifts(
        name: String,
        tiers: List<IncrementTier>,
        liftIds: List<LiftId>,
    ): EquipmentId {
        // Fail flag checked inside addCustomEquipment (single write boundary).
        val id = addCustomEquipment(name, tiers)
        val distinct = liftIds.distinct()
        assignedLifts[id] = distinct
        setLiftReferenceCount(id, distinct.size)
        val lifts = liftRepository
        if (lifts != null) {
            lifts.equipmentNamesById = lifts.equipmentNamesById + (id to name)
            for (liftId in distinct) {
                val existing = lifts.currentLifts.firstOrNull { it.id == liftId }
                lifts.setLiftEquipment(
                    liftId,
                    id,
                    existing?.loadType ?: LoadType.Weighted,
                )
            }
        }
        return id
    }

    override suspend fun renameEquipment(id: EquipmentId, name: String) {
        throwIfFailNextWrite()
        rows.value = rows.value.map { row ->
            if (row.id == id) row.copy(name = name) else row
        }
    }

    override suspend fun setIncrementTiers(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight>?,
    ) {
        throwIfFailNextWrite()
        rows.value = rows.value.map { row ->
            if (row.id == id) {
                row.copy(incrementTiers = tiers, microIncrements = micro ?: row.microIncrements)
            } else {
                row
            }
        }
    }

    private fun throwIfFailNextWrite() {
        if (failNextWrite) {
            failNextWrite = false
            error("Simulated equipment write failure")
        }
    }

    override suspend fun deleteCustomEquipment(id: EquipmentId): Boolean {
        val row = rows.value.firstOrNull { it.id == id } ?: return false
        if (!row.isCustom) return false
        if ((liftRefs[id] ?: 0) > 0) return false
        rows.value = rows.value.filterNot { it.id == id }
        return true
    }

    override suspend fun findOrCreateByName(name: String): EquipmentId {
        val trimmed = name.trim()
        rows.value
            .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?.let { return it.id }
        val key = EquipmentNameMatching.normalizeKey(trimmed)
        if (key.isNotEmpty()) {
            rows.value
                .firstOrNull { EquipmentNameMatching.normalizeKey(it.name) == key }
                ?.let { return it.id }
        }
        EquipmentNameMatching.seedIdFromFreeText(trimmed)?.let { seedId ->
            rows.value.firstOrNull { it.id == seedId }?.let { return it.id }
        }
        return addCustomEquipment(trimmed)
    }

    private fun defaultSingleTiers(): List<IncrementTier> {
        val lb = Weight.WeightUnit.Lb
        return listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    }
}

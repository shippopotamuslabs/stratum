package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.domain.lift.EquipmentRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [EquipmentRepository] for domain and ViewModel tests.
 */
class FakeEquipmentRepository(
    initial: List<Equipment> = emptyList(),
) : EquipmentRepository {
    private val rows = MutableStateFlow(initial)
    private val liftRefs = mutableMapOf<EquipmentId, Int>()

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

    override suspend fun addCustomEquipment(name: String, tiers: List<IncrementTier>): EquipmentId {
        val id = EquipmentId(UUID.randomUUID().toString())
        val resolvedTiers = tiers.ifEmpty { defaultSingleTiers() }
        rows.value = rows.value + Equipment(
            id = id,
            name = name,
            incrementTiers = resolvedTiers,
            isCustom = true,
        )
        return id
    }

    override suspend fun renameEquipment(id: EquipmentId, name: String) {
        rows.value = rows.value.map { row ->
            if (row.id == id) row.copy(name = name) else row
        }
    }

    override suspend fun setIncrementTiers(id: EquipmentId, tiers: List<IncrementTier>) {
        rows.value = rows.value.map { row ->
            if (row.id == id) row.copy(incrementTiers = tiers) else row
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

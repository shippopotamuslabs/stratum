package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.domain.lift.EquipmentRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.defaultSingleTiers
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [EquipmentRepository] for domain and ViewModel tests.
 *
 * Optional [liftRepository] keeps [createCustomEquipmentAndAssignLifts] state-based: fork
 * tests can assert lift equipment ids without a separate Room transaction.
 */
class FakeEquipmentRepository(
    initial: List<Equipment> = emptyList(),
    private val liftRepository: FakeLiftRepository? = null,
    private val programRepository: com.stratum.core.testing.program.FakeProgramRepository? = null,
) : EquipmentRepository {
    private val rows = MutableStateFlow(initial)

    /** Lift ids assigned via [createCustomEquipmentAndAssignLifts], keyed by new equipment. */
    val assignedLiftsByEquipment: Map<EquipmentId, List<LiftId>>
        get() = assignedLifts.toMap()

    private val assignedLifts = mutableMapOf<EquipmentId, List<LiftId>>()

    /**
     * When true, the next write path throws once (then clears). Used to exercise apply-failure
     * restore on the workout-complete proposal session.
     */
    var failNextWrite: Boolean = false

    val current: List<Equipment> get() = rows.value

    fun seed(equipment: List<Equipment>) {
        rows.value = equipment
    }

    override fun observeEquipment(): Flow<List<Equipment>> =
        rows.map { list -> list.filterNot { it.isDeleted } }

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

    override suspend fun renameAndSetIncrementTiers(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    ) {
        // Single write boundary: the fail flag throws before any state changes,
        // mirroring the Room transaction (name and tiers land together or not at all).
        throwIfFailNextWrite()
        rows.value = rows.value.map { row ->
            if (row.id == id) {
                row.copy(name = name, incrementTiers = tiers, microIncrements = micro)
            } else {
                row
            }
        }
    }

    private fun isValidReplacement(deletedId: EquipmentId, replacementId: EquipmentId): Boolean {
        val target = rows.value.firstOrNull { it.id == replacementId } ?: return false
        return !target.isDeleted && replacementId != deletedId &&
            replacementId !in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog
    }

    private fun throwIfFailNextWrite() {
        if (failNextWrite) {
            failNextWrite = false
            error("Simulated equipment write failure")
        }
    }

    override suspend fun deleteCustomEquipment(id: EquipmentId, replacementId: EquipmentId?): Boolean {
        val row = rows.value.firstOrNull { it.id == id } ?: return false
        if (id in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog) return false
        if (row.isDeleted) return true
        return performDelete(id, replacementId)
    }

    private fun performDelete(id: EquipmentId, replacementId: EquipmentId?): Boolean {
        val affectedLifts =
            liftRepository?.currentLifts?.filter { it.equipmentId == id }.orEmpty()
        val affectedSlots = programRepository?.countEquipmentSlots(id) ?: 0
        val hasPointers = affectedLifts.isNotEmpty() || affectedSlots > 0
        val candidates =
            rows.value.filter {
                !it.isDeleted && it.id != id &&
                    it.id !in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog
            }
        if (hasPointers && replacementId == null && candidates.isNotEmpty()) return false
        if (replacementId != null && !isValidReplacement(id, replacementId)) return false
        val effective =
            replacementId
                ?: if (hasPointers) com.stratum.domain.lift.SeedEquipmentIds.Bodyweight else null
        if (hasPointers && effective != null) {
            liftRepository?.repointEquipment(id, effective)
            programRepository?.repointEquipment(id, effective)
        }
        rows.value = rows.value.map { if (it.id == id) it.copy(isDeleted = true) else it }
        return true
    }

    override suspend fun findOrCreateByName(name: String): EquipmentId {
        val trimmed = name.trim()
        rows.value
            .firstOrNull { !it.isDeleted && it.name.equals(trimmed, ignoreCase = true) }
            ?.let { return it.id }
        val key = EquipmentNameMatching.normalizeKey(trimmed)
        if (key.isNotEmpty()) {
            rows.value
                .firstOrNull { !it.isDeleted && EquipmentNameMatching.normalizeKey(it.name) == key }
                ?.let { return it.id }
        }
        EquipmentNameMatching.seedIdFromFreeText(trimmed)?.let { seedId ->
            rows.value.firstOrNull { it.id == seedId && !it.isDeleted }?.let { return it.id }
        }
        return addCustomEquipment(trimmed)
    }
}

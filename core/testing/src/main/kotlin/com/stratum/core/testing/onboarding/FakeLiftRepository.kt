package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftCatalogNote
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.alignedWithEquipment
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.sanitizeLiftNoteBody
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory fake for [LiftRepository]. Defaults to an empty catalog and a `null` preset
 * choice, then `seed()` known lifts for the test. All five operations mutate the held state in
 * the same shape the Room implementation does, so feature/use-case tests observe realistic
 * transitions without a database.
 *
 * [applyPreset] mirrors the Room impl: it enables every held lift tagged with the preset.
 * [addCustomLift] mints a UUID id, marks the lift `isCustom = true`/`enabled = true`, and
 * gives it a single open-ended tier when no increment is supplied — matching the Room default.
 */
class FakeLiftRepository(
    initialLifts: List<Lift> = emptyList(),
    initialPreset: GymPreset? = null,
) : LiftRepository {
    private val lifts = MutableStateFlow(initialLifts)
    private val selectedPreset = MutableStateFlow(initialPreset)
    private val usedHistoricalImport = MutableStateFlow(false)
    /** Source of truth for catalog note lists; [Lift.pinnedNote] mirrors the first body. */
    private val catalogNotes = MutableStateFlow<Map<String, List<LiftCatalogNote>>>(emptyMap())
    /**
     * Serializes catalog-note-map check-then-write sequences (lazy-migrate,
     * replace), mirroring Room's write-lock semantics so concurrent first reads
     * converge on one migrated row instead of minting competing ids.
     */
    private val notesMutex = Mutex()

    /** Optional name lookup for equipment ids when [addCustomLift] only has a name. */
    var equipmentNamesById: Map<EquipmentId, String> = emptyMap()

    override fun observeLifts(): Flow<List<Lift>> = lifts

    override suspend fun getLiftsByIds(ids: Collection<LiftId>): List<Lift> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toSet()
        return lifts.value.filter { it.id in wanted }
    }

    override fun observeEnabledLifts(): Flow<List<Lift>> = lifts.map { rows -> rows.filter { it.enabled } }

    override fun observeLifts(isCustom: Boolean): Flow<List<Lift>> = lifts.map { rows -> rows.filter { it.isCustom == isCustom } }

    override fun observeLiftsContainingPattern(pattern: MovementPattern): Flow<List<Lift>> =
        lifts.map { rows -> rows.filter { pattern in it.movementPatterns } }

    override fun observeSelectedPreset(): Flow<GymPreset?> = selectedPreset

    override fun observeUsedHistoricalImport(): Flow<Boolean> = usedHistoricalImport

    override suspend fun applyPreset(preset: GymPreset) {
        lifts.value = lifts.value.map { lift ->
            if (preset in lift.presetTags) lift.copy(enabled = true) else lift
        }
    }

    override suspend fun setLiftEnabled(id: LiftId, enabled: Boolean) {
        lifts.value = lifts.value.map { lift ->
            if (lift.id == id) lift.copy(enabled = enabled) else lift
        }
    }

    override suspend fun enableLifts(ids: Set<LiftId>) {
        if (ids.isEmpty()) return
        enableLiftsInvocations += ids
        lifts.value = lifts.value.map { lift ->
            if (lift.id in ids) lift.copy(enabled = true) else lift
        }
    }

    /** Test hook: batches passed to [enableLifts]. */
    val enableLiftsInvocations = mutableListOf<Set<LiftId>>()

    override suspend fun setSelectedPreset(preset: GymPreset?) {
        selectedPreset.value = preset
        if (preset != null) usedHistoricalImport.value = false
    }

    override suspend fun setUsedHistoricalImport(used: Boolean) {
        usedHistoricalImport.value = used
        if (used) selectedPreset.value = null
    }

    /**
     * Last [equipmentCreateIncrement] values passed through [addCustomLift] (test hook for
     * import custom loading).
     */
    val lastEquipmentCreateIncrements = mutableListOf<Weight?>()

    override suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId {
        val id = LiftId(UUID.randomUUID().toString())
        lastEquipmentCreateIncrements += definition.equipmentCreateIncrement
        val equipmentId = definition.equipmentId
        val equipmentName =
            definition.equipmentName
                ?: equipmentId?.let { equipmentNamesById[it] }
        val lift =
            Lift(
                id = id,
                name = definition.name,
                movementPatterns = definition.movementPatterns,
                primaryMuscles = definition.primaryMuscles,
                secondaryMuscles = definition.secondaryMuscles,
                incrementTiers =
                    definition.increment?.let {
                        listOf(
                            IncrementTier(
                                Weight(0.0, it.unit),
                                null,
                                it,
                            ),
                        )
                    } ?: emptyList(),
                presetTags = emptySet(),
                equipmentId = equipmentId,
                equipmentName = equipmentName,
                // Fake has no equipment catalog tiers; bodyweight/null behave as fallback.
                inheritsFromEquipmentLattice = false,
                enabled = true,
                isCustom = true,
                muscleLengthBias = definition.muscleLengthBias,
                fatigueProfile = definition.fatigueProfile,
                loadType = definition.loadType,
                defaultRestSeconds = definition.defaultRestSeconds,
            )
        lifts.value = lifts.value + lift
        return id
    }

    /** Test hook: the current catalog snapshot. */
    val currentLifts: List<Lift> get() = lifts.value

    /** Test hook: the current preset choice. */
    val currentPreset: GymPreset? get() = selectedPreset.value

    /** Test hook: seed a known catalog (replaces wholesale). */
    fun seedLifts(lifts: List<Lift>) {
        this.lifts.value = lifts
    }

    /** Test hook: seed a known preset choice. */
    fun seedPreset(preset: GymPreset?) {
        selectedPreset.value = preset
    }

    /** Test hook: seed historical-import flag. */
    fun seedUsedHistoricalImport(used: Boolean) {
        usedHistoricalImport.value = used
    }

    /** Test hook: current historical-import flag. */
    val currentUsedHistoricalImport: Boolean get() = usedHistoricalImport.value

    override suspend fun setLiftEquipment(id: LiftId, equipmentId: EquipmentId?, loadType: LoadType) {
        lifts.value = lifts.value.map { lift ->
            if (lift.id == id) {
                lift.copy(
                    equipmentId = equipmentId,
                    equipmentName = equipmentId?.let { equipmentNamesById[it] },
                    loadType = loadType,
                    // Changing equipment re-inherits the new defaults.
                    incrementTiers = emptyList(),
                    inheritsFromEquipmentLattice = false,
                    microIncrements = emptyList(),
                )
            } else {
                lift
            }
        }
    }

    override suspend fun updateLiftMuscles(
        id: LiftId,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
    ) {
        lifts.value = lifts.value.map { lift ->
            if (lift.id == id && lift.isCustom) {
                lift.copy(
                    primaryMuscles = primaryMuscles,
                    secondaryMuscles = secondaryMuscles,
                )
            } else {
                lift
            }
        }
    }

    override suspend fun updateCustomLiftDetails(id: LiftId, details: CustomLiftDetails) {
        lifts.value = lifts.value.map { lift ->
            if (lift.id == id && lift.isCustom) {
                lift.copy(
                    movementPatterns = details.movementPatterns,
                    muscleLengthBias = details.muscleLengthBias,
                    fatigueProfile = details.fatigueProfile,
                    loadType = details.loadType,
                    equipmentId = details.equipmentId,
                    equipmentName = details.equipmentId?.let { equipmentNamesById[it] },
                )
            } else {
                lift
            }
        }
    }

    override suspend fun setDefaultRestSeconds(id: LiftId, restSeconds: Int?) {
        lifts.value = lifts.value.map { lift ->
            if (lift.id == id) lift.copy(defaultRestSeconds = restSeconds) else lift
        }
    }

    override suspend fun setPinnedNote(id: LiftId, note: String?) {
        val clean = sanitizeLiftNoteBody(note)
        if (clean == null) {
            setPinnedNotes(id, emptyList())
        } else {
            val existingId = getPinnedNotes(id).firstOrNull()?.id
            setPinnedNotes(id, listOf(CatalogNoteInput(id = existingId, body = clean)))
        }
    }

    override suspend fun getPinnedNotes(id: LiftId): List<LiftCatalogNote> {
        // Match Room: a missing lift row yields no notes, never orphaned map entries.
        if (lifts.value.none { it.id == id }) return emptyList()
        notesMutex.withLock {
            catalogNotes.value[id.value]?.let { return it }
            // Sanitizer parity with Room: trim and enforce the length cap before
            // creating the row; a blank legacy pin migrates to nothing.
            val clean = sanitizeLiftNoteBody(lifts.value.firstOrNull { it.id == id }?.pinnedNote)
                ?: return emptyList()
            // Client lazy-migrate parity: persist the legacy pin as the
            // position-0 row (written inline — routing through the setter would
            // recurse via its id-retention read). The mirror already matches,
            // so it is untouched.
            val migrated =
                listOf(
                    LiftCatalogNote(
                        id = LiftCatalogNoteId(UUID.randomUUID().toString()),
                        liftId = id,
                        body = clean,
                        position = 0,
                        updatedAt = Instant.now(),
                    ),
                )
            catalogNotes.value = catalogNotes.value + (id.value to migrated)
            return migrated
        }
    }

    override suspend fun setPinnedNotes(id: LiftId, notes: List<CatalogNoteInput>) {
        // Match Room: writing notes for a missing lift touches zero rows.
        if (lifts.value.none { it.id == id }) return
        notesMutex.withLock {
            val now = Instant.now()
            // Read the map directly: the getter lazy-migrates legacy pins, which
            // would mint a throwaway row just to compute id retention here.
            val currentIds = catalogNotes.value[id.value]?.map { it.id }?.toSet().orEmpty()
            val minted =
                notes.mapNotNull { input ->
                    sanitizeLiftNoteBody(input.body)?.let { body -> input to body }
                }.mapIndexed { index, (input, body) ->
                    LiftCatalogNote(
                        id = input.id?.takeIf { it in currentIds }
                            ?: LiftCatalogNoteId(UUID.randomUUID().toString()),
                        liftId = id,
                        body = body,
                        position = index,
                        updatedAt = now,
                    )
                }
            catalogNotes.value = catalogNotes.value + (id.value to minted)
            val first = minted.firstOrNull()?.body
            lifts.value = lifts.value.map { lift ->
                if (lift.id == id) lift.copy(pinnedNote = first) else lift
            }
        }
    }

    /** STR-105 sweep: re-point lifts off deleted equipment (loadType aligned). */
    fun repointEquipment(deletedId: EquipmentId, replacementId: EquipmentId) {
        lifts.value = lifts.value.map { lift ->
            if (lift.equipmentId == deletedId) {
                lift.copy(
                    equipmentId = replacementId,
                    equipmentName = equipmentNamesById[replacementId],
                    loadType = lift.loadType.alignedWithEquipment(replacementId),
                )
            } else {
                lift
            }
        }
    }

    override suspend fun deleteLifts(ids: Collection<LiftId>) {
        if (ids.isEmpty()) return
        val remove = ids.toSet()
        lifts.value = lifts.value.filterNot { it.id in remove }
        // Notes live in a side map: drop them with the lift so deleted lifts
        // expose no orphaned notes (matches Room cascade behavior).
        val removeValues = remove.map { it.value }.toSet()
        catalogNotes.value = catalogNotes.value.filterKeys { it !in removeValues }
    }
}

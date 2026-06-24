package com.stratum.core.testing.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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

    override fun observeLifts(): Flow<List<Lift>> = lifts

    override fun observeSelectedPreset(): Flow<GymPreset?> = selectedPreset

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

    override suspend fun setSelectedPreset(preset: GymPreset?) {
        selectedPreset.value = preset
    }

    override suspend fun addCustomLift(
        name: String,
        allowWeightChangeBetweenSets: Boolean,
        increment: Weight?,
    ): LiftId {
        val id = LiftId(UUID.randomUUID().toString())
        val lift = Lift(
            id = id,
            name = name,
            movementPattern = com.stratum.domain.lift.MovementPattern.LegPush,
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = allowWeightChangeBetweenSets,
            incrementTiers = increment?.let {
                listOf(
                    com.stratum.domain.lift.IncrementTier(
                        Weight(0.0, it.unit),
                        null,
                        it,
                    ),
                )
            } ?: emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = true,
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
}

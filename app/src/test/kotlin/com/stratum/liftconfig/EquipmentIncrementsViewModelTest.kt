package com.stratum.liftconfig

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveEquipmentAffectedLiftsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.SetEquipmentIncrementTiersUseCase
import com.stratum.domain.lift.ValidateIncrementTiersUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class EquipmentIncrementsViewModelTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private lateinit var subject: EquipmentIncrementsViewModel

    @BeforeEach
    fun setup() {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = openFive,
                    isCustom = false,
                ),
            ),
        )
        fakeLiftRepository.seedLifts(
            listOf(
                lift("lat-pulldown", "Lat pulldown", usesDefaults = true, enabled = true),
                lift("cable-row", "Cable row", usesDefaults = true, enabled = false),
                lift("face-pull", "Face pull", usesDefaults = false, enabled = true),
                lift("tricep-pushdown", "Tricep pushdown", usesDefaults = true, enabled = true),
                lift("cable-crunch", "Cable crunch", usesDefaults = true, enabled = true),
            ),
        )
        subject =
            EquipmentIncrementsViewModel(
                observeEquipmentCatalog = ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
                observeEquipmentAffectedLifts =
                    ObserveEquipmentAffectedLiftsUseCase(fakeLiftRepository),
                setEquipmentIncrementTiers =
                    SetEquipmentIncrementTiersUseCase(
                        fakeEquipmentRepository,
                        ValidateIncrementTiersUseCase(),
                        FakeLoggingPort(),
                    ),
                validateIncrementTiers = ValidateIncrementTiersUseCase(),
                observeWeightDisplayPreferences =
                    com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase(
                        com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository(),
                    ),
            )
    }

    @Test
    @DisplayName(
        "Given inheritors including disabled when initialized then count and names exclude overrides",
    )
    fun affectedCountIncludesDisabledExcludesOverrides() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()

        subject.uiState.test {
            val state = awaitItemWith { it.equipmentName == "Cables" && it.affectedLiftCount > 0 }
            // lat, cable-row (disabled), tricep, cable-crunch — face-pull has override
            assertEquals(4, state.affectedLiftCount)
            assertEquals(
                listOf("Cable crunch", "Cable row", "Lat pulldown", "Tricep pushdown"),
                state.affectedLiftNames,
            )
            assertTrue(state.tiers.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("formatAffectsBanner helpers: preview limit is 3 names")
    fun affectedNamesOrderedForPreview() = runTest {
        subject.initialize(cableId.value)
        advanceUntilIdle()
        subject.uiState.test {
            val state = awaitItemWith { it.affectedLiftCount == 4 }
            assertEquals(4, state.affectedLiftNames.size)
            // First 3 alphabetical for "+1 more" UI path
            assertEquals(
                listOf("Cable crunch", "Cable row", "Lat pulldown"),
                state.affectedLiftNames.take(3),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<EquipmentIncrementsUiState>.awaitItemWith(
        predicate: (EquipmentIncrementsUiState) -> Boolean,
    ): EquipmentIncrementsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun lift(
        id: String,
        name: String,
        usesDefaults: Boolean,
        enabled: Boolean,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openFive,
            presetTags = emptySet(),
            equipmentId = cableId,
            equipmentName = "Cables",
            usesEquipmentDefaultIncrements = usesDefaults,
            enabled = enabled,
        )
}

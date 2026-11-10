package com.stratum.application.workout

import app.cash.turbine.test
import com.stratum.application.lift.ObserveEquipmentCatalogUseCase
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.training.ObserveCycleProgressUseCase
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveWorkoutSessionInputsUseCase] composition tests (STR-209): the
 * cycle-progress + lift-catalog + equipment-catalog combine moved out of
 * `WorkoutViewModel.startSession` must preserve the former pipeline — one
 * snapshot per emission carrying the latest triple.
 */
class ObserveWorkoutSessionInputsUseCaseTest {

    private val fakeProgramRepository = FakeProgramRepository()
    private val fakeTrainingLog = FakeTrainingLogRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private val fakeEquipmentRepository = FakeEquipmentRepository()

    private val subject =
        ObserveWorkoutSessionInputsUseCase(
            ObserveCycleProgressUseCase(fakeProgramRepository, fakeTrainingLog),
            ObserveLiftCatalogUseCase(fakeLiftRepository),
            ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
        )

    private fun lift(id: String, name: String): Lift = Lift(
        id = LiftId(id),
        name = name,
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun equipment(id: String, name: String): Equipment = Equipment(
        id = EquipmentId(id),
        name = name,
        incrementTiers = emptyList(),
    )

    @Test
    @DisplayName("Given empty sources when observed then snapshot carries empty catalogs")
    fun emptySourcesSnapshot() = runTest {
        subject().test {
            val snapshot = awaitItem()
            assertTrue(snapshot.lifts.isEmpty())
            assertTrue(snapshot.equipment.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given seeded catalogs when observed then snapshot carries progress and catalogs")
    fun seededCatalogsSnapshot() = runTest {
        fakeLiftRepository.seedLifts(listOf(lift("l-1", "Squat")))
        fakeEquipmentRepository.seed(listOf(equipment("barbell", "Olympic Barbell")))

        subject().test {
            val snapshot = awaitItem()
            assertEquals(listOf("Squat"), snapshot.lifts.map { it.name })
            assertEquals(listOf("Olympic Barbell"), snapshot.equipment.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given catalog change when observed then snapshot re-emits the latest triple")
    fun catalogChangeReemits() = runTest {
        fakeLiftRepository.seedLifts(listOf(lift("l-1", "Squat")))
        fakeEquipmentRepository.seed(listOf(equipment("barbell", "Olympic Barbell")))

        subject().test {
            val first = awaitItem()
            assertEquals(1, first.lifts.size)
            assertEquals(1, first.equipment.size)

            fakeLiftRepository.seedLifts(
                listOf(lift("l-1", "Squat"), lift("l-2", "Bench Press")),
            )
            val second = awaitItem()
            assertEquals(listOf("Squat", "Bench Press"), second.lifts.map { it.name })
            assertEquals(listOf("Olympic Barbell"), second.equipment.map { it.name })

            fakeEquipmentRepository.seed(
                listOf(
                    equipment("barbell", "Olympic Barbell"),
                    equipment("ssb", "Safety Squat Bar"),
                ),
            )
            val third = awaitItem()
            assertEquals(2, third.lifts.size)
            assertEquals(
                listOf("Olympic Barbell", "Safety Squat Bar"),
                third.equipment.map { it.name },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}

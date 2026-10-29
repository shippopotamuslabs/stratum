package com.stratum.application.lift

import app.cash.turbine.test
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern

class RankSwapCandidatesUseCaseTest {
    private lateinit var repository: FakeLiftRepository
    private lateinit var useCase: RankSwapCandidatesUseCase

    @BeforeEach
    fun setup() {
        repository = FakeLiftRepository()
        useCase = RankSwapCandidatesUseCase(repository)
    }

    @Test
    @DisplayName("Given enabled catalog when invoked then target is excluded from results")
    fun targetExcluded() = runTest {
        repository.seedLifts(listOf(BENCH_PRESS, INCLINE_PRESS, LEG_CURL))

        useCase(BENCH_PRESS.id).test {
            val candidates = awaitItem()
            assertTrue(candidates.none { it.lift.id == BENCH_PRESS.id })
            assertEquals(2, candidates.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given target bench press when ranked then incline press beats leg curl")
    fun similarFirst() = runTest {
        repository.seedLifts(listOf(BENCH_PRESS, INCLINE_PRESS, LEG_CURL))

        useCase(BENCH_PRESS.id).test {
            val candidates = awaitItem()
            assertEquals(INCLINE_PRESS.id, candidates.first().lift.id, "Incline press should rank first — same primary muscle + closer pattern")
            assertEquals(LEG_CURL.id, candidates.last().lift.id, "Leg curl should rank last — disjoint")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given disabled lifts in catalog when ranked then disabled lifts are excluded")
    fun disabledExcluded() = runTest {
        repository.seedLifts(listOf(BENCH_PRESS, INCLINE_PRESS, LEG_CURL.copy(enabled = false)))

        useCase(BENCH_PRESS.id).test {
            val candidates = awaitItem()
            assertTrue(candidates.none { it.lift.id == LEG_CURL.id })
            assertEquals(1, candidates.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given unknown target id when ranked then all enabled lifts score 0 and sort by mainstream then name")
    fun unknownTargetRanksByName() = runTest {
        repository.seedLifts(
            listOf(
                BENCH_PRESS.copy(mainstream = false, name = "Zottman Curl"),
                INCLINE_PRESS.copy(mainstream = true, name = "A-Press"),
                LEG_CURL.copy(mainstream = false, name = "Alpha Curl"),
            ),
        )

        useCase(LiftId("does-not-exist")).test {
            val candidates = awaitItem()
            // mainstream=true first, then alphabetical by name among non-mainstream
            assertEquals("A-Press", candidates.first().lift.name)
            assertEquals(0.0, candidates.first().similarity, "No target means zero similarity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given disabled target when ranked then similar enabled lifts still score by biomechanics")
    fun disabledTargetStillRanksSimilar() = runTest {
        repository.seedLifts(
            listOf(
                BENCH_PRESS.copy(enabled = false),
                INCLINE_PRESS,
                LEG_CURL,
            ),
        )

        useCase(BENCH_PRESS.id).test {
            val candidates = awaitItem()
            assertTrue(
                candidates.none { it.lift.id == BENCH_PRESS.id },
                "Disabled target must not appear as a swap candidate",
            )
            assertEquals(2, candidates.size)
            assertEquals(
                INCLINE_PRESS.id,
                candidates.first().lift.id,
                "Incline press should rank first even when target is recommendation-disabled",
            )
            assertTrue(
                candidates.first().similarity > candidates.last().similarity,
                "Disabled target must still drive non-zero similarity scoring",
            )
            assertTrue(
                candidates.first().similarity > 0.0,
                "Biomechanical similarity must be non-zero for a close enabled match",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given catalog update when collected then re-emits ranked list")
    fun reEmitsOnCatalogChange() = runTest {
        repository.seedLifts(listOf(BENCH_PRESS, INCLINE_PRESS))

        useCase(BENCH_PRESS.id).test {
            assertEquals(1, awaitItem().size)
            repository.seedLifts(listOf(BENCH_PRESS, INCLINE_PRESS, LEG_CURL))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val BENCH_PRESS = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = FatigueProfile.Medium,
        )
        val INCLINE_PRESS = Lift(
            id = LiftId("incline-press"),
            name = "Incline Dumbbell Press",
            movementPatterns = setOf(MovementPattern.InclinePress, MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = FatigueProfile.Medium,
        )
        val LEG_CURL = Lift(
            id = LiftId("leg-curl"),
            name = "Leg Curl",
            movementPatterns = setOf(MovementPattern.KneeFlexion),
            primaryMuscles = setOf(MuscleGroup.Hamstring),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = FatigueProfile.Low,
        )
    }
}

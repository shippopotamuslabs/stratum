package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.LiftTrainingInsights

@DisplayName("ObserveLiftTrainingInsightsUseCase: per-lift fatigue decay lookup")
class ObserveLiftTrainingInsightsUseCaseTest {

    @Test
    @DisplayName("returns a LiftId-keyed map of FatigueDecay from the repository")
    fun returnsKeyedFatigueDecayMap() = runTest {
        val liftA = LiftId("A")
        val liftB = LiftId("B")
        val repo = FakeLiftTrainingInsightsRepository(
            listOf(
                LiftTrainingInsights(
                    liftId = liftA,
                    estimatedOneRm = null,
                    fatigueDecaySlope = -2.5,
                    fatigueDecayIntercept = 0.0,
                    sessionCount = 5,
                    progressTrend = null,
                    lastPerformedAt = null,
                ),
                LiftTrainingInsights(
                    liftId = liftB,
                    estimatedOneRm = null,
                    fatigueDecaySlope = null,
                    fatigueDecayIntercept = null,
                    sessionCount = 1,
                    progressTrend = null,
                    lastPerformedAt = null,
                ),
            ),
        )
        val useCase = ObserveLiftTrainingInsightsUseCase(repo)

        val result = useCase()

        assertEquals(2, result.size)
        assertEquals(FatigueDecay(-2.5, 0.0), result[liftA])
        assertEquals(FatigueDecay(null, null), result[liftB])
    }

    @Test
    @DisplayName("empty repository yields an empty map")
    fun emptyRepositoryYieldsEmptyMap() = runTest {
        val useCase = ObserveLiftTrainingInsightsUseCase(FakeLiftTrainingInsightsRepository())
        assertTrue(useCase().isEmpty())
    }

    @Test
    @DisplayName("lifts without a detected slope surface as null in FatigueDecay")
    fun missingSlopeSurfacesAsNull() = runTest {
        val lift = LiftId("solo")
        val repo = FakeLiftTrainingInsightsRepository(
            listOf(
                LiftTrainingInsights(
                    liftId = lift,
                    estimatedOneRm = null,
                    fatigueDecaySlope = null,
                    fatigueDecayIntercept = null,
                    sessionCount = 2,
                    progressTrend = null,
                    lastPerformedAt = null,
                ),
            ),
        )
        val result = ObserveLiftTrainingInsightsUseCase(repo).invoke()
        assertNull(result[lift]?.slope)
        assertNull(result[lift]?.intercept)
    }

    @Test
    @DisplayName("duplicate liftIds keep the last entry (associate semantics)")
    fun duplicateLiftIdsKeepLast() = runTest {
        val lift = LiftId("dup")
        val repo = FakeLiftTrainingInsightsRepository(
            listOf(
                LiftTrainingInsights(
                    liftId = lift,
                    estimatedOneRm = null,
                    fatigueDecaySlope = -1.0,
                    fatigueDecayIntercept = 0.0,
                    sessionCount = 3,
                    progressTrend = null,
                    lastPerformedAt = null,
                ),
                LiftTrainingInsights(
                    liftId = lift,
                    estimatedOneRm = null,
                    fatigueDecaySlope = -3.0,
                    fatigueDecayIntercept = 1.0,
                    sessionCount = 5,
                    progressTrend = null,
                    lastPerformedAt = null,
                ),
            ),
        )
        val result = ObserveLiftTrainingInsightsUseCase(repo).invoke()
        assertEquals(1, result.size, "Duplicate liftId should collapse to one entry")
        assertEquals(FatigueDecay(-3.0, 1.0), result[lift], "Last entry should win (associate semantics)")
    }
}
package com.stratum.core.testing.training

import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.domain.importing.ExtractTrainingInsightsUseCase
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.LocalDerivedTrainingStages
import com.stratum.domain.training.RefreshWeeklySeriesUseCase
import com.stratum.domain.training.TrainingLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Builds [LocalDerivedTrainingStages] for unit tests with Unconfined default so fire-and-forget
 * finalize/history-edit refresh runs synchronously under [kotlinx.coroutines.test.runTest].
 */
fun localDerivedTrainingStages(
    trainingLog: TrainingLogRepository,
    liftRepository: LiftRepository = FakeLiftRepository(),
    persistence: FakeResponseProfilePersistencePort = FakeResponseProfilePersistencePort(),
    loggingPort: LoggingPort = FakeLoggingPort(),
    extractTrainingInsights: ExtractTrainingInsightsUseCase =
        ExtractTrainingInsightsUseCase(
            trainingLogRepository = trainingLog,
            liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
            supersetAffinityRepository = FakeSupersetAffinityRepository(),
            muscleVolumeBaselineRepository = FakeMuscleVolumeBaselineRepository(),
            importAnalysisMetadataRepository = FakeImportAnalysisMetadataRepository(),
            liftRepository = liftRepository,
        ),
    refreshWeeklySeries: RefreshWeeklySeriesUseCase =
        RefreshWeeklySeriesUseCase(trainingLog, liftRepository, persistence),
    dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
): LocalDerivedTrainingStages =
    LocalDerivedTrainingStages(
        extractTrainingInsights = extractTrainingInsights,
        refreshWeeklySeries = refreshWeeklySeries,
        loggingPort = loggingPort,
        defaultDispatcher = dispatcher,
    )

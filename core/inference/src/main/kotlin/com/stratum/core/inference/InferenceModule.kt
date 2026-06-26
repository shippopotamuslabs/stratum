package com.stratum.core.inference

import com.stratum.domain.importing.CustomLiftInferencePort
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportEquipmentInferencePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {
    @Binds
    @Singleton
    abstract fun bindHistoricalImportPort(impl: FirebaseHistoricalImportAdapter): HistoricalImportPort

    @Binds
    @Singleton
    abstract fun bindHistoricalImportRunner(impl: RunFullHistoricalImportUseCase): HistoricalImportRunnerPort

    @Binds
    @Singleton
    abstract fun bindCustomLiftInferencePort(
        impl: FirebaseCustomLiftInferenceAdapter,
    ): CustomLiftInferencePort

    @Binds
    @Singleton
    abstract fun bindImportEquipmentInferencePort(
        impl: FirebaseImportEquipmentInferenceAdapter,
    ): ImportEquipmentInferencePort
}

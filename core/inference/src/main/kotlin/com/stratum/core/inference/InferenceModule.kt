package com.stratum.core.inference

import com.stratum.domain.auth.AccountDeletionPort
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.featureflag.FeatureFlagRepository
import com.stratum.domain.importing.CustomLiftInferencePort
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportEquipmentInferencePort
import com.stratum.domain.importing.LiftNameEmbeddingPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.swap.LiftSwapPort
import com.stratum.domain.program.generation.synthesis.FlagGatedProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.importing.ProgramTemplateImportPort
import com.stratum.domain.subscription.PlayEntitlementSyncPort
import com.stratum.domain.telemetry.AnalyticsPort
import com.stratum.domain.telemetry.CrashTelemetryPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {
    @Binds
    @Singleton
    abstract fun bindAccountDeletionPort(impl: FirebaseAccountDeletionAdapter): AccountDeletionPort

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

    @Binds
    @Singleton
    abstract fun bindLiftNameEmbeddingPort(
        impl: FirebaseLiftNameEmbeddingAdapter,
    ): LiftNameEmbeddingPort

    @Binds
    @Singleton
    abstract fun bindLiftSwapPort(impl: FirebaseLiftSwapAdapter): LiftSwapPort

    companion object {
        /**
         * Wraps the cloud synthesis adapter in its kill-switch so flipping
         * `cloud_program_synthesis` to false fails fast without network use.
         */
        @Provides
        @Singleton
        fun provideProgramSynthesisPort(
            cloud: FirebaseProgramSynthesisAdapter,
            flags: FeatureFlagRepository,
        ): ProgramSynthesisPort =
            FlagGatedProgramSynthesisPort(
                delegate = cloud,
                flags = flags,
                gate = AppFeatureFlag.CLOUD_PROGRAM_SYNTHESIS,
            )
    }

    @Binds
    @Singleton
    abstract fun bindProgramTemplateImportPort(
        impl: FirebaseProgramTemplateImportAdapter,
    ): ProgramTemplateImportPort

    @Binds
    @Singleton
    abstract fun bindPlayEntitlementSyncPort(
        impl: FirebasePlayEntitlementSyncAdapter,
    ): PlayEntitlementSyncPort

    @Binds
    @Singleton
    abstract fun bindLoggingPort(impl: AndroidLoggingAdapter): LoggingPort

    @Binds
    @Singleton
    abstract fun bindCrashTelemetryPort(impl: AndroidCrashTelemetryAdapter): CrashTelemetryPort

    @Binds
    @Singleton
    abstract fun bindAnalyticsPort(impl: AndroidAnalyticsAdapter): AnalyticsPort
}

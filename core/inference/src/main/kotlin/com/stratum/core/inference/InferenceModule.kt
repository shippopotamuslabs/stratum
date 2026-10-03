package com.stratum.core.inference

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.auth.AccountDeletionPort
import com.stratum.domain.featureflag.AppFeatureFlag
import com.stratum.domain.featureflag.FeatureFlagRepository
import com.stratum.domain.importing.CustomLiftInferencePort
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportEquipmentInferencePort
import com.stratum.domain.importing.LiftNameEmbeddingPort
import com.stratum.domain.lift.IncrementScanPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.PendingClientLogStore
import com.stratum.domain.telemetry.TelemetryUploadPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    abstract fun bindIncrementScanPort(impl: FirebaseIncrementScanAdapter): IncrementScanPort

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

        /**
         * Tier-2 upload buffer (STR-27). Started from `StratumApplication.onCreate`.
         *
         * Owns a dedicated scope (not `@ApplicationScope`): the app scope's
         * exception handler is built from `ProcessErrorSafetyNet`, which needs
         * [LoggingPort], which forwards into this buffer — sharing that scope
         * would be a dependency cycle. Unexpected buffer failures go to
         * [AndroidLoggingAdapter] directly (Crashlytics-only, never re-enqueued).
         */
        @Provides
        @Singleton
        fun provideClientLogBuffer(
            upload: TelemetryUploadPort,
            pendingStore: PendingClientLogStore,
            @ApplicationContext context: Context,
            localLog: AndroidLoggingAdapter,
        ): ClientLogBuffer {
            val scope =
                CoroutineScope(
                    SupervisorJob() + Dispatchers.IO +
                        CoroutineExceptionHandler { _, throwable ->
                            localLog.error(
                                "ClientLogBuffer",
                                "Unhandled telemetry failure: ${throwable.message}",
                                throwable,
                            )
                        },
                )
            return ClientLogBuffer(
                scope = scope,
                upload = upload,
                pendingStore = pendingStore,
                appVersion =
                    resolveAppVersion(
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull(),
                    ),
            )
        }

        // No provideFirebaseAuth here: AuthProvidesModule in :core:data already binds
        // FirebaseAuth app-wide; a second binding fails the :app graph with
        // Dagger/DuplicateBindings.

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
    abstract fun bindLoggingPort(impl: ForwardingLoggingAdapter): LoggingPort

    @Binds
    @Singleton
    abstract fun bindTelemetryUploadPort(impl: FirebaseTelemetryUploadAdapter): TelemetryUploadPort

    @Binds
    @Singleton
    abstract fun bindCrashTelemetryPort(impl: AndroidCrashTelemetryAdapter): CrashTelemetryPort

    @Binds
    @Singleton
    abstract fun bindAnalyticsPort(impl: AndroidAnalyticsAdapter): AnalyticsPort
}

/**
 * Normalizes the user-visible version for the tier-2 upload envelope. The
 * PackageManager lookup happens at the call site inside runCatching so
 * telemetry metadata can never crash app start.
 */
internal fun resolveAppVersion(versionName: String?): String =
    versionName?.takeIf { it.isNotBlank() } ?: ClientLogBatch.APP_VERSION_UNKNOWN

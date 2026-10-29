package com.stratum.core.data.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.stratum.core.data.auth.FirebaseGoogleAuthManager
import com.stratum.application.auth.AuthManager
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.ProcessErrorSafetyNet
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(safetyNet: ProcessErrorSafetyNet): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate +
                safetyNet.applicationCoroutineExceptionHandler(),
        )

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideCredentialManager(
        @ApplicationContext context: Context,
    ): CredentialManager = CredentialManager.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindsModule {
    @Binds
    @Singleton
    abstract fun bindAuthManager(impl: FirebaseGoogleAuthManager): AuthManager
}

package com.stratum.di

import android.content.Context
import com.stratum.R
import com.stratum.core.infrastructure.auth.GoogleServerClientId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides Google Sign-In / Credential Manager server client id from the
 * google-services-generated [R.string.default_web_client_id].
 *
 * A static [R] reference keeps the string reachable under resource shrinking;
 * runtime [android.content.res.Resources.getIdentifier] does not.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthConfigModule {
    @Provides
    @Singleton
    @GoogleServerClientId
    fun provideGoogleServerClientId(
        @ApplicationContext context: Context,
    ): String = context.getString(R.string.default_web_client_id)
}

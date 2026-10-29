package com.stratum.core.data.di

import com.google.firebase.firestore.FirebaseFirestore
import com.stratum.core.data.sync.CloudSyncFeatureGate
import com.stratum.core.data.sync.DefaultCloudSyncFeatureGate
import com.stratum.core.data.sync.FirestoreCloudSyncAdapter
import com.stratum.application.sync.CloudSyncPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudSyncProvidesModule {
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSyncBindsModule {
    @Binds
    @Singleton
    abstract fun bindCloudSyncPort(impl: FirestoreCloudSyncAdapter): CloudSyncPort

    @Binds
    @Singleton
    abstract fun bindCloudSyncFeatureGate(impl: DefaultCloudSyncFeatureGate): CloudSyncFeatureGate
}

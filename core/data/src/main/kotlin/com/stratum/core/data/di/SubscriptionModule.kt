package com.stratum.core.data.di

import com.stratum.core.data.subscription.ComplimentaryAccessStore
import com.stratum.core.data.subscription.FirestoreEntitlementDataSource
import com.stratum.core.data.subscription.PlayBillingDataSource
import com.stratum.core.data.subscription.SubscriptionRepositoryImpl
import com.stratum.domain.subscription.BillingPort
import com.stratum.domain.subscription.EntitlementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionModule {

    @Binds
    @Singleton
    abstract fun bindBillingPort(impl: PlayBillingDataSource): BillingPort

    @Binds
    @Singleton
    abstract fun bindEntitlementRepository(impl: SubscriptionRepositoryImpl): EntitlementRepository

    @Binds
    @Singleton
    abstract fun bindComplimentaryAccessStore(impl: FirestoreEntitlementDataSource): ComplimentaryAccessStore
}

package com.stratum.domain.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/**
 * Qualifies a process-lifetime [kotlinx.coroutines.CoroutineScope] detached from any ViewModel.
 * Backed by a [kotlinx.coroutines.SupervisorJob] so a failure in one launched child does not cancel
 * siblings. Used for fire-and-forget background work (e.g. post-completion insights refresh) that
 * must outlive the screen that triggered it.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

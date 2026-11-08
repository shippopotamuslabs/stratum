package com.stratum.application.dashboard

import com.stratum.application.healthconnect.LoadHealthConnectMetricsUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.sync.ObserveCloudSyncStatusUseCase
import com.stratum.domain.dashboard.DashboardPreferences
import com.stratum.domain.health.HealthMetricsPages
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.sync.SyncStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

/**
 * Owns Dashboard multi-source composition (STR-208 stage 1).
 *
 * Replicates the DashboardViewModel collectors: board + health load on collection, board
 * (+ health when enabled) reload on explicit [reloads], on sync busy → Idle, and on the
 * health-enable rising edge. Selected-tab and display-unit changes re-emit with the last
 * loaded board/health — no reload. Health disabled yields [HealthMetricsPages.Hidden]
 * without touching the metrics port. A prefs store that throws keeps its last value
 * instead of killing the pipeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStateUseCase @Inject constructor(
    private val loadDashboard: LoadDashboardUseCase,
    private val loadHealthConnectMetrics: LoadHealthConnectMetricsUseCase,
    private val observeDashboardPreferences: ObserveDashboardPreferencesUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val observeHealthConnectPreferences: ObserveHealthConnectPreferencesUseCase,
    private val observeCloudSyncStatus: ObserveCloudSyncStatusUseCase,
) {
    operator fun invoke(
        reloads: Flow<Unit>,
        zone: ZoneId,
        clock: () -> Instant = Instant::now,
    ): Flow<DashboardBoardState> {
        val dashboardPrefs = observeDashboardPreferences().tolerateErrors(DashboardPreferences())
        val weightPrefs =
            observeWeightDisplayPreferences().tolerateErrors(WeightDisplayPreferences())
        val healthPrefs =
            observeHealthConnectPreferences().tolerateErrors(HealthConnectPreferences())
        val syncStatus = observeCloudSyncStatus().tolerateErrors(SyncStatus.Idle)

        val healthEnabled = healthPrefs.map { it.enabled }.distinctUntilChanged()
        // Rising edge only: the initial load below already covers a pre-enabled state.
        val healthEnables: Flow<Unit> = healthEnabled.drop(1).filter { it }.map { Unit }

        val loads: Flow<LoadedContent> =
            merge(flowOf(Unit), reloads, syncStatus.idleAfterBusy(), healthEnables)
                .mapLatest {
                    val now = clock()
                    val board = loadDashboard(now = now, zone = zone)
                    LoadedContent(board = board, health = loadHealthPages(zone, now, healthPrefs))
                }

        return combine(loads, dashboardPrefs, weightPrefs) { loaded, dashPrefs, weight ->
            DashboardBoardState(
                board = loaded.board,
                healthPages = loaded.health,
                selectedTab = dashPrefs.selectedTab,
                displayUnit = weight.displayUnit,
            )
        }
    }

    /**
     * Health pages for one load: [HealthMetricsPages.Hidden] without touching the metrics
     * port when disabled. A load that started while enabled but returns after a disable is
     * dropped so a stale in-flight read can never re-publish health.
     */
    private suspend fun loadHealthPages(
        zone: ZoneId,
        now: Instant,
        healthPrefs: Flow<HealthConnectPreferences>,
    ): HealthMetricsPages {
        if (!healthPrefs.first().enabled) return HealthMetricsPages.Hidden
        val pages = loadHealthConnectMetrics(zone = zone, now = now)
        return if (healthPrefs.first().enabled) pages else HealthMetricsPages.Hidden
    }

    /**
     * Emits once when sync returns to [SyncStatus.Idle] after being busy. Mirrors the
     * ViewModel collector: busy on Syncing / Hydrating / Error, reload on Idle.
     */
    private fun Flow<SyncStatus>.idleAfterBusy(): Flow<Unit> {
        var sawBusy = false
        return transform { status ->
            when (status) {
                SyncStatus.Syncing, SyncStatus.Hydrating, is SyncStatus.Error -> sawBusy = true
                SyncStatus.Idle -> {
                    if (sawBusy) {
                        sawBusy = false
                        emit(Unit)
                    }
                }
            }
        }
    }

    /**
     * A prefs flow that can never kill the pipeline: a throw keeps the last value (or the
     * [initial] when nothing arrived yet) and holds it, mirroring the ViewModel collectors
     * that catch per-collector and keep the last prefs.
     */
    private fun <T> Flow<T>.tolerateErrors(initial: T): Flow<T> = flow {
        var last = initial
        var seen = false
        try {
            collect { value ->
                last = value
                seen = true
                emit(value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (!seen) emit(last)
            awaitCancellation()
        }
    }
}

private data class LoadedContent(
    val board: LoadDashboardResult,
    val health: HealthMetricsPages,
)

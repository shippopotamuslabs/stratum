package com.stratum.application.training

import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.training.CycleProgress
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.findNextAdHocLiftName
import com.stratum.domain.training.findNextLiftName
import com.stratum.domain.training.liveWorkoutRouteId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Application-layer read model for the minimized live-workout bar (STR-210).
 *
 * Mirrors the presentation `MinimizedWorkoutState` without the UI type, so the bar
 * assembly can live here per §12.4 while the ViewModel maps it 1:1 at its boundary.
 */
data class MinimizedWorkoutBar(
    val workoutId: String,
    val workoutName: String,
    val nextLiftName: String,
    val startedAtEpochMilli: Long,
)

/**
 * Owns the minimized-bar multi-source composition (STR-210).
 *
 * Replicates the `ProgramsViewModel.activeWorkoutBarState` pipeline: per cycle-progress
 * emission, resolves the live session into bar content (ad-hoc via overlay + logged sets,
 * program workout via template + logged sets + catalog). A failed set/catalog/overlay
 * read degrades to a fallback label, never killing the pipeline. Orphaned program
 * sessions (workout missing from the program) yield `null` so the bar hides while the
 * ViewModel init collector abandons the ghost session.
 */
class ObserveMinimizedWorkoutBarUseCase @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    private val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<MinimizedWorkoutBar?> =
        observeCycleProgress().flatMapLatest { progress ->
            flow {
                emit(resolveBar(progress))
            }
        }

    private suspend fun resolveBar(progress: CycleProgress): MinimizedWorkoutBar? {
        val session = progress.activeSession ?: return null
        return when (session.kind) {
            TrainingSessionKind.AD_HOC -> {
                val nextLiftName =
                    runCatching {
                        val lifts = observeLiftCatalog().first()
                        val liftNames = lifts.associate { it.id to it.name }
                        val overlay = manageWorkoutOverlay.load(session.id)
                        val loggedSets = getLoggedSetsForSession(session.id)
                        findNextAdHocLiftName(overlay, loggedSets, liftNames)
                    }.getOrNull()
                MinimizedWorkoutBar(
                    workoutId = session.liveWorkoutRouteId(),
                    workoutName = TrainingSessionDisplayName.resolve(
                        session = session,
                        program = progress.program,
                    ),
                    nextLiftName = nextLiftName ?: "Add lifts",
                    startedAtEpochMilli = session.startedAt.toEpochMilli(),
                )
            }
            TrainingSessionKind.PROGRAM_WORKOUT -> {
                val workoutId = session.workoutId ?: return null
                val program = progress.program
                val workout = program?.workouts?.firstOrNull { it.id == workoutId }
                if (workout == null) {
                    // Orphan: init collector abandons; hide bar so expand cannot open a dead route.
                    return null
                }
                // Soft-fail set/catalog load so a bad read never kills the bar.
                val nextLiftName =
                    runCatching {
                        val loggedSets = getLoggedSetsForSession(session.id)
                        val lifts = observeLiftCatalog().first()
                        val liftNames = lifts.associate { it.id to it.name }
                        findNextLiftName(workout, loggedSets, liftNames)
                    }.getOrNull()
                MinimizedWorkoutBar(
                    workoutId = workoutId.value,
                    workoutName = workout.name,
                    nextLiftName = nextLiftName ?: "Workout in progress",
                    startedAtEpochMilli = session.startedAt.toEpochMilli(),
                )
            }
            // Rest-day ledger rows are not interactive live sessions.
            TrainingSessionKind.REST_DAY -> null
        }
    }
}

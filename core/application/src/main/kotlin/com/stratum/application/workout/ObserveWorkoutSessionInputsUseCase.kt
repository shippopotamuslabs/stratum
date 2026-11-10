package com.stratum.application.workout

import com.stratum.application.lift.ObserveEquipmentCatalogUseCase
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.training.ObserveCycleProgressUseCase
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Lift
import com.stratum.domain.training.CycleProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Application-layer read model for the live workout session inputs (STR-209).
 *
 * Cycle progress plus the lift and equipment catalogs, so the workout
 * ViewModel can feed the session runtime from a single durable source per
 * §12.4 instead of combining three observe use cases itself. Tombstoned-row
 * resolution stays downstream in `LiveWorkoutSessionRuntime`, which merges
 * retained rows into its own maps.
 */
data class WorkoutSessionInputs(
    val progress: CycleProgress,
    val lifts: List<Lift>,
    val equipment: List<Equipment>,
)

/**
 * Owns the live-workout input composition (STR-209).
 *
 * Combines cycle progress, the lift catalog, and the equipment catalog into
 * one [WorkoutSessionInputs] stream. Emission semantics are unchanged from
 * the former ViewModel pipeline: every emission of any source re-emits the
 * latest triple.
 */
class ObserveWorkoutSessionInputsUseCase @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
) {
    operator fun invoke(): Flow<WorkoutSessionInputs> =
        combine(
            observeCycleProgress(),
            observeLiftCatalog(),
            observeEquipmentCatalog(),
        ) { progress, lifts, equipment ->
            WorkoutSessionInputs(
                progress = progress,
                lifts = lifts,
                equipment = equipment,
            )
        }
}

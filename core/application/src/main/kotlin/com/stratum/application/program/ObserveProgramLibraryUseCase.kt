package com.stratum.application.program

import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.training.ObserveActiveSessionUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.program.Program
import com.stratum.domain.program.mergeActiveAndLibraryPrograms
import com.stratum.domain.training.TrainingSession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Application-layer read model for the program library screen (STR-288).
 *
 * Merged programs (active first, de-duplicated) plus the lift catalog and the
 * active session, so the ViewModel can map presentation state from a single
 * durable source per §12.4. The transient rest-day label stays out of this
 * model — the route passes it to the pure presentation mapper instead.
 */
data class ProgramLibrarySnapshot(
    val programs: List<Program>,
    val lifts: List<Lift>,
    val activeSession: TrainingSession?,
)

/**
 * Owns the program-library multi-source composition (STR-288).
 *
 * Combines the active program, the library inventory, the lift catalog, and
 * the active session into one [ProgramLibrarySnapshot] stream. Merge/dedupe
 * semantics are unchanged from the former ViewModel pipeline: active program
 * first, library sorted newest-first, stale library rows matching the active
 * id dropped.
 */
class ObserveProgramLibraryUseCase @Inject constructor(
    private val observeProgram: ObserveProgramUseCase,
    private val observeLibraryPrograms: ObserveLibraryProgramsUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val observeActiveSession: ObserveActiveSessionUseCase,
) {
    operator fun invoke(): Flow<ProgramLibrarySnapshot> =
        combine(
            observeProgram(),
            observeLibraryPrograms(),
            observeLiftCatalog(),
            observeActiveSession(),
        ) { active, library, lifts, activeSession ->
            ProgramLibrarySnapshot(
                programs = mergeActiveAndLibraryPrograms(active, library),
                lifts = lifts,
                activeSession = activeSession,
            )
        }
}

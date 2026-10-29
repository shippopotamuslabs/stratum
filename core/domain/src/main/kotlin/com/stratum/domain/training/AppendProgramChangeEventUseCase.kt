package com.stratum.domain.training

import javax.inject.Inject

/**
 * Appends a [ProgramChangeEvent] to a program's change history.
 *
 * This is the presentation layer's only entry point for recording program change
 * events: ViewModels depend on use cases, never on [ProgramChangeEventRepository]
 * directly (contracts/ARCHITECTURE.md §4.4).
 */
class AppendProgramChangeEventUseCase @Inject constructor(
    private val programChangeEventRepository: ProgramChangeEventRepository,
) {
    suspend operator fun invoke(event: ProgramChangeEvent) {
        programChangeEventRepository.append(event)
    }
}

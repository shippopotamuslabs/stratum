package com.stratum.domain.program

import com.stratum.domain.program.identity.ProgramId

/**
 * Merges active + library program lists for the library screen.
 *
 * Active and library flows can re-emit out of lockstep during program activation
 * (demote/promote). Without de-duplication the same [ProgramId] can appear twice and crash
 * LazyColumn keys on the library UI.
 */
fun mergeActiveAndLibraryPrograms(
    active: Program?,
    library: List<Program>,
): List<Program> =
    buildList {
        val activeId = active?.id
        if (active != null) add(active)
        addAll(
            library
                .filter { program -> program.id != activeId }
                .sortedByDescending { program -> program.createdAt },
        )
    }

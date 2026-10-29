package com.stratum.application.export

import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import javax.inject.Inject
import com.stratum.domain.export.ExportProgramOption

class PreviewExportProgramsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
) {
    /** Active + library user programs; excludes `import-history` sentinel. */
    suspend operator fun invoke(): List<ExportProgramOption> {
        return programRepository
            .getAllUserPrograms()
            .filter { it.id != ProgramId.ImportHistory }
            .sortedWith(compareBy({ it.name }, { it.id.value }))
            .map { program ->
                ExportProgramOption(
                    id = program.id,
                    name = program.name,
                    isActive = program.isActive,
                )
            }
    }
}

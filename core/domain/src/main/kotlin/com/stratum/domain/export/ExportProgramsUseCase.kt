package com.stratum.domain.export

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ExportProgramsUseCase(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
    private val clock: () -> Instant,
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        programRepository: ProgramRepository,
        liftRepository: LiftRepository,
    ) : this(programRepository, liftRepository, { Instant.now() })

    /**
     * Exports selected programs (must be non-empty, all resolvable, none import-history)
     * as one multi-program Stratum envelope with stable program ids.
     *
     * Lift display names are resolved from [LiftRepository] so re-import can match by name
     * rather than opaque lift ids.
     */
    suspend operator fun invoke(programIds: List<ProgramId>): Result<ExportFileResult> =
        runCatching {
            require(programIds.isNotEmpty()) {
                "Select at least one program to export."
            }
            val programs =
                programIds.map { id ->
                    require(id != ProgramId.ImportHistory) {
                        "One or more programs could not be exported."
                    }
                    val program = programRepository.getProgramById(id)
                    require(program != null) {
                        "One or more programs could not be exported."
                    }
                    id to program
                }
            val liftNames = loadLiftNames(programs.map { it.second })
            val mapped =
                programs.map { (id, program) ->
                    id to ProgramToNormalizedTemplateMapper.map(program, liftNames)
                }
            val now = clock()
            val json =
                StratumExportJson.encodeProgramsEnvelope(
                    exportedAt = now,
                    programs = mapped,
                )
            ExportFileResult(
                bytes = json.encodeToByteArray(),
                fileName = exportFileName(now),
                mimeType = MIME_JSON,
            )
        }

    private suspend fun loadLiftNames(programs: List<Program>): Map<LiftId, String> {
        val liftIds =
            programs
                .flatMap { program ->
                    program.workouts.flatMap { workout ->
                        workout.workoutLifts.map { it.liftId }
                    }
                }
                .toSet()
        if (liftIds.isEmpty()) return emptyMap()
        return liftRepository.getLiftsByIds(liftIds).associate { it.id to it.name }
    }

    private fun exportFileName(now: Instant): String {
        val stamp = FILE_STAMP.format(now.atZone(ZoneOffset.UTC))
        return "stratum-programs-$stamp.json"
    }

    private companion object {
        const val MIME_JSON = "application/json"
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

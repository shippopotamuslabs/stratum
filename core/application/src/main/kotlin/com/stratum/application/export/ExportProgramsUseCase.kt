package com.stratum.application.export

import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.export.ExportFileResult
import com.stratum.domain.export.ProgramToNormalizedTemplateMapper
import com.stratum.domain.export.StratumExportJson
import com.stratum.domain.lift.Lift

/**
 * Outcome of [ExportProgramsUseCase] (STR-243).
 *
 * [NoProgramsSelected] and [ProgramsNotExportable] are the enumerated caller
 * refusals (formerly `require` failures) and are not logged;
 * [ProgramsNotExportable] carries the ids that could not be exported
 * (import-history or unresolvable). An operational failure is logged once via
 * [LoggingPort.error] at the use-case boundary and surfaces as
 * [PersistenceFailed]. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface ExportProgramsResult {
    data class Success(val file: ExportFileResult) : ExportProgramsResult

    /** The caller selected no programs to export. */
    data object NoProgramsSelected : ExportProgramsResult

    /** One or more selected programs could not be exported. */
    data class ProgramsNotExportable(
        val programIds: List<ProgramId>,
    ) : ExportProgramsResult

    /** The export failed (repository / store I/O). */
    data object PersistenceFailed : ExportProgramsResult
}

class ExportProgramsUseCase(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
    private val clock: () -> Instant,
    private val loggingPort: LoggingPort,
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        programRepository: ProgramRepository,
        liftRepository: LiftRepository,
        loggingPort: LoggingPort,
    ) : this(programRepository, liftRepository, { Instant.now() }, loggingPort)

    /**
     * Exports selected programs (must be non-empty, all resolvable, none import-history)
     * as one multi-program Stratum envelope with stable program ids.
     *
     * Lift display names are resolved from [LiftRepository] so re-import can match by name
     * rather than opaque lift ids.
     */
    suspend operator fun invoke(programIds: List<ProgramId>): ExportProgramsResult {
        if (programIds.isEmpty()) {
            return ExportProgramsResult.NoProgramsSelected
        }
        return try {
            val programs = mutableListOf<Pair<ProgramId, Program>>()
            val notExportable = mutableListOf<ProgramId>()
            for (id in programIds) {
                val program =
                    if (id == ProgramId.ImportHistory) {
                        null
                    } else {
                        programRepository.getProgramById(id)
                    }
                if (program == null) {
                    notExportable += id
                } else {
                    programs += id to program
                }
            }
            if (notExportable.isNotEmpty()) {
                return ExportProgramsResult.ProgramsNotExportable(notExportable)
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
            ExportProgramsResult.Success(
                ExportFileResult(
                    bytes = json.encodeToByteArray(),
                    fileName = exportFileName(now),
                    mimeType = MIME_JSON,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to export programs: ${error.message}", error)
            ExportProgramsResult.PersistenceFailed
        }
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
        const val TAG = "ExportPrograms"
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

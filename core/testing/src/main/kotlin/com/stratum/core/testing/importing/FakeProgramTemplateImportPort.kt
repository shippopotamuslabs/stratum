package com.stratum.core.testing.importing

import com.stratum.domain.program.importing.ProgramTemplateImportJobId
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.application.program.importing.ProgramTemplateImportPort
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus

/**
 * Fake [ProgramTemplateImportPort] for unit tests. Mirrors [FakeHistoricalImportPort]: serves a
 * configurable sequence of [ProgramTemplateJobSnapshot]s as the client polls, and returns a fixed
 * template JSON on download.
 */
class FakeProgramTemplateImportPort(
    var snapshotSequence: List<ProgramTemplateJobSnapshot> = listOf(
        ProgramTemplateJobSnapshot(
            status = RemoteProgramTemplateJobStatus.Ready,
            progressPhase = ProgramTemplateImportPhase.Parsing,
            progressMessage = "Done",
            programTemplateStoragePath = "programImports/test/job/conversions/1/template.json",
            errorMessage = null,
        ),
    ),
    var templateJson: String = DEFAULT_TEMPLATE_JSON,
) : ProgramTemplateImportPort {
    val uploads = mutableListOf<Triple<ProgramTemplateImportJobId, ByteArray, String>>()
    private var pollIndex = 0

    override suspend fun uploadAndStart(
        jobId: ProgramTemplateImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<Unit> {
        pollIndex = 0
        uploads += Triple(jobId, bytes, fileName)
        return Result.success(Unit)
    }

    override suspend fun pollJobStatus(
        jobId: ProgramTemplateImportJobId,
    ): Result<ProgramTemplateJobSnapshot> {
        val snapshot = snapshotSequence[pollIndex.coerceAtMost(snapshotSequence.lastIndex)]
        if (pollIndex < snapshotSequence.lastIndex) {
            pollIndex++
        }
        return Result.success(snapshot)
    }

    override suspend fun downloadProgramTemplate(storagePath: String): Result<String> =
        Result.success(templateJson)

    companion object {
        /** A minimal valid template JSON with one workout, one lift, one set. */
        val DEFAULT_TEMPLATE_JSON: String = buildString {
            append("""{"programName":"Test Program","cycleLength":3,""")
            append(""""workouts":[{"name":"Day 1","weekday":null,"position":0,""")
            append(""""lifts":[{"name":"Squat","position":0,""")
            append(""""sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,""")
            append(""""rirGoal":2,"restSeconds":180,"isTopSet":true}],"supersetGroupKey":null}]}]}""")
        }
    }
}

package com.stratum.application.program.importing

import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import com.stratum.domain.program.importing.ProgramTemplateImportJobId

interface ProgramTemplateImportPort {
    /**
     * Upload the raw file bytes and enqueue the server job. Returns once the job doc is in
     * [RemoteProgramTemplateJobStatus.Pending]; processing runs asynchronously via the function
     * trigger.
     */
    suspend fun uploadAndStart(
        jobId: ProgramTemplateImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<Unit>

    /** Poll the current job status (single read; the client drives the poll loop). */
    suspend fun pollJobStatus(jobId: ProgramTemplateImportJobId): Result<ProgramTemplateJobSnapshot>

    /** Download the normalized template JSON produced by a [RemoteProgramTemplateJobStatus.Ready] job. */
    suspend fun downloadProgramTemplate(storagePath: String): Result<String>
}

package com.stratum.core.testing.importing

import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.ImportJobSnapshot
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.RemoteImportJobStatus
import com.stratum.domain.importing.UploadRefs
import com.stratum.domain.importing.identity.ImportJobId

class FakeHistoricalImportPort(
    var snapshotSequence: List<ImportJobSnapshot> = listOf(
        ImportJobSnapshot(
            status = RemoteImportJobStatus.Ready,
            progressPhase = ImportProgressPhase.ConvertingFull,
            progressMessage = "Done",
            conversionStoragePath = "imports/test/job/conversions/1/normalized.json",
            errorMessage = null,
        ),
    ),
    var conversionJson: String = """{"sessions":[],"sets":[]}""",
    /** When set, selects snapshot sequence per upload (1-based upload index). */
    var snapshotSequenceByUpload: List<List<ImportJobSnapshot>>? = null,
) : HistoricalImportPort {
    val uploads = mutableListOf<Triple<ImportJobId, ByteArray, String>>()
    private var pollIndex = 0
    private var uploadCount = 0

    override suspend fun uploadRawAndSample(
        jobId: ImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<UploadRefs> {
        pollIndex = 0
        uploadCount += 1
        snapshotSequenceByUpload?.getOrNull(uploadCount - 1)?.let { perUpload ->
            snapshotSequence = perUpload
        }
        uploads += Triple(jobId, bytes, fileName)
        return Result.success(
            UploadRefs(
                rawStoragePath = "imports/test/${jobId.value}/raw/$fileName",
                sampleStoragePath = "imports/test/${jobId.value}/sample/$fileName",
            ),
        )
    }

    override suspend fun startImport(jobId: ImportJobId, refs: UploadRefs): Result<Unit> =
        Result.success(Unit)

    override suspend fun pollJobStatus(jobId: ImportJobId): Result<ImportJobSnapshot> {
        val snapshot = snapshotSequence[pollIndex.coerceAtMost(snapshotSequence.lastIndex)]
        if (pollIndex < snapshotSequence.lastIndex) {
            pollIndex++
        }
        return Result.success(snapshot)
    }

    override suspend fun downloadConversion(storagePath: String): Result<String> =
        Result.success(conversionJson)
}

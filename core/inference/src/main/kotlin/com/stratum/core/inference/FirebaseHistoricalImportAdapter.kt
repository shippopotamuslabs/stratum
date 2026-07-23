package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.ImportJobSnapshot
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.RemoteImportJobStatus
import com.stratum.domain.importing.UploadRefs
import com.stratum.domain.importing.identity.ImportJobId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseHistoricalImportAdapter @Inject constructor() : HistoricalImportPort {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun uploadRawAndSample(
        jobId: ImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<UploadRefs> = runCatching {
        val uid = requireUid()
        val rawPath = "imports/$uid/${jobId.value}/raw/$fileName"
        val samplePath = "imports/$uid/${jobId.value}/sample/$fileName"
        storage.reference.child(rawPath).putBytes(bytes).await()
        val sampleBytes = bytes.take(SAMPLE_BYTES).toByteArray()
        storage.reference.child(samplePath).putBytes(sampleBytes).await()
        UploadRefs(rawStoragePath = rawPath, sampleStoragePath = samplePath)
    }

    override suspend fun startImport(
        jobId: ImportJobId,
        refs: UploadRefs,
        fallbackWeightUnit: com.stratum.domain.lift.Weight.WeightUnit,
    ): Result<Unit> = runCatching {
        val callable = functions.getHttpsCallable("startImport")
        callable.call(
            mapOf(
                "jobId" to jobId.value,
                "rawStoragePath" to refs.rawStoragePath,
                "sampleStoragePath" to refs.sampleStoragePath,
                "fallbackWeightUnit" to fallbackWeightUnit.name, // "Lb" | "Kg"
            ),
        ).await()
    }

    override suspend fun pollJobStatus(jobId: ImportJobId): Result<ImportJobSnapshot> = runCatching {
        val callable = functions.getHttpsCallable("getImportJobStatus")
        val result = callable.call(mapOf("jobId" to jobId.value)).await()
        val map = result.data as? Map<*, *> ?: error("Invalid getImportJobStatus response")
        val latest = map["latestConversion"] as? Map<*, *>
        ImportJobSnapshot(
            status = parseRemoteStatus(map["status"] as? String),
            progressPhase = parseProgressPhase(map["progressPhase"] as? String),
            progressMessage = map["progressMessage"] as? String,
            conversionStoragePath = latest?.get("storagePath") as? String,
            errorMessage = map["errorMessage"] as? String,
            conversionFormat = latest?.get("format") as? String,
        )
    }

    override suspend fun downloadConversion(storagePath: String): Result<String> = runCatching {
        val bytes = storage.reference.child(storagePath).getBytes(MAX_DOWNLOAD_BYTES).await()
        bytes.decodeToString()
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Sign-in required for import.")

    private fun parseRemoteStatus(value: String?): RemoteImportJobStatus = when (value) {
        "pending_agent" -> RemoteImportJobStatus.PendingAgent
        "agent_running" -> RemoteImportJobStatus.AgentRunning
        "ready" -> RemoteImportJobStatus.Ready
        "failed" -> RemoteImportJobStatus.Failed
        "rejected" -> RemoteImportJobStatus.Rejected
        // Unknown terminal-ish statuses should not hang the poller as PendingAgent.
        else -> RemoteImportJobStatus.Failed
    }

    private fun parseProgressPhase(value: String?): ImportProgressPhase? = when (value) {
        "reading_data" -> ImportProgressPhase.ReadingData
        "creating_converter" -> ImportProgressPhase.CreatingConverter
        "validating_sample" -> ImportProgressPhase.ValidatingSample
        "converting_full" -> ImportProgressPhase.ConvertingFull
        "enriching_custom_lifts" -> ImportProgressPhase.EnrichingCustomLifts
        "failed" -> ImportProgressPhase.Failed
        null -> null
        else -> null
    }

    companion object {
        private const val SAMPLE_BYTES = 50 * 1024
        private const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
    }
}

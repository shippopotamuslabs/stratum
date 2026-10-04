package com.stratum.solver

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.firebase.cloud.StorageClient
import java.nio.charset.StandardCharsets

private const val MAX_REQUEST_BYTES = 5L * 1024L * 1024L

/**
 * Cloud Storage side of a synthesis job: download `request.json`, upload `result.json`.
 *
 * Narrow seam so the proxy worker tests run against an in-memory fake instead
 * of the Firebase Admin SDK (third-party boundary).
 */
interface SynthesisBlobStore {
    fun download(storagePath: String): ByteArray
    fun uploadJson(storagePath: String, json: String)
}

/** Production [SynthesisBlobStore] over Cloud Storage. */
class StorageSynthesisBlobStore(
    private val storage: Storage = StorageClient.getInstance().bucket().storage,
    private val bucketName: String = StorageClient.getInstance().bucket().name,
) : SynthesisBlobStore {
    override fun download(storagePath: String): ByteArray {
        val blob = storage.get(BlobId.of(bucketName, storagePath))
            ?: error("Request object not found in Storage")
        // Fail fast on metadata, then enforce on the fetched bytes: a
        // replacement between the two reads must not bypass the cap.
        val size = blob.size
        require(size > 0) { "Request object is empty" }
        require(size <= MAX_REQUEST_BYTES) {
            "Request object exceeds max size ($MAX_REQUEST_BYTES bytes)"
        }
        val content = blob.getContent()
        checkDownloadedContent(content)
        return content
    }

    override fun uploadJson(storagePath: String, json: String) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath))
            .setContentType("application/json; charset=utf-8")
            .build()
        storage.create(blobInfo, json.toByteArray(StandardCharsets.UTF_8))
    }
}

/**
 * Enforces the size cap on downloaded bytes (not just blob metadata).
 * Testable seam: the Firebase `Storage`/`Blob` calls above are a
 * third-party boundary with no unit-test coverage.
 */
internal fun checkDownloadedContent(content: ByteArray) {
    require(content.isNotEmpty()) { "Request object is empty" }
    require(content.size <= MAX_REQUEST_BYTES) {
        "Request object exceeds max size ($MAX_REQUEST_BYTES bytes)"
    }
}

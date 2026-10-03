package com.stratum.core.testing.telemetry

import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.TelemetryUploadPort

/**
 * In-memory [TelemetryUploadPort] for unit tests. Records uploaded batches;
 * toggle [shouldFail] to simulate offline / server failures.
 */
class FakeTelemetryUploadPort : TelemetryUploadPort {
    val uploadedBatches = mutableListOf<ClientLogBatch>()
    var shouldFail: Boolean = false

    override suspend fun upload(batch: ClientLogBatch): Result<Unit> {
        uploadedBatches += batch
        return if (shouldFail) {
            Result.failure(RuntimeException("telemetry upload failed"))
        } else {
            Result.success(Unit)
        }
    }

    fun clear() {
        uploadedBatches.clear()
        shouldFail = false
    }
}

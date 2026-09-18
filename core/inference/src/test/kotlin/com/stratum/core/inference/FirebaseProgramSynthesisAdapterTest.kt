package com.stratum.core.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pure helpers for [FirebaseProgramSynthesisAdapter] (path build, status parse, error mapping).
 * Full Firebase upload/poll/download requires instrumented or integration coverage.
 */
class FirebaseProgramSynthesisAdapterTest {
    @Nested
    @DisplayName("buildSynthesisRequestStoragePath")
    inner class RequestPath {
        @Test
        @DisplayName("builds synthesis/{uid}/{jobId}/request.json")
        fun buildsExpectedPath() {
            assertEquals(
                "synthesis/user-1/job-abc/request.json",
                buildSynthesisRequestStoragePath(uid = "user-1", jobId = "job-abc"),
            )
        }
    }

    @Nested
    @DisplayName("requireOwnedResultPath")
    inner class ResultPath {
        @Test
        @DisplayName("accepts owned result path")
        fun acceptsOwned() {
            requireOwnedResultPath("u1", "synthesis/u1/j1/result.json")
        }

        @Test
        @DisplayName("rejects cross-user or escape paths")
        fun rejectsBad() {
            assertThrows(com.stratum.domain.program.generation.synthesis.ProgramSynthesisException::class.java) {
                requireOwnedResultPath("u1", "synthesis/other/j1/result.json")
            }
            assertThrows(com.stratum.domain.program.generation.synthesis.ProgramSynthesisException::class.java) {
                requireOwnedResultPath("u1", "synthesis/u1/../x/result.json")
            }
        }
    }

    @Nested
    @DisplayName("parseSynthesisRemoteStatus")
    inner class RemoteStatus {
        @Test
        @DisplayName("maps known server status strings")
        fun mapsKnownStatuses() {
            assertEquals(SynthesisRemoteStatus.Pending, parseSynthesisRemoteStatus("pending"))
            assertEquals(SynthesisRemoteStatus.Running, parseSynthesisRemoteStatus("running"))
            assertEquals(SynthesisRemoteStatus.Ready, parseSynthesisRemoteStatus("ready"))
            assertEquals(SynthesisRemoteStatus.Failed, parseSynthesisRemoteStatus("failed"))
            assertEquals(SynthesisRemoteStatus.Rejected, parseSynthesisRemoteStatus("rejected"))
        }

        @Test
        @DisplayName("unknown or null status becomes Failed so the poller does not hang")
        fun unknownIsFailed() {
            assertEquals(SynthesisRemoteStatus.Failed, parseSynthesisRemoteStatus(null))
            assertEquals(SynthesisRemoteStatus.Failed, parseSynthesisRemoteStatus("weird"))
        }
    }

    @Nested
    @DisplayName("parseSynthesisStatusResponse")
    inner class StatusResponse {
        @Test
        @DisplayName("maps ready payload with result path and progress")
        fun mapsReadyPayload() {
            val snapshot =
                parseSynthesisStatusResponse(
                    mapOf(
                        "status" to "ready",
                        "progressPhase" to "complete",
                        "progressMessage" to "Done",
                        "resultStoragePath" to "synthesis/u/j/result.json",
                        "errorMessage" to null,
                    ),
                )
            assertEquals(SynthesisRemoteStatus.Ready, snapshot.status)
            assertEquals("complete", snapshot.progressPhase)
            assertEquals("Done", snapshot.progressMessage)
            assertEquals("synthesis/u/j/result.json", snapshot.resultStoragePath)
            assertEquals(null, snapshot.errorMessage)
        }

        @Test
        @DisplayName("maps failed payload with errorMessage")
        fun mapsFailedPayload() {
            val snapshot =
                parseSynthesisStatusResponse(
                    mapOf(
                        "status" to "failed",
                        "progressPhase" to "failed",
                        "progressMessage" to "Solver error",
                        "resultStoragePath" to null,
                        "errorMessage" to "Could not find a feasible program.",
                    ),
                )
            assertEquals(SynthesisRemoteStatus.Failed, snapshot.status)
            assertEquals("Could not find a feasible program.", snapshot.errorMessage)
        }

        @Test
        @DisplayName("rejects non-map payload")
        fun rejectsInvalidPayload() {
            assertThrows(IllegalStateException::class.java) {
                parseSynthesisStatusResponse("not-a-map")
            }
        }
    }

    @Nested
    @DisplayName("mapFunctionsExceptionToUserMessage")
    inner class ErrorMapping {
        @Test
        @DisplayName("resource-exhausted maps to monthly limit message")
        fun resourceExhausted() {
            assertEquals(MESSAGE_MONTHLY_LIMIT, mapFunctionsExceptionToUserMessage("RESOURCE_EXHAUSTED"))
        }

        @Test
        @DisplayName("failed-precondition maps to already-in-progress message")
        fun failedPrecondition() {
            assertEquals(
                MESSAGE_ALREADY_IN_PROGRESS,
                mapFunctionsExceptionToUserMessage("FAILED_PRECONDITION"),
            )
        }

        @Test
        @DisplayName("unauthenticated maps to sign-in required message")
        fun unauthenticated() {
            assertEquals(MESSAGE_SIGN_IN_REQUIRED, mapFunctionsExceptionToUserMessage("UNAUTHENTICATED"))
        }

        @Test
        @DisplayName("other codes use generic failure (never raw server text)")
        fun otherCodes() {
            assertEquals(MESSAGE_GENERATION_FAILED, mapFunctionsExceptionToUserMessage("INVALID_ARGUMENT"))
            assertEquals(MESSAGE_GENERATION_FAILED, mapFunctionsExceptionToUserMessage("INTERNAL"))
            assertEquals(MESSAGE_GENERATION_FAILED, mapFunctionsExceptionToUserMessage(null))
        }
    }

    @Nested
    @DisplayName("synthesisTraceOutcome")
    inner class TraceOutcome {
        @Test
        @DisplayName("ready status maps to ready")
        fun ready() {
            assertEquals("ready", synthesisTraceOutcome(SynthesisRemoteStatus.Ready, timedOut = false))
        }

        @Test
        @DisplayName("rejected status maps to rejected")
        fun rejected() {
            assertEquals("rejected", synthesisTraceOutcome(SynthesisRemoteStatus.Rejected, timedOut = false))
        }

        @Test
        @DisplayName("timeout flag wins over any status")
        fun timeout() {
            assertEquals("timeout", synthesisTraceOutcome(status = null, timedOut = true))
            assertEquals("timeout", synthesisTraceOutcome(SynthesisRemoteStatus.Ready, timedOut = true))
        }

        @Test
        @DisplayName("failed, pending, running and unknown states collapse to failed")
        fun failures() {
            assertEquals("failed", synthesisTraceOutcome(SynthesisRemoteStatus.Failed, timedOut = false))
            assertEquals("failed", synthesisTraceOutcome(SynthesisRemoteStatus.Pending, timedOut = false))
            assertEquals("failed", synthesisTraceOutcome(SynthesisRemoteStatus.Running, timedOut = false))
            assertEquals("failed", synthesisTraceOutcome(status = null, timedOut = false))
        }
    }

    @Nested
    @DisplayName("isUserSafeErrorMessage")
    inner class SafeMessage {
        @Test
        fun acceptsShortPlainCopy() {
            assertTrue("Could not build a feasible program for this profile.".isUserSafeErrorMessage())
        }

        @Test
        fun rejectsInfraLeak() {
            assertFalse("gs://bucket/path failed".isUserSafeErrorMessage())
            assertFalse("java.lang.IllegalStateException: boom".isUserSafeErrorMessage())
        }
    }
}

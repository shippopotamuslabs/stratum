package com.stratum.solver

import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWire
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * STR-52: the worker is a version-normalizing proxy to the Python solver.
 * Claim/upload/status/trace plumbing is preserved; CP-SAT never runs here.
 *
 * Fakes back the Firebase Admin SDK seams ([SynthesisJobStore],
 * [SynthesisBlobStore]) — third-party boundary, no MockK needed. Fixture
 * payloads come from `solver-contract/fixtures/v1/` (read-only).
 */
class SynthesisJobWorkerProxyTest {
    private val jobs = FakeJobStore()
    private val blobs = FakeBlobStore()
    private val http = FakeSolverHttpClient()

    private fun worker() = SynthesisJobWorker(
        jobs = jobs,
        blobs = blobs,
        solverBaseUrl = "http://solver.test",
        httpClient = http,
    )

    @Test
    fun `proxy success uploads raw python body byte-identical`() {
        val requestBody = fixture("3day-fullbody-maint.request.json")
        val resultBody = fixture("3day-fullbody-maint.result.json")
        blobs.seed("synthesis/u1/j1/request.json", requestBody)
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(200, resultBody) }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Ready("synthesis/u1/j1/result.json"),
            outcome,
        )
        // Versioned fixture passes through untouched.
        assertEquals(listOf(requestBody), http.requests)
        // Raw Python body stored verbatim — never re-encoded.
        assertEquals(resultBody, blobs.uploads["synthesis/u1/j1/result.json"])
        val update = jobs.updates.single()
        assertEquals("ready", update.fields["status"])
        assertEquals("synthesis/u1/j1/result.json", update.fields["resultStoragePath"])
        assertTrue(update.fields.containsKey(SynthesisJobWorker.TRACE_WORKER_UPLOADED))
    }

    @Test
    fun `legacy request without modelVersion is stamped v1 before POST`() {
        val versioned = fixture("3day-fullbody-maint.request.json")
        val legacy = versioned.replace("\"modelVersion\": \"v1\",", "")
        assertTrue(!legacy.contains("modelVersion"), "precondition: version stripped")
        val resultBody = fixture("3day-fullbody-maint.result.json")
        blobs.seed("synthesis/u1/j1/request.json", legacy)
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(200, resultBody) }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Ready("synthesis/u1/j1/result.json"),
            outcome,
        )
        val forwarded = http.requests.single()
        assertEquals(ProgramSynthesisWire.stampModelVersionV1(legacy), forwarded)
        assertTrue(forwarded.contains("\"modelVersion\":\"v1\""))
        // Stamped payload still validates as v1.
        ProgramSynthesisWire.decodeRequest(forwarded)
    }

    @Test
    fun `unknown major fails without HTTP call`() {
        val versioned = fixture("3day-fullbody-maint.request.json")
        val v2 = versioned.replace("\"modelVersion\": \"v1\"", "\"modelVersion\": \"v2\"")
        blobs.seed("synthesis/u1/j1/request.json", v2)
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Invalid generation request. Please try again."),
            outcome,
        )
        assertTrue(http.requests.isEmpty(), "unknown major must never reach the solver")
        assertTrue(blobs.uploads.isEmpty(), "no result may be stored for a rejected request")
        assertEquals("failed", jobs.updates.single().fields["status"])
    }

    @Test
    fun `solver 400 maps to invalid-request message`() {
        blobs.seed("synthesis/u1/j1/request.json", fixture("3day-fullbody-maint.request.json"))
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(400, """{"detail":"bad week"}""") }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Invalid generation request. Please try again."),
            outcome,
        )
        assertTrue(blobs.uploads.isEmpty())
    }

    @Test
    fun `solver 500 maps to generic failure message`() {
        blobs.seed("synthesis/u1/j1/request.json", fixture("3day-fullbody-maint.request.json"))
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(500, "boom") }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Program generation failed. Please try again."),
            outcome,
        )
        assertTrue(blobs.uploads.isEmpty())
    }

    @Test
    fun `blank solver body fails without upload`() {
        blobs.seed("synthesis/u1/j1/request.json", fixture("3day-fullbody-maint.request.json"))
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(200, "  ") }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Program generation failed. Please try again."),
            outcome,
        )
        assertTrue(blobs.uploads.isEmpty())
    }

    @Test
    fun `undecodable solver body fails without upload`() {
        blobs.seed("synthesis/u1/j1/request.json", fixture("3day-fullbody-maint.request.json"))
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { SolverHttpClient.SolveResponse(200, """{"sessions":[]}""") }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Program generation failed. Please try again."),
            outcome,
        )
        assertTrue(blobs.uploads.isEmpty())
    }

    @Test
    fun `solver transport failure fails gracefully`() {
        blobs.seed("synthesis/u1/j1/request.json", fixture("3day-fullbody-maint.request.json"))
        jobs.claim = SynthesisJobStore.Claim.Claimed("synthesis/u1/j1/request.json")
        http.handler = { throw IOException("connection refused") }

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(
            SynthesisJobWorker.Outcome.Failed("Program generation failed. Please try again."),
            outcome,
        )
        assertTrue(blobs.uploads.isEmpty())
        assertEquals("failed", jobs.updates.single().fields["status"])
    }

    @Test
    fun `missing job short-circuits before storage`() {
        jobs.claim = SynthesisJobStore.Claim.Missing

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(SynthesisJobWorker.Outcome.JobMissing, outcome)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `non-pending job is skipped`() {
        jobs.claim = SynthesisJobStore.Claim.NotClaimable("running")

        val outcome = worker().process(ProcessRequest(uid = "u1", jobId = "j1"))

        assertEquals(SynthesisJobWorker.Outcome.Skipped("status=running"), outcome)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `worker request path references no OR-Tools types`() {
        val banned = listOf("ortools", "cpsat", "domain.solver")
        val seen = mutableSetOf<String>()
        fun collect(type: Class<*>) {
            if (seen.add(type.name)) {
                assertTrue(
                    banned.none { type.name.contains(it, ignoreCase = true) },
                    "SynthesisJobWorker references solver type ${type.name}",
                )
            }
        }
        val workerClass = SynthesisJobWorker::class.java
        collect(workerClass.superclass ?: Any::class.java)
        workerClass.interfaces.forEach(::collect)
        workerClass.declaredFields.forEach { collect(it.type) }
        workerClass.declaredConstructors.forEach { ctor ->
            ctor.parameterTypes.forEach(::collect)
        }
        workerClass.declaredMethods.forEach { method ->
            collect(method.returnType)
            method.parameterTypes.forEach(::collect)
        }
        assertTrue(seen.isNotEmpty(), "reflection scan must observe worker types")
    }

    @Test
    fun `resolveSolverBaseUrl requires the env var`() {
        assertThrows<IllegalArgumentException> {
            SynthesisJobWorker.resolveSolverBaseUrl(emptyMap())
        }
        assertThrows<IllegalArgumentException> {
            SynthesisJobWorker.resolveSolverBaseUrl(
                mapOf(SynthesisJobWorker.SOLVER_BASE_URL_ENV to "  "),
            )
        }
    }

    @Test
    fun `resolveSolverBaseUrl trims trailing slash`() {
        assertEquals(
            "https://stratum-solver-xyz.run.app",
            SynthesisJobWorker.resolveSolverBaseUrl(
                mapOf(SynthesisJobWorker.SOLVER_BASE_URL_ENV to "https://stratum-solver-xyz.run.app/"),
            ),
        )
    }

    private fun fixture(name: String): String {
        val file = File(findRepoRoot(), "solver-contract/fixtures/v1/$name")
        require(file.isFile) { "fixture missing: $file" }
        return file.readText(StandardCharsets.UTF_8)
    }

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            if (File(dir, "solver-contract/fixtures/v1").isDirectory) return dir
            dir = dir.parentFile ?: error(
                "solver-contract/fixtures not found walking up from ${System.getProperty("user.dir")}",
            )
        }
    }

    private class FakeJobStore(
        var claim: SynthesisJobStore.Claim = SynthesisJobStore.Claim.Missing,
    ) : SynthesisJobStore {
        data class Update(val uid: String, val jobId: String, val fields: Map<String, Any>)

        val updates = mutableListOf<Update>()

        override fun claim(uid: String, jobId: String): SynthesisJobStore.Claim = claim

        override fun updateIfPresent(uid: String, jobId: String, fields: Map<String, Any>): Boolean {
            updates.add(Update(uid, jobId, fields))
            return true
        }
    }

    private class FakeBlobStore : SynthesisBlobStore {
        private val objects = mutableMapOf<String, String>()
        val uploads = mutableMapOf<String, String>()

        fun seed(path: String, json: String) {
            objects[path] = json
        }

        override fun download(storagePath: String): ByteArray =
            objects[storagePath]?.toByteArray(StandardCharsets.UTF_8)
                ?: error("no object: $storagePath")

        override fun uploadJson(storagePath: String, json: String) {
            uploads[storagePath] = json
        }
    }

    private class FakeSolverHttpClient(
        var handler: (String) -> SolverHttpClient.SolveResponse =
            { error("unexpected solver call") },
    ) : SolverHttpClient {
        val requests = mutableListOf<String>()

        override fun postSolveV1(requestJson: String): SolverHttpClient.SolveResponse {
            requests.add(requestJson)
            return handler(requestJson)
        }
    }
}

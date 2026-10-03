package com.stratum.solver

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * STR-52: [JdkSolverHttpClient] against a loopback JDK `HttpServer`
 * (no network, no new dependencies).
 */
class JdkSolverHttpClientTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterEach
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }

    @Test
    fun `posts stamped body to solve v1 as json`() {
        val seen = mutableMapOf<String, String>()
        val server = startServer(status = 200, body = """{"modelVersion":"v1"}""", seen = seen)
        val client = JdkSolverHttpClient(baseUrl(server), timeoutMillis = 10_000L)

        val response = client.postSolveV1("""{"modelVersion":"v1","a":1}""")

        assertEquals(200, response.statusCode)
        assertEquals("""{"modelVersion":"v1"}""", response.body)
        assertEquals("/solve/v1", seen["path"])
        assertEquals("POST", seen["method"])
        assertTrue(
            seen["contentType"].orEmpty().startsWith("application/json"),
            "content type must be JSON, got: ${seen["contentType"]}",
        )
        assertEquals("""{"modelVersion":"v1","a":1}""", seen["body"])
    }

    @Test
    fun `non-2xx status is returned not thrown`() {
        val server = startServer(status = 500, body = "boom")
        val client = JdkSolverHttpClient(baseUrl(server), timeoutMillis = 10_000L)

        val response = client.postSolveV1("{}")

        assertEquals(500, response.statusCode)
        assertEquals("boom", response.body)
    }

    @Test
    fun `base url trailing slash does not double the slash`() {
        val seen = mutableMapOf<String, String>()
        val server = startServer(status = 200, body = "ok", seen = seen)
        val client = JdkSolverHttpClient(baseUrl(server) + "/", timeoutMillis = 10_000L)

        client.postSolveV1("{}")

        assertEquals("/solve/v1", seen["path"])
    }

    @Test
    fun `hung solver hits the worker deadline`() {
        val server = startServer(status = 200, body = "too-late", delayMillis = 3_000L)
        val client = JdkSolverHttpClient(baseUrl(server), timeoutMillis = 500L)

        assertThrows<IOException> {
            client.postSolveV1("{}")
        }
    }

    private fun baseUrl(server: HttpServer): String =
        "http://localhost:${server.address.port}"

    private fun startServer(
        status: Int,
        body: String,
        delayMillis: Long = 0L,
        seen: MutableMap<String, String> = mutableMapOf(),
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool { task ->
            Thread(task).apply { isDaemon = true }
        }
        server.createContext("/solve/v1") { exchange ->
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis)
                seen["path"] = exchange.requestURI.path
                seen["method"] = exchange.requestMethod
                seen["contentType"] = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
                seen["body"] = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        servers.add(server)
        return server
    }
}

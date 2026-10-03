package com.stratum.solver

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.stratum.domain.json.jsonString
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cloud Run entrypoint for the program-synthesis CP-SAT worker.
 *
 * Env:
 * - `PORT` (default 8080) — Cloud Run injects this
 * - `GOOGLE_CLOUD_PROJECT` / `GCLOUD_PROJECT` — project id for Admin SDK
 * - `FIREBASE_STORAGE_BUCKET` — optional; defaults to `{project}.firebasestorage.app`
 *   or `{project}.appspot.com` when unset (Admin SDK default bucket resolution)
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    initializeFirebase()

    val server = HttpServer.create(InetSocketAddress(port), /* backlog */ 0)
    server.createContext("/health", HealthHandler())
    server.createContext("/process", ProcessHandler())
    // Cloud Run is configured concurrency=1; keep a small pool for health during long solves.
    server.executor = Executors.newFixedThreadPool(
        HTTP_POOL_SIZE,
        object : ThreadFactory {
            private val n = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread =
                Thread(r, "solver-http-${n.getAndIncrement()}").apply { isDaemon = true }
        },
    )
    server.start()
    appLog.info(
        "solver listening",
        extra = listOf("port" to port.toString()),
    )
}

private fun initializeFirebase() {
    if (FirebaseApp.getApps().isNotEmpty()) {
        appLog.info("Firebase already initialized")
        return
    }
    val projectId = System.getenv("GOOGLE_CLOUD_PROJECT")
        ?: System.getenv("GCLOUD_PROJECT")
        ?: System.getenv("GCP_PROJECT")
    val storageBucket = System.getenv("FIREBASE_STORAGE_BUCKET")
        ?: projectId?.let { "$it.firebasestorage.app" }

    val builder = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.getApplicationDefault())
    if (!projectId.isNullOrBlank()) {
        builder.setProjectId(projectId)
        appLog.info(
            "Firebase project resolved",
            extra = listOf("projectId" to jsonString(projectId)),
        )
    } else {
        appLog.warn("projectId unset (ADC may still resolve)")
    }
    if (!storageBucket.isNullOrBlank()) {
        builder.setStorageBucket(storageBucket)
        appLog.info(
            "storage bucket resolved",
            extra = listOf("storageBucket" to jsonString(storageBucket)),
        )
    }
    FirebaseApp.initializeApp(builder.build())
    appLog.info("FirebaseApp initialized")
}

private class HealthHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        val body = "ok"
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

private val appLog = StructuredLogger(SynthesisJobWorker.SERVICE_NAME)

private const val DEFAULT_PORT = 8080
private const val HTTP_POOL_SIZE = 2
private const val HTTP_OK = 200

package com.stratum.core.infrastructure.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.application.importing.LiftNameEmbeddingPort
import com.stratum.domain.coroutines.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Calls the `embed_exercise_names` Firebase Callable (MiniLM) for distinct import exercise names.
 * Returns L2-normalized vectors keyed by the original raw name string. Batches run concurrently
 * to reduce import latency.
 *
 * This adapter records nothing for a failure (STR-240 chunk 8, closing STR-238's
 * interim A14 exception): the failure rides the returned [Result] into
 * `HybridLiftNameResolver`, which keeps the real cause beside the message string;
 * the import resolution use case carries it as a diagnostic on the import summary
 * to the import boundary, which records it exactly once.
 */
@Singleton
class FirebaseLiftNameEmbeddingAdapter @Inject constructor() : LiftNameEmbeddingPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun embedExerciseNames(names: List<String>): Result<Map<String, FloatArray>> =
        runCatchingCancellable {
            requireAuth()
            val distinct = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (distinct.isEmpty()) return@runCatchingCancellable emptyMap<String, FloatArray>()
            coroutineScope {
                distinct
                    .chunked(EMBEDDING_BATCH_SIZE)
                    .map { batch -> async { invokeBatch(batch) } }
                    .awaitAll()
            }.fold(emptyMap<String, FloatArray>()) { acc, batch -> acc + batch }
        }

    private suspend fun invokeBatch(names: List<String>): Map<String, FloatArray> {
        val callable = functions.getHttpsCallable(CALLABLE_NAME)
        val payload = mapOf("names" to names)
        val result = callable.call(payload).await()
        return Companion.parseResponse(result.data)
    }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for lift name embedding." }
    }

    internal companion object {
        /** Must stay in sync with the batch cap in `functions-python/main.py`. */
        const val EMBEDDING_BATCH_SIZE = 200
        private const val CALLABLE_NAME = "embed_exercise_names"

        @Suppress("UNCHECKED_CAST")
        internal fun parseResponse(data: Any?): Map<String, FloatArray> {
            val map = data as? Map<*, *> ?: return emptyMap()
            val embeddings = map["embeddings"] as? Map<*, *> ?: return emptyMap()
            return embeddings.mapNotNull { (key, value) ->
                val name = key as? String ?: return@mapNotNull null
                val vector = (value as? List<*>)?.mapNotNull { element ->
                    (element as? Number)?.toFloat()
                } ?: return@mapNotNull null
                if (vector.isEmpty()) return@mapNotNull null
                name to vector.toFloatArray()
            }.toMap()
        }
    }
}

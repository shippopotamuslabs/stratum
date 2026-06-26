package com.stratum.core.data.seed

import android.content.Context
import com.stratum.domain.importing.LiftEmbeddingCatalogPort
import com.stratum.domain.lift.identity.LiftId
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads bundled `lift-name-embeddings.bin` (build-time MiniLM, L2-normalized) into memory once.
 * Missing or corrupt asset yields an empty catalog — hybrid resolver falls back to pure BM25.
 */
class AssetLiftEmbeddingCatalog(
    private val context: Context,
    private val assetPath: String = ASSET_PATH,
) : LiftEmbeddingCatalogPort {

    @Volatile
    private var cached: CatalogSnapshot? = null

    override val dimensions: Int
        get() = snapshot().dimensions

    override fun vectorFor(liftId: LiftId): FloatArray? = snapshot().vectors[liftId]

    override fun allVectors(): Map<LiftId, FloatArray> = snapshot().vectors

    private fun snapshot(): CatalogSnapshot {
        cached?.let { return it }
        val loaded = decode()
        cached = loaded
        return loaded
    }

    private fun decode(): CatalogSnapshot {
        return runCatching {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.readBytes()
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = ByteArray(MAGIC.size)
                buffer.get(magic)
                require(magic.contentEquals(MAGIC)) { "Invalid embedding asset magic." }
                val version = buffer.short.toInt() and UNSIGNED_SHORT_MASK
                require(version == VERSION) { "Unsupported embedding asset version: $version" }
                val dimensions = buffer.short.toInt() and UNSIGNED_SHORT_MASK
                val count = buffer.int
                val vectors = LinkedHashMap<LiftId, FloatArray>(count)
                repeat(count) {
                    val idLength = buffer.short.toInt() and UNSIGNED_SHORT_MASK
                    val idBytes = ByteArray(idLength)
                    buffer.get(idBytes)
                    val liftId = LiftId(idBytes.decodeToString())
                    val vector = FloatArray(dimensions)
                    repeat(dimensions) { index ->
                        vector[index] = buffer.float
                    }
                    vectors[liftId] = vector
                }
                CatalogSnapshot(dimensions = dimensions, vectors = vectors)
            }
        }.getOrElse {
            CatalogSnapshot(dimensions = 0, vectors = emptyMap())
        }
    }

    private data class CatalogSnapshot(
        val dimensions: Int,
        val vectors: Map<LiftId, FloatArray>,
    )

    companion object {
        const val ASSET_PATH = "lifts/lift-name-embeddings.bin"
        private val MAGIC = "STRM".encodeToByteArray()
        private const val VERSION = 1
        private const val UNSIGNED_SHORT_MASK = 0xFFFF
    }
}

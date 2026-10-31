package com.stratum.core.infrastructure.sync.handlers

import com.stratum.core.infrastructure.sync.SyncCollection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordered access to every [SyncCollectionHandler]. Engines must not branch on collection enums —
 * they iterate [inHydrateOrder] / [inUploadOrder].
 */
@Singleton
class SyncHandlerRegistry @Inject constructor(
    provider: SyncHandlersProvider,
) {
    private val byCollection: Map<SyncCollection, SyncCollectionHandler> =
        provider.all().associateBy { it.collection }

    init {
        require(byCollection.keys == SyncCollection.entries.toSet()) {
            val missing = SyncCollection.entries.toSet() - byCollection.keys
            val extra = byCollection.keys - SyncCollection.entries.toSet()
            "SyncHandlerRegistry incomplete: missing=$missing extra=$extra"
        }
    }

    fun get(collection: SyncCollection): SyncCollectionHandler = byCollection.getValue(collection)

    fun forFirestorePath(path: String): SyncCollectionHandler? =
        SyncCollection.fromFirestorePath(path)?.let { byCollection[it] }

    /** Parent-before-child order for hydrate and upload. */
    fun inHydrateOrder(): List<SyncCollectionHandler> =
        SyncCollection.HYDRATE_ORDER.map { byCollection.getValue(it) }

    fun inUploadOrder(): List<SyncCollectionHandler> = inHydrateOrder()
}

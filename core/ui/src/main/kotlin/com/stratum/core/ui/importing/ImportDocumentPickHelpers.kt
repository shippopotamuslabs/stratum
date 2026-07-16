package com.stratum.core.ui.importing

import android.content.Context
import android.net.Uri
import com.stratum.domain.importing.ImportFileInput
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a single SAF document URI off the main thread.
 *
 * SAF result callbacks run on the main thread; [ensureReadPermission] (IPC) and
 * [readImportFileFromUri] (blocking provider I/O) must not run there.
 *
 * Permission take is best-effort (cloud providers often need it; local files may not
 * support persistable grants). The read always runs afterward so transient grants still work.
 */
suspend fun loadImportFile(
    context: Context,
    uri: Uri,
): Result<ImportFileInput> =
    withContext(Dispatchers.IO) {
        ensureReadPermission(context, uri)
        readImportFileFromUri(context.contentResolver, uri)
    }

/**
 * Loads multiple SAF document URIs off the main thread.
 *
 * - Returns [Result.success] with every file that could be read (partial success is OK).
 * - Returns [Result.failure] with the first error when **no** file could be read, so callers
 *   can surface feedback instead of silently no-op'ing.
 */
suspend fun loadImportFiles(
    context: Context,
    uris: List<Uri>,
): Result<List<ImportFileInput>> =
    withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext Result.failure(IOException("No files selected."))
        }
        val files = ArrayList<ImportFileInput>(uris.size)
        var firstError: Throwable? = null
        for (uri in uris) {
            ensureReadPermission(context, uri)
            readImportFileFromUri(context.contentResolver, uri)
                .onSuccess { files.add(it) }
                .onFailure { error ->
                    if (firstError == null) firstError = error
                }
        }
        val error = firstError
        when {
            files.isNotEmpty() -> Result.success(files)
            error != null -> Result.failure(error)
            else -> Result.failure(IOException("Could not read the selected files."))
        }
    }

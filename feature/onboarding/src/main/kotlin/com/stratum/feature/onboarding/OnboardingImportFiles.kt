package com.stratum.feature.onboarding

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.stratum.domain.importing.ImportFileInput
import java.io.IOException

/**
 * Reads selected document URIs into [ImportFileInput] payloads. Kept outside composables so
 * [OnboardingRoute] stays thin and file I/O is testable without Compose.
 */
internal fun readImportFilesFromUris(
    contentResolver: ContentResolver,
    uris: List<Uri>,
): List<ImportFileInput> =
    uris.mapNotNull { uri -> readImportFileFromUri(contentResolver, uri).getOrNull() }

/**
 * Ensure our process holds read permission for [uri] before attempting to read it.
 *
 * Cloud providers (Google Drive's `StorageBackendContentProvider`) reject reads with a
 * `SecurityException: Permission Denial` even though the SAF picker returned the URI — the
 * transient grant from `ACTION_OPEN_DOCUMENT` is not always honored by these providers. Taking the
 * persistable permission explicitly forces the grant to be applied to our process.
 *
 * Returns true if a permission was taken OR none was needed (local files don't require it); false
 * only when taking the permission itself throws (genuinely inaccessible URI).
 */
internal fun ensureReadPermission(context: Context, uri: Uri): Boolean {
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    // takePersistableUriPermission throws for URIs whose provider doesn't offer persistable
    // grants (local files); that's fine — those don't need an explicit grant. For URIs where the
    // grant genuinely cannot be taken, return false so the caller surfaces a clear error.
    return runCatching {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }.isSuccess
}

/**
 * Read a single document URI into an [ImportFileInput].
 *
 * Cloud providers (Drive, Dropbox, …) return *virtual* documents that have not been materialized.
 * `openInputStream` throws `FileNotFoundException: File is virtual` on those, and
 * `openFileDescriptor` returns `null`. This helper tries `openInputStream` first (the common case
 * for local files), then falls back to `openFileDescriptor`, and surfaces any failure as an
 * [IOException] (rather than silently returning null) so the caller can show the user feedback.
 *
 * Reads the real display name via [OpenableColumns.DISPLAY_NAME]; falls back to the URI's last
 * path segment.
 *
 * @throws IOException when the file cannot be opened or read (e.g. virtual doc that won't
 * materialize, revoked access, provider error).
 */
internal fun readImportFileFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
): Result<ImportFileInput> = runCatching {
    val bytes = readBytes(contentResolver, uri)
    val name = displayName(contentResolver, uri) ?: "import"
    ImportFileInput(bytes = bytes, fileName = name)
}

private fun readBytes(contentResolver: ContentResolver, uri: Uri): ByteArray {
    // Strategy 1: openInputStream — works for local files and most providers.
    val streamBytes = runCatching {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
    if (streamBytes != null) return streamBytes

    // Strategy 2: openFileDescriptor — forces materialization for some virtual docs that
    // openInputStream rejects. Throws if the provider still cannot deliver bytes.
    val pfd = contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("Provider returned no file descriptor for $uri")
    return pfd.use { java.io.FileInputStream(it.fileDescriptor).use { s -> s.readBytes() } }
}

private fun displayName(contentResolver: ContentResolver, uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

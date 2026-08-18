package com.stratum.core.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How long staged share files may linger before age-based cleanup. */
const val SHARE_CACHE_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

/** Maximum number of staged files retained per bucket. */
const val SHARE_CACHE_KEEP_LAST: Int = 5

/**
 * Isolated staging directories under [Context.getCacheDir] so JSON exports and
 * image shares do not prune each other under a shared keep-last budget.
 */
enum class ShareCacheBucket(val relativePath: String) {
    JSON("exports/json"),
    IMAGES("exports/images"),
}

private val UNSAFE_FILE_NAME = Regex("[^A-Za-z0-9._-]+")

/**
 * Sanitizes a raw id/name for use as a single path segment under the share cache.
 * Collapses unsafe characters so `File(parent, name)` cannot escape the bucket dir.
 */
fun sanitizeShareFileName(raw: String, fallback: String = "share"): String {
    val cleaned = raw.trim().replace(UNSAFE_FILE_NAME, "_").trim('_', '.')
    return cleaned.ifBlank { fallback }.take(120)
}

/**
 * Resolves (and creates) the staging directory for [bucket].
 */
fun shareCacheDir(context: Context, bucket: ShareCacheBucket): File =
    File(context.cacheDir, bucket.relativePath).apply { mkdirs() }

/**
 * Writes [bytes] into [bucket] as [fileName] (sanitized) and returns the staged file.
 * Prunes the bucket first. Safe to call from a background dispatcher.
 */
fun stageShareBytes(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    bucket: ShareCacheBucket,
): File {
    val dir = shareCacheDir(context, bucket)
    pruneShareCacheDir(dir)
    val safeName = sanitizeShareFileName(fileName)
    val outFile = File(dir, safeName)
    assertUnderShareDir(outFile, dir)
    outFile.writeBytes(bytes)
    return outFile
}

/**
 * Compresses [bitmap] to PNG under [ShareCacheBucket.IMAGES]. Copies HARDWARE bitmaps to
 * software ARGB first so [Bitmap.compress] succeeds. Safe to call from a background dispatcher.
 * Does not recycle the caller's [bitmap].
 */
fun stageSharePng(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): File {
    val dir = shareCacheDir(context, ShareCacheBucket.IMAGES)
    pruneShareCacheDir(dir)
    val safeName = sanitizeShareFileName(fileName)
    val outFile = File(dir, safeName)
    assertUnderShareDir(outFile, dir)

    val software =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, /* mutable = */ false)
                ?: error("Failed to copy HARDWARE bitmap for PNG share")
        } else {
            bitmap
        }
    try {
        outFile.outputStream().use { stream ->
            val ok = software.compress(Bitmap.CompressFormat.PNG, 100, stream)
            check(ok) { "PNG compress failed" }
        }
    } finally {
        if (software !== bitmap) {
            software.recycle()
        }
    }
    return outFile
}

/**
 * Opens the system share sheet for a staged file. Must run on the main thread.
 *
 * Sets [ClipData] on both the send Intent and the chooser so URI read grants propagate
 * on OEMs that ignore [Intent.EXTRA_STREAM] alone.
 *
 * @param chooserTitle human-readable chooser title (not the cache file name)
 */
fun launchShareSheet(
    context: Context,
    file: File,
    mimeType: String,
    chooserTitle: String,
) {
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.export",
            file,
        )
    val grantClip = ClipData.newUri(context.contentResolver, file.name, uri)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = grantClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(send, chooserTitle).apply {
            clipData = grantClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    context.startActivity(chooser)
}

/**
 * Stages a captured workout [ImageBitmap] as PNG under `cacheDir/exports/images/` and
 * launches the system share sheet. Safe to call from UI/main thread.
 */
suspend fun shareWorkoutImage(
    context: Context,
    imageBitmap: ImageBitmap,
    sessionId: String,
    chooserTitle: String = context.getString(com.stratum.core.ui.R.string.share_chooser_title),
) {
    val androidBitmap = imageBitmap.asAndroidBitmap()
    val fileName = "workout_share_${sanitizeShareFileName(sessionId)}.png"
    val staged =
        withContext(Dispatchers.IO) {
            stageSharePng(
                context = context,
                bitmap = androidBitmap,
                fileName = fileName,
            )
        }
    launchShareSheet(
        context = context,
        file = staged,
        mimeType = "image/png",
        chooserTitle = chooserTitle,
    )
}

/**
 * Prunes [exportDir]: always drops files past [keepLast] (newest first), and drops any
 * remaining file older than [maxAgeMs]. Avoids unbounded growth within a single day.
 */
fun pruneShareCacheDir(
    exportDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    maxAgeMs: Long = SHARE_CACHE_MAX_AGE_MS,
    keepLast: Int = SHARE_CACHE_KEEP_LAST,
) {
    val files =
        exportDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return
    files.forEachIndexed { index, file ->
        val tooOld = nowMillis - file.lastModified() > maxAgeMs
        if (index >= keepLast || tooOld) {
            file.delete()
        }
    }
}

private fun assertUnderShareDir(outFile: File, dir: File) {
    val outCanonical = outFile.canonicalFile
    val dirCanonical = dir.canonicalFile
    check(outCanonical.path.startsWith(dirCanonical.path + File.separator) || outCanonical == dirCanonical) {
        "Share path escaped staging directory: $outCanonical"
    }
}

package com.stratum.feature.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.stratum.domain.export.ExportFileResult
import java.io.File

private const val EXPORT_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
private const val EXPORT_CACHE_KEEP_LAST = 5

/**
 * Writes [file] into the app cache under `exports/` and launches a Share sheet via
 * [Intent.ACTION_SEND]. Share-only delivery (no SAF CreateDocument).
 *
 * Sets [ClipData] on both the send Intent and the chooser so URI read grants propagate
 * reliably across OEMs that ignore [Intent.EXTRA_STREAM] alone.
 */
fun shareJsonExport(context: Context, file: ExportFileResult) {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    pruneOldExportFiles(exportDir)
    val outFile = File(exportDir, file.fileName)
    outFile.writeBytes(file.bytes)
    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.export",
            outFile,
        )
    val grantClip = ClipData.newUri(context.contentResolver, file.fileName, uri)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = grantClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(send, file.fileName).apply {
            clipData = grantClip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(chooser)
}

/**
 * Keeps the most recent [EXPORT_CACHE_KEEP_LAST] export files and deletes older ones past
 * [EXPORT_CACHE_MAX_AGE_MS]. Avoids unbounded growth of share staging files in cache.
 */
internal fun pruneOldExportFiles(
    exportDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    maxAgeMs: Long = EXPORT_CACHE_MAX_AGE_MS,
    keepLast: Int = EXPORT_CACHE_KEEP_LAST,
) {
    val files =
        exportDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return
    files.forEachIndexed { index, file ->
        val tooOld = nowMillis - file.lastModified() > maxAgeMs
        if (index >= keepLast && tooOld) {
            file.delete()
        }
    }
}

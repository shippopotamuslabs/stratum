package com.stratum.feature.settings

import android.content.Context
import com.stratum.core.ui.share.ShareCacheBucket
import com.stratum.core.ui.share.launchShareSheet
import com.stratum.core.ui.share.stageShareBytes
import com.stratum.domain.export.ExportFileResult

/**
 * Stages a JSON export under `cacheDir/exports/json/` and launches the system share sheet.
 * Share-only delivery (no SAF CreateDocument).
 */
fun shareJsonExport(context: Context, file: ExportFileResult) {
    val staged =
        stageShareBytes(
            context = context,
            bytes = file.bytes,
            fileName = file.fileName,
            bucket = ShareCacheBucket.JSON,
        )
    launchShareSheet(
        context = context,
        file = staged,
        mimeType = file.mimeType,
        chooserTitle = file.fileName,
    )
}

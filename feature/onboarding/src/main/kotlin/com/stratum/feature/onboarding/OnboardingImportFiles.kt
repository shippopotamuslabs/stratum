package com.stratum.feature.onboarding

import android.content.ContentResolver
import android.net.Uri
import com.stratum.domain.importing.ImportFileInput

/**
 * Reads selected document URIs into [ImportFileInput] payloads. Kept outside composables so
 * [OnboardingRoute] stays thin and file I/O is testable without Compose.
 */
internal fun readImportFilesFromUris(
    contentResolver: ContentResolver,
    uris: List<Uri>,
): List<ImportFileInput> =
    uris.mapNotNull { uri ->
        val bytes =
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@mapNotNull null
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "import.csv"
        ImportFileInput(bytes = bytes, fileName = name)
    }

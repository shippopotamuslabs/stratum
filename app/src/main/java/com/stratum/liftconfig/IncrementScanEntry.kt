package com.stratum.liftconfig

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.ScanPhoto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Self-contained AI scan entry for the shared increments editor shell: the bottom
 * entry button, the Camera/Gallery photo-source drawer, the capture launchers,
 * and the scanning / error / retry states. The caller only gates on
 * `scanAvailable` and supplies [onScanPhoto]; a successful scan dismisses the
 * drawer and the prefilled drafts arrive through editor state (explicit save
 * still required). Permission denial and photo failures leave manual entry
 * untouched.
 */
@Composable
internal fun ScanEntrySection(
    scanUi: IncrementScanState,
    onScanPhoto: (ScanPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var cameraBlocked by rememberSaveable { mutableStateOf(false) }
    var photoUnreadable by remember { mutableStateOf(false) }
    var pendingCameraUri: Uri? by rememberSaveable { mutableStateOf(null) }

    fun processBytes(bytes: ByteArray?) {
        if (bytes == null) {
            photoUnreadable = true
            return
        }
        scope.launch(Dispatchers.IO) {
            val photo = ScanPhotoProcessor.process(bytes)
            withContext(Dispatchers.Main) {
                if (photo == null) {
                    photoUnreadable = true
                } else {
                    photoUnreadable = false
                    onScanPhoto(photo)
                }
            }
        }
    }

    fun processUri(uri: Uri?) {
        if (uri == null) return
        scope.launch(Dispatchers.IO) {
            val bytes =
                readBoundedBytes(
                    open = {
                        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                    },
                    capBytes = ScanPhotoProcessor.MAX_INPUT_BYTES,
                )
            withContext(Dispatchers.Main) { processBytes(bytes) }
        }
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = pendingCameraUri
            // A success without a URI (lost staging) must surface, never silently drop.
            if (saved && uri != null) processUri(uri) else if (saved) photoUnreadable = true
        }
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                showSheet = false
                processUri(uri)
            }
        }

    fun launchCamera() {
        cameraBlocked = false
        val uri = newScanCaptureUri(context)
        if (uri == null) {
            photoUnreadable = true
            return
        }
        pendingCameraUri = uri
        showSheet = false
        takePictureLauncher.launch(uri)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                showSheet = false
                cameraBlocked = true
            }
        }

    // Success prefills drafts through editor state — the drawer has served its purpose.
    LaunchedEffect(scanUi) {
        if (scanUi is IncrementScanState.Scanned) showSheet = false
    }

    val scanning = scanUi is IncrementScanState.Scanning
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StratumOutlinedButton(
            text = stringResource(UiR.string.lift_increments_scan_cta),
            onClick = { showSheet = true },
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        )
        if (scanning) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_scanning),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }
        if (scanUi is IncrementScanState.Failed) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_failed),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumTextButton(
                text = stringResource(UiR.string.lift_increments_scan_retry),
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (photoUnreadable) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_unreadable),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (cameraBlocked) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_camera_blocked),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSheet) {
        StratumBottomSheet(
            onDismissRequest = { showSheet = false },
            title = stringResource(UiR.string.lift_increments_scan_sheet_title),
        ) {
            ScanSourceRow(
                icon = Icons.Outlined.PhotoCamera,
                label = stringResource(UiR.string.lift_increments_scan_camera),
                enabled = !scanning,
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
            ScanSourceRow(
                icon = Icons.Outlined.Image,
                label = stringResource(UiR.string.lift_increments_scan_gallery),
                enabled = !scanning,
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }
    }
}

@Composable
private fun ScanSourceRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = !enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Staging URI for a camera capture under `cacheDir/scan`, served through the app
 * FileProvider (`${applicationId}.export`, see `export_file_paths.xml`). Unique per
 * capture so a retake can never read a stale file. Null when staging fails — the
 * caller degrades to an inline message and manual entry stays available.
 */
private fun newScanCaptureUri(context: Context): Uri? {
    val dir = File(context.cacheDir, SCAN_CACHE_SUBDIR).apply { mkdirs() }
    if (!dir.isDirectory) return null
    val file = File(dir, "$SCAN_FILE_PREFIX${System.currentTimeMillis()}$SCAN_FILE_SUFFIX")
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.$SCAN_AUTHORITY_SUFFIX", file)
    } catch (_: Exception) {
        null
    }
}

private const val SCAN_CACHE_SUBDIR = "scan"
private const val SCAN_AUTHORITY_SUFFIX = "export"
private const val SCAN_FILE_PREFIX = "scan_"
private const val SCAN_FILE_SUFFIX = ".jpg"
private const val READ_CHUNK_BYTES = 8 * 1024

/**
 * Reads [open]'s stream aborting with null once more than [capBytes] arrive, so a
 * huge pick never materializes in memory before the size guard runs. IO failures
 * also yield null. Pure JVM apart from the [InputStream] source — unit-tested.
 */
internal fun readBoundedBytes(open: () -> InputStream?, capBytes: Int): ByteArray? {
    val stream =
        try {
            open()
        } catch (_: Exception) {
            null
        } ?: return null
    return try {
        stream.use { readCapped(it, capBytes) }
    } catch (_: Exception) {
        null
    }
}

private fun readCapped(stream: InputStream, capBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(READ_CHUNK_BYTES)
    var total = 0
    while (true) {
        val read = stream.read(buf)
        if (read < 0) return out.toByteArray()
        total += read
        if (total > capBytes) return null
        out.write(buf, 0, read)
    }
}

@file:Suppress("MatchingDeclarationName")

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.lift.ScanPhoto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saveable photo-picker flags for the AI scan prefill: the photo-source drawer
 * and the transient denied/unreadable states. Restored across rotation and
 * process recreation via [saver]; launchers stay outside the saveable payload
 * and are bound by [rememberScanPickerState] instead.
 */
@Stable
internal class ScanPickerState internal constructor(
    showSheet: Boolean = false,
    cameraBlocked: Boolean = false,
    photoUnreadable: Boolean = false,
) {
    var showSheet by mutableStateOf(showSheet)
        private set
    var cameraBlocked by mutableStateOf(cameraBlocked)
        private set
    var photoUnreadable by mutableStateOf(photoUnreadable)
        private set

    fun openPicker() {
        showSheet = true
    }

    fun dismissPicker() {
        showSheet = false
    }

    internal fun markCameraBlocked() {
        cameraBlocked = true
    }

    internal fun clearCameraBlocked() {
        cameraBlocked = false
    }

    internal fun markPhotoUnreadable() {
        photoUnreadable = true
    }

    internal fun clearPhotoUnreadable() {
        photoUnreadable = false
    }

    companion object {
        fun saver(): Saver<ScanPickerState, Any> =
            listSaver(
                save = { state ->
                    listOf(state.showSheet, state.cameraBlocked, state.photoUnreadable)
                },
                restore = { (sheet, blocked, unreadable) ->
                    ScanPickerState(
                        showSheet = sheet,
                        cameraBlocked = blocked,
                        photoUnreadable = unreadable,
                    )
                },
            )
    }
}

/**
 * Picker wiring plus the launchers driving it. The flags instance is saveable
 * and stable across recompositions; this holder is rebuilt each composition
 * with fresh launcher-bound lambdas, so nothing is written during composition
 * and no callback slot can go stale.
 */
internal class ScanPicker(
    val state: ScanPickerState,
    val onPickCamera: () -> Unit,
    val onPickGallery: () -> Unit,
)

@Composable
internal fun rememberScanPickerState(
    scanUi: IncrementScanState,
    onScanPhoto: (ScanPhoto) -> Unit,
): ScanPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = ScanPickerState.saver()) { ScanPickerState() }
    var pendingCameraUri: Uri? by rememberSaveable { mutableStateOf(null) }

    fun processBytes(bytes: ByteArray?) {
        if (bytes == null) {
            state.markPhotoUnreadable()
            return
        }
        scope.launch(Dispatchers.IO) {
            val photo = ScanPhotoProcessor.process(bytes)
            withContext(Dispatchers.Main) {
                if (photo == null) {
                    state.markPhotoUnreadable()
                } else {
                    state.clearPhotoUnreadable()
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
            if (saved && uri != null) {
                processUri(uri)
            } else if (saved) {
                state.markPhotoUnreadable()
            }
        }
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                state.dismissPicker()
                processUri(uri)
            }
        }

    fun launchCamera() {
        state.clearCameraBlocked()
        val uri = newScanCaptureUri(context)
        if (uri == null) {
            state.markPhotoUnreadable()
            return
        }
        pendingCameraUri = uri
        state.dismissPicker()
        takePictureLauncher.launch(uri)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                state.dismissPicker()
                state.markCameraBlocked()
            }
        }

    // Success prefills drafts through editor state — the drawer has served its purpose.
    LaunchedEffect(scanUi) {
        if (scanUi is IncrementScanState.Scanned) state.dismissPicker()
    }

    return ScanPicker(
        state = state,
        onPickCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onPickGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}

@Composable
private fun ScanPickerSheet(
    picker: ScanPicker,
    scanUi: IncrementScanState,
) {
    if (!picker.state.showSheet) return
    val scanning = scanUi is IncrementScanState.Scanning
    StratumBottomSheet(
        onDismissRequest = { picker.state.dismissPicker() },
        title = stringResource(UiR.string.lift_increments_scan_sheet_title),
    ) {
        ScanSourceRow(
            icon = Icons.Outlined.PhotoCamera,
            label = stringResource(UiR.string.lift_increments_scan_camera),
            enabled = !scanning,
            onClick = { picker.onPickCamera() },
        )
        ScanSourceRow(
            icon = Icons.Outlined.Image,
            label = stringResource(UiR.string.lift_increments_scan_gallery),
            enabled = !scanning,
            onClick = { picker.onPickGallery() },
        )
    }
}

/**
 * Inline scanning / error / retry states shared by both entry surfaces. The
 * caller only gates on `scanAvailable` and supplies [onScanPhoto].
 */
@Composable
internal fun ScanStatusLines(
    scanUi: IncrementScanState,
    picker: ScanPicker,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        if (scanUi is IncrementScanState.Scanning) {
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
                onClick = { picker.state.openPicker() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (picker.state.photoUnreadable) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_unreadable),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (picker.state.cameraBlocked) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_camera_blocked),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Compact AI scan entry for the band-strip editors (STR-58, STR-61): a sparkle
 * action for the top app bar. The caller owns [picker] via
 * [rememberScanPickerState] and renders both this action and [ScanStatusLines]
 * in the body so scanning/errors stay visible below the bands. The
 * photo-source drawer is rendered here.
 */
@Composable
internal fun ScanIconEntry(
    picker: ScanPicker,
    scanUi: IncrementScanState,
    modifier: Modifier = Modifier,
) {
    StratumIconButton(
        onClick = { picker.state.openPicker() },
        enabled = scanUi !is IncrementScanState.Scanning,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_auto_awesome),
            contentDescription = stringResource(UiR.string.lift_increments_scan_cta),
            modifier = Modifier.size(24.dp),
        )
    }
    ScanPickerSheet(picker = picker, scanUi = scanUi)
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
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
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

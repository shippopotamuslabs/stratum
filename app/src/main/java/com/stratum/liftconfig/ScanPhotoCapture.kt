package com.stratum.liftconfig

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.stratum.domain.lift.ScanPhoto
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Camera + gallery capture for the increments AI scan. Owns the permission,
 * FileProvider URI, and IO handling; the caller processes nothing — decoded photo
 * bytes arrive via [onScanPhoto], failures via [onPhotoError], and camera
 * denial via [onPermissionDenied].
 */
@Composable
fun rememberScanPhotoCapture(
    onScanPhoto: (ScanPhoto) -> Unit,
    onPhotoError: () -> Unit,
    onPermissionDenied: () -> Unit,
): ScanCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    fun handleBytes(bytes: ByteArray?) {
        if (bytes == null) {
            onPhotoError()
            return
        }
        scope.launch(Dispatchers.IO) {
            val photo = ScanPhotoProcessor.process(bytes)
            withContext(Dispatchers.Main) {
                if (photo == null) onPhotoError() else onScanPhoto(photo)
            }
        }
    }

    fun readUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val bytes =
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            withContext(Dispatchers.Main) { handleBytes(bytes) }
        }
    }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) captureUri?.let(::readUri)
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                captureUri = newCaptureUri(context)
                captureUri?.let(cameraLauncher::launch) ?: onPhotoError()
            } else {
                onPermissionDenied()
            }
        }
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) readUri(uri)
        }

    return remember(cameraLauncher, permissionLauncher, galleryLauncher) {
        ScanCapture(
            launchCamera = {
                if (hasCameraPermission(context)) {
                    captureUri = newCaptureUri(context)
                    captureUri?.let(cameraLauncher::launch) ?: onPhotoError()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            launchGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun newCaptureUri(context: Context): Uri? {
    val dir = File(context.cacheDir, "scan").apply { mkdirs() }
    if (!dir.isDirectory) return null
    val file = File(dir, "capture.jpg")
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.export", file)
    } catch (_: Exception) {
        null
    }
}

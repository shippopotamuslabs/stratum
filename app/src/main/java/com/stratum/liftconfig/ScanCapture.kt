package com.stratum.liftconfig

/** Camera + gallery entry points owned by [rememberScanPhotoCapture]. */
data class ScanCapture(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
)

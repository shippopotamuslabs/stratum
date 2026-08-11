package com.stratum

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.stratum.core.ui.legal.LegalUrls

/**
 * Health Connect privacy-policy / permission-rationale surface.
 * Opened via [androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE] and the Android 14+
 * [android.intent.action.VIEW_PERMISSION_USAGE] activity-alias.
 */
class HealthConnectPrivacyRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(LegalUrls.PRIVACY)),
        )
        finish()
    }
}

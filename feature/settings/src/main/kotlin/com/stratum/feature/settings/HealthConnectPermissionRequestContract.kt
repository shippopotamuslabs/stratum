package com.stratum.feature.settings

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController

/**
 * Health Connect permission request that never short-circuits in the Activity Result registry.
 *
 * On API 34+ the SDK eventually delegates to
 * [androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions].
 * If that contract's [getSynchronousResult] sees every requested type as already granted, Compose
 * never starts an activity — so a later tap does nothing after the first write grant, even when
 * reads are still missing. Always returning null forces [createIntent] to run.
 */
internal class HealthConnectPermissionRequestContract :
    ActivityResultContract<Set<String>, Set<String>>() {

    private val delegate = PermissionController.createRequestPermissionResultContract()

    override fun createIntent(context: Context, input: Set<String>): Intent =
        delegate.createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> =
        delegate.parseResult(resultCode, intent)

    override fun getSynchronousResult(
        context: Context,
        input: Set<String>,
    ): SynchronousResult<Set<String>>? = null
}

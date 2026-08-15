package com.stratum.core.data.subscription

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a weak reference to the foreground [Activity] required to launch Google Play Billing bottom sheets.
 * Attached by [com.stratum.MainActivity] on create and detached on destroy.
 */
@Singleton
class BillingHostContext @Inject constructor() {
    private val hostActivityRef = AtomicReference<WeakReference<Activity>?>(null)

    fun attach(activity: Activity) {
        hostActivityRef.set(WeakReference(activity))
    }

    fun detach(activity: Activity) {
        val current = hostActivityRef.get()?.get()
        if (current === activity || current == null) {
            hostActivityRef.set(null)
        }
    }

    fun requireActivity(): Activity {
        val activity = hostActivityRef.get()?.get()
            ?: error("Google Play Billing requires an attached foreground Activity.")
        check(!activity.isFinishing && !activity.isDestroyed) {
            "Host Activity is finishing or destroyed."
        }
        return activity
    }
}

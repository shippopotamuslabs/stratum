package com.stratum.core.ui.legal

/**
 * Canonical public legal document URLs (Firebase Hosting).
 * Keep in sync with `hosting/public/` paths.
 */
object LegalUrls {
    const val BASE: String = "https://stratum-a1a5b.web.app"
    const val TERMS: String = "$BASE/terms/"
    const val PRIVACY: String = "$BASE/privacy/"
    const val OPEN_SOURCE: String = "$BASE/licenses/"
    const val DELETE_ACCOUNT: String = "$BASE/delete-account/"
}

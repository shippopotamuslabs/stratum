package com.stratum.core.ui.legal

/**
 * Canonical public legal document URLs (Shippopotamus Labs website).
 * The pages moved from Firebase Hosting (stratum-a1a5b.web.app) to the
 * Labs site under shippopotamuslabs.com/stratum (STR-217); the old paths redirect.
 */
object LegalUrls {
    const val BASE: String = "https://shippopotamuslabs.com/stratum"
    const val TERMS: String = "$BASE/terms"
    const val PRIVACY: String = "$BASE/privacy"
    const val OPEN_SOURCE: String = "$BASE/licenses"
    const val DELETE_ACCOUNT: String = "$BASE/delete-account"
}

package com.stratum.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.stratum.core.ui.R

/**
 * Roboto Flex via the Google Fonts downloadable provider — the gospel font
 * step (UI_DESIGN §12.2 names Roboto Flex / Google Sans Flex as the family;
 * STR-147). Only the weights the gospel scale uses are requested:
 * Normal 400 (display/headline/body), Medium 500 (titles/labels),
 * SemiBold 600 (titleLarge).
 *
 * Resolution is asynchronous and failure-tolerant by design: text renders
 * immediately in the platform default and swaps when the download lands, and
 * where the provider cannot resolve at all (no Play services, offline first
 * run, Robolectric screenshot tests) the text simply stays in
 * [FontFamily.Default]. That fallback is the ticket's cold-start contract:
 * never blank, never blocked on a font download.
 */
private val RobotoFlexProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

private val RobotoFlexGoogleFont = GoogleFont("Roboto Flex")

internal val RobotoFlexFontFamily =
    FontFamily(
        Font(googleFont = RobotoFlexGoogleFont, fontProvider = RobotoFlexProvider, weight = FontWeight.Normal),
        Font(googleFont = RobotoFlexGoogleFont, fontProvider = RobotoFlexProvider, weight = FontWeight.Medium),
        Font(googleFont = RobotoFlexGoogleFont, fontProvider = RobotoFlexProvider, weight = FontWeight.SemiBold),
    )

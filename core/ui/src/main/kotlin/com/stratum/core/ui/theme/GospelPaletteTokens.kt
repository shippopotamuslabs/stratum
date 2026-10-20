package com.stratum.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Named gospel color tokens for the Stratum Tonal (Material) family.
 *
 * Single source of token identity for the Shippopotamus Palette Gospel (§11 of
 * `contracts/UI_DESIGN.md`) plus the Light Palette Gospel (PALETTE-LIGHT), per Sean's
 * 2026-10-19 ratification (STR-138). [StratumTonalSpec] references these vals so the
 * canon lives once as code; the raw hex literals below are the canon.
 */
object GospelPaletteTokens {
    object Dark {
        // Gospel indigo family (§11.2): base / on-accent / deep (muted).
        // onAccent is bg-base, not white: #FFFFFF on #7A7CFF is only
        // 3.4:1, while #0D0E11 reaches 5.7:1 (WCAG AA).
        val Accent = Color(0xFF7A7CFF)
        val OnAccent = Color(0xFF0D0E11)
        val AccentMuted = Color(0xFF3B3BAA)
        val SurfaceBase = Color(0xFF121212)
        val SurfaceRaised = Color(0xFF1E1E1E)
        val SurfaceInset = Color(0xFF282828)
        val SurfaceSunken = Color(0xFF0E0E0E)
        // Gospel border ramp: Stratum's two roles map to border-strong /
        // border-default (gospel border-subtle has no Stratum role).
        val BorderStrong = Color(0xFF343A48)
        val BorderSubtle = Color(0xFF2A2E39)
        // Gospel text ramp: primary / secondary; textData has no gospel
        // counterpart and tracks text-primary ink (tabular figures carry
        // the data distinction, not hue).
        val TextPrimary = Color(0xFFEDEEF0)
        val TextMuted = Color(0xFFA1A5B0)
        val TextData = Color(0xFFEDEEF0)
        val Error = Color(0xFFEF4444)
        val OnError = Color(0xFFFFFFFF)
        val SetComplete = Color(0xFF1B3D2F)
        val PrHighlight = Color(0xFFE0B040)
        val Warning = Color(0xFFFBBF24)
    }

    object Light {
        // Light Gospel indigo family (PALETTE-LIGHT): base carries white
        // text at 5.4:1; muted maps to the family's deep step.
        val Accent = Color(0xFF5457E8)
        val OnAccent = Color(0xFFFFFFFF)
        val AccentMuted = Color(0xFF2F31A0)
        // Light Gospel layers: grey canvas, white cards, tonal fills,
        // bg-subtle wells — elevation climbs toward white.
        val SurfaceBase = Color(0xFFF4F4F8)
        val SurfaceRaised = Color(0xFFFFFFFF)
        val SurfaceInset = Color(0xFFEFEFF4)
        val SurfaceSunken = Color(0xFFECECF1)
        // Light Gospel borders: strong meets WCAG 1.4.11 (3:1) for
        // interactive boundaries; subtle maps to border-default.
        val BorderStrong = Color(0xFF83858F)
        val BorderSubtle = Color(0xFFC6C7D1)
        // Light Gospel text ramp: primary (15.5:1) / secondary (AAA);
        // textData tracks text-primary, as in dark.
        val TextPrimary = Color(0xFF1B1C22)
        val TextMuted = Color(0xFF494B5C)
        val TextData = Color(0xFF1B1C22)
        val Error = Color(0xFFDC2626)
        val OnError = Color(0xFFFFFFFF)
        val SetComplete = Color(0xFFE8F5E9)
        val PrHighlight = Color(0xFF8A6708)
        val Warning = Color(0xFFB45309)
    }
}

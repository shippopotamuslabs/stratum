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
        // Gospel dark ramp (§11.2): bg-base / surface-1 / surface-3 /
        // bg-subtle. surfaceInset takes surface-3 (not surface-2) so
        // inset wells stay distinct from pressed cards on OLED (STR-140).
        val SurfaceBase = Color(0xFF0D0E11)
        val SurfaceRaised = Color(0xFF181A20)
        val SurfaceInset = Color(0xFF272A33)
        val SurfaceSunken = Color(0xFF111216)
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
        // Gospel danger-soft-red (§11.2). onError stays bg-base ink:
        // #FFFFFF on #E07A7A is only ~3:1, while #0D0E11 reaches ~7:1.
        val Error = Color(0xFFE07A7A)
        val OnError = Color(0xFF0D0E11)
        // setComplete REMAINS A FILL: gospel success #7DBE8C mixed 30/70
        // toward surface-1 #181A20 → #364B40.
        val SetComplete = Color(0xFF364B40)
        // PR gold keeps the saturated family, not warning-amber (STR-140).
        val PrHighlight = Color(0xFFE0B040)
        // Gospel warning-amber (§11.2).
        val Warning = Color(0xFFD9B07A)
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
        // Light gospel semantics (§11.4, verified in STR-140).
        val Error = Color(0xFFC02E3A)
        val OnError = Color(0xFFFFFFFF)
        // Fill tint of gospel success #2E7D43: 12% on white → #E6EFE8.
        val SetComplete = Color(0xFFE6EFE8)
        val PrHighlight = Color(0xFF8A6708)
        val Warning = Color(0xFF9C6B10)
    }
}

# UI_DESIGN.md
**Stratum — UI Design Contract v1.0**

Immutable UI contract for Android Compose. Defines theme families, token architecture,
component boundaries, and agent workflow.

Read before generating or restyling any UI. Enforced by Detekt rule set `stratum`
(see `config/detekt/detekt.yml` and `:detekt-rules`).

Pair with `AGENTS.md` for architecture and validation commands.

**§11–§12 embed the Shippopotamus gospels in full** — the Color Palette Gospel and the
Android Jetpack Compose Gospel (declared canonical by Sean, 2026-10-19, for Bloviate,
Stratum, Enclave, and all future Shippopotamus products), plus the Button Shape Gospel
(declared canonical by Sean, 2026-10-20, embedded in §12.7 under his Contract Waiver for
that edit) and the Glass Bars Gospel (Haze 2.0; declared canonical by Sean, 2026-10-20,
embedded in §12.8 under the same waiver). They govern all **new** UI work.
Where §§1–10 conflict with §§11–12, the gospels win for new work. The STR-137 conflict
register was **ratified by Sean on 2026-10-19** and executed in STR-138; the adoption
status in §11.3 / §12.6 records what was migrated, what is a sanctioned carve-out (Neo
Brutalist), and the standing differences that remain (shipped screens are not silently
redesigned).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use `StratumTheme` + semantic tokens (`StratumTheme.colors.*`). | Read `MaterialTheme.colorScheme` in `:feature:*` or `:app` UI code. |
| Use `Stratum*` components from `:core:ui` for product chrome. | Use raw `Button`, `Card`, `OutlinedTextField`, or M3 surfaces in features. |
| Define colors in theme specs only (`StratumTonalSpec`, `NeoBrutalistSpec`). | Hard-code hex literals in feature modules (except previews/tests — see §6). |
| Support theme **family** swap (Tonal, NeoBrutalist) + light/dark/system. | Name public components `Neo*` or `Material*` — use neutral `Stratum*` API. |
| Use tabular figures for weights, reps, RPE, volume, and PR values. | Use playful accents (Enclave peach, neon hazard palettes). |
| Dark-first layouts optimized for gym use (OLED, one-handed logging). | Glassmorphism, soft marketing shadows, landing-page hero layouts. |
| Include Light + Dark `@Preview` for new/changed composables. | Inject real ViewModels or Hilt into `@Preview` functions. |
| Run Design Read (§7.1) and Pre-Flight (§7.3) before completing UI tasks. | Ship industrial/CRT brutalism, scanlines, or terminal-aesthetic chrome. |

**Violation response (agents):** Stop and fix before completing the task. Detekt must pass with `maxIssues: 0`.

---

## 1. Design Intent

Stratum is a **quietly competent training partner** — data-forward, gym-first, not chatty.

Two supported **theme families** share one component API:

| Family | Role | Character |
|--------|------|-----------|
| **Stratum Tonal** (default ship target) | Restrained Material 3 baseline | Charcoal/slate surfaces, single accent, instrument-panel density |
| **Neo Brutalist** (optional / user-selectable) | Concrete + iron product chrome | Hard borders, offset shadows, 4dp corners — **not** Enclave peach |

Both families map the **same semantic color roles** (§3). Geometry and elevation differ via `StratumThemeSpec` (§2).

### 1.1 Neo Brutalist — Stratum variant (not Enclave)

Product neo-brutalism for lifting: **concrete, iron, plate-loaded** — friendly enough for daily logging, bold enough to stand apart from Fitbod/Hevy.

**Do:**

- Charcoal / concrete gray surfaces (`#1A1A1A`–`#2D2D2D` dark; `#E8E8E8`–`#F4F4F4` light)
- **Iron / rust accent** (e.g. `#C45C26` amber-rust, `#B87333` copper) **or** **steel accent** (e.g. `#5B7C99`) — pick one primary accent per spec, locked per theme
- 2dp solid borders, hard offset shadows (no blur)
- 4dp corner radius on panels, chips, and buttons (set-log rows may use 8dp for touch comfort)

**Do not:**

- Peach / coral primary (Enclave lock — forbidden)
- Neon green, hazard-yellow as primary accent
- CRT scanlines, ASCII frames, Swiss poster hazard red as dominant chrome

**Anti-purple repeal (2026-10-19):** The former house anti-purple rule is **repealed**.
The Material/Tonal family's accent is the gospel indigo family (§11). Neo's accent stays
iron/rust under the sanctioned carve-out (§11.3) — a family identity choice, not the
repealed ban.

### 1.2 Stratum Tonal (default)

- Material 3 tonal surfaces with **subtle stratum dividers** (horizontal banding motif — optional 1dp `surfaceSunken` stripes, not neo borders)
- 12–16dp corners on cards; **buttons full pill** (§12.3); tonal elevation instead of hard shadow
- Accent is the **gospel indigo family** (§11; light: §11.4) per Sean's 2026-10-19 ratification
- Same semantic palette roles as Neo — different geometry/elevation only

---

## 2. Theme Architecture (mandatory)

Feature modules depend on **semantic theme**, not Material or neo implementation details.

### 2.1 Core types (`:core:ui/theme/`)

```text
StratumThemeId          enum: Tonal, NeoBrutalist
ColorMode               enum: System, Light, Dark
StratumThemeSpec        immutable token bundle
StratumColors           semantic colors (§3)
StratumShapes           corners, border widths
StratumElevations       Material tonal | Neo hard-offset (sealed)
StratumTypography       includes tabular number styles
ThemePreferences        family + colorMode → persisted (DataStore)
```

`StratumTheme { }` is the **only** root theme composable. It:

1. Resolves `StratumThemeSpec` from `ThemePreferences` + system dark mode
2. Provides `LocalStratumTheme` (custom composition local)
3. May wrap `MaterialTheme` **internally** for M3 defaults — **features must not import MaterialTheme**

### 2.2 Theme spec files

| File | Purpose |
|------|---------|
| `StratumTonalSpec.kt` | Default launch family |
| `NeoBrutalistSpec.kt` | Iron/concrete neo family |
| `StratumThemeSpecFactory.kt` | `(StratumThemeId, dark: Boolean) → StratumThemeSpec` |

Adding a third family = **new spec file only** — no feature rewrites if §4 was followed.

### 2.3 Elevation model

```kotlin
sealed interface StratumShadowStyle {
    data class Tonal(val dp: Dp) : StratumShadowStyle
    data class HardOffset(val offset: Dp, val color: Color) : StratumShadowStyle
}
```

`StratumSurface` and `StratumButton` render shadows from `LocalStratumTheme.current.elevations` — never branch on `StratumThemeId` in feature code.

---

## 3. Semantic Colors

Features reference **roles**, never hex or Material slots.

| Role | Use |
|------|-----|
| `accent` / `onAccent` | Primary actions, active tab, key CTA |
| `accentMuted` | Secondary emphasis |
| `surfaceBase` | Screen background |
| `surfaceRaised` | Cards, panels, bottom sheets |
| `surfaceSunken` | Wells, inset track areas, stratum bands |
| `borderStrong` / `borderSubtle` | Neo borders vs tonal dividers |
| `textPrimary` / `textMuted` | Body vs metadata |
| `textData` | Weights, reps — pair with tabular typography |
| `setComplete` | Logged set success |
| `setFailed` / `setSkipped` | Failed or skipped set |
| `prHighlight` | Personal record |
| `stallWarning` | Plateau / stall suggestion |
| `deloadInfo` | Deload recommendation |
| `error` / `onError` | Destructive / validation |
| `success` | Non-PR positive confirmation |

Each `StratumThemeSpec` maps roles to concrete colors for light and dark.

---

## 4. Component API (`:core:ui/component/`)

Features **only** import from `com.stratum.core.ui.component` and `com.stratum.core.ui.theme`.

### 4.1 Required primitives (implement before feature UI)

| Component | Replaces |
|-----------|----------|
| `StratumTheme` | Root theme wrapper |
| `StratumSurface` | `Card`, raw `Surface` + borders |
| `StratumButton` / `StratumIconButton` | M3 button variants |
| `StratumTextField` | `OutlinedTextField`, `TextField` |
| `StratumChip` / `StratumFilterChip` | M3 chips |
| `StratumTopBar` | `TopAppBar` direct usage |
| `StratumListRow` | Settings-style rows |
| `StratumDivider` | Stratum band divider |
| `StratumBottomSheet` | Raw modal bottom sheet chrome |
| `StratumNumber` | Tabular weight/rep/RPE display |
| `StratumSegmentedControl` | Exercise/set mode switches |

Domain-specific composables (e.g. `SetLoggerRow`) live in `:feature:*` but **must** compose `Stratum*` primitives — no raw M3 chrome.

### 4.2 Module import rules

| Module | MaterialTheme / raw M3 | StratumTheme / Stratum* |
|--------|------------------------|-------------------------|
| `:core:ui/theme` | Allowed (bridge implementation) | Defines API |
| `:core:ui/component` | Allowed (implementation detail) | Public API |
| `:feature:*` | **Forbidden** (Detekt) | **Required** |
| `:app` | **Forbidden** in UI code | **Required** — `StratumTheme { }` at root |
| `:core:domain` | N/A — no Compose | N/A |

---

## 5. Typography & Data Display

- **Labels / body:** Material 3 scale via `StratumTypography.body*`
- **Data (weight, reps, RPE, volume, e1RM):** `StratumTypography.data*` with `FontFeatureSettings("tnum")` / `FontFamily.Monospace` or tabular sans
- **Section headers:** `StratumTypography.sectionLabel` — small caps or letter-spacing from spec, not arbitrary `12.sp`

Minimum touch target: **48dp** for in-workout controls (log set, +2.5, complete, rest timer).

---

## 6. Previews & Tests

- `@Preview` must wrap content in `StratumTheme(themeId = …)` — never bare `MaterialTheme`
- Preview parameters should cover **Tonal + Neo** and **Light + Dark** for shared components
- Unit tests in `:core:ui` may assert token resolution; feature tests use fakes — no Detekt enforcement in `src/test`

---

## 7. Agent UI Workflow

### 7.1 Design Read (before code)

> *Reading this as: [surface] on [screen], [greenfield | extend | preserve], via [Stratum* component / semantic token].*

If ambiguous between preserve and overhaul, ask **once**. Otherwise declare the Design Read and proceed.

**Anti-default discipline:** No Enclave peach, no generic three-card feature grids. (The house accent is the gospel indigo family, §11 — the old anti-purple rule was repealed 2026-10-19.)

### 7.2 Extend before invent

1. Existing `Stratum*` component
2. New token in `StratumThemeSpec` (if cross-family)
3. New `Stratum*` in `:core:ui/component` (if repeats across ≥2 screens)
4. One-off in feature (last resort — document why)

### 7.3 Pre-Flight (before task complete)

- [ ] Design Read stated
- [ ] No forbidden imports in `:feature:*` / `:app` (Detekt `stratum` rule set)
- [ ] No raw hex in feature modules
- [ ] Semantic colors only (`StratumTheme.colors.*`)
- [ ] Tabular data uses `StratumNumber` or `textData` style
- [ ] In-workout controls ≥ 48dp
- [ ] Light + Dark previews for changed composables
- [ ] Empty / loading / error states styled (not blank)
- [ ] WCAG AA contrast on primary actions

---

## 8. Detekt Enforcement

Rule set **`stratum`** in `:detekt-rules` (wired in root `build.gradle.kts`):

| Rule | Behavior |
|------|----------|
| `ForbiddenMaterialComposeInFeatures` | Fail on forbidden M3 imports under `feature/` and `app/` |
| `ForbiddenRawColorLiteralInFeatures` | Fail on `Color(0x…)` / `Color(0X…)` in feature UI code |

Allowed paths (exempt): `core/ui/theme/`, `core/ui/component/`, `detekt-rules/`, `**/test/**`, `**/androidTest/**`

Forbidden imports (non-exhaustive — see `ForbiddenMaterialComposeInFeatures.kt`):

- `androidx.compose.material3.MaterialTheme`
- `androidx.compose.material3.Button`, `Card`, `TextField`, `OutlinedTextField`, `*Button`, `Surface` (material3)
- `androidx.compose.material.*` (Material 2)

Run: `./gradlew detekt` — **`maxIssues: 0`** (see `config/detekt/detekt.yml`).

When Android Studio creates the Gradle project, apply `gradle/detekt-convention.gradle.kts` (see file header).

---

## 9. User Theme Preferences (product)

```kotlin
data class ThemePreferences(
    val family: StratumThemeId = StratumThemeId.Tonal,
    val colorMode: ColorMode = ColorMode.System,
)
```

Settings UI exposes **Theme family** (Tonal / Neo Brutalist) and **Appearance** (System / Light / Dark). Changing family recomposes via `StratumThemeSpecFactory` — no activity restart.

Ship **Tonal** first; **Neo Brutalist** spec must exist in codebase early so both specs stay in parity during development.

---

## 10. Maintenance

Review this contract when:

- Adding a theme family or semantic color role
- Adding a new `Stratum*` primitive
- Changing Detekt forbidden import list
- Android Studio module layout changes

**Contract waiver:** Explicit user message **"Contract Waiver"** required to bypass this file.

---

## 11. Shippopotamus Color Palette Gospel — Official Dark Theme (canonical, embedded in full)

Declared canonical by Sean, 2026-10-19. Status: CANONICAL FOR ALL APPS (Bloviate, Stratum,
Enclave, and all future Shippopotamus products). Embedded here in full per Sean's directive
so the system is enforced wherever Stratum is worked on and can never be lost. Where any
mockup/image disagrees with this text, THIS TEXT WINS.

### 11.1 Why (empirical basis)

1. **No pure black/white halation:** #000 on #FFF is 21:1 and glows for ~50% of users with
   astigmatism. Base #0D0E11 with text #EDEEF0 = 15.8:1, the Material/Google sweet spot.
2. **Elevation via lightness, not shadows** (shadows vanish on dark). Each surface 4–8%
   lighter than the one below. The Linear/Raycast/Vercel convergence.
3. **Accents desaturated 20–30%** (saturation 40–60% max) so they don't vibrate on dark.
   Indigo: most universally preferred, least halation-prone.
4. **Warm off-white** (#EDEEF0, 87% white) over blue-white for eye fatigue. Secondary text
   stays WCAG AA 4.5:1+.

### 11.2 Tokens

**Background layers**

- bg-base (app canvas, deepest): #0D0E11
- bg-subtle (navbar, tab bar): #111216
- surface-1 (cards, sheets): #181A20
- surface-2 (elevated/pressed): #1F222A
- surface-3 (hover, modal, popover): #272A33

**Borders / dividers**

- border-default: #2A2E39
- border-strong: #343A48
- border-subtle: #20222E

**Text layers**

- text-primary (87% white, 15.8:1): #EDEEF0
- text-high (secondary headings): #C5C7DD
- text-secondary (body secondary): #A1A5B0
- text-tertiary (captions, metadata): #858994
- text-faint (icons, disabled, placeholder): #6E7280

**Indigo accent family (primary action)**

- accent-base: #7A7CFF
- accent-hover: #8D8FFF
- accent-active: #5F62B9
- accent-border-subtle: #4B4EBF
- accent-deep: #3B3BAA
- accent-muted-bg: rgba(122,124,255,0.15)
- focus-ring: rgba(122,124,255,0.40)

**Semantic colors (all desaturated ≤60%)**

- success-muted-green: #7DBE8C
- warning-amber: #D9B07A
- danger-soft-red: #E07A7A
- info-sky-blue: #7BB5D6

**Mobile extras**

- bg-scrim: rgba(13,14,17,0.80)
- surface-overlay: #1F222AF2

### 11.3 Adoption status in Stratum — RATIFIED (Sean, 2026-10-19; executed in STR-138)

Sean ruled on the full STR-137 conflict register on 2026-10-19. The rulings are binding:

1. **Neo Brutalist is a sanctioned, permanent carve-out.** The gospels govern the
   Material/Tonal theme family only. Neo keeps its 4dp corners, hard offset shadows,
   2dp borders, and iron/rust accent as its identity — permanently, not as a pending
   conflict. `NeoBrutalistSpec` is never re-tokened to the gospel.
2. **The anti-purple rule is repealed** (struck in §1.1, §7.1, and the Strict Boundaries
   table). The Material/Tonal accent is the gospel indigo family.
3. **Light theme stays**, and its canon is the Light Palette Gospel (§11.4).

Migrated in STR-138 (`StratumTonalSpec`, Tonal family only):

- **Accent:** dark steel `#5B7C99` → gospel indigo **#7A7CFF** (on-accent `#FFFFFF` →
  bg-base **#0D0E11** — white on the indigo is only 3.4:1; muted `#3D5A72` → accent-deep
  **#3B3BAA**). Light steel `#5B7C99` → light-gospel indigo **#5457E8** (muted → accent-deep
  **#2F31A0**).
- **Borders (dark):** `#4A4A4A` / `#3A3A3A` → gospel **#343A48** (border-strong) /
  **#2A2E39** (border-default). Stratum's two border roles map to gospel border-strong /
  border-default; gospel border-subtle (hairline) has no Stratum role.
- **Text (dark):** `#F2F2F2` / `#9CA3AF` → gospel **#EDEEF0** (text-primary) /
  **#A1A5B0** (text-secondary). `textData` has no gospel counterpart and now tracks
  text-primary ink in both modes (tabular figures carry the data distinction, not hue).
- **Light spec:** adopts the Light Gospel tokens in full (§11.4) — surfaces, borders,
  text ramp, accent family.

Standing differences (deliberately NOT migrated; each needs its own ruling or follow-up):

- **Dark surface ramp:** Tonal dark surfaces (`#121212` / `#1E1E1E` / `#282828` /
  `#0E0E0E`) predate the gospel ramp (`#0D0E11` → `#272A33`) and were outside the
  ratification sweep. They remain until Sean rules or a follow-up ticket migrates them.
- **Semantic hues:** gospel success/warning/danger/info vs Stratum's `setComplete`
  (a dark *fill* in dark mode, not a foreground), `error`, `prHighlight`, `warning`.
  The usage shapes differ (fill vs foreground), so this is a role decision, not a hex
  swap — deferred.
- Already aligned before ratification: tonal (non-shadow) elevation in the Tonal family
  (§1.2, §2.3); 48dp minimum touch targets for in-workout controls (§5 — the gospel
  extends this to *every* interactive, §12.5; widening is a follow-up).

### 11.4 Light Palette Gospel — Official Light Theme (canonical, embedded)

Ratified by Sean, 2026-10-19 (companion to §11; canonical source: the Shippopotamus
Light Palette Gospel). Same token names as §11.2, re-derived for light: white cards on a
grey canvas, cool indigo-grey neutrals, the safe accent step **#5457E8**, and text-faint
at ~3.3:1 restricted to disabled/incidental use.

**Why (condensed from the canon):** Light mode is the astigmatism-friendly polarity —
halation is a dark-mode disease; light's enemy is glare at maximum contrast, so the 21:1
pure pairing is banned from this side too (text-primary lands at 15.5:1, the same zone as
dark's 15.8:1). The dark accent #7A7CFF measures only 3.4:1 on white and physically
cannot cross over, so the accent is re-derived deeper at the same hue. Elevation inverts:
shadows return and surfaces climb toward white instead of away from black.

**Tokens**

**Background layers**

- bg-base (app canvas, deepest): #F4F4F8
- bg-subtle (navbar, tab bar): #ECECF1
- surface-1 (cards, sheets): #FFFFFF
- surface-2 (hover/pressed fills, tonal containers): #EFEFF4
- surface-3 (modal, popover): #FFFFFF (shadow, not lightness, separates the top layer)

**Borders / dividers** (on light, borders are *darker* than the surface)

- border-subtle (hairline dividers, decorative only): #E2E2EA
- border-default (cards, resting inputs): #C6C7D1
- border-strong (interactive boundaries — meets WCAG 1.4.11's 3:1): #83858F

**Text layers**

- text-primary (15.5:1 on bg-base): #1B1C22
- text-high (secondary headings): #2C2E3E
- text-secondary (body secondary — AAA): #494B5C
- text-tertiary (captions, metadata — AA): #63667A
- text-faint (icons, disabled, placeholder — ~3.3:1, NON-text grade only): #83868F

**Indigo accent family** (state direction inverts: hover/active go *darker*)

- accent-base: #5457E8
- accent-hover: #4849D6
- accent-active: #3739B8
- accent-border-subtle: #7A7CFF (the dark gospel's base, demoted to a boundary tint)
- accent-deep: #2F31A0
- accent-muted-bg (12% opacity): rgba(84,87,232,0.12)
- focus-ring: rgba(84,87,232,0.60) — render critical focus in solid accent-base where the
  platform allows (alpha composites weaker on light)

**Semantic colors (text-grade on white; NOT yet adopted into Stratum roles — see §11.3)**

- success-muted-green: #2E7D43
- warning-amber: #9C6B10
- danger-soft-red: #C02E3A
- info-sky-blue: #1F6FB4

**Mobile extras**

- bg-scrim: rgba(13,14,17,0.45)
- surface-overlay: #FFFFFFF2

**Stratum adoption (Tonal light spec):** `surfaceBase` = bg-base, `surfaceRaised` =
surface-1, `surfaceInset` = surface-2, `surfaceSunken` = bg-subtle, `borderStrong` =
border-strong, `borderSubtle` = border-default, `textPrimary` / `textMuted` =
text-primary / text-secondary, `accent` / `accentMuted` = accent-base / accent-deep.

---

## 12. Shippopotamus Android Jetpack Compose Gospel — Official UI System (canonical, embedded in full)

Declared canonical by Sean, 2026-10-19. Material 3 + M3 Expressive (Android 16 QPR1).
Canonical for BLOVIATE / STRATUM / ENCLAVE. Companion: §11 (colors).

### 12.1 Why

1. **8dp grid + 4dp micro:** consistent spacing per Material; everything aligns, no random
   5dp/7dp.
2. **48dp touch targets are law:** `Modifier.minimumInteractiveComponentSize()` on custom
   interactives; hit area auto-expands to 48dp even when the visual is smaller.
3. **Elevation is tint, not shadow, in dark:** surface tint washes; raised = lighter than
   base in dark mode.
4. **Shape system = hierarchy:** nested radius = outer radius − padding (28dp card + 12dp
   padding → 16dp inner). Buttons capsule; cards fixed radii. Don't invent radii.
5. **M3 Expressive is the baseline:** morphable shapes, spring physics default
   (`MotionScheme.expressive()`), emphasized typography.

### 12.2 Foundation

**Spacing & layout**

- Base unit 4dp; major grid 8dp (margins, section gaps, card padding multiples of 8);
  micro 4dp (icon-to-text, chip gaps).
- Screen margin 16dp mobile / 24dp tablet. Content max-width 600dp centered on tablet.
- Vertical rhythm: 8 / 16 / 24 / 32 / 48 / 64. NEVER 5, 7, 10, 15.
- Spacing object: s4, s8, s12, s16, s24, s32, s48, s64 — only these values.
  (In Stratum code: `StratumSpacing` in `:core:ui/theme`.)
- Stacked button pairs: 8dp (s8) between stacked full-width buttons (CTA stacks,
  bottom-bar action pairs). The 4dp micro value is only for inside a component
  (icon-to-text, chip gaps), never between two controls; section rhythm (16dp)
  separates sections, not buttons in a pair.

**Shapes (corner radius)**

- extraSmall 4dp (text field top, chips, small containers), small 8dp (default
  cards/menus), medium 12dp (cards, dialogs, sheets), large 16dp (large cards, FAB),
  extraLarge 28dp (bottom sheets, large dialogs, hero), full = pill (buttons, FABs,
  toggle track, active nav indicator).
- Nested rule: child radius = parent radius − padding.
- Expressive: Cookie4Sided, Clover, Squircle, Arch for loaders/FAB morph/press morph.
  Buttons morph pill → 12dp + scale 0.96 on press, Spring.StiffnessHigh (pressed shape
  per the Button Shape Gospel, §12.7).

**Typography (M3 scale; font: Shipopotamus Sans, fallback Roboto Flex / Google Sans Flex variable)**

- displayLarge 57/64/−0.25/400 (hero only), displayMedium 45/52, displaySmall 36/44
- headlineLarge 32/40/400, headlineMedium 28/36, headlineSmall 24/32
- titleLarge 22/28/500 SemiBold (card titles, top bar; emphasized in Expressive),
  titleMedium 16/24/500, titleSmall 14/20/500
- labelLarge 14/20/500 (ALL button labels), labelMedium 12/16/500, labelSmall 11/16/500
- bodyLarge 16/24/400 (main reading), bodyMedium 14/20/400 (default text-field input),
  bodySmall 12/16/400 (captions)
- Dark astigmatism rule: never weight 300 on dark; 400–500 body, 600 emphasis;
  letter-spacing +0.15 for bodySmall.

**Elevation (dark)**

- No drop shadows in dark. Tonal: 0dp bg-base (no tint); 1dp surface-1 (5% primary tint);
  3dp surface-2 (8%); 6dp surface-3 (11%). Use surfaceContainer / High / Highest;
  surfaceTint = accent.

### 12.3 Component specs (defaults)

- **BUTTONS:** height 40dp (Large/primary CTA 56dp), full pill, labelLarge, padding 24dp
  text-only / 16dp with leading icon, icon 18dp, gap 8dp. Emphasis: Filled → Tonal →
  Elevated → Outlined → Text. Filled: accent container; Tonal: accent-muted container,
  text-primary on; Outlined: 1dp border-default, no fill; Text: 12dp horizontal padding.
  Icon button 40×40, icon 24dp. Segmented/ToggleButton 40dp, selected = tonal. Press:
  corner morph 50%→12dp + scale 0.96 + ripple (spring). Button shape language per §12.7:
  full pill by default; the square variant (12dp medium / 16dp large) only for
  toolbar/secondary/dense contexts — never mixed randomly in the same row; one screen =
  one button shape language.
- **FAB:** small 40 / medium 56 / large 96; shape large 16dp or full (extended); accent
  container, icon 24dp. Extended FAB 56dp height, labelLarge, 16dp icon gap.
- **TEXT FIELDS:** height 56dp; padding 8dp vertical, 16dp horizontal (12dp with icons);
  icon-to-text gap 16dp; supporting-text top gap 4dp. Filled: surface-2 container,
  extraSmall top-only radius, active indicator 2dp accent / inactive 1dp. Outlined:
  transparent, 1dp border-default (2dp accent focused), extraSmall all corners, label
  padding 4dp. Input bodyLarge; label floats bodySmall→labelSmall; supporting bodySmall.
  Never pure-white label: text-secondary.
- **CARDS:** Elevated = surface-1 + 1dp tonal; Filled = surface-2 flat; Outlined =
  transparent + 1dp border. Shape medium 12dp (large 16dp hero), padding 16dp, click =
  ripple + elevate to 3dp. Nested inner radius = 12 − 8 = 4dp.
- **CHIPS:** all 32dp height, label labelLarge, horizontal padding 16dp, icon gap 8dp.
  Assist: small 8dp, 1dp outline. Filter: small, check 18dp, selected = tonal. Input:
  avatar 24dp + close 18dp. Suggestion: tonal.
- **SWITCH:** track 32dp high × 52dp wide, full pill; thumb 24dp; inactive track
  border-default color; active track accent; thumb = the on-accent content token
  (dark: bg-base ink, 5.7:1 on the bright track — white reads only 3.4:1 and was
  rejected in the palette gospel; light: white); touch auto-48dp.
- **CHECKBOX/RADIO:** checkbox geometry is the M3 default (18dp box, 2dp corner),
  adopted as-is (Sean, 2026-10-21: no custom control for a 20dp/4dp variant);
  48dp touch; radio full; active accent.
- **SLIDER:** track 4dp, active accent, inactive border-default, thumb 20dp, halo 40dp
  press, 48dp touch height.
- **BOTTOM SHEET:** extraLarge 28dp top-only, surface-3 container at 6dp/11% tint, drag
  handle 32×4 #6E7280, scrim palette scrim (§11.2 mobile extras). **DIALOG:** extraLarge
  28dp, surface-3, padding 24dp, button gap 8dp, title titleLarge. (A Haze sheet instead
  follows §12.8: `hazeChild` + `ShippopotamusHazeSheet` + surface-3 tint, no opaque
  scrim.)
- **NAVIGATION:** bar 80dp height, 3–5 items, active indicator 32dp full pill, icon 24dp,
  label labelMedium; container **glass per §12.8** (always glass, never solid —
  supersedes the earlier bg-subtle/surface-1 solid container). Rail 80dp wide. TopAppBar
  small 64dp (titleLarge), medium 112dp, large 152dp — transparent at rest, glass when
  scrolled, per §12.8.
- **LIST ITEM:** one-line 56dp / two-line 72dp / three-line 88dp; leading icon 24dp,
  padding 16dp, gap 16dp; title titleMedium, subtitle bodyMedium text-secondary.

### 12.4 Theme.kt shape (canonical sketch)

- `darkColorScheme(surface = #0D0E11 [bg-base], surfaceContainer = #181A20,
  surfaceContainerHigh = #1F222A, surfaceContainerHighest = #272A33, surfaceTint =
  #7A7CFF, primary = #7A7CFF, outline = #2A2E39)` + palette gospel hexes (§11.2).
- Typography per the scale above; `Shapes(extraSmall 4, small 8, medium 12, large 16,
  extraLarge 28)` RoundedCornerShape.
- Component defaults: buttons 40dp (56 CTA) CircleShape, contentPadding 24dp, labelLarge;
  text fields min 56dp, top 4dp shape; cards 12dp + 1dp tonal; switch track 32/thumb 24;
  minimumInteractiveComponentSize everywhere; Spacing object only.

### 12.5 Accessibility & motion (non-negotiable)

- 48×48dp minimum for EVERY interactive (`minimumInteractiveComponentSize`).
  Radio/checkbox hit areas expand to 48dp.
- Motion: `MotionScheme.expressive()` spring physics default; emphasized easing for hero,
  standard for containers, 300–500ms. Shape morphing is a first-class interaction
  primitive.
- Haptics: light click on toggles/switches, medium on FAB press (HapticFeedback).

### 12.6 Adoption status in Stratum — RATIFIED (Sean, 2026-10-19; executed in STR-138)

- **Neo Brutalist geometry/shadows are a sanctioned carve-out** (§11.3 ruling 1): the
  shape scale, no-shadow rule, and pill buttons in this section govern the Material/Tonal
  family only, permanently.
- **Button pill:** adopted for the Tonal family — `tonalShapes().button` is full pill
  (CircleShape).
- **Type scale:** completed. `StratumTypography` now carries the full 15-style gospel
  scale (plus Stratum's `dataLarge` / `sectionLabel`), `titleMedium` corrected to
  16/24 Medium, and `StratumTheme` wires the Material bridge `Typography` from the spec
  instead of M3 defaults. `titleLarge` (22/28 SemiBold) and `bodyLarge` (16/24/400)
  already matched exactly.
- **Spacing:** the Spacing object (`StratumSpacing`) shipped with STR-137; adopting it
  inside shipped components is layout work — follow-up, not yet done.
- Already aligned: 48dp minimum for in-workout controls (§5); Tonal-family 12dp panel /
  8dp chip corners match the gospel card/chip radii.
- **Remaining follow-ups (not ratified away, not yet done):** Expressive motion scheme
  (`MotionScheme.expressive()`, press morphs, haptics) — no Stratum motion tokens exist
  yet; font family (Shipopotamus Sans / Roboto Flex vs `FontFamily.Default`); widening
  the 48dp law to *every* interactive (component-by-component); spacing adoption inside
  shipped components.

### 12.7 Button Shape Gospel — Canonical (Sean, 2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future
Shippopotamus apps. Embedded here **in full** under Sean's direction (his Contract
Waiver for this edit), like §11/§12: this contract is self-contained. This section
governs button shape specifically and supersedes §12.2/§12.3 wherever they disagree on
button shape or button press morph. Sean's pasted source spelled the tokens
`Shipopotamus*` (one p); the canonical spelling is `Shippopotamus*`, used below.

**Default for Bloviate / consumer: FULL PILL**

- Shape: `CircleShape` / 9999px / full token (`ShippopotamusButtonShape`).
- Why: empirically friendliest, highest tap recognition, matches Google Phone, Gboard,
  etc. More rounded corners + pill buttons are the M3 signature.

**Allowed variant for toolbars / secondary / dense enterprise: SQUARE**

- Shape: `RoundedCornerShape(12.dp)` for medium buttons, `RoundedCornerShape(16.dp)`
  for large (`ShippopotamusButtonShapeSquare`).
- When to use: inside a card that already has 12dp radius — use an 8dp inner button
  (nested radius = outer − padding); data-dense toolbar, filter bar, table actions;
  when you want the Linear/Raycast serious vibe vs. bubbly consumer.

**Rules (non-negotiable)**

- Never mix randomly in the same row. One screen = one button shape language.
- If you use the square variant, it still morphs on press to the same pressed shape as
  the pill variant: `ShippopotamusPressedShape = RoundedCornerShape(12.dp)`.

**Compose implementation**

```kotlin
val ShippopotamusButtonShape = CircleShape // DEFAULT PILL
val ShippopotamusButtonShapeSquare = RoundedCornerShape(12.dp) // ALLOWED VARIANT (medium; 16.dp for large)
val ShippopotamusPressedShape = RoundedCornerShape(12.dp) // Both morph to this on press

Button(
  shape = ShippopotamusButtonShape,
  modifier = Modifier.minimumInteractiveComponentSize() // 48dp enforced
) { Text("Primary", style = MaterialTheme.typography.labelLarge) }

// Expressive press morph
val corner by animateDpAsState(if (pressed) 12.dp else 100.dp, spring(StiffnessHigh))
val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
```

**Adoption in Stratum:** the Tonal family already complies with the default —
`tonalShapes().button` is full pill (CircleShape), ratified in §12.6 / STR-138 — so no
code change is needed for the default. The **Neo Brutalist family is a sanctioned
carve-out** (§11.3 ruling 1, §12.6): its 4dp button geometry is permanent by design and
this gospel does not govern it, exactly as with the §12 shape scale and pill rule.
Where Stratum's dense surfaces (data toolbars, filter bars, table-like actions) use the
square variant is new-work judgment under the one-language-per-screen rule. When the
standing Expressive-motion follow-up in §12.6 lands, its button press morph uses this
section's 12dp pressed shape. Wiring named `Shippopotamus*` button-shape tokens is
implementation work under a future ticket — this amendment changes the contract, not
shipped screens.

### 12.8 Glass Bars Gospel — Top + Bottom + Haze (Sean, 2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future
Shippopotamus apps. Uses **Haze 2.0** (Chris Banes); Android 15+ edge-to-edge enforced.
Embedded here **in full** under Sean's direction (his Contract Waiver for this edit),
like §11/§12/§12.7: this contract is self-contained. This section governs bars, sheets,
and overlay glass for new work and supersedes §12.3 wherever they disagree on those
surfaces. Sean's pasted source spelled the tokens `Shipopotamus*` (one p); the
canonical spelling is `Shippopotamus*`, used below.

**Dependency**

```kotlin
implementation("dev.chrisbanes.haze:haze:2.0.1") // core blur
implementation("dev.chrisbanes.haze:haze-glass:2.0.1") // refraction Glass (optional, for hero)
```

Haze provides hardware-accelerated visual effects — blur, refraction-driven Glass, and
custom effects with reusable config.

**Foundation — edge-to-edge (required for glass to work)**

- Activity: `enableEdgeToEdge()` — status + gesture nav are transparent, content draws
  behind.
- Scaffold: `contentWindowInsets = WindowInsets(0,0,0,0)` — insets are handled
  manually.
- Main body: `Modifier.hazeSource(state)` + `.windowInsetsPadding` +
  `.consumeWindowInsets`.

**Reusable styles — one `HazeState` per screen, shared; don't inline magic numbers**

```kotlin
val hazeState = rememberHazeState()

val ShippopotamusHazeTopBar = HazeStyle.Unspecified.copy(
  blurRadius = 24.dp, // 20–28dp for top bar — enough to read title over photos
  tint = Color(0xFF0D0E11).copy(alpha = 0.65f), // bg-base #0D0E11 at 65%
  noiseFactor = 0.5f, // micro-texture prevents banding on OLED
)

val ShippopotamusHazeBottomBar = HazeStyle.Unspecified.copy(
  blurRadius = 20.dp,
  tint = Color(0xFF181A20).copy(alpha = 0.75f), // surface-1 #181A20 at 75%
  noiseFactor = 0.5f
)

val ShippopotamusHazeSheet = HazeStyle.Thick // preset: heavy blur for bottom sheets
```

Usage pattern: background scrollable = `hazeSource`; bars = `hazeChild`; **never put
haze behind body text directly — behind containers only.**

**Top bar (TopAppBar)** — Height small 64dp (default), medium 112dp, large 152dp for
hero. Container TRANSPARENT when at top (scroll offset 0), becomes glass when scrolled.
Shape: none — full width, no rounded corners. Content: title `titleLarge`, icons 24dp,
padding 16dp. Border: none — blur provides separation. When scrolled: glass + subtle
1dp bottom hairline #2A2E39 at 40%, or borderless for pure glass. Content scrolls
underneath, blurred — movement is visible but the title stays readable.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippopotamusTopBar(
  title: String,
  hazeState: HazeState,
  scrollBehavior: TopAppBarScrollBehavior
) {
  val isScrolled = scrollBehavior.state.overlappedFraction > 0.01f

  TopAppBar(
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = { IconButton({}) { Icon(Icons.Default.Menu, null) } },
    actions = { IconButton({}) { Icon(Icons.Default.Search, null) } },
    scrollBehavior = scrollBehavior,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color.Transparent, // transparent when not scrolled
      scrolledContainerColor = Color.Transparent // we handle via haze
    ),
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = if (isScrolled) ShippopotamusHazeTopBar else HazeStyle.Unspecified.copy(blurRadius = 0.dp)
      )
      .then(
        if (isScrolled) Modifier.drawWithContent {
          drawContent()
          drawLine(Color(0xFF2A2E39).copy(0.4f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        } else Modifier
      )
  )
}

// In screen:
val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior() // or enterAlwaysScrollBehavior()
Scaffold(
  modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  topBar = { ShippopotamusTopBar("Stratum", hazeState, scrollBehavior) },
  containerColor = Color(0xFF0D0E11),
  contentWindowInsets = WindowInsets(0,0,0,0)
) { padding ->
  LazyColumn(
    Modifier
      .fillMaxSize()
      .hazeSource(state = hazeState, zIndex = 0f) // zIndex 0 = source
      .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
      .padding(padding)
  ) { /* main body passes underneath top bar */ }
}
```

**Bottom bar (NavigationBar)** — Height 80dp (68dp tighter is allowed, 80dp is
canonical). Container ALWAYS glass — never solid; translucent tint + blur so content
scrolls under. Shape: none for the bar; active indicator = full pill 32dp height,
accent #7A7CFF at 15% fill. Icons 24dp, label `labelMedium`. Inactive #858994; active
#EDEEF0 + accent indicator. Border: 100% borderless — blur is the separation. Gesture
nav: `windowInsetsPadding(WindowInsets.navigationBars)` + `navigationBarsPadding` on
content so the last item is not hidden behind the system pill. The bottom bar shares
the screen's `hazeState` with the top bar so both blur the same source.

```kotlin
@Composable
fun ShippopotamusBottomBar(hazeState: HazeState) {
  var selected by remember { mutableStateOf(0) }

  NavigationBar(
    containerColor = Color.Transparent, // haze handles color
    tonalElevation = 0.dp,
    modifier = Modifier
      .hazeChild(
        state = hazeState,
        style = ShippopotamusHazeBottomBar
      )
      .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
  ) {
    listOf("Home", "Search", "Library").forEachIndexed { i, label ->
      NavigationBarItem(
        selected = i == selected,
        onClick = { selected = i },
        icon = { Icon(Icons.Default.Home, null) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = Color(0xFFEDEEF0),
          selectedTextColor = Color(0xFFEDEEF0),
          indicatorColor = Color(0xFF7A7CFF).copy(alpha = 0.15f), // accent-muted
          unselectedIconColor = Color(0xFF858994),
          unselectedTextColor = Color(0xFF858994)
        )
      )
    }
  }
}
```

**Where else to use Haze**

1. Bottom sheet: sheet container = `hazeChild` with `ShippopotamusHazeSheet` +
   surface-3 #272A33 tint. Content underneath stays visible blurred — elevated
   without an opaque scrim.
2. FAB over feed: extended FAB that blurs the feed behind it — small blur, 12dp.
3. Image overlays: hero image with a glass chip (e.g. a "Live" badge) — haze-glass
   with refraction for liquid glass.

**Performance:** Haze 2.0 adaptive rendering quality automatically drops quality on a
low-end GPU. Keep `blurRadius <= 28dp`; avoid stacking more than 2 `hazeChild`s in the
same frame.

**Adoption in Stratum:** Stratum has **no Haze dependency yet**, so this is new-work
law, not a description of shipped code: adding Haze 2.0.1, the edge-to-edge
foundation, and glass bars is implementation work under a future ticket. **STR-150
note:** STR-150 (in flight at this amendment) set the nav bar container to solid
bg-subtle under the pre-glass §12.3 spec — that work is **transitional**; this section
supersedes it for new work, and a follow-up ticket should migrate `StratumNavigationBar`
to the glass treatment rather than treating bg-subtle as final. The top-bar title
consumes `titleLarge` (§12.2 scale, SemiBold); Sean's pasted spec line saying "Medium"
is superseded by that scale. The Neo Brutalist family's bar/sheet treatments stay
under its sanctioned carve-out (§11.3/§12.6); this gospel governs the Material/Tonal
family.

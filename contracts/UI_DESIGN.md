# UI_DESIGN.md
**Stratum — UI Design Contract v1.0**

Immutable UI contract for Android Compose. Defines theme families, token architecture,
component boundaries, and agent workflow.

Read before generating or restyling any UI. Enforced by Detekt rule set `stratum`
(see `config/detekt/detekt.yml` and `:detekt-rules`).

Pair with `AGENTS.md` for architecture and validation commands.

**§11–§12 embed the two Shippopotamus gospels in full** — the Color Palette Gospel and the
Android Jetpack Compose Gospel (declared canonical by Sean, 2026-10-19, for Bloviate,
Stratum, Enclave, and all future Shippopotamus products). They govern all **new** UI work.
Where §§1–10 conflict with §§11–12, the gospels win for new work; every known conflict is
listed in STR-137's MR for Sean's ratification (shipped screens are not silently redesigned).

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Use `StratumTheme` + semantic tokens (`StratumTheme.colors.*`). | Read `MaterialTheme.colorScheme` in `:feature:*` or `:app` UI code. |
| Use `Stratum*` components from `:core:ui` for product chrome. | Use raw `Button`, `Card`, `OutlinedTextField`, or M3 surfaces in features. |
| Define colors in theme specs only (`StratumTonalSpec`, `NeoBrutalistSpec`). | Hard-code hex literals in feature modules (except previews/tests — see §6). |
| Support theme **family** swap (Tonal, NeoBrutalist) + light/dark/system. | Name public components `Neo*` or `Material*` — use neutral `Stratum*` API. |
| Use tabular figures for weights, reps, RPE, volume, and PR values. | Use playful accents (Enclave peach, purple AI gradients, neon hazard palettes). |
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
- Purple gradients, neon green, hazard-yellow as primary accent
- CRT scanlines, ASCII frames, Swiss poster hazard red as dominant chrome

### 1.2 Stratum Tonal (default)

- Material 3 tonal surfaces with **subtle stratum dividers** (horizontal banding motif — optional 1dp `surfaceSunken` stripes, not neo borders)
- 12–16dp corners on cards; tonal elevation instead of hard shadow
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

**Anti-default discipline:** No stock fitness-app purple, no Enclave peach, no generic three-card feature grids.

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

### 11.3 Adoption status in Stratum

Already aligned: tonal (non-shadow) elevation in the Tonal family (§1.2, §2.3); 48dp
minimum touch targets for in-workout controls (§5 — the gospel extends this to *every*
interactive, §12.5). Everything else in this section that differs from `StratumTonalSpec` /
`NeoBrutalistSpec` values — accent hue, surface ramp, border ramp, text ramp, semantic
hues, the dark-only scope vs Stratum's shipped light specs — is a **conflict pending Sean's
ratification**, listed in full in STR-137's MR. Until ratified, family specs keep their
current values; new tokens/components follow the gospel.

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

**Shapes (corner radius)**

- extraSmall 4dp (text field top, chips, small containers), small 8dp (default
  cards/menus), medium 12dp (cards, dialogs, sheets), large 16dp (large cards, FAB),
  extraLarge 28dp (bottom sheets, large dialogs, hero), full = pill (buttons, FABs,
  toggle track, active nav indicator).
- Nested rule: child radius = parent radius − padding.
- Expressive: Cookie4Sided, Clover, Squircle, Arch for loaders/FAB morph/press morph.
  Buttons morph pill → 16dp + scale 0.96 on press, Spring.StiffnessHigh.

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
  corner morph 50%→16dp + scale 0.96 + ripple (spring).
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
  border-default color; active track accent; thumb white; touch auto-48dp.
- **CHECKBOX/RADIO:** 20dp visible, 48dp touch; checkbox small 4dp, radio full; active
  accent.
- **SLIDER:** track 4dp, active accent, inactive border-default, thumb 20dp, halo 40dp
  press, 48dp touch height.
- **BOTTOM SHEET:** extraLarge 28dp top-only, surface-3 container at 6dp/11% tint, drag
  handle 32×4 #6E7280, scrim palette scrim (§11.2 mobile extras). **DIALOG:** extraLarge
  28dp, surface-3, padding 24dp, button gap 8dp, title titleLarge.
- **NAVIGATION:** bar 80dp height, 3–5 items, active indicator 32dp full pill, icon 24dp,
  label labelMedium; container bg-subtle/surface-1. Rail 80dp wide. TopAppBar small 64dp
  (titleLarge), medium 112dp, large 152dp.
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

### 12.6 Adoption status in Stratum

Already aligned: 48dp minimum for in-workout controls (§5); Tonal-family 12dp panel /
8dp chip corners match the gospel card/chip radii; `titleLarge` (22/28 SemiBold) and
`bodyLarge` (16/24/400) in `DefaultStratumTypography` match the gospel scale exactly.
Conflicts and gaps (button pill shape, full M3 type scale, Spacing object usage,
Expressive motion, font family, Neo Brutalist geometry/shadows) are listed in STR-137's
MR for Sean's ratification; the Spacing object itself (`StratumSpacing`) ships with
STR-137 so new work has the gospel spacing source available immediately.

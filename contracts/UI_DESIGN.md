# UI_DESIGN.md
**Stratum — UI Design Contract v1.1**

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
embedded in §12.8 under the same waiver). They govern all UI work.
Where §§1–10 conflict with §§11–12, the gospels win. The STR-137 conflict
register was **ratified by Sean on 2026-10-19**, executed in STR-138, and the STR-139
gospel-alignment program (STR-141–STR-148, STR-175, STR-179) brought the shipped app to
the state the gospels describe; the adoption status in §11.3 / §12.6 records that
implemented state. Neo Brutalist is a sanctioned, permanent carve-out (§11.3 ruling 1).

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

## 11. Shippopotamus Color Palette Gospel — Official Dark Theme (canonical, in the gospels submodule)

Declared canonical by Sean, 2026-10-19. Status: CANONICAL FOR ALL APPS (Bloviate, Stratum,
Enclave, and all future Shippopotamus products). The palette canon lives in the
Shippopotamus gospels repo, consumed in this repo as the `contracts/gospel/` submodule.
Where any mockup/image disagrees with the gospel text, THE GOSPEL TEXT WINS.

### 11.1 Why (empirical basis)

See `contracts/gospel/PALETTE.md` — "Why (empirical basis, Sean's decree)".

### 11.2 Tokens

See `contracts/gospel/PALETTE.md` — "Tokens" (background layers, borders / dividers,
text layers, indigo accent family, semantic colors, mobile extras).

### 11.3 Adoption status in Stratum — RATIFIED (Sean, 2026-10-19; executed in STR-138)

Sean ruled on the full STR-137 conflict register on 2026-10-19. The rulings are binding:

1. **Neo Brutalist is a sanctioned, permanent carve-out.** The gospels govern the
   Material/Tonal theme family only. Neo keeps its 4dp corners, hard offset shadows,
   2dp borders, and iron/rust accent as its identity — permanently, not as a pending
   conflict. `NeoBrutalistSpec` is never re-tokened to the gospel.
2. **The anti-purple rule is repealed** (struck in §1.1, §7.1, and the Strict Boundaries
   table). The Material/Tonal accent is the gospel indigo family.
3. **Light theme stays**, and its canon is the Light Palette Gospel (§11.4).

Implemented state (Tonal family; tokens centralized in `GospelPaletteTokens`,
consumed by `StratumTonalSpec`):

- **Accent:** dark steel `#5B7C99` → gospel indigo **#7A7CFF** (on-accent `#FFFFFF` →
  bg-base **#0D0E11** — white on the indigo is only 3.4:1; muted `#3D5A72` → accent-deep
  **#3B3BAA**). Light steel `#5B7C99` → light-gospel indigo **#5457E8** (muted → accent-deep
  **#2F31A0**).
- **Borders (dark):** `#4A4A4A` / `#3A3A3A` → gospel **#343A48** (`BorderStrong`) /
  **#2A2E39** (`BorderSubtle`).
- **Surfaces (dark):** the gospel ramp — base **#0D0E11**, raised **#181A20**,
  inset **#272A33**, sunken **#111216** (Stratum's fourth surface role sits one step
  below base). The pre-gospel `#121212`-family ramp is gone.
- **Text (dark):** `#F2F2F2` / `#9CA3AF` → gospel **#EDEEF0** (text-primary) /
  **#A1A5B0** (text-secondary); the ramp is completed by the `textFaint` token
  **#6E7280** (STR-175). `textData` has no gospel counterpart and tracks text-primary
  ink in both modes (tabular figures carry the data distinction, not hue).
- **Light spec:** adopts the Light Gospel tokens in full (§11.4) — surfaces, borders,
  text ramp, accent family.
- **Semantic hues:** gospel danger **#E07A7A** is `error`; gospel warning **#D9B07A**
  is `warning`. `setComplete` remains a *fill* by design: gospel success `#7DBE8C`
  mixed 30/70 toward the surface (**#364B40** in dark) — a background for
  completed-set rows, never a foreground. `prHighlight` (**#E0B040**) is a
  Stratum-specific role with no gospel counterpart.
- Already aligned before ratification: tonal (non-shadow) elevation in the Tonal
  family (§1.2, §2.3); the 48dp law and its widening to every interactive are
  recorded in §12.6.

### 11.4 Light Palette Gospel — Official Light Theme (canonical, in the gospels submodule)

Ratified by Sean, 2026-10-19 (companion to §11). The light theme canon lives in
`contracts/gospel/PALETTE-LIGHT.md` — same token names as §11.2, re-derived for light.

**Stratum adoption (Tonal light spec):** `surfaceBase` = bg-base, `surfaceRaised` =
surface-1, `surfaceInset` = surface-2, `surfaceSunken` = bg-subtle, `borderStrong` =
border-strong, `borderSubtle` = border-default, `textPrimary` / `textMuted` =
text-primary / text-secondary, `accent` / `accentMuted` = accent-base / accent-deep.

---

## 12. Shippopotamus Android Jetpack Compose Gospel — Official UI System (canonical, in the gospels submodule)

Declared canonical by Sean, 2026-10-19. Material 3 + M3 Expressive (Android 16 QPR1).
Canonical for BLOVIATE / STRATUM / ENCLAVE. Companion: §11 (colors). The compose canon
lives in `contracts/gospel/COMPOSE-GOSPEL.md`; button shape (§12.7) in
`contracts/gospel/BUTTON-SHAPE-GOSPEL.md`; glass bars (§12.8) in
`contracts/gospel/GLASS-BARS-GOSPEL.md`.

### 12.1 Why

See `contracts/gospel/COMPOSE-GOSPEL.md` — "Why".

### 12.2 Foundation

See `contracts/gospel/COMPOSE-GOSPEL.md` — "Foundation" (spacing & layout, shapes,
typography, elevation).

### 12.3 Component specs (defaults)

See `contracts/gospel/COMPOSE-GOSPEL.md` — "Component specs (defaults)". Stratum's
own theme architecture (§2) and component API (§4) stay local and govern Stratum
components.

### 12.4 Theme.kt shape (canonical sketch)

See `contracts/gospel/COMPOSE-GOSPEL.md` — "Theme.kt shape".

### 12.5 Accessibility & motion (non-negotiable)

See `contracts/gospel/COMPOSE-GOSPEL.md` — "Accessibility & motion (non-negotiable)".

### 12.6 Adoption status in Stratum — RATIFIED (Sean, 2026-10-19; implemented STR-138 through STR-147)

- **Neo Brutalist geometry/shadows are a sanctioned carve-out** (§11.3 ruling 1): the
  shape scale, no-shadow rule, and pill buttons in this section govern the Material/Tonal
  family only, permanently.
- **Button pill:** adopted for the Tonal family — `tonalShapes().button` is full pill
  (CircleShape), with the §12.7 press morph to 12dp (STR-146).
- **Type scale and font:** completed. `StratumTypography` now carries the full 15-style gospel
  scale (plus Stratum's `dataLarge` / `sectionLabel`), `titleMedium` corrected to
  16/24 Medium, and `StratumTheme` wires the Material bridge `Typography` from the spec
  instead of M3 defaults. `titleLarge` (22/28 SemiBold) and `bodyLarge` (16/24/400)
  already matched exactly. The font family landed in STR-147: Roboto Flex via the
  Google Fonts downloadable provider (weights 400/500/600) with `FontFamily.Default`
  fallback — resolution is asynchronous, text renders in the fallback and swaps when
  the download lands, and never renders blank. `dataLarge` carries tabular figures
  via the `tnum` OpenType feature rather than a monospace family.
- **Spacing:** the Spacing object (`StratumSpacing`) shipped with STR-137 and is
  adopted inside shipped components (STR-142), enforced by the off-grid detekt guard
  (`ForbiddenRawSpacingLiteralInUi`, maxIssues 0).
- **48dp law:** widened to *every* interactive (STR-145) via real `sizeIn` floors on
  the components. The floors are the enforcing mechanism in layout:
  `minimumInteractiveComponentSize` inflates the touch target without sizing the
  visible face inside it.
- **Motion and haptics:** `MotionScheme.expressive()` is wired via the material3
  1.5.0-beta01 pin, with press morphs and the §12.5 haptics vocabulary (STR-146).
- **Primitives:** the §4.1 required set is complete (STR-144: `StratumSurface`,
  `StratumDivider`, `StratumChip`, `StratumListRow`).
- **Hard shadows:** `stratumHardShadow` anchors to the visible face box centered
  inside the 48dp touch target, not the inflated layout node (STR-179).
- Already aligned: Tonal-family 12dp panel / 8dp chip corners match the gospel
  card/chip radii.

### 12.7 Button Shape Gospel — Canonical (Sean, 2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future
Shippopotamus apps. The button shape canon lives in
`contracts/gospel/BUTTON-SHAPE-GOSPEL.md`. This section governs button shape
specifically and supersedes §12.2/§12.3 wherever they disagree on button shape or
button press morph.

**Stratum local notes**

**Adoption in Stratum:** the Tonal family already complies with the default —
`tonalShapes().button` is full pill (CircleShape), ratified in §12.6 / STR-138 — so no
code change is needed for the default. The **Neo Brutalist family is a sanctioned
carve-out** (§11.3 ruling 1, §12.6): its 4dp button geometry is permanent by design and
this gospel does not govern it, exactly as with the §12 shape scale and pill rule.
Where Stratum's dense surfaces (data toolbars, filter bars, table-like actions) use the
square variant is new-work judgment under the one-language-per-screen rule. The
Expressive-motion adoption recorded in §12.6 has landed: the button press morph uses
this section's 12dp pressed shape, and the named `Shippopotamus*` button-shape tokens
are wired in `ShippopotamusButtonShapes.kt`.

### 12.8 Glass Bars Gospel — Top + Bottom + Haze (Sean, 2026-10-20)

Declared canonical by Sean, 2026-10-20, for Bloviate / Stratum / Enclave and all future
Shippopotamus apps. Uses **Haze 2.0** (Chris Banes); Android 15+ edge-to-edge enforced.
The glass bars canon lives in `contracts/gospel/GLASS-BARS-GOSPEL.md`. This section
governs bars, sheets, and overlay glass for new work and supersedes §12.3 wherever
they disagree on those surfaces.

**Stratum local notes**

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

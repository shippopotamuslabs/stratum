# UI_DESIGN.md
**Stratum — UI Design Contract v1.0**

Immutable UI contract for Android Compose. Defines theme families, token architecture,
component boundaries, and agent workflow.

Read before generating or restyling any UI. Enforced by Detekt rule set `stratum`
(see `config/detekt/detekt.yml` and `:detekt-rules`).

Pair with `AGENTS.md` for architecture and validation commands.

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

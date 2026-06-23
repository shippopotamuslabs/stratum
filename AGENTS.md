# Agent Routing Contract — Stratum

Root source of truth for agent-facing repo instructions. Agent-specific files such as
`CLAUDE.md` and `GEMINI.md` should only point here to avoid drift.

---

## 1. Contract Routing (Read Before Writing Any Code)

Every task maps to binding contracts in `/contracts/`. Read the relevant contract(s) first.

| Task type | Contract |
|-----------|----------|
| Compose UI, theme families, `Stratum*` components, colors, typography, previews | `UI_DESIGN.md` |
| Module structure, navigation, Scaffold, dependency graph, layer rules | `ARCHITECTURE.md` |
| Kotlin style, Compose conventions, testing | `CODE_STYLE_RULES.md` (when added) |
| Unit tests, fakes, Robolectric, golden examples | `UNIT_TESTING.md` |
| Room, repositories, schema | `DATA_LAYER_RULES.md` (when added) |

Never modify files in `/contracts/` without an explicit **"Contract Waiver"** from the user.

---

## 2. Project Overview

**Stratum** is an Android lifting companion: a quietly intelligent training partner that
handles progression, volume accounting, and long-horizon pattern recognition so lifters
can focus on training.

- **Display name:** Stratum (long form: Stratum Workout Log)
- **Application ID / namespace:** `com.stratum` (app module may be `com.stratum.app`)
- **Platform:** Android (Kotlin, Jetpack Compose)
- **Hosting:** gitlawb (pseudonymous DID identity — do not add personal or Brown Town identifiers)

Before broad edits, map the relevant subsystem, identify affected layers and call paths,
then write a brief implementation plan. For large investigations, use subagents if the
harness supports them so exploration does not flood the main context.

---

## 3. Repository Map

Stratum is a modular Android app. Module layout is defined in `contracts/ARCHITECTURE.md`.

- `app/`: application shell, DI wiring, navigation entrypoints, app resources, signing
- `core/domain/`: domain models, ports, use cases (JVM module)
- `core/data/`: Room, repositories, entity/domain mapping, schemas, background work
- `core/ui/`: shared Compose UI components and themes
- `core/testing/`: shared fakes, `MainDispatcherRule`, test fixtures (JVM module)
- `feature/*/`: feature modules (add per journey — see ARCHITECTURE.md §1.2)
- `config/detekt/`: Detekt config + custom `stratum` rule set wiring
- `detekt-rules/`: Custom Detekt rules enforcing `UI_DESIGN.md`

Main toolchain: Gradle Kotlin DSL, Kotlin, Jetpack Compose, Room, coroutines/Flow.

---

## 4. Hard Rules (Non-Negotiable)

### 4.1 Clean Architecture — Zero Tolerance

See `contracts/ARCHITECTURE.md` for the full module graph and violation response. Summary:

- `:core:domain` contains domain models, ports, use cases, and test fixtures. Keep framework dependencies out of domain APIs.
- Dependency direction is inward toward `:core:domain`. Data, UI, app, and feature modules may depend on domain; domain must not depend on app, feature, data, or UI modules.
- Domain models are **never** passed directly to composables. ViewModels map domain → presentation.
- No `NavController` passed into composables. Navigation via pure callbacks hoisted to the route level.

### 4.2 Android Code — AndroJack Level 3 Protocol

For every Kotlin, XML, or Gradle code block generated:

1. **Pre-generation:** Call the appropriate `androjack` tool to ground the solution before writing code:
   - Compose UI tasks → `mcp_androjack_material3_expressive`
   - Dependency changes → `mcp_androjack_gradle_dependency_checker`
   - API-level questions → `mcp_androjack_android_api_level_check`
   - New components/APIs → `mcp_androjack_android_component_status`
   - Architecture questions → `mcp_androjack_architecture_reference`

2. **Post-generation:** Pass every generated code block through `mcp_androjack_android_code_validator` (match project `minSdk` / `targetSdk` once defined).

3. **FAIL verdict:** Fix internally and re-run validator. Never output code that fails validation.

### 4.3 UI Design — Stratum Theme Contract

- Read `contracts/UI_DESIGN.md` before any Compose UI work.
- Feature modules use `StratumTheme` and `Stratum*` components only — never raw Material chrome.
- `./gradlew detekt` must pass with `maxIssues: 0` (custom rule set `stratum` in `:detekt-rules`).

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop. Do not generate code.

### 4.4 Test Coverage — Mandatory

Read `contracts/UNIT_TESTING.md` before writing any test. Sync golden examples with
`./scripts/golden-test-examples-download.sh` after clone (reference patterns from Now in Android).

Whenever production code is added or modified (new classes, public functions, logic in
`:core:domain`, `:core:data`, ViewModels, repositories, mappers, or use cases):

- Write or extend tests in the matching `src/test/kotlin` source set using **JUnit 5 + Turbine + fakes**.
- Use fakes from `:core:testing` for Stratum-owned ports — **not MockK** (Detekt enforced).
- MockK is allowed only for third-party SDK boundaries with an explicit suppress and comment.
- Copy structure from the relevant file in `golden-test-examples/` (see `UNIT_TESTING.md` §4).
- Tests must pass before declaring work complete.
- Trivial styling-only or preview-only changes are exempt.

### 4.5 Schema Lock

**Never** invent new database tables or columns for transient UI state (e.g., `is_loading`,
`is_editing`). Database schema changes require explicit developer authorization.

### 4.6 Intelligence — On-Device First

Prioritize on-device deterministic engines for progression, volume accounting, and session
construction. Use on-device LLM only for high-judgment, long-horizon, or natural-language
moments described in the product vision. Keep deterministic and LLM responsibilities clearly
separated.

### 4.7 Privacy And Identity

- Do not commit real names, personal emails, or Brown Town / LiftLab identifiers.
- Use `com.stratum` package namespace consistently.
- Prefer local-first storage; cloud sync only when explicitly designed and requested.

---

## 5. Scoped Commands

Prefer the narrowest command that covers the changed area (adjust module names once the
Gradle project exists):

- App compile: `./gradlew :app:assembleDebug`
- Single module compile: `./gradlew :core:data:assembleDebug`
- Single module tests (domain): `./gradlew :core:domain:test`
- Single module tests (Android libs): `./gradlew :core:data:testDebugUnitTest`
- Full unit tests: `./gradlew testDebugUnitTest` and `./gradlew :core:domain:test`
- Golden examples sync: `./scripts/golden-test-examples-download.sh`
- Style: `./gradlew ktlintCheck`
- Static analysis: `./gradlew detekt` (required — `maxIssues: 0`; includes `stratum` UI import rules)
- Room/KSP smoke: `./gradlew :app:kspDebugKotlin`

Before merge or a large cross-module change, run the CI-aligned checks for touched areas:
`./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew assembleDebug`, and
`./gradlew testDebugUnitTest`.

---

## 6. Context Noise And Generated Code

Do not inspect or edit these during normal tasks unless the task explicitly targets them:

- Gradle/build outputs: `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.cxx/`
- Binary/assets noise: `*.so`, APK/AAB outputs, keystores, large model files
- IDE metadata unless configuring the project: `.idea/caches/`, `*.iml` churn from Studio

---

## 7. Code Intelligence Setup

- Android/Kotlin: import the root Gradle project in **Android Studio**. Keep Studio open
  during agent sessions so `android studio` bridge commands work when available.
- **Android CLI:** `android` on PATH (`android info`). Run `android init` once per machine;
  project skills may live under `.agents/skills/` when added.
- Use JDK version required by the Gradle project once Android Studio creates it.

---

## 8. Tool Priority (MCP and Android CLI)

Before any Android/Kotlin task, check MCP tools **and** whether Android Studio bridge
commands are available.

### MCP servers

| MCP Server | When to use |
|------------|-------------|
| `androjack` | All Android/Kotlin validation, API research, dependency checks — **mandatory for all code generation** |
| `mobile-mcp` | Install APKs, interact with a running emulator/device, take screenshots |
| `google-developer-knowledge` | Official Android/Firebase/Google API documentation lookup |

### Android CLI and Studio bridge (shell)

| Capability | Command family | When to use |
|------------|----------------|-------------|
| Studio connected | `android studio check` | Start of Compose UI, refactor, or navigation tasks |
| Symbol navigation | `android studio find-declaration`, `find-usages` | Cross-module Kotlin references (prefer over blind grep) |
| IDE inspections | `android studio analyze-file` | Before/after editing a Kotlin/Compose file |
| Resolved versions | `android studio version-lookup` | Dependency bumps, version skew debugging |
| Compose previews | `android studio render-compose-preview` | Visual check of `@Preview` composables — **requires debug build** |
| APK / deploy | `android describe`, `android run` | After `./gradlew :app:assembleDebug` |
| Device UI tree | `android layout` | Layout/debug on connected device or emulator |
| Android docs | `android docs search`, `android docs fetch` | Authoritative KB lookups |

**Project identifier:** `--project="Stratum"` when multiple projects could be open.

**AndroJack vs Android CLI:** AndroJack validates **generated** code (§4.2). Android CLI
queries the **running IDE** and devices. Use both; neither replaces the other.

If `android studio check` reports no running Studio, continue with Gradle/tests and note
that bridge commands were skipped.

---

## 9. Executable Validation Before Completion

Before declaring work complete, run the narrowest relevant validations:

- **Architecture / Domain:** `./gradlew :core:domain:test`
- **App compilation:** `./gradlew :app:assembleDebug`
- **Data layer:** `./gradlew :core:data:assembleDebug` + `./gradlew :app:kspDebugKotlin`
- **Tests:** `./gradlew testDebugUnitTest`
- **Style:** `./gradlew ktlintCheck`
- **Static analysis:** `./gradlew detekt` (required — `maxIssues: 0`; must pass before completion)

Only run checks relevant to what changed during iteration, but run the full affected unit
test set for logic changes.

---

## 10. Maintenance

Repo maintainers own this file. Review every 3–6 months:

- Accuracy of module paths and Gradle commands as the project grows
- Skills or workflow docs under `.agents/`
- Ignore rules in `.gitignore`, IDE settings, and any tracked `.cursorignore`
- MCP/plugin notes and stale tool-specific workarounds

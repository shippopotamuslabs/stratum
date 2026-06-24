# ARCHITECTURE.md
**Stratum — Structural Architecture Contract v1.0**

Non-negotiable module and layering rules. Enforce together with `UI_DESIGN.md` and `AGENTS.md`.

Never modify this file without an explicit **"Contract Waiver"** from the user.

---

## 0. Clean Architecture — Zero Tolerance

Stratum follows **Clean Architecture / ports-and-adapters**. Dependencies point **inward**
toward `:core:domain`. Outer modules implement ports defined in domain.

**The agent MUST NEVER:**

- Violate the module dependency graph in §1.
- Expose Android, Compose, Room, Hilt, or WorkManager types through `:core:domain` public APIs.
- Pass domain models directly to Composables (map to presentation models in ViewModels).
- Pass `NavController` into Composables (use callbacks hoisted to routes).
- Put business logic in `:app` or composables.

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop.

---

## 1. Module Map

### 1.1 Current modules (day-one skeleton)

| Module | Type | Namespace | Purpose |
|--------|------|-----------|---------|
| `:app` | Application | `com.stratum` | Shell: `Application`, `MainActivity`, NavHost, DI composition |
| `:core:domain` | JVM library | `com.stratum.core.domain` | Models, ports, use cases — no Android framework |
| `:core:data` | Android library | `com.stratum.core.data` | Room, repositories, DataStore, WorkManager, mappers |
| `:core:ui` | Android library | `com.stratum.core.ui` | `StratumTheme`, theme specs, shared `Stratum*` components |
| `:core:testing` | JVM library | `com.stratum.core.testing` | Shared fakes, `MainDispatcherRule`, test fixtures |
| `:detekt-rules` | JVM library | `com.stratum.detekt` | Custom static analysis (not part of app runtime) |

### 1.2 Planned modules (add when the vertical slice exists)

| Module | When to add |
|--------|-------------|
| `:feature:onboarding` | Onboarding wizard — added in OS-1 (see `docs/onboarding-vertical-slices.md`) |
| `:feature:workout` | Active session logging (sets, timers, in-workout flow) |
| `:feature:program` | Program view, exercise selection, substitutions |
| `:feature:history` | Session history, trends, personal records |
| `:feature:settings` | Theme, equipment profile, preferences |
| `:feature:insights` | “What actually worked” dashboard, pattern mining UI |
| `:core:inference` or `:feature:inference` | On-device LLM — **no Compose** in inference module |

Do **not** create empty feature modules preemptively. Add one when a journey has **2+ screens**
or a clear ownership boundary.

---

## 2. Dependency Direction (Strict)

```text
:app
  → :core:domain, :core:data, :core:ui, :feature:* (when present)

:feature:*
  → :core:domain, :core:ui
  → :core:data only when declared in that feature's build.gradle.kts

:core:data
  → :core:domain

:core:ui
  → :core:domain

:core:domain
  → (stdlib, coroutines, javax.inject only — no other project modules)

:core:testing
  → :core:domain

:detekt-rules
  → (isolated — not a runtime dependency of the app)
```

**No reverse dependencies** into `:core:domain`.

**Feature modules must not depend on other feature modules.** Cross-feature navigation is
wired in `:app` via callbacks and shared routes.

```text
                    ┌─────────────┐
                    │    :app     │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌────────────┐
    │ :feature:* │  │ :core:data │  │ :core:ui   │
    └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
          │               │               │
          └───────────────┼───────────────┘
                          ▼
                   ┌─────────────┐
                   │:core:domain │
                   └─────────────┘
```

---

## 3. Layer Responsibilities

### `:core:domain` (innermost)

- Domain models and value types
- Repository and service **ports** (interfaces)
- Use cases / interactors
- Pure Kotlin — **JVM module**, no Android SDK in production sources
- Allowed deps: Kotlin stdlib, coroutines, `javax.inject`
- Unit tests run on JVM without Robolectric; use fakes from `:core:testing`

### `:core:testing`

- Hand-written fakes implementing domain ports
- `MainDispatcherRule` and shared test fixtures
- Pure JVM — no Android SDK in production sources
- Test modules (`:core:domain`, `:core:data`, `:feature:*`) depend on `:core:testing`; `:core:testing` depends only on `:core:domain`

### `:core:data`

- Room entities, DAOs, database, schema exports under `core/data/schemas/`
- Repository **implementations** of domain ports
- Entity ↔ domain mappers
- DataStore, WorkManager workers for background persistence and analysis jobs
- **No Compose UI**

### `:core:ui`

- `StratumTheme`, theme specs, `LocalStratumTheme`
- Shared `Stratum*` components (see `UI_DESIGN.md`)
- May use Material 3 internally — **feature modules may not**
- No feature-specific screens

### `:feature:*` (when added)

- Feature screens and ViewModels
- Feature-scoped presentation models (`*UiState`, `*UiEvent`)
- `*Route` composables exposing navigation callbacks
- Uses `:core:ui` for chrome; never raw Material in feature code (Detekt enforced)

### `:app` (outermost shell)

- `Application` class and Hilt `@Module` bindings (port → impl)
- Single `NavHost` and top-level navigation graph
- Manifest, signing, ProGuard, app-level resources
- **No business rules** — only wiring and composition

---

## 4. Presentation Contract

### 4.1 UI state

- Immutable `data class` per screen; updates via `copy()`
- One `StateFlow<UiState>` per ViewModel
- No Android framework types inside UI state

### 4.2 One-shot events

- Prefer `Channel<Event>(Channel.BUFFERED)` exposed as `Flow<Event>` via `receiveAsFlow()`
- Consume in `LaunchedEffect` at the screen level — not `SharedFlow` for one-shots

### 4.3 Model mapping

- ViewModels map **domain → presentation** before Composables render
- No repository or use-case calls inside Composables

### 4.4 Navigation

- Each feature exposes `@Composable fun FeatureRoute(onNavigate…: () -> Unit, …)`
- `:app` owns `NavHost` and passes lambdas — no `NavController` in feature composables

### 4.5 Scaffold ownership

- Each screen owns its own `StratumScaffold` / top bar (via `:core:ui` primitives)
- Do not thread root `Scaffold` `paddingValues` down the tree

---

## 5. Forbidden Imports (Summary)

| Module | Must NOT import |
|--------|-----------------|
| `:core:domain` | Any `:app`, `:feature:*`, `:core:data`, `:core:ui` code; Android/Compose/Room/Hilt |
| `:core:data` | Compose UI, feature modules |
| `:core:ui` | `:core:testing` (test only), feature modules, `:app` |
| `:feature:*` | Other `:feature:*` modules; raw Material (see `UI_DESIGN.md` + Detekt) |
| `:app` | — (shell may reference all modules) |

---

## 6. Data & Background Work (Guidance)

- **Schema lock:** no new Room tables/columns without explicit authorization (see `AGENTS.md`)
- **Local-first:** persistence and intelligence default to on-device; sync is opt-in later
- **WorkManager** for periodic analysis (phase detection, insight generation) lives in `:core:data`
- **On-device LLM** in a dedicated inference module with no Compose — deterministic core stays in domain/data

---

## 7. Package Layout

```text
com.stratum                          → :app
com.stratum.core.domain.*            → :core:domain
com.stratum.core.data.*              → :core:data
com.stratum.core.ui.*                → :core:ui
com.stratum.feature.<name>.*         → :feature:<name> (when added)
```

---

## 8. Gradle Conventions

- **JDK 17**, `minSdk 34`, `compileSdk 36` (adjust in version catalog as platform evolves)
- `./gradlew detekt` — `maxIssues: 0` before merge
- `./gradlew :app:assembleDebug` — compile smoke for app + all runtime modules
- Room schema directory: `core/data/schemas/` (checked in when entities exist; enable KSP + Room deps in `core/data/build.gradle.kts` at that time)

---

## 9. Maintenance

Review when:

- Adding a new `:feature:*` or `:core:*` module
- Changing dependency direction or navigation pattern
- Introducing cloud sync, inference, or native bridges

Keep this file aligned with `settings.gradle.kts` and `AGENTS.md` §3 Repository Map.

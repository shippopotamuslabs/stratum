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
| `:core:domain` | JVM library | `com.stratum.domain` | Models, ports, use cases — no Android framework |
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

### 3.1 Domain model policy: rich aggregates, honest projections

Types in `:core:domain` fall into four classes, and the class decides how much
behavior a type must carry:

- **(A) Aggregates / domain models — rich.** We own the concept and are
  authoritative about its validity (`Program`, `TrainingSession`, `Lift`,
  `Equipment`, `LoggedSet`, `WorkoutOverlay`). They enforce their invariants at
  construction (`init` or factory — `copy()` runs the constructor, so `init`
  checks cannot be bypassed) and expose state transitions as named methods on
  the type, never as raw `copy()` state edits in a use case.
- **(B) Value objects — rich and small.** Immutable, validated at construction,
  behavior attached (`Weight`, `IncrementTier`, typed ids). A collection that
  carries rules is wrapped as a value object, not passed as a bare list.
- **(C) Domain services — procedural by design.** Rules that span aggregates or
  need full history live here (recommendation / progression / generation
  engines, response modeling, `PlateCalculator`). They do not move onto an
  arbitrarily chosen aggregate.
- **(D) Read models / projections / results / wire types — data-only by
  design.** Built by a mapper or engine, consumed read-only, never persisted
  back as truth. No behavior is required; naming must make the class obvious.

The full classification of every data class in `:core:domain` is recorded in
`docs/domain-model-classification-inventory.csv` (STR-164); every later refactor
chunk cites its inventory rows. Only classes A and B get refactor chunks.

Rules that follow from the classes:

- **Impossible states are unrepresentable.** If a state genuinely cannot occur
  (`LiftSet.repMin > repMax`, a cycle position outside the cycle), the
  constructor forbids it, full stop.
- **Legitimate states have exactly one owner of their meaning.** A state that
  looks irregular is usually legitimate: a `COMPLETED` imported session with
  `endedAt == null` means "finished, end time unknown". The model owns that
  meaning in one place — a normalizing factory or named behavior on the type —
  and no consumer re-decides it with a local `?:` fallback. Changing a producer
  (the import mapper above all) to emit the normalized shape is part of the
  same chunk as its model, never a reason to keep the rule off the model.
- **Room entities stay rule-free.** Rules never live on `*Entity` classes in
  `:core:data`; the mapper seam (`toDomain()` / `toEntity()`) is construction
  only, not a home for logic.
- **Kotlin-rich, not Java-rich.** No private-field / getter-setter encapsulation
  theatre that fights data classes, Room mapping, or Compose immutability:
  invariants in `init` / factories, behavior as member functions or extensions
  in the model's own file.
- **Use cases orchestrate.** They load, invoke domain behavior, persist, and
  publish. A validity rule or a state-meaning rule implemented in a use case,
  ViewModel, repository, or consumer fallback instead of on the owning domain
  model is a contract violation.
- **Ports belong to the layer that consumes them.** Repository and service
  ports serving use cases belong in the application layer and move with the
  use cases when that layer is extracted (STR-172); only a port consumed
  solely by a domain service stays defined in `:core:domain`. The inventory
  records the consuming layer per port.

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
- Presentation models (`*UiState`, `*DraftUi`, `*ViewState`) must not embed domain types.
  Map at the ViewModel/mapper boundary.
- Detekt enforces via `ForbiddenDomainTypeInComposable` (public composables) and
  `ForbiddenDomainTypeInUiState` (presentation data classes).

### 4.4 ViewModel dependencies & Flow composition

- **ViewModels depend on use cases, not repository or port interfaces.** A ViewModel
  constructor injects `*UseCase` classes from `:core:domain`; it must not inject a
  `*Repository` or `*Port` interface directly. Repository and service ports are consumed
  by use cases, not by presentation.
- **Feature-layer mappers must not accept use case parameters**; domain validation
  aggregates belong in use cases (e.g. `GetOnboardingStepCapabilitiesUseCase`). Mappers
  are pure domain/ephemeral → presentation transforms; ViewModels invoke use cases and
  pass derived values into mappers.
- **Use cases own multi-source `Flow` composition.** When a screen needs data from more
  than one repository, add an interactor (e.g. `Get*StateUseCase`) in `:core:domain`
  that combines the repository streams into a single domain read model (e.g. an
  `*State` aggregate). The ViewModel then observes that one use case — it must not
  write its own multi-source `combine` over repositories.
- **Only transient/presentation state lives in the ViewModel.** Unsaved drafts (what the
  user is typing before persisting), "reveal errors" flags, and one-shot UI events are
  ViewModel-owned `MutableStateFlow`s overlaid on the domain read model. Anything that
  represents a durable fact about the world belongs in the domain layer and is read
  through a use case.
- **Write paths go through use cases too.** A ViewModel persists state by calling a
  use case (e.g. `Update*UseCase`), never by calling `repository.save(...)` directly.
- Rationale: this keeps repository wiring out of presentation, prevents `combine` chains
  from ballooning inside ViewModels, and gives every screen a single, testable domain
  entry point.

### 4.5 Navigation

- Each feature exposes `@Composable fun FeatureRoute(onNavigate…: () -> Unit, …)`
- `:app` owns `NavHost` and passes lambdas — no `NavController` in feature composables

### 4.6 Scaffold ownership

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
- **Schema version policy:** `StratumDatabase` is versioned with exported JSON checked in
  under `core/data/schemas/`. Apply entity changes by bumping the version: pure additive
  changes via `AutoMigration`, backfill/reshaping via a manual migration plus a migration
  test. Post-release, migrations are additive only (see `DATA_LAYER_RULES.md` §7).
- **Local-first:** persistence and intelligence default to on-device; sync is opt-in later
- **WorkManager** for periodic analysis (phase detection, insight generation) lives in `:core:data`
- **On-device LLM** in a dedicated inference module with no Compose — deterministic core stays in domain/data

### Persistence design rules

See `DATA_LAYER_RULES.md` for the full contract. Summary:

- Entity primary/foreign keys use typed domain id value classes via `TypeConverter`s
- Raw history tables need FK integrity and query indexes before large imports
- Derived analytics tables should record `pipelineVersion` and extraction timestamps
- Prefer SQL window queries over load-all-then-filter in repositories
- Weekly aggregate tables are separate from raw `logged_sets` for analytics scalability

---

## 7. Package Layout

```text
com.stratum                          → :app
com.stratum.domain.*                 → :core:domain
com.stratum.core.data.*              → :core:data
com.stratum.core.ui.*                → :core:ui
com.stratum.feature.<name>.*         → :feature:<name> (when added)
```

### 7.1 Namespace convention

Packages name **architectural layers**, not Gradle folder paths verbatim. The `:core:`
prefix in module names groups shared libraries in the build; only some of that grouping
is mirrored in Kotlin package names.

| Layer | Module | Package | Rationale |
|-------|--------|---------|-----------|
| Domain (innermost) | `:core:domain` | `com.stratum.domain.*` | Short imports at the center of the graph; domain is the layer name, not a Gradle bucket |
| Data | `:core:data` | `com.stratum.core.data.*` | Disambiguates persistence/mappers from domain ports |
| Shared UI | `:core:ui` | `com.stratum.core.ui.*` | Disambiguates Compose components from feature screens |
| Features | `:feature:<name>` | `com.stratum.feature.<name>.*` | One package subtree per vertical slice |

**Do not rename** `com.stratum.domain` to `com.stratum.core.domain` for module-path
symmetry — the omission of `core` in the domain package is intentional. New code in
`:core:domain` uses `com.stratum.domain.<subpackage>` (e.g. `onboarding`, `lift`).

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

# ARCHITECTURE.md
**Stratum — Structural Architecture Contract v1.0**

Non-negotiable module and layering rules. Enforce together with `UI_DESIGN.md` and `AGENTS.md`.

Never modify this file without an explicit **"Contract Waiver"** from the user.

The general Clean Architecture rules are canon in the Shippopotamus gospels repo,
consumed in this repo as the `contracts/gospel/` submodule (see
`contracts/gospel/ARCHITECTURE-GOSPEL.md`). This contract carries Stratum's module
map, dependency graph, and local notes; the general-rule sections below are pointers
to the gospel, at their original section numbers.

---

## 0. Clean Architecture — Zero Tolerance

Stratum follows **Clean Architecture / ports-and-adapters**. The supremacy rule, the
MUST NEVER list, and the violation response are canon: see
`contracts/gospel/ARCHITECTURE-GOSPEL.md` §0. Dependencies point **inward** toward
`:core:domain`; Stratum's exact module graph is §1–§2 below.

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

The dependency-direction rules are canon: see `contracts/gospel/ARCHITECTURE-GOSPEL.md`
§1. Stratum's exact module graph:

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

## 3. Layer Responsibilities

The general layer responsibilities are canon: see
`contracts/gospel/ARCHITECTURE-GOSPEL.md` §3. Stratum's domain model policy notes and
module instantiation notes follow; where a note and the gospel disagree on a general
rule, the gospel wins.

### 3.1 Domain model policy: rich aggregates, honest projections

The domain model policy — rich aggregates, honest projections; the four type classes
(aggregates, value objects, domain services, read models) and the rules that follow
from them — is canon: see `contracts/gospel/ARCHITECTURE-GOSPEL.md` §3.7.

The full classification of every data class in `:core:domain` is recorded in
`docs/domain-model-classification-inventory.csv` (STR-164); every later refactor
chunk cites its inventory rows. Only classes A and B get refactor chunks.

- **Outbound ports live in the application layer, full stop** (see
  `contracts/gospel/ARCHITECTURE-GOSPEL.md` §4). Application owns outbound
  `*Port` interfaces serving use cases; there are no domain ports — a pure
  domain-to-domain interface is a domain service or policy, not a port.
  `Repository` is an infrastructure implementation-class name only, never
  interface vocabulary. The inventory records the consuming layer per port.

### `:core:testing`

- Hand-written fakes implementing application outbound `*Port` interfaces (`Fake*Port`)
- `MainDispatcherRule` and shared test fixtures
- Pure JVM — no Android SDK in production sources
- Test modules (`:core:domain`, `:core:data`, `:feature:*`) depend on `:core:testing`; `:core:testing` depends only on `:core:domain`

### `:core:data`

Legacy module name: in canon this is a persistence package inside
`:core:infrastructure` (see `contracts/gospel/ARCHITECTURE-GOSPEL.md` §6).

- Room entities, DAOs, database, schema exports under `core/data/schemas/`
- Repository **implementations** (implementation-class name only) of application outbound `*Port` interfaces
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

The presentation contract is canon: see `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12
(UI state, one-shot events, model mapping, driving-adapter dependencies & Flow composition)
and §13 (navigation & scaffold architecture).

### 4.1 UI state

See `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.1.

### 4.2 One-shot events

See `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.2.

### 4.3 Model mapping

See `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.3.

- Detekt enforces via `ForbiddenDomainTypeInComposable` (public composables) and
  `ForbiddenDomainTypeInUiState` (presentation data classes).

### 4.4 ViewModel dependencies & Flow composition

See `contracts/gospel/ARCHITECTURE-GOSPEL.md` §12.4.

### 4.5 Navigation

See `contracts/gospel/ARCHITECTURE-GOSPEL.md` §13.

### 4.6 Scaffold ownership

- Each screen owns its own `StratumScaffold` / top bar (via `:core:ui` primitives)
- Do not thread root `Scaffold` `paddingValues` down the tree

---

## 5. Forbidden Imports (Summary)

The forbidden-import principles are canon: see
`contracts/gospel/ARCHITECTURE-GOSPEL.md` §2. Apply them to the modules in §1
along the graph in §2.

## 6. Data & Background Work (Guidance)

**Stratum-local guidance.** The gospel module map
(`contracts/gospel/ARCHITECTURE-GOSPEL.md` §6) names `:core:data` a legacy
label for a persistence package inside `:core:infrastructure`;
`DATA_LAYER_RULES.md` stays local in full — so this section is Stratum's own
guidance and stays.

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

# Onboarding Vertical Slices — Stratum

Planning document for the first user journey: **onboarding → starting program**.

Read alongside `contracts/ARCHITECTURE.md`, `contracts/UI_DESIGN.md`, and
`contracts/UNIT_TESTING.md`. Reference implementation patterns: LiftLab
(`~/Code/LiftLab`) for program/lift/set hierarchy; pocket-crew
(`~/Code/pocket-crew`) for OpenRouter + strict-schema patterns on the **server function**
(`OpenRouterInferenceServiceImpl`, `JsonSchemaWireSupport`, single-shot structured output —
not `agent_core` or tool loops); golden tests via
`./scripts/golden-test-examples-download.sh`.

**Workflow:** One slice = one branch = one PR to protected `main` on gitlawb.

---

## 1. Product goal

First-time users complete a **2–4 minute** flow and land on a **generated starting
program** tailored to profile, schedule, specialization, and available lifts.

Stratum should feel like a competent training partner from minute one — not a generic
logger. Onboarding quality is the highest-leverage UX investment.

### Onboarding steps (final order)

| # | Step | Purpose |
|---|------|---------|
| 1 | Welcome | Set tone; explain value briefly |
| 2 | Profile | Age, gender (optional), experience, last trained seriously |
| 3 | Schedule | **Actual weekdays** + session length + time-since-last-session signal |
| 4 | Specialization | Optional muscle emphasis; capped with validation + coaching hints |
| 5 | Gym & lifts | **Import training data** (JSON/CSV) **or** Commercial/Garage preset → curated lift list; manual enable/disable |
| 6 | Review | Read-only summary of all inputs |
| 7 | Generate | LLM program creation (structured output) → onboarding complete → home |

---

## 2. Domain decisions (locked for onboarding)

These are agreed design choices — implement consistently across slices.

### 2.1 Lift-first catalog (narrow equipment for increments)

- **Lift** remains the primary catalog entity (program, log, filter, availability).
- **Gym preset** (“Commercial gym”, “Garage gym”) bulk-enables a bundled lift set.
- **Equipment** is a narrow persisted catalog used for **shared default increment tiers** and
  display names — not an availability graph and not an exhaustive brand zoo. Lifts optionally
  reference equipment via FK; users can create custom equipment and override per lift.
- Manual edits still mutate **which lifts are available** for programming. Equipment assignment
  only changes default load steps (and display), not enablement wiring.

**Transient equipment tags at import (OS-6).** A structured `GymEquipment` vocabulary and a bundled
`lift-equipment-tags.json` APK asset (built once offline) are used **only** during historical import
to drive Tier B lift enablement (equipment closure) and the sparse-import gym-type fallback.
That enum is **not** the Room `Equipment` table. At seed time, load-bearing tags map into seed
`EquipmentId` rows for default increments. See OS-6 for the two-tier enablement pipeline.

### 2.2 Increment tiers and load steps

All working sets are **straight sets** (same load across sets within a lift). No within-session
weight drops or ramping — that logic is removed with `allowWeightChangeBetweenSets`.

Resolution order for a lift's effective tiers:

1. Lift-scoped **override** rows (if any)
2. Else equipment default tiers (if the lift has `equipmentId`)
3. Else global fallback multi-band defaults

| Field | Purpose | Who sets it |
|-------|---------|-------------|
| Equipment `incrementTiers` | Shared defaults for all lifts on that equipment | Seed asset; Manage Equipment UI |
| Lift override `incrementTiers` | Per-lift exception | Lift Increments UI; empty clears override |

Equipment seed defaults are typically a **single open-ended band** (e.g. dumbbells +5 lb). Multi-band
lists remain valid for specialized machines or user overrides.

Increment tiers example (lb, multi-band fallback / override):

```text
0 – 40   → 2.5
40 – 100 → 5
100 – 300 → 10
300+     → 25
```

Use a domain `Weight` type (amount + unit); validate tiers (sorted, non-overlapping, positive increment).

### 2.3 Progression schemes (locked)

Stratum is **opinionated and streamlined** — unlike LiftLab's many user-configurable
`ProgressionScheme` options (wave loading, top set, dynamic double, etc.), v1 exposes exactly
**two** schemes. The user never picks a scheme in the UI; assignment and set rules run in the
background.

#### Domain enum

```kotlin
enum class ProgressionScheme { Linear, Double }
```

No other variants. Do not port LiftLab's full `ProgressionScheme` enum or progression dropdowns.

#### Scheme assignment (deterministic)

| Profile | Scheme |
|---------|--------|
| `TrainingAge.Beginner` | **Linear** |
| All other `TrainingAge` values | **Double** |

Assigned per `WorkoutLift` at generation time from the user's profile. Not overridable in v1 UI.

#### Linear (beginners only)

- All working sets share the same rep range.
- Add load when the lifter completes the prescribed work at target — standard linear
  progression. No RIR-based top-set / back-off split.
- Increment from `incrementTiers` on the lift.

#### Double (everyone else)

Simple double progression with an explicit top set. **All sets use the same load** (straight
sets) — no within-session weight changes:

| Set role | Rep range | RIR goal | Progression |
|----------|-----------|----------|-------------|
| **Top set** (first working set) | Yes — e.g. 8–12 | **2** | Drives progression |
| **Remaining sets** | **No** rep range | See below | Follow top set load |

**Progression rule:** when the lifter hits the **top of the rep range** on the top set at
**RIR ≥ 2**, bump weight next session (using `incrementTiers`).

**Back-off RIR goals:**

| Lift class | Back-off RIR |
|------------|--------------|
| **Major compound** (`LegPush`, `HipHinge`, `HorizontalPush`, `VerticalPush`, `InclinePush`, `HorizontalPull`, `VerticalPull`) | **1–2** (generation picks 1 or 2; default **2**) |
| Everything else (isolation, calves, etc.) | **0** |

Each set carries an `rirGoal`. Only the top set carries `repRangeMin` / `repRangeMax` on
Double progression lifts.

#### What the user does *not* configure

- Progression scheme (derived from training age)
- RIR rules per scheme (product defaults)
- LiftLab-style per-lift scheme dropdowns or wave-loading UI

Program generation (OS-7) and runtime engines apply these rules. `:feature:program` may allow
lift swaps and set tweaks later — not scheme pickers in v1.

#### LiftLab contrast

| LiftLab | Stratum |
|---------|---------|
| 5+ schemes, UI-configurable | 2 schemes, automatic |
| User picks per lift | `TrainingAge` → scheme |
| Varied RIR/rep rules per scheme | Fixed Linear / Double semantics above |

### 2.4 Program hierarchy

```text
Program
 └── Workout          (training day; bind to weekday from schedule when possible)
      └── WorkoutLift (progressionScheme: Linear | Double; set template)
           └── Set     (rirGoal; repRange optional — top set only on Double;
                          type: Standard | MyoRep | DropSet)
```

Port concepts from LiftLab; extend with fractional **muscle contribution** (vision doc) in
a later slice if not needed for v1 generation.

### 2.5 Intelligence split

| Concern | Layer | Onboarding slice |
|---------|--------|------------------|
| Google Sign-In app entry gate (blocks all Firebase Callables) | Firebase Auth + App Check | **OS-0** |
| Step validation, specialization caps, preset filtering | Deterministic (domain) | OS-1 – OS-5 |
| Historical import, lift resolution, analytics extract + store | Deterministic (domain) + **OpenRouter agent via Firebase proxy** | OS-6 |
| Program generation (split, volume, progression, lift selection) | **OpenRouter via Firebase proxy** | OS-7 |
| Coaching copy on specialization | Static | OS-4 |

**Why LLM for program generation:** Profile × schedule × specialization × available lifts
creates far too many permutations for canned deterministic templates. Programming nuance
(progressions, splits, advanced techniques, volume landmarks) is exactly the kind of
high-judgment synthesis an LLM handles well — provided output is schema-constrained.

**Why no post-generation chat:** A live voice/text assistant after Generate is out of scope.
It is hard to bound (users could treat it as free general LLM access) and low value once a
full program exists — swapping lifts and tweaking sets manually is straightforward and
already removes most cognitive load. Post-onboarding edits belong in `:feature:program`.

Do **not** block OS-1–OS-5 on inference infrastructure. Define **`ProgramGeneratorPort`**
with a fake in `:core:testing`; OS-7 wires a Firebase Callable Function client. OS-6 wires
import-agent Callables in `:core:inference`; both require **OS-0** auth + App Check.

### 2.6 Specialization rules (v1)

- User may **skip** entirely (maintenance-balanced default — non-selected muscles are
  implicitly `Maintenance`, so an empty selection is a valid, complete answer).
- Max **3** specializations per mesocycle. This aligns with the research consensus
  (Renaissance Periodization volume landmarks; Alpha Progression 2–3; Born Fitness 1–3):
  specialization blocks push 1–3 focus muscles toward MRV while the rest sit at
  MEV/MV. (Updated from an earlier "max 2" draft — 2 is too tight for an
  intermediate/advanced user with three lagging groups.)
- No "≥1 maintenance" sub-rule: maintenance is already the default for every non-selected
  muscle, so the qualifier is redundant.
- **No push/pull hard-block at selection time.** Push/pull (antagonist) balance is a
  *volume-distribution* concern handled at program generation (OS-7), where per-muscle
  weekly-set targets live.
- The muscle enum is named **`MuscleGroup`** (not the LiftLab `VolumeType` nor the earlier
  `VolumeMuscle` draft). See §10.

### 2.7 Credentials, OpenRouter, and LLM proxy (locked)

**Never ship provider API keys in the APK.** Keys embedded in a client binary are extractable.

| Do | Don't |
|----|-------|
| Proxy LLM calls through **Firebase Callable Functions** | Call OpenRouter (or any provider) directly from the Android app |
| Store **`OPENROUTER_API_KEY`**, **`OPENROUTER_MODEL`**, and **`OPENROUTER_MODEL_SMALL`** in **Firebase Secret Manager** | Store API keys in Firestore, Remote Config, or any client-readable store |
| Bind both secrets in the Callable Function deploy | Let users pick models in v1 or hardcode model in the APK |
| Require **Firebase Auth (Google Sign-In)** before invoking generate | Expose an unauthenticated open proxy |
| Enforce **App Check** + per-user rate limits on the function | Rely on obscurity alone for abuse control |

#### Where credentials live

| Asset | Location | Committed to git? |
|-------|----------|-------------------|
| Firebase Android config | `app/google-services.json` | **No** — listed in `.gitignore`; each dev/CI job supplies their own copy |
| OpenRouter API key | Firebase Secret Manager → `OPENROUTER_API_KEY` | **No** — **provisioned** (see below) |
| Model slug (program gen) | Firebase Secret Manager → `OPENROUTER_MODEL` | **No** — **provisioned** (`deepseek/deepseek-v4-pro`) |
| Model slug (import equipment inference, Flash) | Firebase Secret Manager → `OPENROUTER_MODEL_SMALL` | **No** — **provisioned** (`deepseek/deepseek-v4-flash`); wired into `inferImportEquipment` callable |
| System prompt | `functions/src/prompts/` (or similar) | **Yes** — versioned with the function |

**Provider (locked for v1):** [OpenRouter](https://openrouter.ai/) — single OpenAI-compatible
API (`https://openrouter.ai/api/v1`) so models can be swapped by changing server config.

**Default model (locked for v1):** `deepseek/deepseek-v4-pro` — stored as `OPENROUTER_MODEL` in
Secret Manager alongside `OPENROUTER_API_KEY`.

#### Firebase project (status)

**Console provisioning (maintainer — done; do not re-prompt):**

| Item | Status |
|------|--------|
| Firebase project | **Done** |
| `app/google-services.json` (local, gitignored) | **Done** — updated after enabling Google Auth |
| Secret Manager → `OPENROUTER_API_KEY` | **Done** |
| Secret Manager → `OPENROUTER_MODEL` | **Done** — `deepseek/deepseek-v4-pro` |
| Secret Manager → `OPENROUTER_MODEL_SMALL` | **Done** — `deepseek/deepseek-v4-flash` for `inferImportEquipment` (OS-6 transient equipment inference) |
| Android SHA-1 / SHA-256 fingerprints (debug) | **Done** |
| Authentication → **Google** sign-in provider | **Done** |
| Authentication → authorized domains | **Done** |
| App Check (Play Integrity + debug provider) | **Done** |

**Agent implementation (not started in repo):**

| Item | Owner |
|------|--------|
| Google Services Gradle plugin + Firebase BOM on `:app` | OS-0 |
| `firebase-auth` + Google Sign-In app entry gate (Credential Manager) | OS-0 |
| Firebase App Check (Play Integrity + debug provider) | OS-0 |
| `functions/` scaffold, `startImport` + `getImportJobStatus` + `processImportJob` | OS-6 |
| `:core:inference` — `RunFullHistoricalImportUseCase`, `FirebaseHistoricalImportAdapter` | OS-6 |
| `firebase-functions` client → Callable `generateProgram` | OS-7 |
| `functions/` OpenRouter call for `generateProgram`, secret binding, deploy | OS-7 |

**Auth (locked for v1):** **Google Sign-In** via Firebase Authentication (not anonymous).
OS-0 gates the app before onboarding or home. Callable invocations (`importAgentTurn`,
`generateProgram`) require an authenticated Firebase Auth session + App Check token.

Same project will host Firestore cloud sync later.

#### Request flow

```text
Android (:core:inference)
  → Firebase Callable Function `generateProgram` (Auth + App Check)
       → reads OPENROUTER_API_KEY from Secret Manager
       → model = OPENROUTER_MODEL (default deepseek/deepseek-v4-pro)
       → builds prompt (system rules + user onboarding snapshot)
       → POST openrouter.ai/api/v1/chat/completions (strict JSON schema)
       → validates lifts ⊆ enabled list; retry once on schema/validation failure
       → returns Program JSON
  → parse, validate, persist locally (Room)
```

OpenRouter is called **only from the server function**, not from the Android app. pocket-crew's
`OpenRouterInferenceServiceImpl` and strict-schema patterns inform the function implementation;
`:core:inference` on Android is a thin Firebase Callable client.

---

## 3. Module plan

| Module | Created in slice | Notes |
|--------|------------------|-------|
| `:feature:onboarding` | OS-1 | Wizard UI, ViewModels, `*Route` composables |
| `:core:domain` | OS-1+ | Models, ports, use cases (extend each slice) |
| `:core:data` | OS-1+ | Room, repositories, mappers |
| `:core:testing` | Each slice | `Fake*` for every new port |
| `:core:inference` | OS-6 | No Compose; upload/poll/download Callable client + `RunFullHistoricalImportUseCase` |
| `functions/` (Firebase) | OS-6 + OS-7 | OS-6: `startImport`, `getImportJobStatus`, `processImportJob`, `ImportAgentSession`; OS-7: `generateProgram` |
| `:app` | OS-0 + OS-1 | OS-0: auth gate; OS-1: Hilt, NavHost, onboarding vs home routing |

Add `:feature:onboarding` to `settings.gradle.kts` in OS-1. Add `:core:inference` in OS-6.
Update `ARCHITECTURE.md` when those modules land (contract waiver or maintainer edit).

---

## 4. Schema policy

`AGENTS.md` **schema lock** applies: new Room tables/columns require explicit
authorization before implementation.

Each slice below lists **Schema: authorize before coding**. Treat authorization as:
you (maintainer) confirm in the PR or issue, then agent implements.

---

## 5. Vertical slices

### OS-0 — Google Sign-In (app entry gate)

**Branch:** `feature/auth-google-sign-in`

**Goal:** Mandatory Google Sign-In before onboarding or home. Prerequisite for all Firebase
Callable usage (OS-6 import agent, OS-7 program generation).

**Not an onboarding wizard step** — activity-level gate in `MainActivity` before `StratumNavHost`.

| In scope | Out of scope |
|----------|--------------|
| `AuthManager` port, `AuthState`, `LoginWithGoogleUseCase` | Onboarding wizard steps |
| `FirebaseGoogleAuthManager` (Credential Manager + Google Identity) | Guest mode / skip |
| `StratumGoogleSignInButton` + official `ic_google.xml` branding | Vault / optional-sync UX (pocket-crew) |
| `SignInScreen` — single CTA | Sign-in on Generate step (OS-7 uses existing session) |
| `MainActivity` as `FragmentActivity`; auth gate before NavHost | |
| Firebase App Check (Play Integrity + debug provider) | |

**UX flow:**

```text
App launch
  → Auth loading (brief spinner)
  → Unauthenticated? → SignInScreen
  → Authenticated?   → StratumNavHost (onboarding or home)
```

**Reference:** pocket-crew `FirebaseGoogleAuthManager`, `GoogleSignInButton`, `AuthManager` port —
mirror layering; Stratum uses Google-only `SignInScreen` (no vault unlock).

**Domain / data / app:**

| Layer | Artifacts |
|-------|-----------|
| `:core:domain` | `AuthManager` port, `AuthState`, `GetAuthStateUseCase`, `LoginWithGoogleUseCase`, `LogoutUseCase` |
| `:core:data` | `FirebaseGoogleAuthManager`; Hilt: `FirebaseAuth.getInstance()`, `CredentialManager.create()` |
| `:core:ui` | `StratumGoogleSignInButton`, `ic_google.xml` (official multicolor G; `tint = Color.Unspecified`) |
| `:core:testing` | `FakeAuthManager` |
| `:app` | `MainViewModel`, `SignInScreen`, `MainActivity` gate; Firebase BOM, `firebase-auth`, `google-services` plugin |

**Tests:**

- `LoginWithGoogleUseCaseTest` with `FakeAuthManager`
- `FirebaseGoogleAuthManagerTest` — auth listener + signOut (MockK allowed for Firebase SDK with suppress)
- `SignInScreenTest` (Robolectric) — unauthenticated shows branded button; loading disables CTA

**Acceptance:**

- Fresh install → Sign In screen only; no Welcome until authenticated
- Successful sign-in → Welcome (or home if onboarding complete)
- Kill app while signed in → resumes past sign-in (Firebase session restored)
- Blocks Firebase Callable invocations until authenticated (OS-6, OS-7)
- `./gradlew :app:assembleDebug` with local `google-services.json`

**Blocks:** OS-6 (`startImport`, `getImportJobStatus`), OS-7 (`generateProgram`). Recommend merging before any Firebase Callable PR.

---

### OS-1 — Onboarding foundation

**Branch:** `feature/onboarding-foundation`

**Goal:** App routes new users into onboarding; wizard shell persists progress.

| In scope | Out of scope |
|----------|--------------|
| `:feature:onboarding` module | Profile/schedule UI fields |
| Hilt `Application` + app NavHost | Lift catalog |
| `OnboardingRoute` wizard scaffold (stepper / back-next) | Program generation |
| Welcome screen (step 1) | LLM |
| Domain: `OnboardingStep`, `OnboardingProgress` | |
| Port: `OnboardingRepository` | |
| Room: onboarding progress row | |
| App launch: if incomplete → onboarding; else → placeholder home | |

**Schema (authorize first):**

- `onboarding_progress` — current step enum, started_at, completed_at nullable

**Tests:**

- `SaveOnboardingProgressUseCaseTest` with fake repo
- ViewModel test: welcome → next advances step (Turbine if StateFlow)

**Acceptance:**

- Fresh install opens Welcome
- Kill app mid-flow → resumes same step
- `./gradlew :feature:onboarding:testDebugUnitTest` + detekt green

---

### OS-2 — Profile & schedule

**Branch:** `feature/onboarding-profile-schedule`

**Depends on:** OS-1

**Goal:** Capture training identity and weekly commitment.

| Step | Fields |
|------|--------|
| Profile | age, gender (optional), experience (novice / intermediate / advanced), last_trained_seriously (date or bucket) |
| Schedule | weekdays (Mon–Sun multi-select), session_length_minutes (45 / 60 / 75 / custom), |

**Schema (authorize first):**

- `user_training_profile` — columns above + FK or embed in onboarding progress

**Tests:**

- Validation use cases (age bounds, at least 1 weekday, session length > 0)
- ViewModel tests per step with fakes

**Acceptance:**

- Complete profile + schedule → persisted → Review in OS-5 can read values
- Back navigation preserves edits

---

### OS-3 — Specialization

**Branch:** `feature/onboarding-specialization`

**Depends on:** OS-2

**Goal:** Optional muscle emphasis with enforced caps and coaching.

**Schema (authorize first):**

- `specialization_preference` — `muscle` PK (TEXT), `level` (TEXT). **Authorized for OS-3.**
  One row per explicitly-set muscle; absence == `Maintenance`. No `program_id` FK yet —
  deferred to OS-7 (becomes a composite-PK concern once programs exist). DB v2 → v3 with an
  additive migration. Enum↔String persistence standardized via a single `EnumConverters`
  `@TypeConverter` class (retrofitted across all onboarding enums; entity columns are now
  typed enums, not raw Strings).

**Domain:**

- `MuscleGroup` enum (15 values, LiftLab `VolumeType` name parity; **named `MuscleGroup`**,
  not `VolumeMuscle` — resolves §10).
- `SpecializationLevel` enum (`Specialize` / `Maintenance` / `Omit`); `Omit` reserved for OS-7.
- `SpecializationPreference` value type (`Map<MuscleGroup, SpecializationLevel>`,
  absence == Maintenance).
- `ValidateSpecializationStepUseCase` (cap of 3; empty == valid).

**Tests:**

- Validation: empty → Valid, 1–3 → Valid, 4 → Invalid(TooManySpecialized); maintenance/Omit
  levels don't consume the cap.
- ViewModel: skip path, selection round-trip, cap gating, next() persists + advances,
  back() preserves.

**Acceptance:**

- Skip → empty specialization stored (valid)
- Over-cap → blocked (chip-disable + inline coaching; cap enforced in domain too)
- Cap of 3 enforced; selection doesn't block Next (skip is valid)

---

### OS-4 — Gym preset & lift catalog

**Branch:** `feature/onboarding-lift-catalog`

**Depends on:** OS-2 (OS-3 optional parallel after OS-2)

**Goal:** User picks preset; toggles lifts; seed data defines defaults.

**Assets:**

- `app/src/main/assets/lifts/` or `core/data/.../assets/` — bundled catalog JSON
- Seed fields per lift: name, movement pattern, volume contributions (v1 bitmask OK), `incrementTiers`, preset tags

**Schema (authorize first):**

- `lifts` — catalog (seed + user custom)
- `user_lift_availability` — enabled flag per lift for this user

**UI:**

- Preset chips: Commercial gym / Garage gym
- Search + toggle list
- “Add custom lift” minimal form: name + optional single increment (tiers later). No progression
  scheme picker — §2.3 applies automatically at generation.

**Tests:**

- Repository: seed loads N lifts; toggle persists
- Filter by preset tag

**Acceptance:**

- Preset selects sensible default set
- Manual toggle survives relaunch
- Custom lift appears in enabled list

---

### OS-5 — Review

**Branch:** `feature/onboarding-review`

**Depends on:** OS-2, OS-3, OS-4

**Goal:** Single summary screen before generation.

**UI:**

- Sections: Profile, Schedule, Specialization (or “Balanced”), Lift count + preset name
- Primary CTA: **Generate program** (wired in OS-7; disabled stub OK in OS-5 if needed)

**Tests:**

- ViewModel maps domain → `ReviewUiState`

**Acceptance:**

- All prior inputs visible and accurate
- Edit links jump back to correct step (optional in OS-5; required before ship if omitted)

---

### OS-6 — Historical data import & training insights

**Branch:** `feature/onboarding-historical-import`

**Depends on:** OS-0, OS-4

**Goal:** Optional import path on the **Gym & Lifts** step. User uploads JSON/CSV training
history → LLM conversion agent normalizes to `LoggedSet` rows → on-device analytics
**extract and persist** training insights. OS-6 does **not** apply programming decisions —
OS-7 reads persisted insights as LLM context.

**Wizard step count stays 7** — no new `OnboardingStep`. Import affordance extends
`GymAndLiftsScreen` (preset XOR import; user picks one path).

```text
Gym & Lifts step (OnboardingStep.GymAndLifts)
  ├── [Commercial gym]         → bulk-enable preset lifts (existing)
  ├── [Garage gym]           → bulk-enable preset lifts (existing)
  └── [Import training data] → file picker (JSON/CSV) → async import pipeline
```

| In scope | Out of scope |
|----------|--------------|
| Import UI on Gym & Lifts (file picker, phased progress, error) | Program generation (OS-7) |
| `RunFullHistoricalImportUseCase` — upload, poll, download, local persist | Rep-range / volume-cap / superset decisions |
| Firebase Callables `startImport` + `getImportJobStatus` + `inferImportEquipment` (Flash, transient equipment inference); Firestore-triggered `ImportAgentSession` | `programs` / `workouts` tables (OS-7) |
| `functions-python/runConversionScript` sandbox (RestrictedPython-style denylist) | Re-running analytics at generate time |
| Normalize → `logged_sets`; fuzzy lift name resolution | Google Sign-In (OS-0) |
| `ExtractTrainingInsightsUseCase` — compute + **persist** insights | |
| Two-tier lift enablement (Tier A proven + Tier B equipment closure + sparse-import fallback); bundled `lift-equipment-tags.json` asset | |

**OS-6 scope boundary:** extract and store, do not act.

| OS-6 writes | OS-7+ consumes |
|-------------|----------------|
| `LoggedSet` rows from import | Starting weights from e1RM estimates |
| Per-lift fatigue coefficients, e1RM, progress trends | Rep-range width from fatigue decay |
| Superset affinity pairs (median rest, co-occurrence) | Gym-layout / pairing hints |
| Per-muscle volume baselines (MEV/MRV proxy inputs) | Initial volume anchoring |
| Import analysis metadata (window, **`provenLiftIds`**) | LLM context at generation (proven vs. available) |

**Two-tier lift enablement (import path).** Import is a high-confidence signal, not an exhaustive
inventory. Enablement is split into:

- **Tier A (proven):** catalog lifts matched from the user's logged history + custom lifts created
  from it. Their ids are persisted as `provenLiftIds` in `ImportAnalysisMetadata` and prioritized as
  primary movements by OS-7 program generation.
- **Tier B (inferred available):** additional catalog lifts enabled by **equipment closure** — a
  lift is enabled when its required equipment (from the bundled `lift-equipment-tags.json` asset) is
  a subset of the equipment confirmed from the user's import (Tier A matches + a transient Flash
  `inferImportEquipment` callable). The closure is the sole gate; no preset gate.

If the closure enables too few lifts (sparse import), a gym type is **derived transiently from the
confirmed equipment** (cable/machine present → Commercial; else Garage) and the matching preset's
tagged lifts are backfilled. The derived gym type is discarded after this step — it is never
persisted (the old `inferredGymProfile` heuristic is removed). Equipment and gym type are transient
import-enablement signals only.

**Custom-lift loading method (`inferCustomLifts`).** Unmatched import names get movement/muscle
metadata plus a closed **seed loading-method** enum (`dumbbell`, `double-weight-plates`,
`pin-loaded-machine`, …) or an explicit `custom` branch (name + single open-ended increment).
Server returns structured `loading` objects; Android decodes that shape and maps seed ids to
Room `Equipment` (or find-or-create for custom names). Unknown / blank loading uses a shared
**Default** custom equipment at +5 lb so progression always has a lattice. Plate-loaded dual-horn
machines (e.g. Hammer Strength chest press) use `double-weight-plates`.

#### Domain models (new in `:core:domain`)

Historical logging is distinct from program-template hierarchy (OS-7):

- `HistoricalSession`, `LoggedSet`, `ImportJob`
- `LiftTrainingInsights` — per-lift e1RM, fatigue decay slope/intercept, `ProgressTrend`, session count
- `SupersetAffinity` — lift pairs with median rest seconds, co-occurrence, confidence
- `MuscleVolumeBaseline` — per `MuscleGroup` mean/p90 weekly hard sets, trend slope
- `ImportAnalysisMetadata` — analysis window, inferred gym profile, unmatched exercise names

**Ports:** `HistoricalImportPort`, `HistoricalImportRunnerPort`, `TrainingHistoryRepository`,
`LiftTrainingInsightsRepository`, `SupersetAffinityRepository`, `MuscleVolumeBaselineRepository`,
`ImportAnalysisMetadataRepository`, `LiftNameResolverPort`

**Use cases:** `StartHistoricalImportUseCase`, `RunFullHistoricalImportUseCase` (via `HistoricalImportRunnerPort`),
`ExtractTrainingInsightsUseCase`, `ResolveImportLiftsUseCase`, `PersistImportedHistoryUseCase`

#### Inference architecture

**Server-owned agent loop** — Android uploads raw + sample, starts job, polls status, downloads
normalized JSON from Storage; local domain code parses, persists rows, and runs analytics.

```text
Android (:core:inference)
  FirebaseHistoricalImportAdapter  ← Storage upload, startImport, getImportJobStatus, download
  RunFullHistoricalImportUseCase   ← Flow<HistoricalImportEvent> with ImportProgress phases

Firestore  users/{uid}/importJobs/{jobId}
  status, progressPhase, progressMessage, latestConversion.storagePath, storage path refs

Storage  imports/{uid}/{jobId}/raw|sample|script|slice|conversions/.../normalized.json

functions/ (Node/TS)
  startImport, getImportJobStatus     ← client callables (Auth + App Check)
  processImportJob                  ← Firestore trigger when status=pending_agent
  ImportAgentSession                ← OpenRouter tool loop (write/execute sample/full), blob gate

functions-python/
  runConversionScript               ← sandboxed Python; Storage I/O; returns normalized JSON
```

Agent tools (each requires user-facing `intent` for progress UI): `write_conversion_script`,
`execute_on_sample`, `execute_on_full`. Server writes `progressPhase` + `progressMessage` to
Firestore on every tool dispatch; Android polls every 1.5s (3s after 30s).

Reference: pocket-crew `OpenRouterInferenceServiceImpl` for server patterns — **no `agent_core` port**.

**Abuse boundaries:** Auth + App Check (requires OS-0); per-uid rate limit (1 active job,
max 15 agent turns); max upload size (~10 MB v1); `processImportJob` timeout 540s.

#### Analytics pipeline (`ExtractTrainingInsightsUseCase`)

Runs **on-device** after import + lift resolution (local-first, offline-capable):

1. **Analysis window** — most recent window with ≥30 hard sets per major muscle or ≥12 weeks, capped at 6 months
2. **Fatigue profiling** — e1RM per set; regress `e1RM ~ setIndex`; persist slope/intercept on `LiftTrainingInsights`
3. **Superset mining** — median Δt between lift pairs; cluster if <90s and co-occurrence ≥5; persist `SupersetAffinity`
4. **Gym inference** — enable lifts from usage; heuristic commercial vs garage in `ImportAnalysisMetadata`
5. **Volume baselines** — weekly hard sets per `MuscleGroup`; persist `MuscleVolumeBaseline`
6. **Progress detection** — regress e1RM over time; persist `ProgressTrend` on `LiftTrainingInsights`
7. **e1RM storage** — persist `estimatedOneRm` per lift; cross-lift extrapolation is OS-7 prompt concern

#### Schema (authorize first)

| Table | Purpose |
|-------|---------|
| `import_jobs` | status, storage refs, timestamps |
| `historical_sessions` | imported session headers |
| `logged_sets` | normalized set rows (indexed by session, lift, time) |
| `lift_training_insights` | per-lift e1RM, fatigue decay, progress trend, session count |
| `superset_affinities` | liftA + liftB pairs with median rest, co-occurrence |
| `muscle_volume_baselines` | per `MuscleGroup` mean/p90 weekly hard sets, trend |
| `import_analysis_metadata` | analysis window, inferred gym profile |

`programs` / `workouts` tables remain **OS-7** — do not conflate template sets with logged sets.

#### Tests

- `ExtractTrainingInsightsUseCaseTest` — synthetic histories; assert persisted insight row values
- `LiftNameResolverTest` — BM25/fuzzy match against seed catalog
- `RunFullHistoricalImportUseCaseTest` — fake port returns ready snapshot + JSON fixture
- `ImportAgentSession` / intent sanitization tests in `functions/`
- `GymAndLiftsViewModelTest` — preset path vs import path mutual exclusion
- Robolectric: `logged_sets` DAO round-trip

#### Acceptance

- User can skip import and continue with preset flow (Commercial/Garage)
- User can upload JSON/CSV → see progress → enabled lifts reflect import usage
- Analytics extracted and **persisted** to insight tables (verify via repository tests)
- No rep ranges, volume caps, or superset pairings applied automatically in OS-6
- Import upload requires network; extraction runs locally after normalized rows land
- `./gradlew :core:domain:test` + `:core:inference:test` + detekt green

---

### OS-7 — LLM program generation

**Branch:** `feature/onboarding-program-generate`

**Depends on:** OS-0, OS-5, OS-6

**Goal:** User taps **Generate program** → single structured LLM call produces a complete
program → persisted → onboarding complete → home. **First consumer** of OS-6 persisted
insights — does not re-run analytics.

**Auth:** satisfied by **OS-0** — user is already signed in before reaching Generate. No
separate sign-in on the Generate screen; Callable uses existing Firebase Auth session + App Check.

**Rationale (vs deterministic templates):** A rules engine would need an enormous library
of canned programs to cover experience level, training frequency, specialization, session
length, and lift availability. One schema-constrained generation call is simpler to maintain
and produces better-tailored output.

#### Prompt composition

| Part | Source |
|------|--------|
| System instructions | Bundled asset (`assets/prompts/program_generation_system.md` or similar): **§2.3 progression rules** (Linear vs Double, top-set semantics, major-compound RIR), split templates, volume landmarks (MEV/MRV), advanced techniques (myo-reps, drop sets), push/pull balance, time-budget heuristics — grounded in current exercise-science consensus |
| User context | Serialized onboarding snapshot: profile (**`TrainingAge`** for scheme), schedule, specialization, enabled lifts split into **`provenLifts`** (Tier A — matched from import history, carry `LiftTrainingInsights`, `proven: true`) and **`availableLifts`** (Tier B — equipment-closure / preset-backfill, no insights, `proven: false`, the substitution pool); plus **`SupersetAffinity`**, **`MuscleVolumeBaseline`**, **`ImportAnalysisMetadata`** (window, sessions, sets — no gym-type hint) when import path used (empty if skipped) |
| Output contract | Strict JSON schema matching domain `Program` → `Workout` → `WorkoutLift` → `Set` |

The model must only select lifts from the user's enabled list, assign `ProgressionScheme` per
§2.3 from `TrainingAge`, and structure Double lifts with a single top set (rep range + RIR 2)
plus back-offs (RIR only). Prefer **proven lifts** as primary movements; **available lifts** are the
substitution pool. No gym-type hint is sent — the curated lift pool itself is the gym-type signal.

#### Inference architecture

**Android** — thin Firebase client; **server** — OpenRouter call with strict structured output.

```text
:core:domain
  ProgramGeneratorPort          ← interface + GenerateProgramUseCase
  ProgramGenerationPromptBuilder ← serializes onboarding snapshot for the function payload
:core:inference
  FirebaseProgramGenerator      ← Callable Function client; parses response JSON → domain
:core:data
  ProgramRepository             ← persist parsed program
:feature:onboarding
  GenerateStepViewModel         ← loading / error UI; calls use case

functions/  (Firebase — Node/TS recommended)
  generateProgram               ← validates auth + App Check; rate limit
                                ← OPENROUTER_API_KEY from Secret Manager
                                ← OPENROUTER_MODEL (default deepseek/deepseek-v4-pro)
                                ← system prompt + user payload → OpenRouter strict schema
                                ← returns Program JSON
```

Reference implementations:

- **Server OpenRouter wiring:** pocket-crew `OpenRouterInferenceServiceImpl`,
  `JsonSchemaWireSupport`, strict schema, single-shot request — port to the Functions runtime
  (OpenAI-compatible Node SDK pointed at `https://openrouter.ai/api/v1`).
- **Skip on Android:** direct OpenRouter/OpenAI SDK, `agent_core`, tool loops, live voice.

**Structured output:** Function requests OpenRouter **strict JSON schema**
(`response_format: { type: "json_schema", ... }`) so the model returns a parseable `Program`
document — no free-form prose.

**Abuse boundary:** Exactly **one** bounded generation call per onboarding completion (enforce
server-side idempotency / rate limit per uid). No follow-up turns, no voice session, no open
chat. Regeneration (if added later) is a separate, explicit action in `:feature:program`.

#### Domain

- `Program`, `Workout`, `WorkoutLift`, `Set` models + `SetType`
- `GenerateProgramUseCase` — inputs: profile, schedule, specialization, enabled lifts → `Program`
- `ProgramGeneratorPort` — suspend fun returning `Result<Program>` or similar

#### Schema (authorize first)

- `programs`, `workouts`, `workout_lifts`, `sets` (prefer normalized for workout logging next)

#### App

- Generate step: loading state with progress copy (“Building your program…”)
- Error state: retry + back to review (basic handling in OS-7; richer copy in OS-8)
- Mark onboarding complete on success
- Navigate to home (“Your program is ready” stub)

#### Firebase setup (OS-7)

**Maintainer console work — done (do not re-prompt):** see §2.7 (Auth, App Check, secrets).

**Agent code work — OS-7 implementation:**

- [ ] `firebase-functions`: Callable client for `generateProgram` in `:core:inference`
- [ ] `functions/`: bind secrets, OpenRouter strict-schema call, auth + App Check check, deploy

(Auth + App Check client wiring lands in **OS-0**, not here.)

#### Tests

- `GenerateProgramUseCaseTest` with `FakeProgramGeneratorPort` — happy path + failure
- `ProgramGenerationPromptBuilderTest` — snapshot of user-context JSON from fixture inputs (with and without OS-6 insights)
- `ProgramJsonSchemaTest` or mapper test — recorded fixture JSON deserializes to domain models
- Contract test with **recorded** OpenRouter response fixtures (no live API in CI)
- Robolectric integration: program DAO round-trip (golden: `OfflineFirstUserDataRepositoryTest` pattern)

#### Acceptance

- End-to-end: fresh install → sign in (OS-0) → complete onboarding → program exists in DB
- Generate requires network + authenticated Firebase Auth session (from OS-0); function returns program JSON
- Payload includes persisted insights when import path used; omits insight blocks when skipped
- Offline / unauthenticated / rate-limited → clear error; onboarding not marked complete
- Home screen shows program name or first workout
- No conversational UI after generation

---

### OS-8 — Onboarding polish & resume

**Branch:** `feature/onboarding-polish`

**Depends on:** OS-7

**Goal:** Production-ready edges.

- Resume incomplete onboarding from any step
- “Start over” clears progress (confirm dialog)
- Error states (generation failed, empty lift selection, network/API errors)
- Light + dark previews on all onboarding screens (UI_DESIGN)
- Accessibility: touch targets, semantics

**Acceptance:**

- `./gradlew testDebugUnitTest`, `detekt`, `:app:assembleDebug` green
- Manual QA script in PR description

---

## 6. Dependency graph

```text
OS-0 Google Sign-In gate
  └── OS-1 foundation
        └── OS-2 profile & schedule
              ├── OS-3 specialization
              └── OS-4 lift catalog
                    └── OS-5 review
                          └── OS-6 historical import
                                └── OS-7 LLM program generate
                                      └── OS-8 polish
```

**Recommended implementation order:**

1. OS-0 → OS-1 → OS-2 → OS-4 → OS-3 → OS-5 → OS-6 → OS-7 → OS-8

OS-0 before any Firebase Callable work (OS-6, OS-7). OS-6 can start after OS-4 (lift catalog).

---

## 7. Per-PR checklist (every slice)

- [ ] Read `contracts/UNIT_TESTING.md`; copy golden test structure
- [ ] Fakes in `:core:testing` for new ports
- [ ] No MockK on owned ports; ViewModel tests use Turbine
- [ ] `StratumTheme` + `Stratum*` only in feature UI
- [ ] Schema authorized before Room entities
- [ ] `./gradlew detekt` — maxIssues 0
- [ ] Narrow tests during dev; full `testDebugUnitTest` before merge
- [ ] PR via gitlawb: `gl pr create stratum --head <branch> --base main`

---

## 8. Post-onboarding (out of scope here)

Next vertical slice after OS-7/OS-8:

- **`:feature:workout`** — active session logging against generated program
- **`:feature:program`** — view/edit program, swap lifts, tweak sets, optional regenerate
- **`:feature:settings`** — theme family, units

---

## 9. LiftLab porting map

| LiftLab | Stratum onboarding |
|---------|-------------------|
| `Lift` + bitmasks | `Lift` + contributions + boolean + tiers |
| `VolumeType` (muscle enum) | `MuscleGroup` (renamed for readability; 15 values, OS-3) |
| `ProgramGenerationRequest.specializationMuscles` | `SpecializationPreference` (OS-3) |
| `ProgramGenerationRequest` | Profile + schedule + specialization + enabled lifts |
| `LiftLabDatabaseWorker` seed | OS-4 asset seed + Room |
| No onboarding | OS-0 – OS-8 |
| `ProgressionScheme` (5+ UI options) | **`Linear` \| `Double` only** — auto from `TrainingAge` (§2.3) |

---

## 10. Open questions (resolve during OS-4 / OS-6 / OS-7)

- [x] Canonical muscle enum naming → **`MuscleGroup`** (resolved in OS-3). Named for
  fitness-app readability ("which muscle groups do you want to specialize?"), not the LiftLab
  `VolumeType` nor the earlier `VolumeMuscle` draft. 15 values; plain enum, no bitmask
  (bitmask/contributions added in OS-4).
- [ ] v1 generation: normalized program tables vs single JSON blob (prefer normalized for workout logging next)
- [ ] Home destination after onboarding: program overview vs “start first workout” CTA
- [ ] Units default: lb vs kg from locale vs explicit onboarding ask
- [x] OpenRouter secrets provisioned → `OPENROUTER_API_KEY` + `OPENROUTER_MODEL` in Secret Manager (§2.7)
- [x] API key model → **Firebase Callable Function proxy**; keys in Secret Manager only
- [x] Provider + model → **OpenRouter** / **`deepseek/deepseek-v4-pro`**
- [x] Progression schemes → **`Linear` \| `Double` only**; automatic from training age (§2.3)
- [ ] Program generation system prompt: bundled in `functions/` vs shared repo asset
- [x] Firebase Auth → **Google Sign-In** via Firebase Authentication (console provisioned §2.7; client wiring OS-0)
- [ ] Import: e1RM formula split threshold (reps 10 vs 11); BM25 vs embedding lift match (OS-6)
- [x] Import: gym type at import → **derived transiently from confirmed equipment** (sparse-import fallback only); `inferredGymProfile` removed, never persisted (OS-6)

---

*Document version: 2026-06-25 — OS-0 auth gate; OS-6 historical import (two-tier enablement, transient equipment inference, `provenLiftIds`); OS-7 program generation (`provenLifts` / `availableLifts` split).*

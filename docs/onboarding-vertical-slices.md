# Onboarding Vertical Slices — Stratum

Planning document for the first user journey: **onboarding → starting program**.

Read alongside `contracts/ARCHITECTURE.md`, `contracts/UI_DESIGN.md`, and
`contracts/UNIT_TESTING.md`. Reference implementation patterns: LiftLab
(`~/Code/LiftLab`) for program/lift/set hierarchy; golden tests via
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
| 5 | Gym & lifts | Preset → curated lift list; manual enable/disable (voice in later slice) |
| 6 | Review | Read-only summary of all inputs |
| 7 | Generate | Hybrid program creation → onboarding complete → home |

---

## 2. Domain decisions (locked for onboarding)

These are agreed design choices — implement consistently across slices.

### 2.1 Lift-first catalog (no equipment graph)

- **Lift** is the primary catalog entity (program, log, filter).
- **Gym preset** (“Commercial gym”, “Garage gym”) bulk-enables a bundled lift set.
- Equipment names live in **display copy / tags**, not as a separate entity graph.
- Voice and manual edits mutate **which lifts are available**, not equipment → lift wiring.

### 2.2 Loading / progression inputs (no `LoadingProfile`)

Per lift:

| Field | Purpose | Who sets it |
|-------|---------|-------------|
| `allowWeightChangeBetweenSets: Boolean` | Drives default progression style | Seed for catalog; user for custom lifts |
| `incrementTiers: List<IncrementTier>` | Realistic weight steps when load changes | Seed defaults; optional per-lift override |

**Deterministic mapping:**

- `allowWeightChangeBetweenSets == true` → default **dynamic double progression**
- `allowWeightChangeBetweenSets == false` → default **double progression**

Increment tiers example (lb, per lift or template):

```text
0 – 40   → 2.5
40 – 100 → 5
100 – 300 → 10
300+     → 25
```

Use a domain `Weight` type (amount + unit); validate tiers (sorted, non-overlapping, positive increment).

### 2.3 Program hierarchy

```text
Program
 └── Workout          (training day; bind to weekday from schedule when possible)
      └── WorkoutLift (progression scheme, rep range, set template)
           └── Set     (Standard | MyoRep | DropSet)
```

Port concepts from LiftLab; extend with fractional **muscle contribution** (vision doc) in
a later slice if not needed for v1 generation.

### 2.4 Intelligence split

| Concern | Layer | Onboarding slice |
|---------|--------|------------------|
| Step validation, specialization caps, preset filtering | Deterministic (domain) | OS-1 – OS-6 |
| Program skeleton (split, volume targets, time budget) | Deterministic (domain) | OS-6 |
| Program polish, rationales, voice catalog edits | On-device LLM | OS-7, OS-8 |
| Coaching copy on specialization | Static or light LLM | OS-4 (static first) |

Do **not** block OS-1–OS-6 on LLM. Define **ports** (`ProgramGeneratorPort`, `VoiceCatalogPort`) with fakes in `:core:testing`.

### 2.5 Specialization rules (v1)

- User may **skip** entirely (maintenance-balanced default).
- Max **2** full specializations, or **3** with at least one at maintenance level.
- Block obviously invalid combos (e.g. all push, no pull) with inline coaching.
- Optional hint copy: antagonist pairs (chest/back, quads/hams).

---

## 3. Module plan

| Module | Created in slice | Notes |
|--------|------------------|-------|
| `:feature:onboarding` | OS-1 | Wizard UI, ViewModels, `*Route` composables |
| `:core:domain` | OS-1+ | Models, ports, use cases (extend each slice) |
| `:core:data` | OS-1+ | Room, repositories, mappers |
| `:core:testing` | Each slice | `Fake*` for every new port |
| `:core:inference` or `:feature:inference` | OS-7 | No Compose; LLM + tool calls only |
| `:app` | OS-1 | Hilt, NavHost, onboarding vs home routing |

Add `:feature:onboarding` to `settings.gradle.kts` in OS-1. Update `ARCHITECTURE.md`
when the module lands (contract waiver or maintainer edit).

---

## 4. Schema policy

`AGENTS.md` **schema lock** applies: new Room tables/columns require explicit
authorization before implementation.

Each slice below lists **Schema: authorize before coding**. Treat authorization as:
you (maintainer) confirm in the PR or issue, then agent implements.

---

## 5. Vertical slices

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

- `specialization_preference` — muscle id, level (specialize / maintenance / omit), or JSON blob on profile

**Domain:**

- `VolumeMuscle` enum (port from LiftLab `VolumeType` naming to Stratum)
- `ValidateSpecializationUseCase`

**Tests:**

- Validation: max 2 specialize, skip path, invalid all-push rejected

**Acceptance:**

- Skip → empty specialization stored
- Invalid selection → inline error, cannot proceed
- Static coaching strings visible (UI_DESIGN compliant)

---

### OS-4 — Gym preset & lift catalog

**Branch:** `feature/onboarding-lift-catalog`

**Depends on:** OS-2 (OS-3 optional parallel after OS-2)

**Goal:** User picks preset; toggles lifts; seed data defines defaults.

**Assets:**

- `app/src/main/assets/lifts/` or `core/data/.../assets/` — bundled catalog JSON
- Seed fields per lift: name, movement pattern, volume contributions (v1 bitmask OK), `allowWeightChangeBetweenSets`, `incrementTiers`, preset tags

**Schema (authorize first):**

- `lifts` — catalog (seed + user custom)
- `user_lift_availability` — enabled flag per lift for this user

**UI:**

- Preset chips: Commercial gym / Garage gym
- Search + toggle list
- “Add custom lift” minimal form: name + `allowWeightChangeBetweenSets` + optional single increment (tiers later)

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
- Primary CTA: **Generate program** (wired in OS-6; disabled stub OK in OS-5 if needed)

**Tests:**

- ViewModel maps domain → `ReviewUiState`

**Acceptance:**

- All prior inputs visible and accurate
- Edit links jump back to correct step (optional in OS-5; required before ship if omitted)

---

### OS-6 — Deterministic program generation

**Branch:** `feature/onboarding-program-generate`

**Depends on:** OS-5

**Goal:** User taps Generate → deterministic skeleton program persisted → onboarding complete → home placeholder.

**Domain:**

- `Program`, `Workout`, `WorkoutLift`, `Set` models + `SetType`
- `GenerateProgramUseCase` — inputs: profile, schedule, specialization, enabled lifts
- Logic: split template from weekdays + count, filter lifts, assign default progression from `allowWeightChangeBetweenSets`, rough volume/time budget (simple rules v1)

**Schema (authorize first):**

- `programs`, `workouts`, `workout_lifts`, `sets` (or JSON blob v1 — prefer normalized if LiftLab parity matters)

**Ports:**

- `ProgramGeneratorPort` — deterministic impl in `:core:data` or `:core:domain`
- Fake returns fixed program for tests

**App:**

- Mark onboarding complete
- Navigate to home (“Your program is ready” stub)

**Tests:**

- `GenerateProgramUseCaseTest` — 3-day schedule → 3 workouts; pin-stack lift → dynamic double progression
- Robolectric integration: program DAO round-trip (golden: `OfflineFirstUserDataRepositoryTest` pattern)

**Acceptance:**

- End-to-end: fresh install → complete onboarding → program exists in DB
- No network / no LLM required
- Home screen shows program name or first workout

---

### OS-7 — LLM program refinement

**Branch:** `feature/onboarding-program-llm`

**Depends on:** OS-6, `:core:inference` module

**Goal:** After deterministic skeleton, on-device LLM refines flow, variations, short rationales.

**Architecture:**

- Inference module: no Compose
- Port: `ProgramRefinementPort` — input skeleton + user context JSON → refined program
- Feature/onboarding or domain orchestrates: deterministic first, then refine

**Tests:**

- Fake refinement port in unit tests
- Contract test with recorded JSON fixtures (no live model in CI)

**Acceptance:**

- Generate works offline with model present
- Fallback: if model missing, ship deterministic program only (graceful degrade)

---

### OS-8 — Voice catalog customization

**Branch:** `feature/onboarding-voice-catalog`

**Depends on:** OS-4, OS-7 (inference module)

**Goal:** Optional voice step (or overlay on lift step): “I don’t have hack squat…” → tool calls update enabled lifts.

**Tools (examples):**

- `disable_lift`, `enable_lift`, `substitute_lift`, `add_custom_lift`

**UI:**

- Entry from lift step: “Customize with voice”
- Live session UI (Stratum components only)

**Acceptance:**

- Voice off path unchanged (OS-4 manual still works)
- Tool mutations persist to same availability store as OS-4

---

### OS-9 — Onboarding polish & resume

**Branch:** `feature/onboarding-polish`

**Depends on:** OS-6 minimum (OS-8 optional)

**Goal:** Production-ready edges.

- Resume incomplete onboarding from any step
- “Start over” clears progress (confirm dialog)
- Error states (generation failed, empty lift selection)
- Light + dark previews on all onboarding screens (UI_DESIGN)
- Accessibility: touch targets, semantics

**Acceptance:**

- `./gradlew testDebugUnitTest`, `detekt`, `:app:assembleDebug` green
- Manual QA script in PR description

---

## 6. Dependency graph

```text
OS-1 foundation
  └── OS-2 profile & schedule
        ├── OS-3 specialization
        └── OS-4 lift catalog
              └── OS-5 review
                    └── OS-6 deterministic generate
                          ├── OS-7 LLM refine
                          └── OS-8 voice (parallel after OS-4 + inference)
OS-9 polish (after OS-6)
```

**Recommended implementation order:**

1. OS-1 → OS-2 → OS-4 → OS-3 → OS-5 → OS-6 → OS-9  
2. OS-7 when inference stack ready  
3. OS-8 after OS-7 or in parallel if voice uses separate model path  

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

Next vertical slice after OS-6/9:

- **`:feature:workout`** — active session logging against generated program
- **`:feature:program`** — view/edit program, regenerate
- **`:feature:settings`** — theme family, units

---

## 9. LiftLab porting map

| LiftLab | Stratum onboarding |
|---------|-------------------|
| `Lift` + bitmasks | `Lift` + contributions + boolean + tiers |
| `ProgramGenerationRequest` | Profile + schedule + specialization + enabled lifts |
| `LiftLabDatabaseWorker` seed | OS-4 asset seed + Room |
| No onboarding | OS-1 – OS-9 |
| `ProgressionScheme` | Derived from `allowWeightChangeBetweenSets` default |

---

## 10. Open questions (resolve during OS-4 / OS-6)

- [ ] Canonical muscle enum naming (`VolumeMuscle` vs reuse LiftLab names)
- [ ] v1 generation: normalized program tables vs single JSON blob (prefer normalized for workout logging next)
- [ ] Home destination after onboarding: program overview vs “start first workout” CTA
- [ ] Units default: lb vs kg from locale vs explicit onboarding ask

---

*Document version: 2026-06-23 — aligns with Lifting Companion vision doc and Stratum architecture contracts.*

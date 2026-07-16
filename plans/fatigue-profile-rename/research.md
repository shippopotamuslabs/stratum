# Research: fatigue-profile-rename

Objective codebase map only. No design, structure, or implementation recommendations.

---

## Research Questions

Derived from ticket categories of unknown (naming/rest, persistence, seed catalog,
filters/roles, compound semantics, create-lift surfaces, logged snapshots, tests).

### A. Domain model — kinematic / lift classification

1. Where is `KinematicProfile` defined, and what are its values and documented purpose?
2. How is the profile stored on the domain `Lift` model (property name, default)?
3. What pure-domain inference exists for kinematic when catalog data is missing?
4. How does kinematic map to recovery / stress classification (`LiftStressClass`)?
5. Where else does generation context carry kinematic (e.g. `LiftContext`, wire codecs)?

### B. Default rest

6. How is default inter-set rest resolved for a lift today (constants, override path)?
7. What is `Lift.isCompound`, and does rest consult it?
8. Which call sites assign rest from resolved defaults vs fixed program defaults?
9. Do seed lifts ship with `defaultRestSeconds` overrides?

### C. Room schema and migrations

10. What is the current Room database version and migration policy in code?
11. Which tables/columns store kinematic profile strings?
12. How are enum values converted for Room (encode/decode, unknown-name behavior)?
13. Are schema JSON exports present, and what do contract tests assert about versions?
14. Is `fallbackToDestructiveMigration` configured?

### D. Seed catalog

15. Where is the seed lift catalog, and how is `kinematicProfile` authored?
16. What is the distribution of kinematic values in the seed catalog?
17. When does seeding run (first open vs always upsert), and does upgrade re-seed?

### E. LiftCategory filters and “Isolation” user language

18. What is `LiftCategory`, its variants, and display names?
19. How is category derived (`classifyLiftCategory` / `isCompoundForCategory`)?
20. Which UI surfaces expose category filters/labels?

### F. SlotRole and universe goldens

21. What is `SlotRole`, and where is `Isolation` used in slot templates?
22. How do universe / library slot keys encode role?
23. Where are golden universe key sets defined, and which keys contain `|Isolation|`?
24. Do isolation slots admit non-isolation kinematic profiles via allow-lists?

### G. `isCompound` usages beyond rest

25. Where is `Lift.isCompound` or kinematic-derived compound used for progression / UI?
26. Where is local `isCompound` computed from kinematic only (not `Lift.isCompound`)?
27. What does `schemeForTrainingAge(..., isCompound)` do?

### H. Custom / create-lift UI

28. How does create-lift and lift-info present and edit kinematic profile?
29. What defaults apply on create (kinematic, rest draft)?
30. When is kinematic re-inferred from patterns/name?

### I. Logged sets and remote sync

31. Do logged-set snapshots store kinematic profile?
32. Do cloud sync codecs encode/decode kinematic on lifts and logged sets?
33. What happens if a remote value does not match current enum names?

### J. Tests and commands

34. Which unit tests directly cover rest, kinematic inference, category, goldens, converters?
35. What Gradle commands run the relevant modules’ tests and static analysis?
36. What test frameworks are used in domain vs data modules?

---

## Findings

### A. Domain model — kinematic / lift classification

#### F1. `KinematicProfile` enum

- **File:** `core/domain/src/main/kotlin/com/stratum/domain/lift/KinematicProfile.kt`
- **Values:** `AxialCompound`, `NonAxialCompound`, `Isolation` (`KinematicProfile.kt:17–21`).
- **Documented purpose:** “loading category” for recovery classification; maps to
  `LiftStressClass`: Axial → HeavyAxial, NonAxial → Compound/UpperCompound path,
  Isolation → Isolation (`KinematicProfile.kt:3–15`). Described as a first-class catalog
  property set in `seed-lifts.json`, not a name/pattern heuristic
  (`KinematicProfile.kt:14–15`).

#### F2. Domain `Lift` property

- **File:** `core/domain/src/main/kotlin/com/stratum/domain/lift/Lift.kt`
- **Property:** `kinematicProfile: KinematicProfile = KinematicProfile.Isolation`
  (`Lift.kt:54`).
- **Also:** optional `defaultRestSeconds: Int? = null` (`Lift.kt:56`).

#### F3. Inference for custom / import lifts

- **File:** `core/domain/src/main/kotlin/com/stratum/domain/lift/InferKinematicProfile.kt`
- **Functions:** `inferKinematicProfile(patterns, liftName?)` and
  `inferKinematicProfile(pattern, liftName?)` (`InferKinematicProfile.kt:10–29`).
- **Rules (verified):** empty patterns → Isolation; isolation pattern set → Isolation;
  squat/hinge + free-weight name heuristics → AxialCompound; else NonAxialCompound
  (`InferKinematicProfile.kt:14–29`, `31–73`).
- **Callers include:** create-lift draft (`CreateLiftViewModel.kt`), import stub guesser
  (`ImportStubLiftGuesser.kt` via grep of main sources).

#### F4. Stress classification mapping

- **File:** `core/domain/src/main/kotlin/com/stratum/domain/program/generation/prescription/LiftStressClassification.kt`
- `kinematicProfileToStressClass`: AxialCompound → HeavyAxial, NonAxialCompound →
  UpperCompound, Isolation → Isolation (`LiftStressClassification.kt:56–61`).
- `classifyLiftStressClass(LiftContext)` uses that mapping only
  (`LiftStressClassification.kt:52–53`).
- Separate overload still classifies from `RpRepProfile` + pattern + name when a slot
  template is present (`LiftStressClassification.kt:25–46`).

#### F5. Generation context and wire

- `LiftContext.kinematicProfile` defaults to `KinematicProfile.Isolation`
  (`ProgramGenerationContext.kt:41–42`).
- Mapper copies `lift.kinematicProfile` into context
  (`ProgramGenerationContextMapper.kt:105` — path from grep).
- Synthesis wire encodes `"kinematicProfile"` as enum `.name`
  (`ContextWireCodec.kt:189` — path from grep).
- `allowedKinematicProfiles` is serialized on synthesis slots as JSON arrays of enum names
  (`ResultWireCodec.kt` around lines 87–151 — path from grep).

---

### B. Default rest

#### F6. Rest resolution API

- **Constants:** `DEFAULT_COMPOUND_REST_SECONDS = 180`,
  `DEFAULT_ISOLATION_REST_SECONDS = 120` (`Lift.kt:78–82`).
- **Resolver:** `Lift.resolvedDefaultRestSeconds()` =
  `defaultRestSeconds ?: (if (isCompound) 180 else 120)` (`Lift.kt:89–91`).
- **Override use case:** `SetLiftDefaultRestUseCase` persists override; range 0–600 seconds;
  does not rewrite existing workout templates (`SetLiftDefaultRestUseCase.kt:9–36`).

#### F7. `Lift.isCompound` definition (rest driver)

- **True when** `movementPatterns.size > 1` **or** kinematic is AxialCompound **or**
  NonAxialCompound (`Lift.kt:71–75`).
- **Documented dual semantics:** rest/programming that must match logged rest use
  `isCompound`; Gym/swap “Lift Type” uses broader `isCompoundForCategory` (`Lift.kt:67–69`,
  `LiftCategory.kt:170–183`).

#### F8. Call sites that assign rest

| Path | Behavior | Citation |
|------|----------|----------|
| Add lift to workout | Catalog `resolvedDefaultRestSeconds()`, else `DEFAULT_LIFT_SET_REST_SECONDS` (120) | `AddWorkoutLiftUseCase.kt:22–36`, `Program.kt:13` |
| Live workout overlay add | Catalog `resolvedDefaultRestSeconds()` or overlay default | `LiveWorkoutOverlayOps.kt:55` |
| Program generation materialize | **Always** `DEFAULT_LIFT_SET_REST_SECONDS` (120) per set — not lift rest resolver | `ProgramDomainMapper.kt:48` |
| Program template import | Template set rest or 120 | `ResolveProgramTemplateLiftsUseCase.kt:128` |
| Lift swap | Changes `liftId` only; **does not** rewrite set `restSeconds` | `SwapWorkoutLiftUseCase.kt:31–34` |

**Verified implication:** generated programs store 120s on all template sets at materialize
time; lift-default rest affects **add/swap-into-session** paths that call the resolver,
not the CP-SAT materializer’s fixed 120.

#### F9. Seed rest overrides

- Seed catalog has **266** lifts; **none** include a non-null `defaultRestSeconds` field
  in `seed-lifts.json` (asset scan 2026-07-16). Rest for seeds is purely resolved at
  runtime from `isCompound` / kinematic.

---

### C. Room schema and migrations

#### F10. Database version and policy

- `@Database(..., version = 1, exportSchema = true)` on `StratumDatabase`
  (`StratumDatabase.kt:7–42`).
- Contracts / DI comments: pre-release keep version 1 with **no migrations**; identity-hash
  changes require uninstall (`DataModule.kt:134–136`, `DATA_LAYER_RULES.md` §7,
  `LiftEntity.kt:28` comment “v1 reinstall, no migration”).
- **No** `Migration` / `autoMigrations` classes found under production Kotlin sources
  (search of `*.kt` for Migration symbols only hits comments + `fallbackToDestructiveMigration`).

#### F11. Columns storing kinematic strings

| Table / entity | Column | Default in entity | Citation |
|----------------|--------|-------------------|----------|
| `lifts` / `LiftEntity` | `kinematicProfile` TEXT NOT NULL | `Isolation` | `LiftEntity.kt:58`, schema `1.json` lifts table |
| `logged_sets` / `LoggedSetEntity` | `kinematicProfile` TEXT NOT NULL | `NonAxialCompound` | `ImportEntities.kt:50–65`, schema `1.json` logged_sets |

Program template tables (`programs`, `workouts`, `workout_lifts`, `sets`) store per-set
`rest_seconds` but **not** kinematic (`ProgramEntities.kt` rest column; schema).

#### F12. EnumConverters

- `fromKinematicProfile` → `profile?.name`; `toKinematicProfile` →
  `runCatching { KinematicProfile.valueOf(it) }.getOrNull()` (`EnumConverters.kt:138–142`).
- Unknown/renamed strings decode to **null** without throw (`EnumConverters.kt:49–51`
  class KDoc; pattern matches other enums).
- **Legacy mapping exists only for** onboarding step `"Generate"` → `Review`
  (`EnumConverters.kt:90–94`); no legacy aliases for kinematic names today.

#### F13. Schema export and contract tests

- Export location: `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json`
  (`core/data/build.gradle.kts:46–48` KSP `room.schemaLocation`).
- `ProgramSchemaContractTest` asserts `1.json` exists and **`2.json` / `3.json` must not**
  (`ProgramSchemaContractTest.kt:54–58`). Comment: “v1 database (pre-release: no migration
  file; uninstall/reinstall to apply)” (`ProgramSchemaContractTest.kt:23–25`).

#### F14. Destructive fallback

- `Room.databaseBuilder(...).fallbackToDestructiveMigration(dropAllTables = true)`
  (`DataModule.kt:137–139`). Comment: applies if version is bumped **without** a
  `Migration` (`DataModule.kt:136–137`).

---

### D. Seed catalog

#### F15. Asset and seeder

- Asset: `core/data/src/main/assets/lifts/seed-lifts.json`.
- DTO field: `kinematicProfile: KinematicProfile?` (`LiftSeedDto.kt:57`).
- Seeder: missing profile → `KinematicProfile.Isolation` (`LiftCatalogSeeder.kt:88`).
- Upserts by stable id; never clears custom rows (`LiftCatalogSeeder.kt:28–29`, `56–61`).

#### F16. Distribution (asset scan)

| Value | Count |
|-------|------:|
| NonAxialCompound | 131 |
| Isolation | 125 |
| AxialCompound | 10 |
| **Total** | **266** |

#### F17. When seeding runs

- On DB open: if `liftDao.countSeedLifts() == 0`, run `LiftCatalogSeeder.seed`
  (`DataModule.kt:157–159` region).
- Seeder itself is idempotent upsert (`LiftCatalogSeeder.kt:45–47`), but the **gate**
  skips seeding when any seed lifts already exist — so normal app upgrades with a
  populated catalog **do not** re-run seeder solely due to process start.
- After user-owned data clear, seeder is invoked again (`UserOwnedDataClearer` injects
  `LiftCatalogSeeder` — path from grep).

---

### E. LiftCategory filters and “Isolation” user language

#### F18. Enum and display names

- **File:** `core/domain/src/main/kotlin/com/stratum/domain/lift/LiftCategory.kt`
- Variants: `CompoundUpper`, `CompoundLower`, `IsolationUpper`, `IsolationLower`, `Core`
  (`LiftCategory.kt:111–117`).
- `displayName`: “Compound Upper”, “Compound Lower”, “**Upper Isolation**”,
  “**Lower Isolation**”, “Core” (`LiftCategory.kt:120–128`).
- Category is **derived**, not a DB column (`LiftCategory.kt:108–109`,
  `CustomLiftDefinition.kt` KDoc).

#### F19. Classification rules

- Compound bit for category: `isCompoundForCategory()` =
  `isCompound || any pattern in MAJOR_COMPOUND_PATTERNS` (`LiftCategory.kt:181–183`).
- Core short-circuit for non-compound core pattern/muscle (`LiftCategory.kt:148–156`).
- Else region × compound → five buckets (`LiftCategory.kt:158–164`).

#### F20. UI surfaces

- Gym filter dropdown options use `LiftCategory.*.displayName`
  (`app/.../GymViewModel.kt:309–316`).
- Lift info shows `classifyLiftCategory().displayName` as category label
  (`LiftInformationViewModel.kt:497`).
- Lift swap filters hold `selectedCategory: LiftCategory?`
  (`LiftSwapFilters.kt:15`); sheet state built from domain classification (feature/program
  + core/ui lift-swap components).

---

### F. SlotRole and universe goldens

#### F21. `SlotRole`

- **File:** `SessionTypeCatalog.kt`
- Values: `Anchor`, `SecondaryCompound`, `Isolation`, `Optional`
  (`SessionTypeCatalog.kt:12–17`).
- Many templates use `SlotRole.Isolation` (quad-iso, ham-iso, arms, lat-raise, etc.)
  (`SessionTypeCatalog.kt:81–122` and further lists).
- Spec-focused promotion can rewrite Optional → Isolation
  (`SpecFocusedSessionSlots.kt:30–31`).
- Solver treats `SlotRole.Isolation` in role priority / caps
  (`ConstructiveSynthesisSeed.kt:408`, `ProgramSynthesisModel.kt` role checks).

#### F22. Slot key format

- `librarySlotKey` / universe id:
  `pattern|muscle|role|touch|variant` joined by `|`
  (`SlotLibrary.kt:79–86`, `SlotUniverseBuilder.kt:119`, golden comment
  `SlotUniverseGoldenTest.kt:77`).
- Role segment is `slot.role.name` (e.g. `Isolation`).

#### F23. Golden keys containing Isolation

- **File:** `core/domain/src/test/kotlin/.../SlotUniverseGoldenTest.kt`
- Maintenance keys include e.g.
  `ElbowExtension|Tricep|Isolation|false|Any`,
  `KneeExtension|Quad|Isolation|false|Any`, etc. (`SlotUniverseGoldenTest.kt:144–161`).
- Arms / calf / glute scenarios add more `|Isolation|` keys
  (`SlotUniverseGoldenTest.kt:163–184`).
- Test compares `buildSlotUniverse(...).map { it.id }.toSet()` to golden sets
  (`SlotUniverseGoldenTest.kt:31–40`).

#### F24. Allowed kinematic profiles on isolation slots

- `quad-iso` / `ham-iso`: `allowedKinematicProfiles = setOf(Isolation, NonAxialCompound)`
  (`SessionTypeCatalog.kt:81–84`).
- Selector ranks true Isolation above NonAxial when allow-list present
  (`LiftSlotSelector.kt:64–78`).
- Property also on `WorkoutSlotTemplate` (`SessionTypeCatalog.kt:44–50`) and synthesis slots.

#### F25. Library group names

- `LOWER_ISOLATIONS`, `UPPER_ISOLATIONS` filter by `SlotRole.Isolation` (and Optional /
  muscle names) (`SlotLibrary.kt:39–72`). Names are Kotlin identifiers, not user-facing
  display strings.

---

### G. `isCompound` usages beyond rest

#### F26. `Lift.isCompound` consumers (non-rest)

| Area | Usage | Citation |
|------|-------|----------|
| Edit workout UI | Linear/Double toggle only if `lift.isCompound` | `EditWorkoutScreen.kt:612`, `EditWorkoutViewModel.kt:519` |
| Edit / create program state | Catalog map `lift.id → isCompound` into UI state | `EditWorkoutViewModel.kt:136`, `CreateProgramViewModel.kt:115`, mappers |
| Category | `isCompoundForCategory` includes `isCompound` | `LiftCategory.kt:181–183` |

#### F27. Local kinematic-only compound (not full `Lift.isCompound`)

These treat Axial **or** NonAxial as compound **without** multi-pattern check:

- `WorkoutAssembler` progression scheme (`WorkoutAssembler.kt:165–168`).
- `LiftSlotSelector` anchor bonuses (`LiftSlotSelector.kt:45–47`).
- `LiftCatalogQuality.isHighSystemicFatigueCompound` (`LiftCatalogQuality.kt:100–102`).

Other sites use **pattern count only** (`movementPatterns.size > 1`) for priority
(`LiftSwapPass.kt:48`, parts of `LiftSlotSelector` / tuning tests).

#### F28. Linear progression gate

- `schemeForTrainingAge(trainingAge, lastTrained, isCompound)`:
  if `!isCompound` → always `Double`; else Linear for Beginner or >3 months layoff
  (`ProgramRules.kt:30–40`).
- Tests: `Slice4LinearProgressionTest` mutation defenses on `isCompound`
  (`Slice4LinearProgressionTest.kt:210–263` region).
- Ticket notes proper eligibility is increment/load % — **not implemented** as such in
  this function today (fact: gate is boolean `isCompound` only).

---

### H. Custom / create-lift UI

#### F29. Presentation

- Labels: Axial → `"Compound -- Spine Loaded"`, NonAxial →
  `"Compound -- No Spine Loading"`, Isolation → `"Isolation"`
  (`LiftDetailsPresentation.kt:22–27`).
- String resource field title: `lift_info_kinematic` = `"Kinematic profile"`
  (`core/ui/.../strings.xml:208`).
- Options built from `KinematicProfile.entries` (`LiftDetailsPresentation.kt:69–76`).
- Editor field enum `LiftDetailEditField.Kinematic` (`LiftDetailsEditor.kt:57`).

#### F30. Defaults on create

- Draft kinematic default: `KinematicProfile.Isolation` (`CreateLiftViewModel.kt:60`).
- Rest draft default: `DEFAULT_ISOLATION_REST_SECONDS` (120)
  (`CreateLiftViewModel.kt:72`).
- `CustomLiftDefinition.kinematicProfile` default Isolation
  (`CustomLiftDefinition.kt:32`).
- `AddCustomLiftUseCase` validates rest range if override set; does **not** re-infer
  kinematic (`AddCustomLiftUseCase.kt:16–35` region).

#### F31. Re-inference

- On name change when patterns non-empty: `inferKinematicProfile(patterns, name)`
  (`CreateLiftViewModel.kt:125–135`).
- On pattern commit: same inference (`CreateLiftViewModel.kt:234` region).
- Manual `onSelectKinematic(key)` sets enum by `.name` (`CreateLiftViewModel.kt:202–204`).
- Lift info edit: `onSelectKinematic` persists `kinematicProfile` on details
  (`LiftInformationViewModel.kt:381–383`).

---

### I. Logged sets and remote sync

#### F32. Domain snapshot

- `LoggedLiftContext.kinematicProfile` default `NonAxialCompound`
  (`LoggedLiftContext.kt:14`).
- Mapped to/from `LoggedSetEntity.kinematicProfile` via `TrainingLogMapper`
  (`TrainingLogMapper.kt:64`, `94`).

#### F33. Cloud sync codecs

- Lift encode: `"kinematic_profile" to e.kinematicProfile.name`
  (`SyncEntityEncoders.kt:80`).
- Logged set encode: same key (`SyncEntityEncoders.kt:169`).
- Lift decode: `enumOrNull(data["kinematic_profile"]) ?: Isolation`
  (`SyncEntityDecoders.kt:167`).
- Logged set decode: `?: NonAxialCompound` (`SyncEntityDecoders.kt:300`).
- `enumOrNull` uses `enumValueOf` — unknown names → null → defaults above
  (`SyncEntityDecoders.kt:332–335`).

#### F34. Programs / rest on cloud

- Lift sets sync `rest_seconds` as integers (`SyncEntityDecoders.kt:255`); independent of
  kinematic rename.

---

### J. Tests and commands

#### F35. Representative tests in scope

| Concern | Test location |
|---------|----------------|
| Rest resolver | `core/domain/.../lift/ResolvedDefaultRestSecondsTest.kt` |
| Add workout lift rest | `core/domain/.../program/AddWorkoutLiftUseCaseTest.kt` |
| Infer kinematic | `core/domain/.../lift/InferKinematicProfileTest.kt`, `.../importing/InferredCustomLiftMetadataTest.kt` |
| Lift category / display | `core/domain/.../lift/LiftCategoryTest.kt` (includes displayName assertions) |
| Stress map | `core/domain/.../prescription/LiftStressClassificationTest.kt` |
| Slot kinematic allow-list | `core/domain/.../selection/LiftSlotSelectorKinematicProfileTest.kt` |
| Universe goldens | `core/domain/.../universe/SlotUniverseGoldenTest.kt` |
| Linear / isCompound | `core/domain/.../workout/Slice4LinearProgressionTest.kt` |
| Enum converters | `core/data/.../db/EnumConvertersTest.kt` (legacy Generate; not kinematic-specific) |
| Schema v1 only | `core/data/.../db/ProgramSchemaContractTest.kt` |
| Room lift rest/kinematic | `core/data/.../repository/RoomLiftRepositoryTest.kt` |
| Create lift VM | `app/.../liftconfig/CreateLiftViewModelTest.kt` |
| Edit scheme toggle | `feature/program/.../EditWorkoutViewModelSchemeToggleTest.kt` |

#### F36. Gradle commands (from `AGENTS.md` §5/§9 and module layout)

- Domain unit tests: `./gradlew :core:domain:test`
- Domain solver tests: `./gradlew :core:domain-solver-tests:test` (module present;
  solver uses `SlotRole`)
- Data unit tests (Robolectric/JUnit5): `./gradlew :core:data:testDebugUnitTest`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Feature program/workout: `./gradlew :feature:program:testDebugUnitTest`,
  `:feature:workout:testDebugUnitTest`
- Static analysis: `./gradlew detekt` (`maxIssues: 0`)
- Room/KSP: `./gradlew :app:kspDebugKotlin` / schema export via KSP on `:core:data`

#### F37. Test frameworks

- Domain: JUnit 5 + Turbine + fakes from `:core:testing` (`core/domain/build.gradle.kts`,
  `AGENTS.md` §4.4).
- Data: JUnit 5 + Robolectric (`RobolectricExtension` on schema tests;
  `apter.robolectric.junit5` plugin in `core/data/build.gradle.kts`).

---

### Cross-cutting inventory notes (verified, not exhaustive)

#### F38. Constants naming tied to compound/isolation rest

- `DEFAULT_COMPOUND_REST_SECONDS` / `DEFAULT_ISOLATION_REST_SECONDS` in `Lift.kt`.
- Program-level `DEFAULT_LIFT_SET_REST_SECONDS = 120` in `Program.kt:13` (generation default).

#### F39. Check-in ranking

- `CheckInRanking.isolationRank`: Isolation → 0, null → 1, else → 2
  (`CheckInRanking.kt:29–35`).

#### F40. Dashboard

- Falls back to `KinematicProfile.Isolation` when lift missing
  (`LoadDashboardUseCase.kt:212` — path from earlier grep).

#### F41. Dual compound semantics summary (as implemented)

| API | Multi-pattern | Axial/NonAxial kinematic | MAJOR_COMPOUND_PATTERNS |
|-----|:-------------:|:------------------------:|:----------------------:|
| `Lift.isCompound` | yes | yes | no |
| `isCompoundForCategory` | via `isCompound` | via `isCompound` | yes |
| Assembler / quality local flags | no | yes | no |

---

### Gaps / unknowns (cannot verify from repo alone)

1. **Live maintainer device row counts** for `lifts` / `logged_sets` kinematic value mix —
   not in repo; only seed asset distribution is known.
2. **Whether Firestore already holds production documents** with `kinematic_profile` enum
   names for this account — codecs exist; remote contents are external.
3. **No Room migration test harness** exists yet (no `MigrationTestHelper` usages found);
   migration testing pattern is therefore unknown for this project.
4. **Exact runtime behavior of Room** when a non-null entity field’s type converter returns
   null after enum rename is not exercised by a test in-repo (converters document null-on-unknown).

---

## Self-review

- All research questions Q1–Q36 have findings or gap notes.
- Findings are descriptive of current code; no implementation plan.
- No `TBD` / `TODO` placeholders in findings body.
- Test frameworks and Gradle commands recorded in §J.

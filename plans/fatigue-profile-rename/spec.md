# Spec: fatigue-profile-rename

**Ticket:** `fatigue-profile-rename`  
**Phase:** Plan (Synthitect)  
**Grounding:** `research.md`, `design.md` (approved 2026-07-16, wipe policy), `structure.md` (approved 2026-07-16), `ticket.md`  
**Workspace:** `/home/sean/Code/Stratum`  
**Companion:** `test_spec.md`

Implementation specification only — no runnable code. Signatures, type shapes, file maps,
ordered tasks, and scenario-ID links.

---

## Feature summary

Reframe lift loading/recovery cost as **Fatigue profile** (`High` / `Medium` / `Low`),
simplify default rest to **High → 180s, Medium+Low → 120s**, cascade-rename domain/data/seed/
wire off `KinematicProfile`, materialize generated program sets with per-lift resolved rest,
hard-rename Isolation → **Accessory** for `SlotRole` + `LiftCategory` + universe goldens, and
present Fatigue profile labels with inline help on create/edit.

**Wipe policy (locked):** Room stays at version **1** with rewritten `1.json`; no
`Migration(1, 2)`, no dual-read converters, no `kinematic_profile` remote dual-key. Maintainer
wipes device app data + Firestore before install.

**Interim Linear (locked D6):** `Lift.isCompound` remains multi-pattern **or** High **or**
Medium for Linear toggle / `schemeForTrainingAge(..., isCompound)`. Full eligibility by
`increment / workingWeight` is **follow-on only** (documented below; not an implementation
slice).

---

## Binding decisions

| ID | Decision |
|----|----------|
| D1 | `KinematicProfile` → `FatigueProfile` (`High`/`Medium`/`Low`); property `fatigueProfile` |
| D2 | Rest: `defaultRestSeconds ?: (High → 180 else 120)`; never `isCompound` |
| D3 | Materialize uses per-lift `resolvedDefaultRestSeconds()` when catalog lookup available |
| D4 | Room v1 rewrite `1.json`; no Migration; wipe + reinstall |
| D5 | Wire key `fatigue_profile` only; values High/Medium/Low only |
| D6 | `isCompound = multi-pattern \|\| High \|\| Medium` (behavior-preserving interim Linear gate) |
| D7 | `SlotRole.Accessory`, `LiftCategory.Accessory*`, goldens `|Accessory|`, no Isolation alias |
| D8 | Infer: empty→Low; isolation patterns→Low; free-weight axial squat/hinge→High; else non-empty→Medium |
| D9 | No MigrationTestHelper; no legacy alias matrix |
| D10 | Seed asset bijective rewrite; seeder missing→Low |
| D11 | Minimal `isCompound` adapters only; rest leaves the flag |
| OQ1 | Wipe remote; no dual-read |
| OQ2 | Logged-set / converter unknown fallback → Medium; lift unknown/missing → Low |
| OQ4 | Inline supporting text under Fatigue profile; no new tooltip chrome |

### Value map (compile-time only)

| Legacy | New |
|--------|-----|
| `AxialCompound` | `High` |
| `NonAxialCompound` | `Medium` |
| `Isolation` (kinematic / category role sense) | `Low` / `Accessory` as applicable |

Stress class names **unchanged**: High→`HeavyAxial`, Medium→`UpperCompound`, Low→`LiftStressClass.Isolation`.

### Seed distribution (bijective after rewrite)

| New value | Count |
|-----------|------:|
| High | 10 |
| Medium | 131 |
| Low | 125 |
| **Total** | **266** |

---

## Vertical slice table

| # | Goal | Verifiable artifact | Upstream |
|---|------|---------------------|----------|
| 1 | Domain `FatigueProfile` + rest resolver + interim `isCompound` + infer/stress renames | `:core:domain:test` (rest, infer, stress, isCompound, slot allow-list property) | — |
| 2 | Data/seed/schema/wire cascade; Room v1 rewrite; full project compile | `:core:data:testDebugUnitTest`, `:app:assembleDebug`, no `KinematicProfile` symbols | 1 |
| 3 | Materialize generated programs with per-lift resolved rest | Domain materialize rest matrix + `GenerateProgramUseCase` catalog pass-through | 2 |
| 4 | Accessory hard rename + goldens + allow-lists | Golden universe keys, category display, solver role priority | 2 |
| 5 | UI labels, inline help, create/edit presentation | App VM tests, strings, detekt | 2 (4 for Accessory chips) |

**Dependency chain:** `1 → 2 → 3`; `2 → 4 → 5`; UI fatigue labels may start after 2; Accessory chip copy requires 4.

**Serial order:** 1 → 2 → 3 → 4 → 5.

**Explicit non-slice:** Linear `%` eligibility follow-on note only (see § Follow-on).

---

## File structure

Paths verified against `/home/sean/Code/Stratum` on 2026-07-16.

### Create

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/lift/FatigueProfile.kt` | Create | Enum `High` / `Medium` / `Low` + KDoc mapping to stress classes |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/InferFatigueProfile.kt` | Create | Pure `inferFatigueProfile` rules (D8); replaces `InferKinematicProfile.kt` |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/InferFatigueProfileTest.kt` | Create | Rename/replace of `InferKinematicProfileTest.kt` |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/selection/LiftSlotSelectorFatigueProfileTest.kt` | Create | Rename/replace of `LiftSlotSelectorKinematicProfileTest.kt` |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/schema/ProgramDomainMapperRestTest.kt` | Create | Materialize rest matrix (Slice 3) |

### Delete (after replace)

| Path | Action | Notes |
|------|--------|-------|
| `core/domain/src/main/kotlin/com/stratum/domain/lift/KinematicProfile.kt` | Delete | Superseded by `FatigueProfile.kt` |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/InferKinematicProfile.kt` | Delete | Superseded by `InferFatigueProfile.kt` |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/InferKinematicProfileTest.kt` | Delete | After rename to `InferFatigueProfileTest.kt` |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/selection/LiftSlotSelectorKinematicProfileTest.kt` | Delete | After rename to fatigue-named test |

Prefer `git mv` / rename-in-place when practical to preserve history.

### Modify — `:core:domain` production

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/lift/Lift.kt` | Modify | `fatigueProfile` default Low; rest constants + resolver (D2); `isCompound` High\|Medium\|multi (D6) |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/LiftCategory.kt` | Modify | Slice 1: KDoc dual-semantics update; Slice 4: `AccessoryUpper`/`AccessoryLower` + display names |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/CustomLiftDefinition.kt` | Modify | `fatigueProfile` default Low |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/CustomLiftDetails.kt` | Modify | `fatigueProfile` field |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/LiftSimilarity.kt` | Modify | Compare `fatigueProfile` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/Program.kt` | Modify | Keep `DEFAULT_LIFT_SET_REST_SECONDS = 120`; KDoc if isolation wording |
| `core/domain/src/main/kotlin/com/stratum/domain/program/AddWorkoutLiftUseCase.kt` | Modify | Continues to call `resolvedDefaultRestSeconds()` (behavior via Lift change) |
| `core/domain/src/main/kotlin/com/stratum/domain/program/GenerateProgramUseCase.kt` | Modify | Slice 3: pass catalog rest lookup into `toDomainProgram` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/schema/ProgramDomainMapper.kt` | Modify | Slice 3: `restSecondsForLift` parameter |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/context/ProgramGenerationContext.kt` | Modify | `LiftContext.fatigueProfile` default Low |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/context/ProgramGenerationContextMapper.kt` | Modify | Map `lift.fatigueProfile` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/slots/SessionTypeCatalog.kt` | Modify | Slice 1: `allowedFatigueProfiles`; Slice 4: `SlotRole.Accessory` + Low\|Medium allow-lists |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/slots/SlotLibrary.kt` | Modify | Slice 4: `LOWER_ACCESSORIES` / `UPPER_ACCESSORIES`; role filters |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/slots/SlotUniverseBuilder.kt` | Modify | Keys via `role.name` (rename-driven) |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/slots/SpecFocusedSessionSlots.kt` | Modify | Slice 4: Optional → Accessory promotion |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/selection/LiftSlotSelector.kt` | Modify | High\|Medium compound flags; Low-first ranking on allow-list; Accessory role branch |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/selection/LiftCatalogQuality.kt` | Modify | High systemic fatigue from High\|Medium |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/build/WorkoutAssembler.kt` | Modify | Local High\|Medium compound for scheme |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/prescription/LiftStressClassification.kt` | Modify | `fatigueProfileToStressClass` |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/synthesis/SynthesisMaterializer.kt` | Modify | `allowedFatigueProfiles` property pass-through |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/synthesis/wire/ContextWireCodec.kt` | Modify | JSON key `fatigueProfile` / enum names High\|Medium\|Low |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/synthesis/wire/ResultWireCodec.kt` | Modify | `allowedFatigueProfiles` encode/decode |
| `core/domain/src/main/kotlin/com/stratum/domain/program/generation/reference/ProgramRules.kt` | Modify | KDoc only if needed; still `isCompound: Boolean` |
| `core/domain/src/main/kotlin/com/stratum/domain/checkin/CheckInRanking.kt` | Modify | Rank Low first (`lowFatigueRank` or rename of `isolationRank`) |
| `core/domain/src/main/kotlin/com/stratum/domain/training/LoggedLiftContext.kt` | Modify | `fatigueProfile` default Medium (OQ2) |
| `core/domain/src/main/kotlin/com/stratum/domain/dashboard/LoadDashboardUseCase.kt` | Modify | Missing-lift fallback → `FatigueProfile.Low` |
| Import / guesser paths referencing `inferKinematicProfile` (e.g. import stub guesser under domain import packages) | Modify | Call `inferFatigueProfile` |

### Modify — `:core:domain` tests (representative; cascade all compile breaks)

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain/src/test/kotlin/com/stratum/domain/lift/ResolvedDefaultRestSecondsTest.kt` | Modify | High 180 / Medium+Low 120 / override; multi-pattern Low still 120 |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/LiftCategoryTest.kt` | Modify | Fatigue enum fixtures; Slice 4 Accessory display + variants |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/AddCustomLiftUseCaseTest.kt` | Modify | `fatigueProfile` field |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/UpdateCustomLiftDetailsUseCaseTest.kt` | Modify | `fatigueProfile` field |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/LiftSimilarityTest.kt` | Modify | Fatigue values |
| `core/domain/src/test/kotlin/com/stratum/domain/lift/RankSwapCandidatesUseCaseTest.kt` | Modify | Fatigue values |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/prescription/LiftStressClassificationTest.kt` | Modify | `fatigueProfileToStressClass` mapping |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/prescription/RepRirPlannerTest.kt` | Modify | Fixture fatigue profiles |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/build/WorkoutAssemblerTest.kt` | Modify | Role Accessory; fatigue fixtures |
| `core/domain/src/test/kotlin/com/stratum/domain/program/generation/universe/SlotUniverseGoldenTest.kt` | Modify | Slice 4: all `\|Isolation\|` → `\|Accessory\|` |
| `core/domain/src/test/kotlin/com/stratum/domain/workout/Slice4LinearProgressionTest.kt` | Modify | Keep `isCompound` boolean semantics via fatigue-backed gate |
| `core/domain/src/test/kotlin/com/stratum/domain/checkin/CheckInSwapSelectionTest.kt` | Modify | Fatigue fixtures |
| `core/domain/src/test/kotlin/com/stratum/domain/checkin/EvaluateCheckInUseCaseTest.kt` | Modify | Fatigue fixtures |
| `core/domain/src/test/kotlin/com/stratum/domain/response/fixtures/ResponseFixtures.kt` | Modify | Fatigue fixtures |
| Other domain tests that construct `Lift` / `LiftContext` with kinematic fields | Modify | Rename cascade to compile |

### Modify — `:core:domain-solver` + tests

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/domain-solver/src/main/kotlin/com/stratum/domain/solver/ConstructiveSynthesisSeed.kt` | Modify | `SlotRole.Accessory` priority |
| `core/domain-solver/src/main/kotlin/com/stratum/domain/solver/ProgramSynthesisModel.kt` | Modify | Role checks if Isolation referenced |
| `core/domain-solver-tests/src/test/kotlin/**` | Modify | Fixtures: fatigue values, Accessory roles; `toDomainProgram` default ok without rest map |

### Modify — `:core:data`

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/data/src/main/kotlin/com/stratum/core/data/db/LiftEntity.kt` | Modify | Column `fatigueProfile` TEXT NOT NULL default Low |
| `core/data/src/main/kotlin/com/stratum/core/data/db/ImportEntities.kt` | Modify | `LoggedSetEntity.fatigueProfile` default Medium |
| `core/data/src/main/kotlin/com/stratum/core/data/db/LiftDao.kt` | Modify | Update/query params `fatigueProfile` |
| `core/data/src/main/kotlin/com/stratum/core/data/db/EnumConverters.kt` | Modify | `fromFatigueProfile` / `toFatigueProfile` only; no legacy aliases |
| `core/data/src/main/kotlin/com/stratum/core/data/db/StratumDatabase.kt` | Modify | Version stays 1; entities already registered |
| `core/data/src/main/kotlin/com/stratum/core/data/seed/LiftSeedDto.kt` | Modify | `fatigueProfile: FatigueProfile?` |
| `core/data/src/main/kotlin/com/stratum/core/data/seed/LiftCatalogSeeder.kt` | Modify | Missing → Low; map field |
| `core/data/src/main/assets/lifts/seed-lifts.json` | Modify | Field `fatigueProfile` with High/Medium/Low for all 266 rows |
| `core/data/schemas/com.stratum.core.data.db.StratumDatabase/1.json` | Modify | Column rename rewrite (KSP export after entity change) |
| `core/data/src/main/kotlin/com/stratum/core/data/sync/codecs/SyncEntityEncoders.kt` | Modify | Key `fatigue_profile` |
| `core/data/src/main/kotlin/com/stratum/core/data/sync/codecs/SyncEntityDecoders.kt` | Modify | Key `fatigue_profile`; lift→Low; logged set→Medium |
| `core/data/src/main/kotlin/com/stratum/core/data/repository/RoomLiftRepository.kt` | Modify | Property rename map |
| Training log mappers under `core/data/.../mapper/` referencing kinematic | Modify | `fatigueProfile` |
| `app/src/main/java/com/stratum/di/DataModule.kt` | Modify | Comments only if migration/kinematic wording stale; no Migration registration |

### Modify — `:core:data` tests

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/data/src/test/kotlin/com/stratum/core/data/db/EnumConvertersTest.kt` | Modify | Fatigue converters new names only |
| `core/data/src/test/kotlin/com/stratum/core/data/db/ProgramSchemaContractTest.kt` | Modify | v1 only; column shape expectations for `fatigueProfile` if asserted |
| `core/data/src/test/kotlin/com/stratum/core/data/repository/RoomLiftRepositoryTest.kt` | Modify | Fatigue round-trip |
| `core/data/src/test/kotlin/com/stratum/core/data/db/WorkoutLoggingLoggedSetContractTest.kt` | Modify | Fatigue fixture Medium |
| `core/data/src/test/kotlin/com/stratum/core/data/repository/RoomEquipmentRepositoryBatchTest.kt` | Modify | Fatigue fixture |

### Modify — `:core:testing`

| Path | Action | Responsibility |
|------|--------|----------------|
| `core/testing/src/main/kotlin/com/stratum/core/testing/onboarding/FakeLiftRepository.kt` | Modify | `fatigueProfile` |
| `core/testing/src/main/kotlin/com/stratum/core/testing/program/ProgramGenerationScenarioFixtures.kt` | Modify | High/Medium/Low values |
| `core/testing/src/main/kotlin/com/stratum/core/testing/program/FakeProgramSynthesisPort.kt` | Modify | `allowedFatigueProfiles` |
| `core/testing/src/main/kotlin/com/stratum/core/testing/training/WorkoutLoggingFixtures.kt` | Modify | Fatigue fixtures |

### Modify — app / feature / UI

| Path | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/stratum/liftconfig/CreateLiftViewModel.kt` | Modify | Draft Low; rest tracks resolver; `onSelectFatigue`; `inferFatigueProfile` |
| `app/src/main/java/com/stratum/liftconfig/LiftInformationViewModel.kt` | Modify | Fatigue edit; rest display from resolver |
| `app/src/main/java/com/stratum/liftconfig/LiftDetailsPresentation.kt` | Modify | Labels High/Medium/Low; `fatigueOptions` |
| `app/src/main/java/com/stratum/liftconfig/LiftDetailsEditor.kt` | Modify | `LiftDetailEditField.Fatigue`; string resource; inline help |
| `app/src/main/java/com/stratum/liftconfig/CreateLiftScreen.kt` | Modify | Wire `onSelectFatigue` |
| `app/src/main/java/com/stratum/liftconfig/LiftInformationScreen.kt` | Modify | Wire `onSelectFatigue` |
| `app/src/main/java/com/stratum/GymViewModel.kt` | Modify | Accessory category filter options |
| `core/ui/src/main/res/values/strings.xml` | Modify | `lift_info_fatigue_profile`, `lift_info_fatigue_help`; remove/retire `lift_info_kinematic` |
| `feature/program/src/main/kotlin/com/stratum/feature/program/LiftSwapStateMapper.kt` | Modify | Accessory filter options |
| `feature/program/...` create/edit mappers using isCompound maps | Modify | Compile via domain rename |
| `feature/workout/src/main/kotlin/com/stratum/feature/workout/LiveWorkoutOverlayOps.kt` | Modify | Continues `resolvedDefaultRestSeconds()` |
| `app/src/test/kotlin/com/stratum/liftconfig/CreateLiftViewModelTest.kt` | Modify | Fatigue selection + defaults |
| `feature/workout/src/test/.../WorkoutViewModelTest.kt` | Modify | Fatigue fixtures |
| `feature/program/src/test/...` as compile breaks require | Modify | Rename cascade |

### Explicitly not created

| Item | Why |
|------|-----|
| `Migration(1, 2)` / `2.json` | D4/D9 wipe; keep v1 rewrite |
| Legacy dual-read helpers / alias tables | OQ1 / D5 |
| Linear `%` eligibility module | Follow-on |
| New tooltip component | OQ4 inline only |
| Rename of `LiftStressClass.Isolation` | Out of scope (internal recovery math) |

---

## API contracts

Packages remain under `com.stratum.*`.

### Fatigue profile (domain)

```kotlin
// CREATE: core/domain/.../lift/FatigueProfile.kt
// DELETE: core/domain/.../lift/KinematicProfile.kt
package com.stratum.domain.lift

/**
 * Loading / recovery cost band for a lift (catalog-authored; not biomechanics wording).
 * Maps to LiftStressClass: High→HeavyAxial, Medium→UpperCompound, Low→Isolation
 * (stress class names unchanged).
 */
enum class FatigueProfile {
    High,    // was AxialCompound
    Medium,  // was NonAxialCompound
    Low,     // was Isolation
}
```

### Lift surface + rest

```kotlin
// core/domain/.../lift/Lift.kt
data class Lift(
    // ...unchanged fields...
    val fatigueProfile: FatigueProfile = FatigueProfile.Low,
    val defaultRestSeconds: Int? = null,
    // ...
) {
    /**
     * Interim Linear / programming gate (D6) — NOT used for rest.
     * multi-pattern || High || Medium
     */
    val isCompound: Boolean
        get() = movementPatterns.size > 1 ||
            fatigueProfile == FatigueProfile.High ||
            fatigueProfile == FatigueProfile.Medium
}

const val DEFAULT_HIGH_FATIGUE_REST_SECONDS: Int = 180
const val DEFAULT_STANDARD_REST_SECONDS: Int = 120
// Program.kt keeps DEFAULT_LIFT_SET_REST_SECONDS = 120 as unknown-lift fallback

fun Lift.resolvedDefaultRestSeconds(): Int =
    defaultRestSeconds
        ?: if (fatigueProfile == FatigueProfile.High) {
            DEFAULT_HIGH_FATIGUE_REST_SECONDS
        } else {
            DEFAULT_STANDARD_REST_SECONDS
        }
```

Removed symbols: `DEFAULT_COMPOUND_REST_SECONDS`, `DEFAULT_ISOLATION_REST_SECONDS`,
`kinematicProfile`, `KinematicProfile`.

### Inference

```kotlin
// CREATE InferFatigueProfile.kt; DELETE InferKinematicProfile.kt
fun inferFatigueProfile(
    patterns: Set<MovementPattern>,
    liftName: String? = null,
): FatigueProfile

fun inferFatigueProfile(
    pattern: MovementPattern,
    liftName: String? = null,
): FatigueProfile
// Rules (D8):
//   empty patterns → Low
//   isolation pattern set → Low
//   free-weight axial squat/hinge name → High
//   else non-empty → Medium
```

### Stress mapping

```kotlin
// LiftStressClassification.kt
fun fatigueProfileToStressClass(profile: FatigueProfile): LiftStressClass
// High → HeavyAxial, Medium → UpperCompound, Low → LiftStressClass.Isolation
fun classifyLiftStressClass(lift: LiftContext): LiftStressClass
// uses lift.fatigueProfile via fatigueProfileToStressClass
```

Removed: `kinematicProfileToStressClass`.

### Generation context / slots

```kotlin
// LiftContext
val fatigueProfile: FatigueProfile = FatigueProfile.Low

// WorkoutSlotTemplate / synthesis slot types
val allowedFatigueProfiles: Set<FatigueProfile>? = null

// Slice 4:
enum class SlotRole {
    Anchor,
    SecondaryCompound,
    Accessory,   // was Isolation
    Optional,
}

// Accessory allow-lists (quad-iso / ham-iso templates):
allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)
// Ranking: prefer Low before Medium when allow-list present
```

### Materialize rest (Slice 3)

```kotlin
// ProgramDomainMapper.kt
fun NormalizedProgram.toDomainProgram(
    createdAt: Instant = Instant.now(),
    restSecondsForLift: (LiftId) -> Int? = { null },
): Program
// Per set:
//   restSeconds = restSecondsForLift(LiftId(lift.liftId))
//       ?: DEFAULT_LIFT_SET_REST_SECONDS

// GenerateProgramUseCase (has LiftRepository):
//   val liftsById = liftRepository.observeEnabledLifts().first().associateBy { it.id }
//   normalized.toDomainProgram(
//       restSecondsForLift = { id -> liftsById[id]?.resolvedDefaultRestSeconds() },
//   )
```

Callers without catalog keep default 120 behavior via null lookup return.

### Category (Slice 4)

```kotlin
enum class LiftCategory {
    CompoundUpper,
    CompoundLower,
    AccessoryUpper,  // was IsolationUpper
    AccessoryLower,  // was IsolationLower
    Core,
    ;

    val displayName: String
        get() = when (this) {
            CompoundUpper -> "Compound Upper"
            CompoundLower -> "Compound Lower"
            AccessoryUpper -> "Upper Accessory"
            AccessoryLower -> "Lower Accessory"
            Core -> "Core"
        }
}

// SlotLibrary:
// LOWER_ACCESSORIES, UPPER_ACCESSORIES  (was LOWER_ISOLATIONS / UPPER_ISOLATIONS)
// Universe key role segment: SlotRole.Accessory.name → "Accessory"
```

### Persistence / seed / sync (Slice 2)

```kotlin
// LiftEntity.fatigueProfile: FatigueProfile = FatigueProfile.Low
// LoggedSetEntity.fatigueProfile: FatigueProfile = FatigueProfile.Medium

// EnumConverters
fun fromFatigueProfile(profile: FatigueProfile?): String?  // profile?.name
fun toFatigueProfile(value: String?): FatigueProfile?
// runCatching { FatigueProfile.valueOf(it) }.getOrNull()
// NO AxialCompound / NonAxialCompound / Isolation aliases

// Sync encode: "fatigue_profile" to enum.name
// Sync decode lifts: enumOrNull(data["fatigue_profile"]) ?: Low
// Sync decode logged sets: enumOrNull(data["fatigue_profile"]) ?: Medium
// NO kinematic_profile key read

// LiftSeedDto.fatigueProfile: FatigueProfile? = null
// Seeder missing → FatigueProfile.Low
// seed-lifts.json field "fatigueProfile": "High"|"Medium"|"Low"

// Room @Database version remains 1; exportSchema true; rewrite 1.json
```

### UI presentation (Slice 5)

```kotlin
// LiftDetailsPresentation
fun FatigueProfile.toDetailLabel(): String  // "High" | "Medium" | "Low"
fun fatigueOptions(selected: FatigueProfile): List<LiftDetailOptionUi>

// LiftDetailEditField.Fatigue  (was Kinematic)

// strings.xml
// lift_info_fatigue_profile = "Fatigue profile"
// lift_info_fatigue_help =
//   "High: spine-loaded free-weight compounds (e.g. barbell squat, deadlift).
//    Medium: other multi-joint compounds.
//    Low: lighter accessories — not single-joint only."

// CreateLiftViewModel
// draft fatigue = Low; restDraft tracks resolvedDefaultRestSeconds unless override
// onSelectFatigue(key: String)
// re-infer via inferFatigueProfile on name/pattern change
```

### Explicitly unchanged

- `LiftStressClass` variant names (`HeavyAxial` / `UpperCompound` / `Isolation`).
- `DEFAULT_LIFT_SET_REST_SECONDS` meaning (unknown-lift fallback 120).
- `SetLiftDefaultRestUseCase` range 0–600 and “do not rewrite existing templates”.
- Full Linear `%` rule.
- No dual-read, no Room Migration class, no SQL value remap.
- `SwapWorkoutLiftUseCase` does not rewrite set `restSeconds` (unchanged).

---

## Data model / schema changes

| Surface | Change |
|---------|--------|
| Room version | Stays **1** |
| `lifts.fatigueProfile` | TEXT NOT NULL (was `kinematicProfile`) |
| `logged_sets.fatigueProfile` | TEXT NOT NULL (was `kinematicProfile`) |
| Exported schema | Rewrite `core/data/schemas/.../1.json` only |
| Forbidden | `2.json`, `3.json`, `Migration(1,2)` |
| Seed JSON | `fatigueProfile` field; bijective value rewrite |
| Firestore wire | `fatigue_profile` key only |
| Template `sets.rest_seconds` | No bulk rewrite; new materialize/add paths only |

---

## Ordered implementation tasks

### Slice 1 — Domain FatigueProfile + rest + interim isCompound

| Task ID | Work | Scenario IDs |
|---------|------|--------------|
| T1.1 | Create `FatigueProfile.kt` (High/Medium/Low + KDoc). Delete `KinematicProfile.kt` after domain cascade of this slice. | FPR-S1-010, FPR-S1-011, FPR-S1-012 |
| T1.2 | Update `Lift.kt`: property `fatigueProfile` default Low; rename rest constants; implement `resolvedDefaultRestSeconds` from fatigue only (D2); implement `isCompound` as multi-pattern \|\| High \|\| Medium (D6). Update KDoc so rest is explicitly **not** driven by `isCompound`. | FPR-S1-001, FPR-S1-002, FPR-S1-003, FPR-S1-004, FPR-S1-005, FPR-S1-006, FPR-S1-007, FPR-S1-008, FPR-S1-009, FPR-S1-M001 |
| T1.3 | Create `InferFatigueProfile.kt` with D8 rules; delete `InferKinematicProfile.kt`. Replace all domain callers of `inferKinematicProfile`. | FPR-S1-013, FPR-S1-014, FPR-S1-015, FPR-S1-016, FPR-S1-017 |
| T1.4 | Rename stress mapper to `fatigueProfileToStressClass`; update `LiftContext` / `classifyLiftStressClass` / generation context mapper to `fatigueProfile`. | FPR-S1-010, FPR-S1-011, FPR-S1-012 |
| T1.5 | Rename slot property `allowedKinematicProfiles` → `allowedFatigueProfiles` on templates/synthesis types; map allow-list values Isolation→Low, NonAxial→Medium (role still Isolation until Slice 4). Update selector High\|Medium local flags and Low-first ranking. | FPR-S1-018, FPR-S1-019 |
| T1.6 | Update domain wire codecs (`ContextWireCodec`, `ResultWireCodec`) to fatigue property keys and enum names. | FPR-S1-020 |
| T1.7 | Update `LoggedLiftContext.fatigueProfile` default Medium; `CheckInRanking` Low-first; `LiftCatalogQuality` / `WorkoutAssembler` High\|Medium; `CustomLiftDefinition` / `CustomLiftDetails` / `LiftSimilarity`. | FPR-S1-006, FPR-S1-007, FPR-S1-009 |
| T1.8 | Update domain unit tests for rest, infer, stress, isCompound, selection allow-list property. Checkpoint: `./gradlew :core:domain:test`. **Explicit:** `:core:data`, `:app`, features do not compile until Slice 2. | All FPR-S1-* |

### Slice 2 — Data / seed / schema / wire cascade (wipe v1)

| Task ID | Work | Scenario IDs |
|---------|------|--------------|
| T2.1 | Rename entity fields `LiftEntity.fatigueProfile` (default Low), `LoggedSetEntity.fatigueProfile` (default Medium); update `LiftDao` params. | FPR-S2-001, FPR-S2-002, FPR-S2-003 |
| T2.2 | Replace EnumConverters kinematic pair with `fromFatigueProfile` / `toFatigueProfile` only (no legacy aliases). | FPR-S2-001, FPR-S2-002, FPR-S2-003, FPR-S2-004, FPR-S2-M001 |
| T2.3 | Rewrite `seed-lifts.json` field+values bijectively (10 High / 131 Medium / 125 Low); update `LiftSeedDto` + seeder missing→Low. | FPR-S2-007, FPR-S2-008 |
| T2.4 | Update sync encoders/decoders to `fatigue_profile` only; lift unknown→Low; logged set unknown→Medium; do not read `kinematic_profile`. | FPR-S2-009, FPR-S2-010, FPR-S2-011, FPR-S2-012 |
| T2.5 | Cascade all remaining production + test references (`KinematicProfile`, `kinematicProfile`, `kinematic_profile`) across data, testing fakes, app, features, solver tests so the tree compiles. | FPR-S2-013 |
| T2.6 | KSP-export rewritten `1.json`; keep Room version 1; schema contract still forbids `2.json`/`3.json`; assert column name `fatigueProfile` in lifts/logged_sets create SQL when tests inspect schema. Update DataModule comments if stale. | FPR-S2-005, FPR-S2-006 |
| T2.7 | Checkpoint: `./gradlew :core:data:testDebugUnitTest`, `./gradlew :core:domain:test`, `./gradlew :app:assembleDebug`. Grep clean: no production `KinematicProfile` / `kinematicProfile` / `kinematic_profile`. | FPR-S2-013 |

### Slice 3 — Materialize rest from resolver

| Task ID | Work | Scenario IDs |
|---------|------|--------------|
| T3.1 | Extend `NormalizedProgram.toDomainProgram` with `restSecondsForLift: (LiftId) -> Int? = { null }`; per-set rest = lookup ?: `DEFAULT_LIFT_SET_REST_SECONDS` (120). | FPR-S3-001, FPR-S3-002, FPR-S3-003, FPR-S3-004, FPR-S3-005, FPR-S3-M001 |
| T3.2 | Wire `GenerateProgramUseCase` to load enabled lifts (already observes for known ids) into a map and pass `{ id -> liftsById[id]?.resolvedDefaultRestSeconds() }`. | FPR-S3-001, FPR-S3-006 |
| T3.3 | Add `ProgramDomainMapperRestTest` covering High/Medium/Low/missing/override matrix. Leave swap-lift rest rewrite unchanged. | FPR-S3-001 … FPR-S3-005, FPR-S3-M001 |
| T3.4 | Checkpoint: `./gradlew :core:domain:test`; `./gradlew :app:assembleDebug`. | FPR-S3-* |

### Slice 4 — Accessory hard rename + goldens

| Task ID | Work | Scenario IDs |
|---------|------|--------------|
| T4.1 | Rename `SlotRole.Isolation` → `Accessory` in `SessionTypeCatalog`, promotion (`SpecFocusedSessionSlots`), solver seed/model, all templates. | FPR-S4-001, FPR-S4-007 |
| T4.2 | Set accessory-slot allow-lists to Low+Medium; selector ranks Low before Medium; rename Isolation role branch to Accessory. | FPR-S4-005, FPR-S4-006 |
| T4.3 | Rename `LiftCategory.IsolationUpper/Lower` → `AccessoryUpper/Lower`; displayName “Upper Accessory” / “Lower Accessory”. | FPR-S4-003, FPR-S4-004 |
| T4.4 | Rename library groups `LOWER_ISOLATIONS`/`UPPER_ISOLATIONS` → `LOWER_ACCESSORIES`/`UPPER_ACCESSORIES`. | FPR-S4-001 |
| T4.5 | Update `SlotUniverseGoldenTest` (and any other golden keys) `\|Isolation\|` → `\|Accessory\|` with no dual-accept. | FPR-S4-002, FPR-S4-M001 |
| T4.6 | Update Gym/swap filter option lists to Accessory categories. | FPR-S4-003, FPR-S4-004, FPR-S5-006 |
| T4.7 | Checkpoint: `./gradlew :core:domain:test`, `./gradlew :core:domain-solver-tests:test`. Grep clean: no production `SlotRole.Isolation`, `IsolationUpper`, `IsolationLower`, `LOWER_ISOLATIONS`, `UPPER_ISOLATIONS`. | All FPR-S4-* |

### Slice 5 — UI labels, inline help, create/edit defaults

| Task ID | Work | Scenario IDs |
|---------|------|--------------|
| T5.1 | Strings: `lift_info_fatigue_profile` = `"Fatigue profile"`; `lift_info_fatigue_help` multi-line supporting text (OQ4). Remove use of `lift_info_kinematic`. | FPR-S5-002, FPR-S5-007 |
| T5.2 | Presentation: High/Medium/Low labels; `fatigueOptions`; field enum `Fatigue`; remove “Compound -- Spine Loaded” etc. | FPR-S5-001, FPR-S5-M001 |
| T5.3 | CreateLift / LiftInformation ViewModels: draft Low; rest tracks resolver; re-infer Medium for majors; High rest draft 180; `onSelectFatigue`. | FPR-S5-003, FPR-S5-004, FPR-S5-005 |
| T5.4 | Inline supporting text under fatigue field on create/edit Details (equipment-help style). Confirm Accessory chips after Slice 4. | FPR-S5-006, FPR-S5-007 |
| T5.5 | Update VM tests; run `./gradlew :app:testDebugUnitTest`, touched feature tests, `./gradlew detekt` (`maxIssues: 0`). | All FPR-S5-* |
| T5.6 | Maintainer smoke (manual, not automated): wipe app data + Firestore → reinstall → reseed → create major-pattern custom lift defaults Medium + 120 rest; High axial infer → 180; generate program High sets 180; filters show Accessory. | FPR-S5-SMOKE |

---

## Scenario coverage matrix

| Scenario family | Tasks |
|-----------------|-------|
| FPR-S1-* | T1.1–T1.8 |
| FPR-S2-* | T2.1–T2.7 |
| FPR-S3-* | T3.1–T3.4 |
| FPR-S4-* | T4.1–T4.7 |
| FPR-S5-* | T5.1–T5.6 |

Every behavior-changing task references at least one scenario ID above. Full Gherkin lives in
`test_spec.md`.

---

## Follow-on (not this ticket)

Documented for plan completeness; **do not implement** here:

1. **Linear eligibility by load ratio** — Replace D6 boolean gate with a rule approximating
   “Linear when `increment / workingWeight` is small enough.” Proper thresholds, unit policy,
   and UI copy are a separate ticket. Interim gate remains:
   `isCompound = multi-pattern || High || Medium` feeding `schemeForTrainingAge(..., isCompound)`.
2. Optional deeper collapse of local compound flags (assembler / quality / category).
3. `LiftStressClass` user-language cleanup if ever exposed.
4. Multi-version Room migrations / dual-read if a future release cannot wipe.
5. MigrationTestHelper harness for multi-version chains.

---

## Drift and escalation rules

| Condition | Response |
|-----------|----------|
| Need to introduce `Migration(1,2)`, dual-read converters, or `kinematic_profile` dual-key | **Stop.** Request explicit Contract Waiver / design re-approval; contradicts D4/D5/OQ1. |
| Proposal to keep rest on `isCompound` or Medium→180 | **Stop.** Contradicts D2; product locked High-only 180. |
| Proposal to alias Isolation≡Accessory in goldens/solver | **Stop.** Contradicts D7. |
| Proposal to implement full Linear `%` rule inside this ticket | Defer to follow-on; do not expand slices. |
| Proposal to rename `LiftStressClass.Isolation` | Out of scope; leave internal recovery labels. |
| Schema contract forbids `2.json` but implementer wants version 2 | Structure locked v1 rewrite; escalate before changing. |
| New Room columns for UI state (loading flags, etc.) | Schema lock (AGENTS.md §4.5); forbidden. |
| File path missing or renamed in repo vs this map | Prefer repo truth; update plan note in PR description; do not invent parallel types. |
| Detekt `maxIssues: 0` failure on Stratum UI import rules | Fix with `Stratum*` components / theme; no Material chrome bypass without waiver. |

---

## Checkpoints (runnable)

| After slice | Commands | Must pass |
|-------------|----------|-----------|
| 1 | `./gradlew :core:domain:test` | Rest High/else 120; override; infer D8; stress map; isCompound High\|Medium\|multi |
| 2 | `./gradlew :core:data:testDebugUnitTest`<br>`./gradlew :core:domain:test`<br>`./gradlew :app:assembleDebug` | Converters; schema v1 only; seed mapping; full compile; no Kinematic symbols |
| 3 | `./gradlew :core:domain:test`<br>`./gradlew :app:assembleDebug` | Materialize rest matrix; add-lift rest still green |
| 4 | `./gradlew :core:domain:test`<br>`./gradlew :core:domain-solver-tests:test` | Goldens Accessory; category display; solver roles |
| 5 | `./gradlew :app:testDebugUnitTest`<br>`./gradlew :feature:program:testDebugUnitTest` (if touched)<br>`./gradlew detekt` | VM labels/defaults; help resources; detekt 0 |

---

## Self-review (spec)

- Every approved design decision D1–D11 and OQ1–OQ4 appears in tasks or explicit out-of-scope.
- Slice order matches `structure.md` (1→2→3→4→5).
- Wipe policy: no Migration, no dual-read, v1 rewrite only.
- Linear `%` is plan-doc follow-on only.
- No placeholders (`TBD`, `TODO`, `similar to`, `as above`, `implement later`).
- File paths verified against the codebase on 2026-07-16.

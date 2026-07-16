# Structure: fatigue-profile-rename

**Ticket:** `fatigue-profile-rename` | **Phase:** Structure (Synthitect)  
**Grounding:** `research.md`, `design.md` (approved 2026-07-16, wipe policy)

Vertical-slice execution outline: signatures, types, phases, and checkpoints — not
implementation bodies. Honors locked decisions **D1–D11** and **OQ1–OQ4**. Does **not**
reopen rest tiers, wipe-vs-migrate, Accessory hard rename, or Linear % follow-on.

---

## Locked structure choices (from design)

| ID | Structure pick |
|----|----------------|
| D4 schema | **Keep Room version 1.** Rewrite entities + exported `1.json` column/field names to `fatigueProfile` / High\|Medium\|Low. No `Migration(1, 2)`, no dual-read converters, no value CASE remaps. Maintainer wipe + reinstall. |
| D5 remote | Wire key `fatigue_profile` only; values High/Medium/Low only. No `kinematic_profile` dual-key. |
| D9 tests | No MigrationTestHelper; no legacy alias matrix. Schema contract still asserts `1.json` exists and forbids `2.json`/`3.json` (column shapes updated inside `1.json`). |
| D2 rest | `High → 180`, else `120`; never `isCompound`. |
| D3 materialize | Per-lift `resolvedDefaultRestSeconds()` when catalog available; else `DEFAULT_LIFT_SET_REST_SECONDS` (120). |
| D6 Linear interim | `isCompound = multi-pattern \|\| High \|\| Medium` (behavior-preserving). % rule is plan-doc follow-on only. |
| D7 Accessory | Hard rename `SlotRole` + `LiftCategory` + goldens; no Isolation alias. |
| D8 defaults | Infer empty → Low; isolation patterns → Low; free-weight axial squat/hinge → High; other non-empty → **Medium**. |
| OQ2 | Logged-set / converter unknown fallback → **Medium**. Lift unknown/missing → **Low**. |
| OQ4 | Inline supporting text under Fatigue profile if it fits; no new tooltip chrome. |

---

## Slice index

| # | Goal (one line) |
|---|-----------------|
| 1 | Domain: `FatigueProfile` + rest resolver + interim `isCompound` + infer/stress renames |
| 2 | Data/seed/schema/wire + cascade rename so the full tree compiles on wipe-friendly v1 |
| 3 | Materialize generated programs with per-lift resolved rest |
| 4 | Accessory hard rename (`SlotRole`, `LiftCategory`, library, goldens, allow-lists) |
| 5 | UI labels, inline help, create/edit defaults presentation |

Non-code (plan phase only, not a build slice): document Linear `%` eligibility as follow-on;
interim gate remains D6.

---

## Dependencies between slices

```text
1 ──► 2 ──► 3
       │
       └──► 4 ──► 5
       │
       └──► 5   (UI may start after 2; 4 only required for Accessory chips/goldens surfaces)
```

- **Slice 1** has no upstream dependency. Leaves **domain** green; **data/app/feature do not compile** until Slice 2 (explicit).
- **Slice 2** requires Slice 1 public contracts; restores **full project compile**.
- **Slice 3** requires Slice 2 (catalog lookup at materialize call sites already on renamed types).
- **Slice 4** requires Slice 2 (slots already carry `allowedFatigueProfiles`); independent of Slice 3.
- **Slice 5** requires Slice 2 for compile; benefits from Slice 4 for filter chip copy (`Upper/Lower Accessory`). Can land presentation for fatigue labels in parallel with Slice 4 if Accessory filter strings stay temporary until 4 merges.

**Recommended serial order for a single implementer:** 1 → 2 → 3 → 4 → 5.

---

## Public contracts

Signatures and type shapes only. Packages stay under `com.stratum.*`.

### Fatigue profile (domain)

```kotlin
// REPLACE file: core/domain/.../lift/KinematicProfile.kt
// WITH:      core/domain/.../lift/FatigueProfile.kt
package com.stratum.domain.lift

/**
 * Loading / recovery cost band for a lift (catalog-authored; not biomechanics wording).
 * Maps to LiftStressClass: High→HeavyAxial, Medium→UpperCompound, Low→Isolation (stress names unchanged).
 */
enum class FatigueProfile {
    High,    // was AxialCompound
    Medium,  // was NonAxialCompound
    Low,     // was Isolation
}
```

**Value map (compile-time only — no runtime legacy decode):**

| Legacy enum | New |
|-------------|-----|
| `AxialCompound` | `High` |
| `NonAxialCompound` | `Medium` |
| `Isolation` | `Low` |

### Lift surface

```kotlin
// core/domain/.../lift/Lift.kt  (property + defaults + rest)
data class Lift(
    // ...unchanged fields...
    val fatigueProfile: FatigueProfile = FatigueProfile.Low,  // was kinematicProfile / Isolation
    val defaultRestSeconds: Int? = null,
    // ...
) {
    /**
     * Interim Linear / programming gate (D6) — NOT used for rest.
     * multi-pattern || High || Medium  (preserves pre-rename Axial|NonAxial compound set)
     */
    val isCompound: Boolean
        get() = movementPatterns.size > 1 ||
            fatigueProfile == FatigueProfile.High ||
            fatigueProfile == FatigueProfile.Medium
}

const val DEFAULT_HIGH_FATIGUE_REST_SECONDS: Int = 180   // was DEFAULT_COMPOUND_REST_SECONDS
const val DEFAULT_STANDARD_REST_SECONDS: Int = 120       // was DEFAULT_ISOLATION_REST_SECONDS
// Program.kt keeps DEFAULT_LIFT_SET_REST_SECONDS = 120 as "unknown lift" fallback

fun Lift.resolvedDefaultRestSeconds(): Int =
    defaultRestSeconds
        ?: if (fatigueProfile == FatigueProfile.High) {
            DEFAULT_HIGH_FATIGUE_REST_SECONDS
        } else {
            DEFAULT_STANDARD_REST_SECONDS
        }
```

### Inference

```kotlin
// RENAME InferKinematicProfile.kt → InferFatigueProfile.kt
fun inferFatigueProfile(
    patterns: Set<MovementPattern>,
    liftName: String? = null,
): FatigueProfile

fun inferFatigueProfile(
    pattern: MovementPattern,
    liftName: String? = null,
): FatigueProfile
// Rules (D8): empty → Low; isolation pattern set → Low;
// free-weight axial squat/hinge name → High; else non-empty → Medium
```

### Stress mapping (stress enum names unchanged)

```kotlin
// LiftStressClassification.kt
fun fatigueProfileToStressClass(profile: FatigueProfile): LiftStressClass
// High → HeavyAxial, Medium → UpperCompound, Low → Isolation
// LiftStressClass.Isolation remains internal recovery label
```

### Generation context / slots

```kotlin
// ProgramGenerationContext / LiftContext
data class LiftContext(
    // ...
    val fatigueProfile: FatigueProfile = FatigueProfile.Low,
    // ...
)

// SessionTypeCatalog.kt / WorkoutSlotTemplate
data class WorkoutSlotTemplate(
    // ...
    val allowedFatigueProfiles: Set<FatigueProfile>? = null,  // was allowedKinematicProfiles
)

// Synthesis slot types mirror the same property name
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
// Caller supplies { id -> catalog[id]?.resolvedDefaultRestSeconds() } when catalog known.

// Equivalent optional overload if call graph prefers Map:
// fun NormalizedProgram.toDomainProgram(
//     createdAt: Instant = Instant.now(),
//     liftsById: Map<LiftId, Lift> = emptyMap(),
// ): Program
```

Callers of `toDomainProgram` that already hold a lift catalog pass the lookup; callers without
catalog keep default 120 behavior.

### Accessory (Slice 4)

```kotlin
enum class SlotRole {
    Anchor,
    SecondaryCompound,
    Accessory,   // was Isolation
    Optional,
}

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

// SlotLibrary group identifiers
// LOWER_ACCESSORIES, UPPER_ACCESSORIES  (was LOWER_ISOLATIONS / UPPER_ISOLATIONS)

// Universe key role segment: SlotRole.Accessory.name → "Accessory"
// Goldens: all "|Isolation|" → "|Accessory|"  (no dual-accept)
```

Accessory-slot allow-lists (was Isolation + NonAxialCompound):

```kotlin
allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)
// Ranking: prefer Low before Medium when allow-list present (was Isolation before NonAxial)
```

### Persistence / seed / sync (Slice 2) — wipe-friendly only

```kotlin
// LiftEntity / LoggedSetEntity field
val fatigueProfile: FatigueProfile  // column name fatigueProfile TEXT NOT NULL
// LiftEntity default: Low
// LoggedSetEntity default: Medium  (OQ2)

// EnumConverters
fun fromFatigueProfile(profile: FatigueProfile?): String?
fun toFatigueProfile(value: String?): FatigueProfile?
// encode: profile?.name
// decode: runCatching { FatigueProfile.valueOf(it) }.getOrNull()
// NO legacy AxialCompound/NonAxialCompound/Isolation aliases

// Sync codecs
// encode key: "fatigue_profile" → enum.name
// decode: enumOrNull(data["fatigue_profile"]) ?: Low   // lifts
// decode: enumOrNull(data["fatigue_profile"]) ?: Medium // logged sets
// NO kinematic_profile key read

// LiftSeedDto
val fatigueProfile: FatigueProfile? = null
// Seeder missing → FatigueProfile.Low

// seed-lifts.json field "fatigueProfile" with "High"|"Medium"|"Low" (bijective from old asset)

// Room @Database version remains 1; exportSchema true; rewrite 1.json
// fallbackToDestructiveMigration remains safety net only
```

### UI presentation (Slice 5)

```kotlin
// LiftDetailsPresentation
fun FatigueProfile.toDetailLabel(): String  // "High" | "Medium" | "Low"
fun fatigueOptions(selected: FatigueProfile): List<LiftDetailOptionUi>

// LiftDetailEditField.Fatigue  (was Kinematic)

// strings.xml
// lift_info_fatigue_profile = "Fatigue profile"
// lift_info_fatigue_help = short multi-line supporting text (OQ4):
//   High = spine-loaded free-weight compounds (e.g. barbell squat/deadlift);
//   Medium = other multi-joint compounds;
//   Low = lighter accessories — not "single-joint only"

// CreateLiftViewModel draft: fatigue = Low; restDraft tracks resolver unless override
// onSelectFatigue(key: String); re-infer via inferFatigueProfile on name/pattern change
```

### Explicitly unchanged in this ticket

- `LiftStressClass` variant names (`HeavyAxial` / `UpperCompound` / `Isolation`).
- `DEFAULT_LIFT_SET_REST_SECONDS` meaning (unknown-lift fallback 120).
- `SetLiftDefaultRestUseCase` range and “do not rewrite existing templates” contract.
- Full Linear `%` rule (`increment / workingWeight`) — follow-on only.
- No dual-read, no Room Migration class, no SQL value remap.

---

## Per-slice checklist

### Slice 1 — Domain FatigueProfile + rest + interim isCompound

**Goal:** Domain API speaks fatigue; rest is High/else 120; Linear interim gate preserved.

| Area | Touch |
|------|--------|
| Types | Add `FatigueProfile`; delete `KinematicProfile` type |
| Property | `Lift.fatigueProfile`, `LiftContext.fatigueProfile`, `LoggedLiftContext.fatigueProfile`, custom-lift / details DTOs in domain |
| Rest | Rename rest constants; `resolvedDefaultRestSeconds` from fatigue only |
| isCompound | High\|Medium\|multi-pattern (D6); leave `isCompoundForCategory` structure, update KDoc |
| Infer | `inferFatigueProfile` (D8 Medium-for-majors) |
| Stress | `fatigueProfileToStressClass` mapping only |
| Slots (property name) | `allowedFatigueProfiles` on templates/synthesis types (values still Isolation→Low etc. via new enum) |
| Wire domain codecs | Synthesis context/result property JSON keys renamed to fatigue names (domain-side codecs) |
| Ranking/helpers | `CheckInRanking` Low-first rank; local High\|Medium compound flags in assembler/selector/quality |
| **Not in this slice** | Room entities, seed JSON asset rewrite, app string resources, SlotRole.Accessory, goldens role segment |

**New / changed signatures:** see Public contracts — Fatigue, Lift, infer, stress, slot property.

**Checkpoint**

- `./gradlew :core:domain:test` green for rest, infer, stress, isCompound, category (still Isolation* until Slice 4), selection tests updated to new enum.
- **Explicit:** `:core:data`, `:app`, features **do not compile** until Slice 2.

---

### Slice 2 — Data / seed / schema / wire cascade (wipe v1)

**Goal:** Persistence and all modules compile on 1:1 new names; wipe-friendly schema only.

| Area | Touch |
|------|--------|
| Entities | `LiftEntity.fatigueProfile`, `LoggedSetEntity.fatigueProfile` (+ defaults Low / Medium) |
| Converters | `fromFatigueProfile` / `toFatigueProfile` only |
| Schema export | Rewrite `.../StratumDatabase/1.json` column names; version stays **1** |
| Schema contract | Assert `1.json` present; still forbid `2.json`/`3.json`; column/identity expectations updated |
| Seeder + DTO + asset | `fatigueProfile` field/values; missing → Low; rewrite all 266 seed rows bijectively |
| Mappers / repos | Training log, lift mappers, Room repository tests |
| Sync | Encode/decode `fatigue_profile` only |
| Fakes / fixtures | `:core:testing` fakes, program generation fixtures, solver test helpers |
| Cascade | Every remaining production + test reference to `KinematicProfile` / `kinematicProfile` |
| DI comments | Update wipe/reinstall commentary if it still says “kinematic”; no Migration registration |

**New / changed signatures:** entity fields, converter pair, seed DTO, sync key.

**Checkpoint**

- `./gradlew :core:data:testDebugUnitTest` (converters, schema contract, room lift/logged-set tests).
- `./gradlew :app:kspDebugKotlin` / schema export produces updated `1.json`.
- `./gradlew :app:assembleDebug` (and domain/solver test modules) **compile green**.
- No production symbol `KinematicProfile` / `kinematicProfile` / `kinematic_profile` remains.

---

### Slice 3 — Materialize rest from resolver

**Goal:** Generated programs stamp High lifts at 180s when catalog is available.

| Area | Touch |
|------|--------|
| Mapper | `NormalizedProgram.toDomainProgram` gains lift→rest lookup (see Public contracts) |
| Call sites | Generation persist / onboarding generate / any path that materializes `NormalizedProgram` → domain `Program` and has catalog access |
| Unknown lift | Keep `DEFAULT_LIFT_SET_REST_SECONDS` (120) |
| Non-goals | Bulk rewrite of existing template `rest_seconds`; swap-lift rest rewrite (unchanged) |

**New / changed signatures:** `toDomainProgram(..., restSecondsForLift)` or map overload only.

**Checkpoint**

- Domain unit test: High catalog lift → set `restSeconds == 180`; Medium/Low → 120; missing id → 120; override on `Lift.defaultRestSeconds` respected when lookup uses `resolvedDefaultRestSeconds()`.
- `./gradlew :core:domain:test` (materialize / mapper tests).
- Call-site compile: `./gradlew :app:assembleDebug` or narrow module compile for touched features.

---

### Slice 4 — Accessory hard rename + goldens

**Goal:** Isolation language gone from roles, categories, universe keys, and goldens.

| Area | Touch |
|------|--------|
| `SlotRole` | `Isolation` → `Accessory` (templates, promotion Optional→Accessory, solver priority) |
| Allow-lists | Low + Medium; selector ranks Low first |
| `LiftCategory` | `AccessoryUpper` / `AccessoryLower` + display names |
| Library groups | `LOWER_ACCESSORIES` / `UPPER_ACCESSORIES` |
| Goldens | `SlotUniverseGoldenTest` (and any other `|Isolation|` keys) → `|Accessory|` |
| UI filters | Gym / swap filter options use new category enum + displayName |
| Solver tests | Role assertions updated |

**New / changed signatures:** `SlotRole`, `LiftCategory` variants, library identifiers.

**Checkpoint**

- `./gradlew :core:domain:test` — golden universe keys pass with Accessory only.
- `./gradlew :core:domain-solver-tests:test` if role priority / caps reference Accessory.
- Category display tests: “Upper Accessory” / “Lower Accessory”.
- Grep clean: no production `SlotRole.Isolation`, `IsolationUpper`, `IsolationLower`, `LOWER_ISOLATIONS`, `UPPER_ISOLATIONS` (except historical comments if any — prefer zero).

---

### Slice 5 — UI labels, inline help, create/edit defaults

**Goal:** User-facing Fatigue profile copy and create/edit UX match product language.

| Area | Touch |
|------|--------|
| Strings | Field title “Fatigue profile”; inline help string (OQ4) |
| Presentation | High / Medium / Low labels; remove “Compound -- Spine Loaded” etc. |
| Create lift | Draft Low; rest tracks resolver; re-infer Medium for majors; `onSelectFatigue` |
| Lift info | Edit field Fatigue; rest draft from resolver |
| Filters | Confirm Accessory chip labels after Slice 4 |
| ViewModel tests | CreateLift / LiftInformation updated names and defaults |

**New / changed signatures:** presentation helpers, string resources, VM event names (Fatigue not Kinematic).

**Checkpoint**

- `./gradlew :app:testDebugUnitTest` (create-lift / lift-info VM tests).
- `./gradlew :feature:program:testDebugUnitTest` if scheme-toggle / editor maps touch fatigue or compound labels.
- `./gradlew detekt` clean (`maxIssues: 0`).
- Maintainer smoke (manual): wipe app data + Firestore → reinstall → reseed → create custom major-pattern lift defaults Medium + 120 rest; High axial seed / infer → 180 rest; generate program High sets 180; filters show Accessory.

---

## File map preview

### Create

| Path | Responsibility |
|------|----------------|
| `core/domain/src/main/kotlin/com/stratum/domain/lift/FatigueProfile.kt` | Enum High/Medium/Low + KDoc (maps to stress classes) |
| `core/domain/src/main/kotlin/com/stratum/domain/lift/InferFatigueProfile.kt` | Pure infer rules (D8) — replaces InferKinematicProfile.kt |
| `core/domain/src/test/kotlin/.../lift/InferFatigueProfileTest.kt` | Rename/replace of InferKinematicProfileTest |
| `core/domain/src/test/kotlin/.../lift/ResolvedDefaultRestSecondsTest.kt` | Extend/replace cases for High 180 / Medium+Low 120 / override (file may already exist — modify if present) |
| `core/domain/src/test/kotlin/.../selection/LiftSlotSelectorFatigueProfileTest.kt` | Rename of kinematic allow-list tests |

### Delete (after replace)

| Path | Notes |
|------|-------|
| `.../lift/KinematicProfile.kt` | Superseded by FatigueProfile.kt |
| `.../lift/InferKinematicProfile.kt` | Superseded by InferFatigueProfile.kt |
| Matching obsolete test file names if not rename-in-place | Prefer git mv / rename-in-place to preserve history |

### Modify — domain (`:core:domain`)

| Path | Responsibility |
|------|----------------|
| `.../lift/Lift.kt` | Property, isCompound (D6), rest constants + resolver (D2) |
| `.../lift/LiftCategory.kt` | Accessory* variants + displayName; KDoc dual-semantics update |
| `.../lift/CustomLiftDefinition.kt` (or equivalent) | `fatigueProfile` default Low |
| `.../program/Program.kt` | Keep `DEFAULT_LIFT_SET_REST_SECONDS`; KDoc if it mentions isolation |
| `.../program/generation/schema/ProgramDomainMapper.kt` | Rest lookup parameter (Slice 3) |
| `.../program/generation/context/*` | LiftContext field rename |
| `.../program/generation/slots/SessionTypeCatalog.kt` | SlotRole.Accessory; allowedFatigueProfiles; allow-lists Low\|Medium |
| `.../program/generation/slots/SlotLibrary.kt` | Group renames; role filters |
| `.../program/generation/slots/SlotUniverseBuilder.kt` | Keys via role.name (no logic change beyond rename) |
| `.../program/generation/slots/SpecFocusedSessionSlots.kt` | Optional → Accessory promotion |
| `.../program/generation/selection/LiftSlotSelector.kt` | Fatigue allow-list + Low preference |
| `.../program/generation/selection/LiftCatalogQuality.kt` | High systemic fatigue flag from High profile |
| `.../program/generation/build/WorkoutAssembler.kt` | Local High\|Medium compound checks |
| `.../program/generation/prescription/LiftStressClassification.kt` | Map from FatigueProfile |
| `.../program/generation/synthesis/wire/ContextWireCodec.kt` | `fatigueProfile` JSON key/values |
| `.../program/generation/synthesis/wire/ResultWireCodec.kt` | `allowedFatigueProfiles` encode/decode |
| `.../program/rules/ProgramRules.kt` | KDoc only if needed; still takes `isCompound: Boolean` |
| `.../checkin/CheckInRanking.kt` | Rank Low first (rename helper) |
| `.../dashboard/LoadDashboardUseCase.kt` | Missing-lift fallback → Low |
| `.../training/LoggedLiftContext.kt` | `fatigueProfile` default Medium |
| Domain tests listed in research F35 + goldens `SlotUniverseGoldenTest.kt` | Assert new enums, rest, Accessory keys |
| Import / custom metadata tests | `inferFatigueProfile` |

### Modify — domain solver

| Path | Responsibility |
|------|----------------|
| `core/domain-solver/.../ConstructiveSynthesisSeed.kt` | `SlotRole.Accessory` priority |
| `core/domain-solver/.../ProgramSynthesisModel.kt` | Role checks if Isolation referenced |
| `core/domain-solver-tests/...` | Fixtures and role/fatigue assertions |

### Modify — data (`:core:data`)

| Path | Responsibility |
|------|----------------|
| `.../db/StratumDatabase.kt` | Version stays 1; entity field renames via entities |
| `.../db/LiftEntity.kt` | Column `fatigueProfile`, default Low |
| `.../db/*ImportEntities*` / logged set entity | Column `fatigueProfile`, default Medium |
| `.../db/EnumConverters.kt` | Fatigue converters only; no legacy map |
| `.../seed/LiftSeedDto.kt`, `LiftCatalogSeeder.kt` | Field + missing→Low |
| `assets/lifts/seed-lifts.json` | `fatigueProfile` High/Medium/Low for all 266 |
| `schemas/.../1.json` | Column rename rewrite (KSP export) |
| `.../sync/codecs/SyncEntityEncoders.kt` | `fatigue_profile` |
| `.../sync/codecs/SyncEntityDecoders.kt` | `fatigue_profile`; lift→Low, set→Medium |
| Mappers (`TrainingLogMapper`, lift mappers) | Property rename |
| `.../di/DataModule.kt` | Comments only if migration wording stale |
| Data tests: `EnumConvertersTest`, `ProgramSchemaContractTest`, `RoomLiftRepositoryTest`, logged-set contracts | New names; schema still v1-only |

### Modify — testing fakes / fixtures

| Path | Responsibility |
|------|----------------|
| `core/testing/.../FakeLiftRepository.kt` | fatigueProfile |
| `core/testing/.../ProgramGenerationScenarioFixtures.kt` | fatigue values High/Medium/Low |

### Modify — app / features / UI

| Path | Responsibility |
|------|----------------|
| `app/.../liftconfig/CreateLiftViewModel.kt` | Fatigue draft, infer, rest draft, select handler |
| `app/.../liftconfig/LiftInformationViewModel.kt` | Fatigue edit + rest display |
| `app/.../liftconfig/LiftDetailsPresentation.kt` | Labels High/Medium/Low; options |
| `app/.../liftconfig/LiftDetailsEditor.kt` | Field enum Fatigue |
| `app/.../GymViewModel.kt` | Accessory category filter options |
| `core/ui/.../values/strings.xml` | Fatigue profile title + inline help |
| Create/edit Compose screens | Inline supporting text under fatigue field (OQ4) |
| `feature/program/...` edit/create mappers | isCompound map; any kinematic labels |
| App/feature unit tests for VMs | New enum names and copy |

### Explicitly not created

| Item | Why |
|------|-----|
| `Migration(1, 2)` / `2.json` | D4/D9 wipe; keep v1 rewrite |
| Legacy dual-read helpers | OQ1 / D5 |
| Linear % eligibility module | Follow-on |
| New tooltip component | OQ4 inline only |

---

## Test checkpoints (runnable after each slice)

| After slice | Minimum commands | What must pass |
|-------------|------------------|----------------|
| 1 | `./gradlew :core:domain:test` | Rest High/else 120; override; infer D8; stress map; isCompound High\|Medium\|multi; slot allow-list property on new enum |
| 2 | `./gradlew :core:data:testDebugUnitTest`<br>`./gradlew :core:domain:test`<br>`./gradlew :app:assembleDebug` | Converters new names; schema contract v1 only with new columns; seed DTO mapping; full compile; no Kinematic symbols |
| 3 | `./gradlew :core:domain:test` (+ touched feature tests) | Materialize rest matrix High/Medium/Low/missing; existing add-lift rest tests still green |
| 4 | `./gradlew :core:domain:test`<br>`./gradlew :core:domain-solver-tests:test` | Goldens Accessory keys; category display; solver role priority |
| 5 | `./gradlew :app:testDebugUnitTest`<br>`./gradlew :feature:program:testDebugUnitTest` (if touched)<br>`./gradlew detekt` | VM labels/defaults; inline help resources present; detekt 0 |

**Final maintainer smoke (not automated):** wipe device app data + Firestore → install → confirm reseed → generate or import program → High rest 180 on materialize → Accessory filters → create major-pattern custom lift Medium.

---

## Vertical-slice verification (self-check)

| Slice | End-to-end testable behavior | Not a horizontal layer-only phase |
|-------|------------------------------|-----------------------------------|
| 1 | Pure domain: rest + fatigue enum + infer + Linear gate semantics | Domain stack only, but complete product rules for rest/gate |
| 2 | Install-after-wipe path: seed + Room + sync codecs + app compile | Cross-layer mechanical rename finishing one compile-ready product |
| 3 | Generate program → sets carry resolved rest | Single behavior vertical through materialize API + callers |
| 4 | Universe keys + filters + slots say Accessory | Role/category/golden vertical through domain (+ solver) |
| 5 | User sees Fatigue profile labels + help + correct create defaults | Presentation + VM vertical for create/edit/library chrome |

No slice is “all migrations then all services then all UI.” No legacy migration balloon appears in any slice.

---

## Follow-on (documented for plan; not structured as slices here)

- Linear eligibility when `increment / workingWeight` is small enough (replace D6 gate).
- Optional collapse of remaining local compound flags.
- `LiftStressClass` user-language cleanup if ever exposed.
- Multi-version Room migrations if a future release cannot wipe.

---

## Self-review

- Every slice has a numbered goal, components, signatures, and a **checkpoint**.
- Slices are vertical; Slice 1 documents non-compile of data/app until Slice 2.
- File map aligns with design D1–D11 and wipe revisions D4/D5/D9.
- Schema pick locked: **v1 rewrite**, no `Migration(1,2)`, no dual-read.
- No `TBD` / `TODO` / “implement later” placeholders.
- Linear % rule is out of implementation slices (plan-doc only).

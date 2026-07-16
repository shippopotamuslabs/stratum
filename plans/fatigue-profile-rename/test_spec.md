# Test Spec: fatigue-profile-rename

**Ticket:** `fatigue-profile-rename`  
**Phase:** Plan (Synthitect)  
**Companion:** `spec.md`  
**Framework:** JUnit 5 (`org.junit.jupiter.api`), Turbine for Flow/StateFlow where ViewModels are tested, fakes from `:core:testing`. No MockK on Stratum-owned ports. Data module: JUnit 5 + Robolectric (`tech.apter.junit.jupiter.robolectric.RobolectricExtension`) where existing Room tests require it.

---

## Run commands

| Scope | Command |
|-------|---------|
| Domain unit tests | `./gradlew :core:domain:test` |
| Domain single class | `./gradlew :core:domain:test --tests "com.stratum.domain.lift.ResolvedDefaultRestSecondsTest"` |
| Domain solver tests | `./gradlew :core:domain-solver-tests:test` |
| Data unit tests | `./gradlew :core:data:testDebugUnitTest` |
| Data single class | `./gradlew :core:data:testDebugUnitTest --tests "com.stratum.core.data.db.EnumConvertersTest"` |
| App unit tests | `./gradlew :app:testDebugUnitTest` |
| Feature program tests | `./gradlew :feature:program:testDebugUnitTest` |
| Feature workout tests | `./gradlew :feature:workout:testDebugUnitTest` |
| Compile smoke | `./gradlew :app:assembleDebug` |
| Static analysis | `./gradlew detekt` |
| Room/KSP schema | `./gradlew :app:kspDebugKotlin` |
| Pre-merge (touched areas) | `./gradlew detekt` + `./gradlew :core:domain:test` + `./gradlew :core:data:testDebugUnitTest` + `./gradlew :app:testDebugUnitTest` + `./gradlew :app:assembleDebug` |

---

## Scenario index

| ID | Slice | Summary |
|----|-------|---------|
| FPR-S1-001 | 1 | High fatigue, no override → rest 180 |
| FPR-S1-002 | 1 | Medium fatigue, no override → rest 120 |
| FPR-S1-003 | 1 | Low fatigue, no override → rest 120 |
| FPR-S1-004 | 1 | Explicit defaultRestSeconds override wins |
| FPR-S1-005 | 1 | Multi-pattern Low does not get 180 (rest ignores isCompound) |
| FPR-S1-006 | 1 | isCompound true for High single-pattern |
| FPR-S1-007 | 1 | isCompound true for Medium single-pattern |
| FPR-S1-008 | 1 | isCompound true for multi-pattern Low |
| FPR-S1-009 | 1 | isCompound false for single-pattern Low |
| FPR-S1-010 | 1 | fatigueProfileToStressClass High → HeavyAxial |
| FPR-S1-011 | 1 | fatigueProfileToStressClass Medium → UpperCompound |
| FPR-S1-012 | 1 | fatigueProfileToStressClass Low → LiftStressClass.Isolation |
| FPR-S1-013 | 1 | infer empty patterns → Low |
| FPR-S1-014 | 1 | infer isolation pattern → Low |
| FPR-S1-015 | 1 | infer free-weight axial squat/hinge → High |
| FPR-S1-016 | 1 | infer machine squat → Medium |
| FPR-S1-017 | 1 | infer major press/pull non-empty → Medium |
| FPR-S1-018 | 1 | allowedFatigueProfiles Low+Medium admit both |
| FPR-S1-019 | 1 | Low ranks above Medium when allow-list present |
| FPR-S1-020 | 1 | Synthesis wire encodes fatigueProfile enum names |
| FPR-S1-M001 | 1 | Mutation defense: Medium rest is 120 not 180 |
| FPR-S2-001 | 2 | Converter High round-trip `"High"` |
| FPR-S2-002 | 2 | Converter Medium round-trip `"Medium"` |
| FPR-S2-003 | 2 | Converter Low round-trip `"Low"` |
| FPR-S2-004 | 2 | Unknown string decodes to null |
| FPR-S2-005 | 2 | Schema export 1.json uses fatigueProfile columns |
| FPR-S2-006 | 2 | Schema forbids 2.json and 3.json |
| FPR-S2-007 | 2 | Seeder missing fatigueProfile → Low |
| FPR-S2-008 | 2 | Seed asset bijective counts High 10 / Medium 131 / Low 125 |
| FPR-S2-009 | 2 | Sync encode key fatigue_profile |
| FPR-S2-010 | 2 | Sync decode lift unknown → Low |
| FPR-S2-011 | 2 | Sync decode logged set unknown → Medium |
| FPR-S2-012 | 2 | Sync does not apply kinematic_profile key |
| FPR-S2-013 | 2 | No production KinematicProfile / kinematicProfile / kinematic_profile symbols |
| FPR-S2-M001 | 2 | Mutation defense: legacy AxialCompound does not map to High |
| FPR-S3-001 | 3 | Materialize High lift restSeconds 180 |
| FPR-S3-002 | 3 | Materialize Medium lift restSeconds 120 |
| FPR-S3-003 | 3 | Materialize Low lift restSeconds 120 |
| FPR-S3-004 | 3 | Materialize missing lift id restSeconds 120 |
| FPR-S3-005 | 3 | Materialize honors Lift.defaultRestSeconds override via resolver |
| FPR-S3-006 | 3 | GenerateProgramUseCase passes catalog lookup into toDomainProgram |
| FPR-S3-M001 | 3 | Mutation defense: High lookup must not remain hard-coded 120 |
| FPR-S4-001 | 4 | SlotRole.Accessory is the isolation-lane role |
| FPR-S4-002 | 4 | Universe golden keys use \|Accessory\| |
| FPR-S4-003 | 4 | LiftCategory.AccessoryUpper.displayName is Upper Accessory |
| FPR-S4-004 | 4 | LiftCategory.AccessoryLower.displayName is Lower Accessory |
| FPR-S4-005 | 4 | Accessory slots allow Low and Medium only |
| FPR-S4-006 | 4 | Selector prefers Low over Medium on accessory allow-list |
| FPR-S4-007 | 4 | Spec-focused promotion Optional → Accessory |
| FPR-S4-M001 | 4 | Mutation defense: golden set rejects \|Isolation\| keys |
| FPR-S5-001 | 5 | Detail labels are High / Medium / Low |
| FPR-S5-002 | 5 | Field title string is Fatigue profile |
| FPR-S5-003 | 5 | Create-lift draft starts Low with rest 120 |
| FPR-S5-004 | 5 | Re-infer major pattern set → Medium |
| FPR-S5-005 | 5 | Re-infer free-weight axial → High and rest draft 180 |
| FPR-S5-006 | 5 | Gym/swap filter options include Upper Accessory and Lower Accessory |
| FPR-S5-007 | 5 | Inline help string present and states Low is not single-joint only |
| FPR-S5-M001 | 5 | Mutation defense: labels are not Compound -- Spine Loaded |
| FPR-S5-SMOKE | 5 | Manual wipe/reinstall smoke checklist |

**Total scenarios:** 58 automated/specified + 1 manual smoke = 59 IDs
(S1: 21, S2: 14, S3: 7, S4: 8, S5: 9 including smoke).

---

## Fixture conventions

| Fixture ID | Description |
|------------|-------------|
| `FIX-LIFT-HIGH` | `Lift` id `lift-high`, single pattern `SquatPattern`, primary Quad, `fatigueProfile = High`, `defaultRestSeconds = null` |
| `FIX-LIFT-MEDIUM` | `Lift` id `lift-medium`, single pattern `HorizontalPress`, primary Chest, `fatigueProfile = Medium`, `defaultRestSeconds = null` |
| `FIX-LIFT-LOW` | `Lift` id `lift-low`, single pattern `ElbowFlexion`, primary Bicep, `fatigueProfile = Low`, `defaultRestSeconds = null` |
| `FIX-LIFT-LOW-MULTI` | `Lift` id `lift-low-multi`, patterns `{HorizontalPress, ElbowExtension}`, primary Chest, `fatigueProfile = Low`, `defaultRestSeconds = null` |
| `FIX-LIFT-OVERRIDE-90` | Same as FIX-LIFT-HIGH but `defaultRestSeconds = 90` |
| `FIX-LIFT-OVERRIDE-240` | Same as FIX-LIFT-MEDIUM but `defaultRestSeconds = 240` |
| `FIX-NORM-ONE-SET` | Minimal `NormalizedProgram` with one workout, one workout lift, one set (position 0); liftId varies per scenario |
| `FIX-SEED-ASSET` | `core/data/src/main/assets/lifts/seed-lifts.json` as packaged for install |

Expected values are **literals** in each scenario. Tests must not read production constants as oracles for the asserted number when the production constant is under test; prefer literal `180` / `120` / `90` / `240` in assertions for rest matrix scenarios (constants may still be named in production code under test).

---

## Slice 1 — Domain FatigueProfile + rest + isCompound + infer/stress

**Primary test classes:**

- `com.stratum.domain.lift.ResolvedDefaultRestSecondsTest`
- `com.stratum.domain.lift.InferFatigueProfileTest`
- `com.stratum.domain.program.generation.prescription.LiftStressClassificationTest`
- `com.stratum.domain.program.generation.selection.LiftSlotSelectorFatigueProfileTest`
- Existing `Lift` construction helpers in category/similarity tests updated for fatigue

### FPR-S1-001 — High rest 180

```gherkin
Scenario: FPR-S1-001 High fatigue without override uses 180 seconds
  Given FIX-LIFT-HIGH with fatigueProfile High and defaultRestSeconds null
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 180
```

### FPR-S1-002 — Medium rest 120

```gherkin
Scenario: FPR-S1-002 Medium fatigue without override uses 120 seconds
  Given FIX-LIFT-MEDIUM with fatigueProfile Medium and defaultRestSeconds null
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 120
```

### FPR-S1-003 — Low rest 120

```gherkin
Scenario: FPR-S1-003 Low fatigue without override uses 120 seconds
  Given FIX-LIFT-LOW with fatigueProfile Low and defaultRestSeconds null
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 120
```

### FPR-S1-004 — Override wins

```gherkin
Scenario: FPR-S1-004 stored override wins over fatigue defaults
  Given FIX-LIFT-OVERRIDE-90 with fatigueProfile High and defaultRestSeconds 90
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 90
  Given FIX-LIFT-OVERRIDE-240 with fatigueProfile Medium and defaultRestSeconds 240
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 240
```

### FPR-S1-005 — Multi-pattern Low rest still 120

```gherkin
Scenario: FPR-S1-005 multi-pattern Low uses 120 not 180
  Given FIX-LIFT-LOW-MULTI with movementPatterns size 2 and fatigueProfile Low and defaultRestSeconds null
  And isCompound equals true
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 120
  And the result does not equal 180
```

### FPR-S1-006 — isCompound High

```gherkin
Scenario: FPR-S1-006 High single-pattern is compound
  Given FIX-LIFT-HIGH with movementPatterns size 1 and fatigueProfile High
  When isCompound is read
  Then isCompound equals true
```

### FPR-S1-007 — isCompound Medium

```gherkin
Scenario: FPR-S1-007 Medium single-pattern is compound
  Given FIX-LIFT-MEDIUM with movementPatterns size 1 and fatigueProfile Medium
  When isCompound is read
  Then isCompound equals true
```

### FPR-S1-008 — isCompound multi-pattern Low

```gherkin
Scenario: FPR-S1-008 multi-pattern Low is compound
  Given FIX-LIFT-LOW-MULTI with movementPatterns size 2 and fatigueProfile Low
  When isCompound is read
  Then isCompound equals true
```

### FPR-S1-009 — isCompound single-pattern Low

```gherkin
Scenario: FPR-S1-009 single-pattern Low is not compound
  Given FIX-LIFT-LOW with movementPatterns size 1 and fatigueProfile Low
  When isCompound is read
  Then isCompound equals false
```

### FPR-S1-010 — Stress High

```gherkin
Scenario: FPR-S1-010 High maps to HeavyAxial
  Given fatigue profile High
  When fatigueProfileToStressClass is invoked
  Then the result equals LiftStressClass.HeavyAxial
```

### FPR-S1-011 — Stress Medium

```gherkin
Scenario: FPR-S1-011 Medium maps to UpperCompound
  Given fatigue profile Medium
  When fatigueProfileToStressClass is invoked
  Then the result equals LiftStressClass.UpperCompound
```

### FPR-S1-012 — Stress Low

```gherkin
Scenario: FPR-S1-012 Low maps to LiftStressClass.Isolation
  Given fatigue profile Low
  When fatigueProfileToStressClass is invoked
  Then the result equals LiftStressClass.Isolation
```

### FPR-S1-013 — Infer empty → Low

```gherkin
Scenario: FPR-S1-013 empty patterns infer Low
  Given movementPatterns is empty set
  And liftName is null
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Low
```

### FPR-S1-014 — Infer isolation pattern → Low

```gherkin
Scenario: FPR-S1-014 isolation-style patterns infer Low
  Given pattern ElbowExtension and liftName "Skull Crusher"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Low
  Given pattern PlantarFlexion and liftName "Calf Raise"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Low
```

### FPR-S1-015 — Infer free-weight axial → High

```gherkin
Scenario: FPR-S1-015 free-weight axial squat or hinge infers High
  Given pattern HipHinge and liftName "Barbell Romanian Deadlift"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.High
  Given pattern SquatPattern and liftName "Barbell Back Squat"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.High
```

### FPR-S1-016 — Infer machine squat → Medium

```gherkin
Scenario: FPR-S1-016 machine squat or non-free-weight hinge infers Medium
  Given pattern SquatPattern and liftName "Smith Machine Back Squat"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Medium
  Given pattern HipHinge and liftName "Weighted Roman Chair Hip Hinge"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Medium
```

### FPR-S1-017 — Infer major press/pull → Medium

```gherkin
Scenario: FPR-S1-017 major multi-joint patterns infer Medium
  Given pattern VerticalPull and liftName "Overhand Grip Cable Lat Pulldown"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Medium
  Given pattern HorizontalPress and liftName "Machine Chest Press"
  When inferFatigueProfile is invoked
  Then the result equals FatigueProfile.Medium
```

### FPR-S1-018 — Allow-list admits Low and Medium

```gherkin
Scenario: FPR-S1-018 accessory-style allow-list admits Low and Medium candidates
  Given a WorkoutSlotTemplate with allowedFatigueProfiles equal to setOf(Low, Medium)
  And candidate lift A with fatigueProfile Low matching pattern and primary muscle
  And candidate lift B with fatigueProfile Medium matching pattern and primary muscle
  And candidate lift C with fatigueProfile High matching pattern and primary muscle
  When LiftSlotSelector filters candidates for the slot
  Then lift A is eligible
  And lift B is eligible
  And lift C is not eligible
```

### FPR-S1-019 — Low ranks above Medium

```gherkin
Scenario: FPR-S1-019 Low ranks before Medium when allow-list present
  Given a WorkoutSlotTemplate with allowedFatigueProfiles equal to setOf(Low, Medium)
  And two otherwise equal candidates Low and Medium
  When LiftSlotSelector ranks candidates for the slot
  Then the Low candidate sort position is before the Medium candidate
```

### FPR-S1-020 — Wire encodes fatigue names

```gherkin
Scenario: FPR-S1-020 context wire encodes fatigueProfile as High Medium Low names
  Given a LiftContext with fatigueProfile High
  When ContextWireCodec encodes the lift context JSON
  Then the JSON object contains key "fatigueProfile"
  And the value for "fatigueProfile" equals the string "High"
  And the JSON object does not contain key "kinematicProfile"
```

### FPR-S1-M001 — Mutation defense: Medium must not get compound rest

```gherkin
Scenario: FPR-S1-M001 Medium single-pattern rest is 120 not 180
  Given FIX-LIFT-MEDIUM with fatigueProfile Medium, movementPatterns size 1, defaultRestSeconds null
  And isCompound equals true
  When resolvedDefaultRestSeconds is invoked
  Then the result equals 120
  And the result does not equal 180
```

**Mutation risk:** Lazy implementer keeps rest on `isCompound` (pre-rename behavior). Medium is compound for Linear but must **not** receive 180s rest. This scenario fails that implementation.

**Run:** `./gradlew :core:domain:test --tests "com.stratum.domain.lift.ResolvedDefaultRestSecondsTest"`

---

## Slice 2 — Data / seed / schema / wire

**Primary test classes:**

- `com.stratum.core.data.db.EnumConvertersTest` (extend)
- `com.stratum.core.data.db.ProgramSchemaContractTest`
- `com.stratum.core.data.repository.RoomLiftRepositoryTest`
- `com.stratum.core.data.db.WorkoutLoggingLoggedSetContractTest`
- Seed distribution assertion (new test method or dedicated seed asset test in `:core:data`)
- Sync codec unit tests co-located with encoders/decoders if present; otherwise pure decoder/encoder tests added under `core/data/src/test`

### FPR-S2-001 — Converter High

```gherkin
Scenario: FPR-S2-001 FatigueProfile High round-trips
  Given EnumConverters instance
  When fromFatigueProfile is invoked with FatigueProfile.High
  Then the encoded string equals "High"
  When toFatigueProfile is invoked with "High"
  Then the result equals FatigueProfile.High
```

### FPR-S2-002 — Converter Medium

```gherkin
Scenario: FPR-S2-002 FatigueProfile Medium round-trips
  Given EnumConverters instance
  When fromFatigueProfile is invoked with FatigueProfile.Medium
  Then the encoded string equals "Medium"
  When toFatigueProfile is invoked with "Medium"
  Then the result equals FatigueProfile.Medium
```

### FPR-S2-003 — Converter Low

```gherkin
Scenario: FPR-S2-003 FatigueProfile Low round-trips
  Given EnumConverters instance
  When fromFatigueProfile is invoked with FatigueProfile.Low
  Then the encoded string equals "Low"
  When toFatigueProfile is invoked with "Low"
  Then the result equals FatigueProfile.Low
```

### FPR-S2-004 — Unknown → null

```gherkin
Scenario: FPR-S2-004 unknown fatigue string decodes to null
  Given EnumConverters instance
  When toFatigueProfile is invoked with "NotARealProfile"
  Then the result equals null
```

### FPR-S2-005 — Schema 1.json column names

```gherkin
Scenario: FPR-S2-005 exported 1.json uses fatigueProfile columns
  Given schema file schemas/com.stratum.core.data.db.StratumDatabase/1.json exists
  When the lifts table createSql is read
  Then createSql contains the substring "fatigueProfile"
  And createSql does not contain the substring "kinematicProfile"
  When the logged_sets table createSql is read
  Then createSql contains the substring "fatigueProfile"
  And createSql does not contain the substring "kinematicProfile"
```

### FPR-S2-006 — No version 2/3 exports

```gherkin
Scenario: FPR-S2-006 schema export forbids 2.json and 3.json
  Given schemas directory com.stratum.core.data.db.StratumDatabase
  Then file 1.json exists
  And file 2.json does not exist
  And file 3.json does not exist
```

### FPR-S2-007 — Seeder missing → Low

```gherkin
Scenario: FPR-S2-007 seeder maps missing fatigueProfile to Low
  Given a LiftSeedDto with fatigueProfile null and otherwise valid seed fields
  When LiftCatalogSeeder maps the DTO to an entity or domain lift for insert
  Then fatigueProfile equals FatigueProfile.Low
```

### FPR-S2-008 — Seed distribution

```gherkin
Scenario: FPR-S2-008 seed-lifts.json bijective fatigue counts
  Given FIX-SEED-ASSET seed-lifts.json
  When all 266 rows are counted by fatigueProfile field
  Then count of "High" equals 10
  And count of "Medium" equals 131
  And count of "Low" equals 125
  And no row contains field "kinematicProfile"
  And every row contains field "fatigueProfile"
```

### FPR-S2-009 — Sync encode key

```gherkin
Scenario: FPR-S2-009 lift and logged-set sync encode fatigue_profile
  Given a LiftEntity with fatigueProfile High
  When SyncEntityEncoders encodes the lift document map
  Then the map contains key "fatigue_profile"
  And map["fatigue_profile"] equals "High"
  And the map does not contain key "kinematic_profile"
  Given a LoggedSetEntity with fatigueProfile Medium
  When SyncEntityEncoders encodes the logged set document map
  Then the map contains key "fatigue_profile"
  And map["fatigue_profile"] equals "Medium"
  And the map does not contain key "kinematic_profile"
```

### FPR-S2-010 — Sync decode lift fallback Low

```gherkin
Scenario: FPR-S2-010 lift sync decode unknown or missing fatigue_profile yields Low
  Given a lift document map with fatigue_profile absent
  When SyncEntityDecoders decodes the lift
  Then fatigueProfile equals FatigueProfile.Low
  Given a lift document map with fatigue_profile equal to "GarbageValue"
  When SyncEntityDecoders decodes the lift
  Then fatigueProfile equals FatigueProfile.Low
```

### FPR-S2-011 — Sync decode logged set fallback Medium

```gherkin
Scenario: FPR-S2-011 logged set sync decode unknown or missing fatigue_profile yields Medium
  Given a logged set document map with fatigue_profile absent
  When SyncEntityDecoders decodes the logged set
  Then fatigueProfile equals FatigueProfile.Medium
  Given a logged set document map with fatigue_profile equal to "GarbageValue"
  When SyncEntityDecoders decodes the logged set
  Then fatigueProfile equals FatigueProfile.Medium
```

### FPR-S2-012 — No kinematic_profile dual-read

```gherkin
Scenario: FPR-S2-012 kinematic_profile key is ignored when fatigue_profile missing
  Given a lift document map containing only kinematic_profile equal to "High" and no fatigue_profile key
  When SyncEntityDecoders decodes the lift
  Then fatigueProfile equals FatigueProfile.Low
  And the decoder does not set fatigueProfile to High from kinematic_profile
```

### FPR-S2-013 — Symbol purge

```gherkin
Scenario: FPR-S2-013 production tree has no Kinematic symbols
  Given the repository production source sets under core/, app/, feature/
  When searching for identifiers KinematicProfile, kinematicProfile, and kinematic_profile
  Then zero matches exist in production Kotlin/XML/JSON seed sources
  And seed-lifts.json uses fatigueProfile only
```

**Verification method:** CI or developer `rg`/grep gate during Slice 2 checkpoint; may be documented as a checklist step in the assemble/detekt gate rather than a JUnit class.

### FPR-S2-M001 — Mutation defense: no legacy enum alias

```gherkin
Scenario: FPR-S2-M001 legacy AxialCompound does not decode to High
  Given EnumConverters instance
  When toFatigueProfile is invoked with "AxialCompound"
  Then the result equals null
  When toFatigueProfile is invoked with "NonAxialCompound"
  Then the result equals null
  When toFatigueProfile is invoked with "Isolation"
  Then the result equals null
```

**Mutation risk:** Lazy dual-read converter maps old kinematic names to High/Medium/Low. Wipe policy forbids aliases; this scenario fails any remapping balloon.

**Run:** `./gradlew :core:data:testDebugUnitTest --tests "com.stratum.core.data.db.EnumConvertersTest"`

---

## Slice 3 — Materialize rest

**Primary test classes:**

- `com.stratum.domain.program.generation.schema.ProgramDomainMapperRestTest` (create)
- `com.stratum.domain.program.GenerateProgramUseCaseTest` or existing generate program test if present — extend with catalog rest assertion; if no dedicated class, unit-test mapper + a focused use-case test with fakes from `:core:testing`

### FPR-S3-001 — High → 180

```gherkin
Scenario: FPR-S3-001 materialize stamps High rest 180 when lookup provided
  Given FIX-NORM-ONE-SET with liftId "lift-high"
  And restSecondsForLift returns 180 for LiftId("lift-high")
  When toDomainProgram is invoked with that restSecondsForLift
  Then the single LiftSet restSeconds equals 180
```

### FPR-S3-002 — Medium → 120

```gherkin
Scenario: FPR-S3-002 materialize stamps Medium rest 120 when lookup provided
  Given FIX-NORM-ONE-SET with liftId "lift-medium"
  And restSecondsForLift returns 120 for LiftId("lift-medium")
  When toDomainProgram is invoked with that restSecondsForLift
  Then the single LiftSet restSeconds equals 120
```

### FPR-S3-003 — Low → 120

```gherkin
Scenario: FPR-S3-003 materialize stamps Low rest 120 when lookup provided
  Given FIX-NORM-ONE-SET with liftId "lift-low"
  And restSecondsForLift returns 120 for LiftId("lift-low")
  When toDomainProgram is invoked with that restSecondsForLift
  Then the single LiftSet restSeconds equals 120
```

### FPR-S3-004 — Missing id → 120

```gherkin
Scenario: FPR-S3-004 materialize falls back to 120 when lookup returns null
  Given FIX-NORM-ONE-SET with liftId "unknown-lift"
  And restSecondsForLift returns null for every LiftId
  When toDomainProgram is invoked with that restSecondsForLift
  Then the single LiftSet restSeconds equals 120
```

### FPR-S3-005 — Override via resolver

```gherkin
Scenario: FPR-S3-005 materialize uses resolvedDefaultRestSeconds including override
  Given FIX-LIFT-OVERRIDE-90 with id lift-high and defaultRestSeconds 90
  And restSecondsForLift is { id -> mapOf(LiftId("lift-high") to FIX-LIFT-OVERRIDE-90).get(id)?.resolvedDefaultRestSeconds() }
  And FIX-NORM-ONE-SET with liftId "lift-high"
  When toDomainProgram is invoked
  Then the single LiftSet restSeconds equals 90
```

### FPR-S3-006 — GenerateProgramUseCase catalog pass-through

```gherkin
Scenario: FPR-S3-006 GenerateProgramUseCase supplies catalog rest lookup
  Given a fake pipeline that returns FIX-NORM-ONE-SET with liftId matching FIX-LIFT-HIGH.id
  And LiftRepository observeEnabledLifts emits a list containing FIX-LIFT-HIGH
  When GenerateProgramUseCase is invoked successfully
  Then the returned Program contains a LiftSet for that lift with restSeconds equal to 180
```

### FPR-S3-M001 — Mutation defense: no hard-coded 120 when High lookup present

```gherkin
Scenario: FPR-S3-M001 High lookup must change restSeconds away from blanket 120
  Given FIX-NORM-ONE-SET with liftId "lift-high"
  And restSecondsForLift returns 180 for LiftId("lift-high")
  When toDomainProgram is invoked
  Then the single LiftSet restSeconds equals 180
  And the single LiftSet restSeconds does not equal 120
```

**Mutation risk:** Leaving `restSeconds = DEFAULT_LIFT_SET_REST_SECONDS` hard-coded in `ProgramDomainMapper` ignores the new parameter. This scenario fails that lazy path.

**Run:** `./gradlew :core:domain:test --tests "com.stratum.domain.program.generation.schema.ProgramDomainMapperRestTest"`

---

## Slice 4 — Accessory hard rename + goldens

**Primary test classes:**

- `com.stratum.domain.program.generation.universe.SlotUniverseGoldenTest`
- `com.stratum.domain.lift.LiftCategoryTest`
- `com.stratum.domain.program.generation.selection.LiftSlotSelectorFatigueProfileTest`
- Solver tests under `core/domain-solver-tests` asserting `SlotRole.Accessory`

### FPR-S4-001 — SlotRole.Accessory

```gherkin
Scenario: FPR-S4-001 isolation-lane templates use SlotRole.Accessory
  Given LOWER_SLOTS templates for slotIds "quad-iso" and "ham-iso"
  When each template role is read
  Then role equals SlotRole.Accessory
  And SlotRole does not declare an Isolation entry
```

### FPR-S4-002 — Golden keys Accessory

```gherkin
Scenario: FPR-S4-002 universe golden keys use Accessory role segment
  Given SlotUniverseGoldenTest maintenance golden set
  Then the set contains "ElbowExtension|Tricep|Accessory|false|Any"
  And the set contains "ElbowFlexion|Bicep|Accessory|false|Any"
  And the set contains "KneeExtension|Quad|Accessory|false|Any"
  And the set contains "KneeFlexion|Hamstring|Accessory|false|Any"
  And the set contains "ShoulderAbduction|LateralDeltoid|Accessory|false|Any"
  And the set does not contain any string with substring "|Isolation|"
```

### FPR-S4-003 — Upper Accessory display

```gherkin
Scenario: FPR-S4-003 AccessoryUpper display name
  When LiftCategory.AccessoryUpper.displayName is read
  Then the value equals "Upper Accessory"
```

### FPR-S4-004 — Lower Accessory display

```gherkin
Scenario: FPR-S4-004 AccessoryLower display name
  When LiftCategory.AccessoryLower.displayName is read
  Then the value equals "Lower Accessory"
```

### FPR-S4-005 — Allow-list Low and Medium

```gherkin
Scenario: FPR-S4-005 quad-iso and ham-iso allow Low and Medium only
  Given LOWER_SLOTS templates for "quad-iso" and "ham-iso"
  When allowedFatigueProfiles is read
  Then the set equals setOf(FatigueProfile.Low, FatigueProfile.Medium)
  And the set does not contain FatigueProfile.High
```

### FPR-S4-006 — Prefer Low

```gherkin
Scenario: FPR-S4-006 Low preferred over Medium for accessory allow-list ranking
  Given an Accessory role slot with allowedFatigueProfiles Low and Medium
  And two equal candidates differing only by fatigue Low vs Medium
  When LiftSlotSelector ranks candidates
  Then the Low candidate sort position is before the Medium candidate
```

### FPR-S4-007 — Optional → Accessory promotion

```gherkin
Scenario: FPR-S4-007 spec-focused promotion rewrites Optional to Accessory
  Given a WorkoutSlotTemplate with role Optional for a specialized muscle
  When applySpecFocusedUpperSlots (or equivalent promotion) runs with Specialize on that muscle
  Then the promoted role equals SlotRole.Accessory
  And the promoted role does not equal SlotRole.Optional
```

### FPR-S4-M001 — Mutation defense: Isolation keys rejected

```gherkin
Scenario: FPR-S4-M001 golden equality fails if Isolation keys remain
  Given the golden set built by SlotUniverseGoldenTest for the maintenance scenario
  And a polluted actual key set that still contains "ElbowFlexion|Bicep|Isolation|false|Any"
  When the golden set is compared for equality to the polluted set
  Then equality is false
  And the production golden set itself does not contain "ElbowFlexion|Bicep|Isolation|false|Any"
```

**Mutation risk:** Updating only some templates while leaving goldens dual-accepting Isolation, or renaming display strings without renaming `role.name`. Hard equality on Accessory keys fails Isolation leftovers.

**Run:** `./gradlew :core:domain:test --tests "com.stratum.domain.program.generation.universe.SlotUniverseGoldenTest"`

---

## Slice 5 — UI labels, inline help, create/edit

**Primary test classes:**

- `com.stratum.liftconfig.CreateLiftViewModelTest`
- Lift information VM tests if present under `app/src/test`
- Presentation pure-function tests may live next to ViewModel tests or as small unit tests in app test source set
- Gym filter options can be asserted via `GymViewModel` option list test if present; otherwise pure assertion on `LiftCategory.displayName` already covered in FPR-S4-003/004 plus a ViewModel options list test

### FPR-S5-001 — Labels High/Medium/Low

```gherkin
Scenario: FPR-S5-001 fatigue detail labels are High Medium Low
  Given FatigueProfile.High
  When toDetailLabel is invoked
  Then the result equals "High"
  Given FatigueProfile.Medium
  When toDetailLabel is invoked
  Then the result equals "Medium"
  Given FatigueProfile.Low
  When toDetailLabel is invoked
  Then the result equals "Low"
```

### FPR-S5-002 — Field title Fatigue profile

```gherkin
Scenario: FPR-S5-002 string resource title is Fatigue profile
  Given resource lift_info_fatigue_profile in core/ui values/strings.xml
  When the string value is read
  Then the value equals "Fatigue profile"
```

### FPR-S5-003 — Create draft Low + 120 rest

```gherkin
Scenario: FPR-S5-003 create-lift draft defaults to Low and 120 rest
  Given a fresh CreateLiftViewModel
  When the initial draft state is observed
  Then draft fatigue equals FatigueProfile.Low
  And restDraft equals 120
```

### FPR-S5-004 — Re-infer major → Medium

```gherkin
Scenario: FPR-S5-004 committing a major compound pattern re-infers Medium
  Given a CreateLiftViewModel draft with empty patterns
  When the user commits movement pattern HorizontalPress
  And lift name is "Machine Chest Press"
  Then draft fatigue equals FatigueProfile.Medium
  And restDraft equals 120
```

### FPR-S5-005 — Re-infer axial High + 180 rest

```gherkin
Scenario: FPR-S5-005 free-weight axial name and squat pattern re-infer High and 180 rest
  Given a CreateLiftViewModel draft
  When the user sets name to "Barbell Back Squat"
  And commits movement pattern SquatPattern
  Then draft fatigue equals FatigueProfile.High
  And restDraft equals 180
```

### FPR-S5-006 — Filter chips Accessory

```gherkin
Scenario: FPR-S5-006 Gym and lift-swap filter options include Accessory labels
  Given GymViewModel category filter options (or LiftSwapStateMapper options)
  When the option labels are collected
  Then the labels contain "Upper Accessory"
  And the labels contain "Lower Accessory"
  And the labels do not contain "Upper Isolation"
  And the labels do not contain "Lower Isolation"
```

### FPR-S5-007 — Inline help copy

```gherkin
Scenario: FPR-S5-007 fatigue help string documents tiers and rejects single-joint-only framing
  Given resource lift_info_fatigue_help in core/ui values/strings.xml
  When the string value is read
  Then the value contains the substring "High"
  And the value contains the substring "Medium"
  And the value contains the substring "Low"
  And the value contains the substring "not"
  And the value contains the substring "single-joint"
```

### FPR-S5-M001 — Mutation defense: old compound labels gone

```gherkin
Scenario: FPR-S5-M001 presentation no longer uses Compound -- Spine Loaded labels
  Given FatigueProfile.High
  When toDetailLabel is invoked
  Then the result equals "High"
  And the result does not equal "Compound -- Spine Loaded"
  Given FatigueProfile.Medium
  When toDetailLabel is invoked
  Then the result equals "Medium"
  And the result does not equal "Compound -- No Spine Loading"
  Given FatigueProfile.Low
  When toDetailLabel is invoked
  Then the result equals "Low"
  And the result does not equal "Isolation"
```

**Mutation risk:** Renaming the enum but leaving presentation when-branches on old labels. Asserting exact new strings fails the old biomechanics copy.

**Run:** `./gradlew :app:testDebugUnitTest --tests "com.stratum.liftconfig.CreateLiftViewModelTest"`

### FPR-S5-SMOKE — Manual maintainer smoke

```gherkin
Scenario: FPR-S5-SMOKE wipe reinstall reseed generate
  Given device app data wiped and Firestore user data cleared
  When the app is reinstalled and first-opened so seed runs
  Then seed lifts load with fatigueProfile High Medium Low only
  When a custom lift is created with HorizontalPress pattern
  Then inferred fatigue is Medium and default rest display is 120 seconds
  When a free-weight axial squat is inferred or selected as High
  Then default rest display is 180 seconds
  When a program is generated with at least one High catalog lift
  Then materialize template set rest for that lift is 180 seconds
  When Gym or swap filters are opened
  Then filter chips show Upper Accessory and Lower Accessory
```

---

## Error and edge cases (consolidated)

| ID | Edge / error | Expected |
|----|--------------|----------|
| FPR-S1-004 | Override 90 on High | rest equals 90 |
| FPR-S1-005 | Multi-pattern Low | rest 120; isCompound true |
| FPR-S1-013 | Empty patterns | Low |
| FPR-S1-016 | Smith machine squat name | Medium not High |
| FPR-S2-004 | Unknown converter string | null |
| FPR-S2-010 / FPR-S2-011 | Missing or garbage remote value | Low (lift) / Medium (logged set) |
| FPR-S2-012 | Only legacy key present | Low (no dual-read) |
| FPR-S2-M001 | Legacy enum names | null (no alias) |
| FPR-S3-004 | Unknown lift id at materialize | restSeconds 120 |
| FPR-S3-005 | Override via resolver at materialize | restSeconds 90 |
| FPR-S4-M001 | Residual Isolation golden keys | equality fails / set must not contain |

---

## Mutation-defense summary

| ID | Lazy implementation | Why it fails the scenario |
|----|---------------------|---------------------------|
| FPR-S1-M001 | Keep rest on `isCompound` | Medium is compound → would yield 180; assert 120 |
| FPR-S2-M001 | Dual-read AxialCompound→High | `"AxialCompound"` must decode to null |
| FPR-S3-M001 | Ignore restSecondsForLift; hard-code 120 | High lookup must yield 180 |
| FPR-S4-M001 | Leave Isolation in goldens/role.name | Golden keys must be Accessory-only |
| FPR-S5-M001 | Keep old presentation strings | Labels must be High/Medium/Low exactly |

---

## Mapping to implementation tasks

| Task | Scenarios |
|------|-----------|
| T1.1–T1.8 | FPR-S1-001 … FPR-S1-020, FPR-S1-M001 |
| T2.1–T2.7 | FPR-S2-001 … FPR-S2-013, FPR-S2-M001 |
| T3.1–T3.4 | FPR-S3-001 … FPR-S3-006, FPR-S3-M001 |
| T4.1–T4.7 | FPR-S4-001 … FPR-S4-007, FPR-S4-M001 |
| T5.1–T5.6 | FPR-S5-001 … FPR-S5-007, FPR-S5-M001, FPR-S5-SMOKE |

---

## Self-review (test_spec)

- Every Then clause uses a literal expected value, enum case, or explicit null.
- Mutation defenses name the lazy implementation they catch.
- Scenario IDs match `spec.md` task references.
- No placeholders (`TBD`, `TODO`, `similar to`, `as above`).
- Frameworks and Gradle commands match AGENTS.md and research §J.
- Wipe policy covered: no MigrationTestHelper scenarios; explicit no-legacy-alias scenario FPR-S2-M001.

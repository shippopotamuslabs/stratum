# Status: fatigue-profile-rename

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-07-16)
- [x] Structure — approval: yes (2026-07-16)
- [x] Plan — approval: yes (2026-07-16)
- [x] TDD Red
- [x] Red Review — result: pass
- [x] Implementation
- [x] Final Review — result: pass

## Current Gate
none

## Next Phase
Complete (thermos polish applied 2026-07-16)

## Loop Counts
- Red Review: 2/3
- Final Review: 1/3

## Notes
- Ticket created 2026-07-16 from MovementPattern / KinematicProfile / rest /
  Isolation-vs-Accessory design conversation.
- Research complete 2026-07-16: `plans/fatigue-profile-rename/research.md`
- Design complete 2026-07-16: `plans/fatigue-profile-rename/design.md`
- **Design approved 2026-07-16** with OQ resolutions (see design.md § Open questions
  — resolved). **Wipe policy supersedes** earlier “preserve Room/Firestore” stance:
  no legacy dual-read/remap balloon; uninstall/reinstall + clear Firestore; schema
  may stay wipe-friendly (destructive / v1 rewrite or clean v2 without data migration).
- Structure complete 2026-07-16: `plans/fatigue-profile-rename/structure.md`
  (5 vertical slices). Schema pick: **keep Room v1 + rewrite `1.json`** (no Migration,
  no dual-read).
- **Structure approved 2026-07-16.**
- Plan complete 2026-07-16: `plans/fatigue-profile-rename/spec.md` +
  `plans/fatigue-profile-rename/test_spec.md` (5 slices; task IDs T1.*–T5.*;
  scenarios FPR-S1-* … FPR-S5-*; Linear % rule is follow-on note only).
- **Plan approved 2026-07-16.**
- **TDD Red complete 2026-07-16.**
- **Red Review FAIL 2026-07-16** (loop 1/3). Stop Implementation until Red
  corrections land. Findings:
  1. **FPR-S2-007** (`FatigueProfileSeedAssetTest.kt`) is a tautology — never
     calls `LiftCatalogSeeder.mapDto` / seeder; inlines `?: FatigueProfile.Low`.
  2. **FPR-S3-006** (`ProgramDomainMapperRestTest`) does not invoke
     `GenerateProgramUseCase`; only re-tests mapper + catalog-style lambda
     (same path as S3-001 once lookup is honored). Use-case wiring untested.
- **TDD Red correction pass 2026-07-16** (loop 1 findings fixed; re-enter Red Review):
  1. **FPR-S2-007** now invokes private `LiftCatalogSeeder.mapDto` via reflection
     with a DTO that omits profile; asserts mapped entity profile name is `Low`.
     Currently red: seeder still maps missing → `KinematicProfile.Isolation`
     (`expected: <Low> but was: <Isolation>`).
  2. **FPR-S3-006** moved to `GenerateProgramUseCaseTest` with
     `FixedNormalizedProgramPipeline` (FIX-NORM-ONE-SET / lift-high) +
     `FakeLiftRepository` containing FIX-LIFT-HIGH (AxialCompound stand-in).
     Asserts generated set `restSeconds == 180`. Currently red: use case calls
     `toDomainProgram()` without catalog lookup → hard-coded `120`.
  3. Minimal fallout: `ProgramGenerationPipeline` made `open` so the fixed
     pipeline test double can override `generate`. Mapper matrix tests for
     S3-001…005/M001 unchanged in `ProgramDomainMapperRestTest`.
- **Red Review PASS 2026-07-16** (loop 2/3). Prior findings re-verified clean:
  - FPR-S2-007 exercises real seeder `mapDto` (not Elvis-in-test oracle).
  - FPR-S3-006 exercises `GenerateProgramUseCase` + fakes; asserts rest 180.
  No new admissible red defects. Residual: legacy kinematic stand-ins that stay
  green until Implementation bijective rename; reflection on private `mapDto`;
  status test-command list should include `GenerateProgramUseCaseTest` for S3-006.
- **Implementation GREEN complete 2026-07-16** (all 5 slices):
  1. Domain: `FatigueProfile` High/Medium/Low; rest High→180 else 120; isCompound
     multi|High|Medium; `inferFatigueProfile` D8; stress map; wire codecs; deleted
     `KinematicProfile` / `InferKinematicProfile`.
  2. Data: entities/converters/seed/sync `fatigueProfile` / `fatigue_profile`;
     seed bijective 10/131/125; Room v1 `1.json` rewritten; no Migration/dual-read.
  3. Materialize: `toDomainProgram(restSecondsForLift)`; `GenerateProgramUseCase`
     passes catalog `resolvedDefaultRestSeconds()`.
  4. Accessory: `SlotRole.Accessory`, `LiftCategory.Accessory*`, library groups,
     goldens `|Accessory|`, allow-lists Low+Medium.
  5. UI: Fatigue profile labels High/Medium/Low; inline help; create-lift draft Low
     with rest tracking resolver; Accessory filter chips.
  - FPR-S2-013: production clean of `KinematicProfile` / `kinematicProfile` /
    `kinematic_profile`.
  - Full `:core:domain:test` green; FPR data/app suites green; `:app:assembleDebug`
    green; detekt green.
  - Note: `:core:domain-solver-tests:compileTestKotlin` still fails on pre-existing
    unrelated `weekdays` / `LocalSynthesisLab` errors (not introduced by this rename;
    ProgramCandidateAuditTest / ProgramSynthesisAcceptanceTest renames applied).
- **Final Review PASS 2026-07-16** (loop 1/3). Admissible findings: none.
  Re-verified D1–D11 / OQ wipe / rest / materialize / Accessory / no dual-read;
  production symbol purge clean; FPR suites + full domain:test + assembleDebug +
  detekt green. domain-solver-tests compile failure confirmed pre-existing
  (stale `weekdays=` on `ProgramGenerationContext` in AntagonistMaintenanceBalanceTest
  from gen-identity work; LocalSynthesisLab type inference) — FPR only renamed
  kinematic→fatigue fixtures in two audit acceptance tests. Ticket complete
  pending maintainer manual FPR-S5-SMOKE wipe/reinstall.

### Implementation verification commands

```bash
./gradlew :core:domain:test \
  --tests "com.stratum.domain.lift.ResolvedDefaultRestSecondsTest" \
  --tests "com.stratum.domain.lift.InferFatigueProfileTest" \
  --tests "com.stratum.domain.program.generation.prescription.LiftStressClassificationTest" \
  --tests "com.stratum.domain.program.generation.selection.LiftSlotSelectorFatigueProfileTest" \
  --tests "com.stratum.domain.program.generation.schema.ProgramDomainMapperRestTest" \
  --tests "com.stratum.domain.program.GenerateProgramUseCaseTest" \
  --tests "com.stratum.domain.program.generation.slots.AccessoryRoleRenameTest" \
  --tests "com.stratum.domain.program.generation.universe.SlotUniverseAccessoryGoldenContractTest" \
  --tests "com.stratum.domain.lift.LiftCategoryTest" \
  --tests "com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWireTest"

./gradlew :core:data:testDebugUnitTest \
  --tests "com.stratum.core.data.db.EnumConvertersTest" \
  --tests "com.stratum.core.data.db.FatigueProfileSchemaContractTest" \
  --tests "com.stratum.core.data.seed.FatigueProfileSeedAssetTest" \
  --tests "com.stratum.core.data.sync.codecs.FatigueProfileSyncCodecTest"

./gradlew :app:testDebugUnitTest \
  --tests "com.stratum.liftconfig.FatigueProfilePresentationTest" \
  --tests "com.stratum.liftconfig.CreateLiftViewModelTest"

./gradlew :core:domain:test
./gradlew :app:assembleDebug
./gradlew detekt
```

**Deferred / manual:**
- FPR-S5-SMOKE — maintainer wipe app data + Firestore → reinstall → reseed

# Status: workout-logging

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-07-05)
- [x] Structure — approval: yes (2026-07-05)
- [x] Plan — approval: yes (2026-07-05)
- [x] TDD Red
- [x] Red Review — result: pass (2026-07-05, pass 2)
- [x] Implementation
- [ ] Final Review — result: pending

## Current Gate
Final Review

## Next Phase
Final Review

## Loop Counts
- Red Review: 2/3
- Final Review: 0/3

## Notes
- Ticket created 2026-07-05 from Programs screen / logging schema design discussion.
- Prior conversation captured agreed direction in ticket.md; research verified against codebase objectively.
- **Open unknowns from research:**
  - `ProgramsViewModel` KDoc references swap sheet state not present in current implementation.
  - `Program.cycleLength` is persisted but not wired to Programs next-workout / completion UI.
  - No incremental `TrainingHistoryRepository` API — import path uses bulk `replaceAll` only.
  - No `:feature:workout` module or live-session tables exist yet.
  - No program change-event log in current schema.
- **Design approved 2026-07-05.** Key resolutions: zero-set sessions for rest/skip; cycle
  completion via session ledger (not fragile list index); block second active session; rename
  to TrainingSession; nullable workout_id for ad-hoc; CYCLE_COMPLETED triggers check-in;
  import snapshots lift-only.
- **Plan complete 2026-07-05.** `spec.md` (61 tasks, 9 slices), `test_spec.md` (44 scenarios).
- **TDD Red complete 2026-07-05.** 44 scenario tests (WL-001–WL-044); 41 failing red, 3 schema
  contract tests green (entity/enum round-trip stubs). Failures are `NotImplementedError` or
  assertion mismatches — no compile errors.
- **Red Review pass 1 (2026-07-05):** 7 admissible defects — wrong SUT (WL-004/005), broken
  Given (WL-022/026/032), weakened mutation-defense (WL-007/044), bitmask literals, missing
  WL-030/038 prerequisite session.
- **TDD Red correction pass 1 (2026-07-05):** Fixed all 7 findings + WL-030/038 prerequisites.
  `WorkoutLoggingFixtures` bitmasks aligned to LiftMapper (`32`/`1`/`256`); `FakeTrainingLogRepository`
  in-memory CRUD + seed helpers; WL-004/005 exercise `PersistImportedHistoryUseCase`; WL-007/044
  persist-then-mutate-then-recompute via real entry points; `TrainingSessionNotInProgressException`
  stub for WL-032.
- **Test command:** `./gradlew :core:domain:test :core:data:testDebugUnitTest`
- **Red Review pass 2 (2026-07-05):** **pass** — 44/44 coverage, 41 red failures valid. Proceed to Implementation.
- **Red Review pass 2 (2026-07-05):** PASS — 44/44 scenario coverage; 41 red failures
  (3 green schema contracts); all 7 pass-1 defects verified fixed in test code; failures are
  `NotImplementedError`, `NoSuchElementException` (persist not yet writing training log), or
  assertion mismatches — no compile errors, no broken Givens.
- **Changed files (high level):**
  - Domain stubs: `core/domain/.../training/*`, `Program.kt` cycle fields, `MuscleVolumeCredit`
    overload, analytics snapshot stubs
  - Data stubs: `TrainingSessionEntity`, `ProgramChangeEventEntity`, `TrainingLogDaos`,
    `RoomTrainingLogRepository`, enum converters
  - Testing: `FakeTrainingLogRepository`, `FakeProgramChangeEventRepository`,
    `WorkoutLoggingFixtures`
- **Implementation complete 2026-07-05.** All WL scenarios green;
  `./gradlew :core:domain:test :core:data:testDebugUnitTest detekt` pass.
  Fixed `TRICEPS_BITMASK` constant in WorkoutLoggingFixtures post-impl.

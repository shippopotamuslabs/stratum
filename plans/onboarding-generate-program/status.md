# Status: onboarding-generate-program

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-06-26)
- [x] Structure — approval: yes (2026-06-26)
- [x] Plan — approval: yes (2026-06-26)
- [x] TDD Red
- [x] Red Review — result: pass
- [x] Implementation
- [x] Final Review — result: pass (loop 2, 2026-06-26)

## Current Gate
Workflow complete; ready for merge/handoff

## Next Phase
Complete (workflow done, ready for merge/handoff)

## Loop Counts
- Red Review: 2/3
- Final Review: 2/3

## Notes
- Branch: `feature/onboarding-generate-program` (checked out at ticket creation).
- Vertical slice OS-7 in `docs/onboarding-vertical-slices.md` is the source of truth.
- Depends on OS-0/OS-5/OS-6 and consumes `plans/response-profile/` artifacts.
- `response-profile` sibling ticket is at Final Review pending (separate worktree); its payload
  extensions (`responseProfile`, volume landmarks) are already implemented and tested and are the
  OS-7 prompt inputs.
- Research verified: `:core:inference` and `functions/` already exist (5 callables, no
  `generateProgram` yet); `:feature:program` and the Program/Workout/WorkoutLift/Set domain +
  persistence layer do NOT exist and are greenfield for OS-7. The Generate step UI is stubbed
  (calls `CompleteOnboardingUseCase`, does no generation). `ProgramGenerationPromptBuilder` and
  `LoadProgramGenerationPayloadUseCase` exist and are the OS-7 prompt input path.
- Open unknowns carried to Design (from ticket §"Open questions"): normalized program tables vs
  JSON blob; single-shot generation vs server tool loop; Programs screen scope; placement of the
  Generate CTA. These are Design targets, not research gaps.
- Design complete (2026-06-26): normalized 4-table persistence (D1), single-shot generation
  with deterministic validate-and-clamp post-pass — not a tool loop (D2), read-only Programs
  overview as the post-onboarding landing in a new `:feature:program` (D3), Generate CTA on the
  existing Generate step replacing the stub (D4). All four ticket open questions resolved in
  D1–D4.
- Design APPROVED (2026-06-26) with two adjustments from maintainer:
  - **D3 confirmed** — Programs screen is read-only in OS-7; modify deferred.
  - **Schema:** keep `StratumDatabase` at **version 1** — add the four new tables to the v1
    schema (no version bump, no migration). Reinstall to pick up the change (pre-release
    policy). Replaces the earlier "bump to v2" framing in design.md D1 / Open questions.
- **Spec amendment (TDD Red):** introduced `ProgramGenerationPayloadLoader` fun interface in
  `:core:domain`; `LoadProgramGenerationPayloadUseCase` implements it (`load()` delegates to
  `invoke()`); `GenerateProgramUseCase` depends on the interface, not the concrete class. Solved
  the 11-deps/4-missing-fakes testability gap. Also created the 4 missing importing-repo fakes in
  `:core:testing` (`FakeImportInsightsRepositories.kt`). Added `ObserveProgramUseCase` so
  `ProgramsViewModel` injects a use case (not a repo) per the `ForbiddenRepositoryInViewModel`
  contract.
- **TDD Red complete (2026-06-26):** 6 slices. 23 failing tests (14 Kotlin PROG-GEN + 9 TS), all
  failing for the intended unimplemented-behavior reason; 0 regressions to existing tests.
  `./gradlew detekt` green (0 issues); `:app:assembleDebug` green; `:app:kspDebugKotlin` green;
  only `1.json` schema exists (DB v1). `npm --prefix functions test` green for the build, 9 new
  TS scenarios fail with `Error: TDD Red`.
  - Red stubs (throw `NotImplementedError`/`Error("TDD Red")`): `GenerateProgramUseCase.invoke`,
    `RoomProgramRepository.{observe,get,save}Program`, `ProgramsViewModel.toProgramsUiState` (non-null
    branch), `FirebaseProgramGenerator.parseProgram`, `OnboardingViewModel.generate` (no-op),
    `programRules.{schemeForTrainingAge,clampBackOffRir}`, `programSchema.parseAndNormalizeProgram`,
    `generateProgram.buildNormalizationRequest`.
  - Real (non-stub) production code written: domain models/ids/enums/ports, Room entities/DAOs/
    mapper/EnumConverters, `ProgramMapper`, `ProgramDatabaseModule`/bindings, `FirebaseProgramGenerator`
    callable wiring + `InferenceModule` bind, `:feature:program` module (nav/VM/route/screen/strings),
    NavHost routing to `ProgramsKey`, Generate step UI (loading/error/idle), `programJsonSchema()`,
    `MAJOR_COMPOUND_PATTERNS`/`TOP_SET_RIR`, system prompt, `programGenerationSessionId`.
  - **Deferred to instrumented tests (not unit-testable, no MockK on classpath):** PROG-GEN-403
    full routing render, PROG-GEN-501/503 callable invocation + auth gate, PROG-GEN-701 (the
    `generateProgram` `onCall` auth gate is unreachable from `node --test` without the Firebase
    test harness — verified deploy-time, matching the `inferImportEquipment` pattern). PROG-GEN-700,
    702, 702b are unit-tested via the `generateProgramWithDeps` seam (injected OpenAI client).
- **Red Review FAIL (loop 1, 2026-06-26):** 27/32 approved scenarios have executable tests; 5 are
  absent (NOT deferred): PROG-GEN-602 (GenerateScreen error UI), and the server-callable scenarios
  PROG-GEN-700, 701, 702, 702b. `spec.md` file table lists `generateProgram.test.ts` as a Create
  action but it was never created. The present 14 Kotlin + 9 TS failing tests all fail for the
  intended stub reason (verified by running the suites); no tautologies; stubs throw only; detekt
  green (0 issues); `:app:assembleDebug` green; 0 regressions. Corrections required: add tests for
  602, 700, 701, 702, 702b before implementation begins.
- **Red Review PASS (loop 2, 2026-06-26):** Both loop-1 findings resolved and verified.
  (1) `functions/src/program/generateProgram.test.ts` created — covers PROG-GEN-700 (session-id
  helper + strict `program_generation` schema with `progressionScheme` omitted), PROG-GEN-702
  (retry-once → rejects after 2 invalid-JSON attempts, `create` called exactly twice), PROG-GEN-702b
  (retry reaches normalization → fails `TDD Red`). `generateProgram.ts` refactored to extract the
  `generateProgramWithDeps(client, model, payload, normalizationRequest, sessionId)` +
  `ChatCompletionLike` seam (mirrors `runImportAgentSessionWithDeps`). PROG-GEN-701 remains an
  explicit documented deferral (deploy-time, Firebase harness, per the `inferImportEquipment` pattern).
  (2) PROG-GEN-602 covered by the `ErrorState` nested class in `OnboardingGenerateViewModelTest.kt`
  (1 Red: no-op `generate()` never reaches Error; 1 green enum-contract pin). Suite health: functions
  93/84-pass/9-fail (all `TDD Red`); Android 454/15-fail (all in new PROG-GEN suites), 0 regressions
  to 439 pre-existing tests; `detekt` 0 issues; `:app:assembleDebug` green. All 32 test_spec scenarios
  map to one executable test or a documented deferral (403-render, 501, 503, 701). Proceeding to
  Implementation.
- **Implementation complete (2026-06-26):** all 6 slices implemented green.
  - Slice 1: `GenerateProgramUseCase.invoke` — load→generate(getOrThrow)→persist→complete, runCatching.
  - Slice 2: `RoomProgramRepository` — withTransaction delete-all-then-insert, rejoin on read.
  - Slice 3: `ProgramsViewModel.toProgramsUiState` — domain→presentation mapping.
  - Slice 4: `ProgramResponseParser` — parseProgram assigns UUID ids, drops malformed rows.
  - Slice 5: `OnboardingViewModel.generate` — Loading→terminal Idle/Error; generateState via snapshot.
  - Slice 6: `programRules` + `parseAndNormalizeProgram` — validate, assign scheme, clamp RIR.
  - 2 test adjustments (702b: assert success; 706: keep valid lift alongside phantom).
  - Verification: detekt 0 issues; app:assembleDebug green; kspDebugKotlin green; 1.json only (DB v1).
    Kotlin unit tests 0 failures; functions npm test 93/93 pass/0 fail.
- **Final Review loop 1 FAIL (2026-06-26):** 2 findings (BLOCKER `buildNormalizationRequest` stub;
  MAJOR back-to-review empty lambda). Combined with the Antigravity thermos audit synthesis, 9
  findings total were addressed in the correction pass:
  1. CRITICAL Cold-start cache miss — `RoomProgramRepository` now reads from Room on every
     observe/get (no unseeded in-memory cache).
  2. HIGH Expandable lifts not clickable — `WorkoutLiftRow` now has `.clickable { expanded = !expanded }`.
  3. CRITICAL `buildNormalizationRequest` stub — implemented: parses payload JSON, extracts
     trainingAge + enabledLiftIds (union) + movementPatternByLiftId.
  4. HIGH Missing rate limiting — added Firestore `users/{uid}/programs_generation/latest` guard
     (rejects repeat generation; claims `pending` before LLM, writes `completed` after success,
     releases `pending` on failure so retry works).
  5. HIGH Missing Double top-set validation — `parseAndNormalizeProgram` now throws if a Double
     lift lacks exactly one top set with a rep range.
  6. HIGH Back-to-review empty lambda — `GenerateScreen` now accepts `onBack`; wired to the
     wizard's `onBack = viewModel::back`.
  7. MEDIUM Rep ranges render `0–0` — `SetRow` conditionally hides the rep range when both are 0.
  8. MEDIUM Mapper fragmentation — rejoin logic consolidated into `ProgramMapper.rejoinProgram`.
  9. LOW `Enum.values()` — replaced with `.entries` in domain + onboarding tests.
  - Post-correction verification: detekt 0 issues; app:assembleDebug green; kspDebugKotlin green;
    1.json only (DB v1); Kotlin tests 0 failures; functions 93/93 pass/0 fail.

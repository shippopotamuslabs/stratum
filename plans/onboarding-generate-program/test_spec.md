# Test Spec — Onboarding Generate Program (OS-7)

**Ticket:** `onboarding-generate-program` | **Phase:** Plan (Synthitect)
**Status:** awaiting human approval
**Grounded in:** `plans/onboarding-generate-program/spec.md`, `design.md` (D1–D4),
`structure.md`, `contracts/UNIT_TESTING.md`, `AGENTS.md` §4.4.

Gherkin scenarios with stable IDs (`PROG-GEN-NNN`). Every `Then` clause names a literal expected
value, observable state, enum case, or specific exception type and exact message. Expected values
are **not** derived from production code. Recorded-fixture scenarios describe the fixture shape
concretely.

---

## 1. Test framework and run commands

**Kotlin (Android/JVM):** JUnit 5 (`org.junit.jupiter.api`) + Turbine (`app.cash.turbine`) + fakes
from `:core:testing`; `@ExtendWith(MainDispatcherRule::class)` for ViewModel/StateFlow tests;
`@ExtendWith(RobolectricExtension::class)` + `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`
for Room DAO round-trips. No MockK on owned ports.

**TypeScript (functions):** Node built-in test runner (`node --test`); recorded OpenRouter response
fixtures under `functions/src/program/__fixtures__/`.

**Run commands (per slice, cumulative — copied from structure.md test-checkpoint table):**

| After slice | Command |
|-------------|---------|
| 1 | `./gradlew :core:domain:test --tests "*GenerateProgramUseCaseTest*"` |
| 2 | `./gradlew :core:data:testDebugUnitTest` ; `./gradlew :app:kspDebugKotlin` |
| 3 | `./gradlew :feature:program:testDebugUnitTest` ; `./gradlew :app:assembleDebug` ; `./gradlew detekt` |
| 4 | `./gradlew :core:inference:testDebugUnitTest` |
| 5 | `./gradlew :feature:onboarding:testDebugUnitTest` ; `./gradlew :app:assembleDebug` |
| 6 | `npm --prefix functions test` |

---

## 2. Scenario index

**Domain / use-case (Slice 1):** PROG-GEN-100, 101, 102.
**Persistence (Slice 2):** PROG-GEN-200, 201, 202, 203, 204, 301, 302.
**Programs screen (Slice 3):** PROG-GEN-400, 401, 402, 403.
**Inference client (Slice 4):** PROG-GEN-500, 501, 502, 503.
**Onboarding Generate step (Slice 5):** PROG-GEN-600, 601, 602, 603.
**Server callable (Slice 6):** PROG-GEN-700, 701, 702, 702b, 703, 704, 705, 706, 707.

Mutation-defense scenarios (called out explicitly): PROG-GEN-203, PROG-GEN-601, PROG-GEN-703.

---

## 3. Domain / use-case scenarios (Slice 1)

### PROG-GEN-100 — Generate happy path persists program and completes onboarding
```gherkin
Feature: GenerateProgramUseCase orchestration
  Scenario: PROG-GEN-100 happy path persists and completes
    Given a GenerateProgramUseCase wired with a FakeProgramGeneratorPort that returns a fixed Program P1
      And a FakeProgramRepository
      And a FakeOnboardingRepository with isComplete = false
      And a FakeUserTrainingProfileRepository seeded with a persisted profile
    When invoke() is called
    Then the result is Result.success(Unit)
      And FakeProgramRepository.current is P1
      And FakeProgramRepository.saveProgramCalls equals [P1]
      And FakeProgramGeneratorPort.invokedPayload is the JSON string from LoadProgramGenerationPayloadUseCase
      And FakeOnboardingRepository.isComplete is true
      And FakeUserTrainingProfileRepository.savedProfile equals the seeded persisted profile
```
Run: `./gradlew :core:domain:test --tests "*GenerateProgramUseCaseTest*"`.

### PROG-GEN-101 — Value-class IDs reject blank and enums are closed
```gherkin
Feature: Program domain identity and enums
  Scenario Outline: PROG-GEN-101 IDs reject blank and enums are closed
    Given the ProgramId, WorkoutId, WorkoutLiftId, and SetId value classes
    When constructed with value ""
    Then an IllegalArgumentException is thrown with message containing "cannot be blank"
    And the ProgressionScheme enum has exactly the values [Linear, Double]
    And the SetType enum has exactly the values [Standard, MyoRep, DropSet]
```
Run: `./gradlew :core:domain:test`.

### PROG-GEN-102 — LiftSet carries no load field
```gherkin
Feature: Program domain hierarchy
  Scenario: PROG-GEN-102 LiftSet has no weight/load field
    Given the LiftSet data class
    Then its declared properties are exactly { id, workoutLiftId, position, setType, repMin, repMax, rirGoal, isTopSet }
      And no property has a name containing "weight" or "load"
    And the WorkoutLift data class has property progressionScheme of type ProgressionScheme
    And the Workout data class has property weekday of type Weekday? (nullable)
```
Run: `./gradlew :core:domain:test`.

---

## 4. Persistence scenarios (Slice 2)

### PROG-GEN-200 — Repository getProgram returns null when empty
```gherkin
Feature: RoomProgramRepository reads
  Scenario: PROG-GEN-200 empty repo returns null
    Given a RoomProgramRepository backed by an in-memory StratumDatabase
    When getProgram() is called
    Then the result is null
    And observeProgram().first() is null
```

### PROG-GEN-201 — saveProgram round-trips the full hierarchy
```gherkin
Feature: RoomProgramRepository writes
  Scenario: PROG-GEN-201 full hierarchy round-trips
    Given a RoomProgramRepository backed by an in-memory StratumDatabase
      And a Program P1 with name "Push/Pull/Legs", createdAt = Instant.parse("2026-06-26T12:00:00Z")
      And P1 has 1 Workout W1 (name "Push Day", weekday = Weekday.Monday, position 0)
      And W1 has 1 WorkoutLift WL1 (liftId = LiftId("bench-press"), progressionScheme = ProgressionScheme.Linear, position 0)
      And WL1 has 2 LiftSets: S1 (position 0, setType = SetType.Standard, repMin 8, repMax 12, rirGoal 2, isTopSet = true)
        and S2 (position 1, setType = SetType.Standard, repMin 8, repMax 12, rirGoal 2, isTopSet = false)
    When saveProgram(P1) is called
    Then getProgram() returns a Program equal-by-value to P1
      And the returned Program.workouts[0].workoutLifts[0].sets has size 2
      And the returned sets[0].setType is SetType.Standard
      And the returned workouts[0].weekday is Weekday.Monday
      And the returned workoutLifts[0].progressionScheme is ProgressionScheme.Linear
```
Fixture: `ProgramDaoTest` + `RoomProgramRepositoryTest` (Robolectric).

### PROG-GEN-202 — Null weekday round-trips as null
```gherkin
Feature: RoomProgramRepository writes
  Scenario: PROG-GEN-202 unbound weekday persists as null
    Given a RoomProgramRepository backed by an in-memory StratumDatabase
      And a Program with a Workout whose weekday is null
    When saveProgram is called
    Then getProgram().workouts[0].weekday is null
```

### PROG-GEN-203 — MUTATION DEFENSE: saveProgram REPLACES not appends
```gherkin
Feature: RoomProgramRepository writes
  Scenario: PROG-GEN-203 second saveProgram replaces the entire program
    Given a RoomProgramRepository with P1 already saved (P1 has 3 workouts, 5 workoutLifts, 9 sets)
      And a second Program P2 with 1 workout, 1 workoutLift, 1 set
    When saveProgram(P2) is called
    Then getProgram() returns a Program equal-by-value to P2
      And the database contains exactly 1 row in the programs table
      And exactly 1 row in the workouts table
      And exactly 1 row in the workout_lifts table
      And exactly 1 row in the sets table
    # A lazy "append/insert without deleteAll" implementation would leave P1's rows behind
    # and fail the "exactly 1 row" assertions.
```

### PROG-GEN-204 — Use-case does not persist or complete on generation failure
```gherkin
Feature: GenerateProgramUseCase failure ordering
  Scenario: PROG-GEN-204 generation failure leaves no program and no completion
    Given a GenerateProgramUseCase wired with a FakeProgramGeneratorPort configured fail = true
      And a FakeProgramRepository
      And a FakeOnboardingRepository with isComplete = false
    When invoke() is called
    Then the result is Result.failure
      And FakeProgramRepository.saveProgramCalls is empty
      And FakeProgramRepository.current is null
      And FakeOnboardingRepository.isComplete is false
```
Run: `./gradlew :core:domain:test --tests "*GenerateProgramUseCaseTest*"`.

### PROG-GEN-301 — Schema stays at v1 with 4 new tables
```gherkin
Feature: StratumDatabase schema invariants
  Scenario: PROG-GEN-301 DB version is 1 and 4 program tables exist
    Given the StratumDatabase @Database annotation
    Then version is 1
      And exportSchema is true
      And the entities array includes ProgramEntity, WorkoutEntity, WorkoutLiftEntity, SetEntity
    And no class implementing androidx.room.migration.Migration is added in this slice
    And the exported schema file core/data/schemas/com.stratum.core.data.db.StratumDatabase/2.json does not exist
```

### PROG-GEN-302 — EnumConverters round-trip ProgressionScheme and SetType
```gherkin
Feature: EnumConverters additions
  Scenario Outline: PROG-GEN-302 enum converters round-trip
    Given the EnumConverters class
    Then fromProgressionScheme(ProgressionScheme.Linear) is "Linear"
      And toProgressionScheme("Linear") is ProgressionScheme.Linear
      And toProgressionScheme("Wave") is null
      And fromSetType(SetType.MyoRep) is "MyoRep"
      And toSetType("MyoRep") is SetType.MyoRep
      And toSetType("Drop") is null
      And fromProgramId(ProgramId("p-1")) is "p-1"
      And toProgramId("p-1") equals ProgramId("p-1")
```
Run: `./gradlew :core:data:testDebugUnitTest`.

---

## 5. Programs screen scenarios (Slice 3)

### PROG-GEN-400 — ProgramsViewModel maps empty repo to Loaded(empty) state
```gherkin
Feature: ProgramsViewModel
  Scenario: PROG-GEN-400 empty state
    Given a ProgramsViewModel wired with a FakeProgramRepository emitting null
    When programsUiState is collected
    Then the first emitted state is ProgramsUiState with status = Empty
      And the program view state is null
```

### PROG-GEN-401 — ProgramsViewModel maps populated repo to Loaded state
```gherkin
Feature: ProgramsViewModel
  Scenario: PROG-GEN-401 populated state maps domain to presentation
    Given a ProgramsViewModel wired with a FakeProgramRepository
      And the repo emits a Program P1 (name "Push/Pull/Legs") with a Workout "Push Day" containing
        a WorkoutLift (liftId "bench-press", progressionScheme Linear) with 2 sets
    When programsUiState is collected after the repo emits P1
    Then the emitted state has status = Loaded
      And the program view state name is "Push/Pull/Legs"
      And the workout view states has size 1 with first name "Push Day"
      And the first workout's workoutLift view states has size 1
      And the presentation model does NOT expose any domain type (Program/Workout/WorkoutLift/LiftSet)
        as a public property of ProgramsUiState
```

### PROG-GEN-402 — WorkoutLift expansion toggles a presentation flag
```gherkin
Feature: ProgramsViewModel
  Scenario: PROG-GEN-402 expand a workout lift reveals its sets
    Given a ProgramsViewModel in the Loaded state of PROG-GEN-401
    When the user toggles expansion of the first WorkoutLift
    Then that WorkoutLift view state isExpanded becomes true
      And its sets view states has size 2
      And each set view state exposes repMin, repMax, rirGoal, and setType
      And no edit/swap/regenerate affordance is present in the view state
```

### PROG-GEN-403 — Completion routes to ProgramsKey, not HomeKey
```gherkin
Feature: StratumNavHost routing
  Scenario: PROG-GEN-403 post-onboarding start key is ProgramsKey
    Given the StratumNavHost entryProvider
    Then it registers programsEntry
    And the onboardingEntry onComplete callback removes OnboardingKey and adds ProgramsKey
    And when OnboardingStatusViewModel.isComplete is true the startKey is ProgramsKey (not HomeKey)
```
Run: `./gradlew :feature:program:testDebugUnitTest`; `./gradlew :app:assembleDebug`; `./gradlew detekt`.

---

## 6. Inference client scenarios (Slice 4)

Recorded-fixture shape for PROG-GEN-500/501/502/503: the test feeds a captured Callable response
`Map` (i.e. `result.data`) shaped exactly as the server's `{ program: NormalizedProgram }` return,
where `NormalizedProgram` has `{ name: string, workouts: [{ name, weekday: string|null, position: int,
workoutLifts: [{ liftId: string, position: int, workoutLiftSets: [{ position: int, setType: "Standard"|"MyoRep"|"DropSet", repMin: int, repMax: int, rirGoal: int, isTopSet: boolean }] }] }] }`.
Note: the normalized program contains NO `progressionScheme` field and NO ids (the client assigns
ids). A concrete fixture used across scenarios:

```
{
  "program": {
    "name": "Upper/Lower",
    "workouts": [
      {
        "name": "Upper A",
        "weekday": "Monday",
        "position": 0,
        "workoutLifts": [
          {
            "liftId": "bench-press",
            "position": 0,
            "workoutLiftSets": [
              { "position": 0, "setType": "Standard", "repMin": 8, "repMax": 12, "rirGoal": 2, "isTopSet": true },
              { "position": 1, "setType": "Standard", "repMin": 8, "repMax": 12, "rirGoal": 1, "isTopSet": false }
            ]
          },
          {
            "liftId": "lat-pulldown",
            "position": 1,
            "workoutLiftSets": [
              { "position": 0, "setType": "Standard", "repMin": 10, "repMax": 15, "rirGoal": 2, "isTopSet": true }
            ]
          }
        ]
      }
    ]
  }
}
```

### PROG-GEN-500 — parseProgram assigns UUID value-class IDs at parse time
```gherkin
Feature: FirebaseProgramGenerator.parseProgram
  Scenario: PROG-GEN-500 IDs are assigned client-side and are non-blank
    Given the recorded fixture response Map above
    When parseProgram(fixtureData) is called
    Then the returned Program.id is a ProgramId with a non-blank value
      And every Workout.id, WorkoutLift.id, LiftSet.id is non-blank
      And Program.workouts[0].programId equals Program.id
      And WorkoutLift.workoutId equals its parent Workout.id
      And LiftSet.workoutLiftId equals its parent WorkoutLift.id
      And the parsed Program has 1 workout, 2 workoutLifts, and 3 sets total
```

### PROG-GEN-501 — generateProgram success returns the parsed Program
```gherkin
Feature: FirebaseProgramGenerator.generateProgram
  Scenario: PROG-GEN-501 happy path
    Given a FirebaseProgramGenerator with FirebaseFunctions returning the recorded fixture response
      And FirebaseAuth with a current user
    When generateProgram("{}") is called (any payload string)
    Then the result is Result.success
      And the returned Program.name is "Upper/Lower"
      And the callable was invoked as functions.getHttpsCallable("generateProgram").call(mapOf("payload" to "{}"))
```

### PROG-GEN-502 — Unknown weekday maps to null
```gherkin
Feature: FirebaseProgramGenerator.parseProgram
  Scenario: PROG-GEN-502 unknown weekday string maps to null
    Given a recorded fixture identical to the shared one but with workouts[0].weekday = "Moonday"
    When parseProgram(fixtureData) is called
    Then the returned Program.workouts[0].weekday is null
```

### PROG-GEN-503 — Unauthenticated returns Result.failure
```gherkin
Feature: FirebaseProgramGenerator auth gate
  Scenario: PROG-GEN-503 no current user fails before the callable is invoked
    Given a FirebaseProgramGenerator with FirebaseAuth.currentUser = null
    When generateProgram("{}") is called
    Then the result is Result.failure
      And the cause is an IllegalStateException with message "Sign-in required for program generation."
      And the FirebaseFunctions callable was never invoked
```
Run: `./gradlew :core:inference:testDebugUnitTest`.

---

## 7. Onboarding Generate step scenarios (Slice 5)

### PROG-GEN-600 — Tapping Generate emits Loading then completes
```gherkin
Feature: OnboardingViewModel generate flow
  Scenario: PROG-GEN-600 generate success
    Given an OnboardingViewModel on the Generate step
      And FakeProgramGeneratorPort configured to return a Program
      And FakeOnboardingRepository with isComplete = false
    When generate() is called
    Then uiState emits a state with generateState = Loading
      And subsequently the wizard state's isComplete becomes true
      And FakeProgramRepository.current is the returned Program
      And the Generate CTA is disabled while generateState = Loading
```

### PROG-GEN-601 — MUTATION DEFENSE: onboarding NOT complete on generation failure
```gherkin
Feature: OnboardingViewModel generate failure
  Scenario: PROG-GEN-601 generation failure does not complete onboarding
    Given an OnboardingViewModel on the Generate step
      And FakeProgramGeneratorPort configured fail = true
      And FakeOnboardingRepository with isComplete = false
    When generate() is called
    Then uiState emits generateState = Loading followed by generateState = Error
      And isComplete remains false
      And FakeProgramRepository.current is null
      And FakeOnboardingRepository.isComplete is false
    # A lazy implementation that calls completeOnboarding before the generate result is known
    # would set isComplete = true and fail this scenario.
```

### PROG-GEN-602 — Error state offers retry and back-to-review
```gherkin
Feature: GenerateScreen error UI
  Scenario: PROG-GEN-602 error state surface
    Given the OnboardingViewModel in generateState = Error
    Then the GenerateScreen renders a retry affordance (label onboarding_generate_error_retry)
      And a back-to-review affordance (label onboarding_generate_error_back_to_review)
    When the retry affordance is tapped
    Then viewModel.generate() is invoked again
    When the back-to-review affordance is tapped
    Then viewModel.back() is invoked
```

### PROG-GEN-603 — Loading shows "Building your program…" and disables back-to-review
```gherkin
Feature: GenerateScreen loading UI
  Scenario: PROG-GEN-603 loading copy and control lock
    Given the OnboardingViewModel in generateState = Loading
    Then the GenerateScreen renders the loading copy "Building your program…"
      And the Generate CTA is disabled
      And the back-to-review affordance is not shown during Loading
```
Run: `./gradlew :feature:onboarding:testDebugUnitTest`; `./gradlew :app:assembleDebug`.

---

## 8. Server callable scenarios (Slice 6)

Recorded-fixture shape for the post-pass tests: a raw model `Program` JSON (pre-normalization),
shaped per `programJsonSchema()` (no `progressionScheme`, with `workoutLiftSets`). Two fixtures:

Fixture A (Beginner, mixed patterns) — raw model output before normalization:
```
{
  "program": {
    "name": "Full Body",
    "workouts": [{
      "name": "Session 1", "weekday": "Monday", "position": 0,
      "workoutLifts": [
        { "liftId": "back-squat", "position": 0, "workoutLiftSets": [
            { "position": 0, "setType": "Standard", "repMin": 5, "repMax": 5, "rirGoal": 9, "isTopSet": true }
        ]},
        { "liftId": "bicep-curl", "position": 1, "workoutLiftSets": [
            { "position": 0, "setType": "Standard", "repMin": 12, "repMax": 15, "rirGoal": 3, "isTopSet": true }
        ]}
      ]
    }]
  }
}
```
Associated request context: `{ trainingAge: "Beginner", enabledLiftIds: ["back-squat","bicep-curl"],
movementPatternByLiftId: { "back-squat": "LegPush", "bicep-curl": "BicepIso" } }`.

Fixture B (Intermediate, Double, back-off RIR out of range) — raw model output:
```
{
  "program": {
    "name": "PPL",
    "workouts": [{
      "name": "Pull", "weekday": "Monday", "position": 0,
      "workoutLifts": [
        { "liftId": "deadlift", "position": 0, "workoutLiftSets": [
            { "position": 0, "setType": "Standard", "repMin": 3, "repMax": 5, "rirGoal": 7, "isTopSet": true },
            { "position": 1, "setType": "Standard", "repMin": 3, "repMax": 5, "rirGoal": 5, "isTopSet": false }
        ]}
      ]
    }]
  }
}
```
Associated request context: `{ trainingAge: "Intermediate", enabledLiftIds: ["deadlift"],
movementPatternByLiftId: { "deadlift": "HipHinge" } }`.

### PROG-GEN-700 — Callable shape mirrors inferImportEquipment
```gherkin
Feature: generateProgram callable wiring
  Scenario: PROG-GEN-700 callable contract
    Given the generateProgram export
    Then it is registered with onCall({ secrets: ["OPENROUTER_API_KEY","OPENROUTER_MODEL"] })
      And it reads process.env.OPENROUTER_API_KEY (throwing HttpsError "failed-precondition" with message "OPENROUTER_API_KEY missing." when absent)
      And the model defaults to "deepseek/deepseek-v4-pro" when OPENROUTER_MODEL is unset
      And it builds the OpenAI client via createOpenRouterClient(apiKey, programGenerationSessionId(uid))
      And it calls buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true })
      And it uses response_format { type: "json_schema", json_schema: programJsonSchema() }
      And functions/src/index.ts re-exports generateProgram
```

### PROG-GEN-701 — Unauthenticated request rejected
```gherkin
Feature: generateProgram auth
  Scenario: PROG-GEN-701 no auth throws unauthenticated
    Given a generateProgram invocation with request.auth = undefined
    When the handler runs
    Then it throws an HttpsError with code "unauthenticated" and message "Sign-in required."
```

### PROG-GEN-702 — Retry once on schema failures, fail on second
```gherkin
Feature: generateProgram retry-once
  Scenario: PROG-GEN-702 retry-once then internal
    Given a generateProgram invocation where the first OpenRouter completion returns invalid JSON
      And the second OpenRouter completion also returns invalid JSON
    When the handler runs
    Then chat.completions.create is called exactly 2 times
      And the handler throws an HttpsError with code "internal"
  Scenario: PROG-GEN-702b retry succeeds
    Given a generateProgram invocation where the first completion returns invalid JSON
      And the second completion returns Fixture A (valid, parseable)
    When the handler runs
    Then chat.completions.create is called exactly 2 times
      And the handler returns { program: <normalized Fixture A> }
```

### PROG-GEN-703 — MUTATION DEFENSE: progressionScheme assigned from TrainingAge, not model output
```gherkin
Feature: parseAndNormalizeProgram scheme assignment
  Scenario: PROG-GEN-703 scheme is overwritten from trainingAge
    Given Fixture A parsed with trainingAge = "Beginner"
    When parseAndNormalizeProgram is called
    Then every workoutLift.progressionScheme is "Linear"
    And given the same Fixture A parsed with trainingAge = "Intermediate"
    Then every workoutLift.progressionScheme is "Double"
    # A lazy implementation that trusts a model-supplied progressionScheme would fail because
    # the schema has no progressionScheme field at all; any implementation that reads one from
    # the model output (e.g. by extending the schema) would diverge from the Beginner=Linear /
    # Intermediate=Double expectation here.
```

### PROG-GEN-704 — Back-off RIR clamped to §2.3 lift-class table; top-set RIR = 2
```gherkin
Feature: parseAndNormalizeProgram RIR clamping
  Scenario: PROG-GEN-704 major-compound back-off clamped, top-set fixed
    Given Fixture B parsed with trainingAge = "Intermediate" (so scheme = Double)
      And movementPatternByLiftId["deadlift"] = "HipHinge" (major compound)
    When parseAndNormalizeProgram is called
    Then the top set (isTopSet = true) rirGoal is 2
      And the back-off set (isTopSet = false) rirGoal is in [1, 2]  # proposed 5 clamped to range; default 2 when out of range
      And clampBackOffRir(true, 5) returns 2   # out of range → default 2
      And clampBackOffRir(true, 1) returns 1   # in range, preserved
      And clampBackOffRir(false, 3) returns 0  # non-compound → 0
      And clampBackOffRir(true, 2) returns 2   # in range, preserved
```

### PROG-GEN-705 — Major-compound pattern set is exactly the §2.3 list
```gherkin
Feature: programRules major-compound classification
  Scenario: PROG-GEN-705 MAJOR_COMPOUND_PATTERNS is the exact §2.3 list
    Given programRules.MAJOR_COMPOUND_PATTERNS
    Then it equals the set { "LegPush", "HipHinge", "HorizontalPush", "VerticalPush", "InclinePush", "HorizontalPull", "VerticalPull" }
      And schemeForTrainingAge("Beginner") returns "Linear"
      And schemeForTrainingAge("EarlyIntermediate") returns "Double"
      And schemeForTrainingAge("Intermediate") returns "Double"
      And schemeForTrainingAge("LateIntermediate") returns "Double"
      And schemeForTrainingAge("Advanced") returns "Double"
      And TOP_SET_RIR is 2
```

### PROG-GEN-706 — Unknown lift id rejected
```gherkin
Feature: parseAndNormalizeProgram lift membership validation
  Scenario: PROG-GEN-706 lift id not in enabledLiftIds is dropped
    Given a fixture with a workoutLift whose liftId = "phantom-lift"
      And enabledLiftIds = ["bench-press"]
    When parseAndNormalizeProgram is called
    Then that workoutLift is dropped from the normalized program
      And if the drop leaves the workout with zero valid workoutLifts, the workout is dropped
```

### PROG-GEN-707 — Empty program after validation rejected
```gherkin
Feature: parseAndNormalizeProgram empty program rejection
  Scenario: PROG-GEN-707 zero workouts throws
    Given a fixture whose only workout is dropped by PROG-GEN-706 (all lifts unknown)
    When parseAndNormalizeProgram is called
    Then it throws an Error (the callable maps this to HttpsError "invalid-argument")
```
Run: `npm --prefix functions test`.

---

## 9. Self-review

- Every `Then` names a literal value, observable state, enum case, or specific exception type +
  exact message. PASS.
- Mutation-defense scenarios that fail against the named lazy implementations:
  - PROG-GEN-203 (saveProgram appends vs replaces) — fails if no `deleteAll` in the transaction.
  - PROG-GEN-601 (onboarding complete on failure) — fails if `completeOnboarding` runs before the
    generate result.
  - PROG-GEN-703 (scheme from TrainingAge) — fails if the model's scheme is trusted.
  PASS.
- Edge/error paths covered: generation failure (204, 601), unknown lift id (706), retry-once on
  schema failure (702), empty program rejected (707), unauthenticated (503, 701), unknown weekday
  (502). PASS.
- Names/signatures match spec.md (`GenerateProgramUseCase`, `ProgramGeneratorPort`,
  `ProgramRepository`, `FirebaseProgramGenerator.parseProgram`, `parseAndNormalizeProgram`,
  `schemeForTrainingAge`, `clampBackOffRir`, `MAJOR_COMPOUND_PATTERNS`, `TOP_SET_RIR`,
  `programGenerationSessionId`, `ProgramsKey`, `ProgramsViewModel`). PASS.
- Recorded-fixture shapes are concrete (no recorded bytes required, but fields are specified).
  PASS.
- DB stays v1 (PROG-GEN-301 asserts version = 1 and no `2.json`). PASS.
- No placeholders. PASS.

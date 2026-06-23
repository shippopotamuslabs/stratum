# Unit Testing Contract — Stratum

Binding standards for unit and JVM integration tests. Read this contract before writing any test.
Golden reference implementations live in `golden-test-examples/` (Now in Android — sync via
`./scripts/golden-test-examples-download.sh`).

Never modify this file without an explicit **"Contract Waiver"** from the user.

---

## 1. Core Principles (ALWAYS / NEVER)

| Rule | Description |
| :--- | :--- |
| **ALWAYS** Prefer Fakes | Use lightweight in-memory implementations of Stratum ports and repositories (in `:core:testing` or module-local `Fake*`) instead of programming mock expectations. |
| **ALWAYS** JUnit 5 | Use `org.junit.jupiter.api` (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`). |
| **ALWAYS** State-Based Assertions | Assert on *results* (StateFlow value, returned domain object, DB row) — not *process* ("was this method called"). |
| **ALWAYS** Use Turbine | Test Kotlin `Flow` / `StateFlow` event-by-event with Turbine. |
| **ALWAYS** MainDispatcherRule | Use `com.stratum.core.testing.MainDispatcherRule` (`@ExtendWith`) to swap `Dispatchers.Main` for a `TestDispatcher`. |
| **ALWAYS** Copy Golden Patterns | Before inventing a test structure, open the matching file in `golden-test-examples/` (see §4). |
| **NEVER** Mock Android Framework | Do not mock `Context`, `SharedPreferences`, or `Bundle`. Use Robolectric or fakes. |
| **NEVER** Mock Owned Ports | Do not `mockk` Stratum repository or use-case ports defined in `:core:domain`. Use fakes from `:core:testing`. |
| **NEVER** Use JUnit 4 in New Tests | Avoid `@Rule`, `@Before`, `@RunWith` in new tests. |
| **NEVER** `verify()` ViewModels | Do not use MockK `verify` / `coVerify` in `*ViewModelTest.kt` — assert UI state instead (Detekt enforced). |
| **NEVER** Await IO with `delay()` Polls | Inside `runTest`, `delay(N)` does not wait for real `Dispatchers.IO` work. Inject a `TestDispatcher` or `join()` background jobs (see §3.3). |

---

## 2. Technical Stack

| Tool | When |
|------|------|
| **JUnit 5** | All local unit tests (`src/test`) |
| **Turbine** | All Flow / StateFlow assertions |
| **Robolectric** | Room DAO, DataStore, Context-dependent code on JVM (`testDebugUnitTest`) |
| **StandardTestDispatcher** | Coroutine tests with explicit `advanceUntilIdle()` |
| **MockK** | Only when a Fake is impractical (third-party SDK, static Android APIs). Requires `@file:Suppress("ForbiddenMockKInTests")` with a one-line justification comment. |
| **`:core:testing`** | Shared fakes, `MainDispatcherRule`, fixtures |

### Test pyramid by module

| Module | Runner | Doubles |
|--------|--------|---------|
| `:core:domain` | JVM (`test`) | Fakes implementing domain ports |
| `:core:data` | Robolectric (`testDebugUnitTest`) | In-memory Room, real DAOs/mappers |
| `:feature:*` ViewModels | JVM + `MainDispatcherRule` | Fakes from `:core:testing` |
| Compose screens | Robolectric + `createComposeRule` (sparse) | Fake ViewModel state |
| `:app` E2E | `@HiltAndroidTest` on device (rare) | Hilt `@BindValue` → fakes |

---

## 3. Pattern Examples

### 3.1 ViewModel (JUnit 5 + Turbine + Fake)

```kotlin
@ExtendWith(MainDispatcherRule::class)
class WorkoutViewModelTest {

    private val fakeRepository = FakeWorkoutRepository()
    private lateinit var viewModel: WorkoutViewModel

    @BeforeEach
    fun setup() {
        viewModel = WorkoutViewModel(fakeRepository)
    }

    @Test
    @DisplayName("Given empty history when session logged then state lists the session")
    fun logSessionUpdatesState() = runTest {
        viewModel.uiState.test {
            assertEquals(WorkoutUiState.Empty, awaitItem())

            viewModel.logSession(session = WorkoutFixtures.completedSession)

            val success = awaitItem() as WorkoutUiState.Success
            assertEquals(1, success.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### 3.2 Fakes over Mocks

**Wrong (brittle — reward hacking):**

```kotlin
val mockRepo = mockk<WorkoutRepository>()
coEvery { mockRepo.observeSessions() } returns flowOf(listOf(session))
```

**Right (robust fake):**

```kotlin
class FakeWorkoutRepository : WorkoutRepository {
    private val sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    override fun observeSessions(): Flow<List<WorkoutSession>> = sessions
    fun seed(list: List<WorkoutSession>) { sessions.value = list }
}
```

Place reusable fakes in `:core:testing`. Module-local fakes are OK when used in one feature only.

### 3.3 Awaiting fire-and-forget persistence

Repositories that launch `scope.launch(Dispatchers.IO) { dao.insert(...) }` cannot be awaited
with `delay()` inside `runTest` — virtual time does not wait for real IO threads.

**Preferred:** inject the IO `CoroutineDispatcher` and pass `StandardTestDispatcher()` in tests,
then `advanceUntilIdle()`.

**Alternative:** expose test hooks to `join()` background jobs before asserting cache **and** DB state.

### 3.4 Self-check (anti reward-hacking)

After writing a test, break the production code under test (wrong return value, skip persistence).
The test **must fail**. If it still passes, the test is invalid — rewrite before marking work complete.

---

## 4. Golden Test References (Now in Android)

Run `./scripts/golden-test-examples-download.sh` after clone to populate `golden-test-examples/`.
These files are **read-only references** — adapt patterns to Stratum packages and `Stratum*` UI,
never copy-paste imports from NIA into production modules.

| Pattern | Golden file |
|---------|-------------|
| Repository + in-memory fakes | `golden-test-examples/data/OfflineFirstUserDataRepositoryTest.kt` |
| ViewModel + Turbine + fakes | `golden-test-examples/settings/logic_test.kt` |
| Robolectric / Android logic | `golden-test-examples/foryou/logic_test.kt` |
| Compose UI (instrumented reference) | `golden-test-examples/settings/ui_instrumented.kt` |
| Coroutine Main dispatcher utility | `golden-test-examples/utils/MainDispatcherRule.kt` (Stratum uses `:core:testing` copy) |
| Additional ViewModel examples | `golden-test-examples/{bookmarks,search,topic,interests}/logic_test.kt` |
| Additional Compose UI examples | `golden-test-examples/{bookmarks,search,topic,interests,foryou}/ui_instrumented.kt` |

---

## 5. Implementation Mandate

Any agent creating or modifying production logic **must**:

1. Read this contract and the relevant golden example.
2. Add or extend tests in the module's `src/test/kotlin` (or `src/androidTest` when device-only).
3. Run the narrowest test task during iteration, e.g.  
   `./gradlew :core:domain:test --tests "*LogSetUseCaseTest*"`  
   then `./gradlew testDebugUnitTest` before completion.
4. Ensure `./gradlew detekt` passes (includes `ForbiddenMockKInTests` and `ForbiddenVerifyInViewModelTests`).

---

## 6. Adding New Fakes

When a new domain port is introduced:

1. Add `Fake<PortName>` to `:core:testing` (or feature-local if single-use).
2. Fake must compile against the port and behave plausibly (not return hard-coded nulls).
3. Seed/toggle methods for tests (`seed`, `setShouldFail`, `clear`) — not MockK DSL.

See `ARCHITECTURE.md` §1.1 for `:core:testing` placement in the module graph.

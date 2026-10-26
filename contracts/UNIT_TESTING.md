# Unit Testing Contract — Stratum

Binding standards for unit and JVM integration tests. Read this contract before writing any test.
Golden reference implementations live in `golden-test-examples/` (Now in Android — sync via
`./scripts/golden-test-examples-download.sh`).

Never modify this file without an explicit **"Contract Waiver"** from the user.

**Canon:** The shared Shippopotamus testing standards live in
`contracts/gospel/TESTING-GOSPEL.md` (gospels submodule). This contract
adopts them for shared standards; the Stratum text below governs wherever it
differs or adds Stratum specifics. Canon pointers: the test pyramid is
`TESTING-GOSPEL.md` §1 and the level-escalation decision test is
`TESTING-GOSPEL.md` §2. The Android-specific rules formerly at §§1–5 of the
renamed gospel are re-homed at `TESTING-GOSPEL.md` §6: §6.1 core principles,
§6.2 technical stack, §6.3 patterns (§6.3.1 ViewModel, §6.3.2 fakes over
mocks, §6.3.3 awaiting fire-and-forget persistence), §6.4 golden test
references, and §6.5 implementation mandate (§6.5.1 self-check).

---

## 1. Core Principles (ALWAYS / NEVER)

The core principles are canon: see `contracts/gospel/TESTING-GOSPEL.md` §6.1
(core principles, ALWAYS/NEVER) and §4 (test doubles rules).

Stratum names preserved for code and Detekt citations of this section:
`com.stratum.core.testing.MainDispatcherRule` (in `:core:testing`), and the
Detekt rules `ForbiddenMockKInTests` and `ForbiddenVerifyInViewModelTests`.
`Repository` names an infrastructure implementation class only; outbound ports
are application `*Port` interfaces, and reusable fakes live in `:core:testing`.

---

## 2. Technical Stack

The technical stack is canon: see `contracts/gospel/TESTING-GOSPEL.md` §6.2.
Stratum's module-by-module instantiation follows.

### Test pyramid by module

| Module | Runner | Doubles |
|--------|--------|---------|
| `:core:domain` | JVM (`test`) | Fakes implementing owned `*Port` interfaces |
| `:core:data` (legacy name — in canon a persistence package inside `:core:infrastructure`) | Robolectric (`testDebugUnitTest`) | In-memory Room, real DAOs/mappers |
| `:feature:*` ViewModels | JVM + `MainDispatcherRule` | Fakes from `:core:testing` |
| Compose screens | Robolectric + `createComposeRule` (sparse) | Fake ViewModel state |
| `:app` E2E | `@HiltAndroidTest` on device (rare) | Hilt `@BindValue` → fakes |

---

## 3. Pattern Examples

### 3.1 ViewModel (JUnit 5 + Turbine + Fake)

```kotlin
@ExtendWith(MainDispatcherRule::class)
class WorkoutViewModelTest {

    private val fakePort = FakeWorkoutPort()
    private lateinit var viewModel: WorkoutViewModel

    @BeforeEach
    fun setup() {
        fakePort.seed(emptyList())
        viewModel = WorkoutViewModel(
            observeSessions = ObserveSessionsUseCase(fakePort),
            logSession = LogSessionUseCase(fakePort),
        )
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
val mockPort = mockk<WorkoutPort>()
coEvery { mockPort.observeSessions() } returns flowOf(listOf(session))
```

**Right (robust fake):**

```kotlin
class FakeWorkoutPort : WorkoutPort {
    private val sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    override fun observeSessions(): Flow<List<WorkoutSession>> = sessions
    fun seed(list: List<WorkoutSession>) { sessions.value = list }
}
```

Place reusable fakes in `:core:testing`. Module-local fakes are OK when used in one feature only.

### 3.3 Awaiting fire-and-forget persistence

Infrastructure repository implementations that launch `scope.launch(Dispatchers.IO) { dao.insert(...) }` cannot be awaited
with `delay()` inside `runTest` — virtual time does not wait for real IO threads.

**Preferred:** inject the IO `CoroutineDispatcher` and pass `StandardTestDispatcher()` in tests,
then `advanceUntilIdle()`.

**Alternative:** expose test hooks to `join()` background jobs before asserting cache **and** DB state.

### 3.4 Self-check (anti reward-hacking)

See `contracts/gospel/TESTING-GOSPEL.md` §6.5.1 (self-check, anti reward-hacking).

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

The implementation mandate is canon: see `contracts/gospel/TESTING-GOSPEL.md`
§6.5 and `contracts/gospel/CODE-STYLE-GOSPEL.md` §8.1 (test coverage discipline).

Stratum-local enforcement: run the narrowest test task during iteration, e.g.
`./gradlew :core:domain:test --tests "*LogSetUseCaseTest*"`, then
`./gradlew testDebugUnitTest` before completion, and ensure
`./gradlew detekt` passes (includes `ForbiddenMockKInTests` and
`ForbiddenVerifyInViewModelTests`).

---

## 6. Adding New Fakes

When a new application outbound port (`*Port`) is introduced:

1. Add `Fake<PortName>` to `:core:testing` (or feature-local if single-use).
2. Fake must compile against the port and behave plausibly (not return hard-coded nulls).
3. Seed/toggle methods for tests (`seed`, `setShouldFail`, `clear`) — not MockK DSL.

See `ARCHITECTURE.md` §1.1 for `:core:testing` placement in the module graph.

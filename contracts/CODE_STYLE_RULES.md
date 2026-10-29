# Code Style Rules — Stratum

Binding conventions for Kotlin, Gradle, and cross-layer patterns. Read alongside
`ARCHITECTURE.md` and `UNIT_TESTING.md`.

Never modify this file without an explicit **"Contract Waiver"** from the user.

**Canon:** The shared Shippopotamus code style standards live in
`contracts/gospel/CODE-STYLE-GOSPEL.md` (gospels submodule). This contract adopts
them for shared standards; the Stratum text below governs wherever it differs or
adds Stratum specifics. General style is the gospel; this file carries Stratum
examples and enforcement only (see CODE-STYLE-GOSPEL.md §Local material).

---

## 1. Error handling

Stratum uses **containment below, UI reaction above** — not try/catch/rethrow chains.

### Layer responsibilities

| Layer | Pattern | Logging |
|-------|---------|---------|
| **Infrastructure adapter** (Firebase, credentials, Room-backed auth) | `runCatching { … }.onFailure { log; Result.failure(mapped) }` | `LoggingPort.error` once, with tag + context |
| **Use case (orchestration)** | `runCatching` or propagate `Result`; return `Result<T>`, sealed types, or `Flow<Event>` | Only when adding context the adapter lacks (avoid duplicate logs) |
| **Repository implementation** (infrastructure) | Plain `suspend` / `Flow`; let exceptions propagate to the caller's `runCatching` | No logging |
| **ViewModel** | Branch on `Result` / sealed type / Flow event → `UiState` | No logging; map to user-visible strings |

### Return types

- **Operational failures** (network, I/O, unexpected exceptions): `Result<T>` from ports and orchestrating use cases.
- **Expected business outcomes** (validation): sealed interfaces (e.g. `AdvanceOnboardingStepResult.ValidationFailed`).
- **Long-running multi-step work**: `Flow` of terminal events (e.g. `HistoricalImportEvent.Failed`).

ViewModels must **not** rely on try/catch for business failures. Use `result.onFailure { … }` or
`if (result.isFailure)`. Reserve try/catch for `CancellationException` and similar coroutine
machinery.

### User-visible messages

Map infrastructure errors to readable copy **at the adapter** (e.g. `ProgramGenerationErrorMapper`,
`toGoogleSignInMessage()` in `:core:ui`). ViewModels copy `exception.message` into presentation state when the
lower layer already attached a safe string; otherwise use a feature-level fallback constant aligned
with `strings.xml`.

### LoggingPort

- Single outbound application port: `com.stratum.domain.logging.LoggingPort` (`*Port` interface; in canon outbound ports live in the application layer — there are no domain ports).
- Production adapter: `AndroidLoggingAdapter` in `:core:inference` — Logcat + Firebase Crashlytics
  non-fatals on `error(…, throwable)` (PII scrubbed via `TelemetryPiiScrubber` before breadcrumbs).
- Future OpenTelemetry export should compose through the same port (composite adapter or extended
  `AndroidLoggingAdapter`), mirroring pocket-crew.
- Tests use `FakeLoggingPort` from `:core:testing`.

### Partial success: diagnostics as values

Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §10 (Partial Success: Diagnostics as Values). In Stratum terms: a computation that degrades without failing returns typed diagnostics on its outcome inside the use case's `Result`; the boundary drains them through the wiring above (`LoggingPort` / `LogErrorUseCase`), each exactly once; diagnostics never reach the UI.

**Reference implementation (STR-250).** `ProgressionDiagnostic` (`:core:domain`, `workout/progression/ProgressionDiagnostic.kt`): `BaselineSkipped` records the lift, the slot, the offending history input, and which load-math step lost its baseline. `GenerateProgressionSnapshotUseCase` (`:core:application`) collects diagnostics through the computation and returns them on `ProgressionSnapshotOutcome` inside its `Result`. `LiveWorkoutSessionRuntime.logProgressionDiagnostics` (`:feature:workout`) drains them through the session's `LogErrorUseCase` wiring. `ProgressionZeroLoadHistoryTest` asserts the diagnostics as values.

### Anti-patterns

- Logging the same failure at every layer.
- Rethrowing through repositories/use cases for the ViewModel to catch.
- Passing raw `Throwable` or domain exceptions to Composables.
- Swallowing failures without `Result`, event, or UI state.
- Degrading silently: skipping, dropping, or defaulting without a diagnostic, a `Result`, or modeled state.

---

## 2. Imports

Keep imports at the top of the file. No inline imports unless a documented circular-dependency
exception exists.

---

## 3. Related contracts

| Topic | Contract |
|-------|----------|
| Module graph, ViewModel deps | `ARCHITECTURE.md` |
| Tests, fakes, MockK boundaries | `UNIT_TESTING.md` |
| Compose UI | `UI_DESIGN.md` |

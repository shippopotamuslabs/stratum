# Background program generation + notifications

**Status:** Planned  
**Branch suggestion:** `feat/program-generation-background`  
**Depends on:** existing `GenerateProgramUseCase`, workout FGS/notification patterns  
**Out of scope (v1):** server-side CP-SAT, WorkManager reboot recovery of mid-solve state, multi-device jobs

---

## 1. Problem

Program generation runs 2–3 minutes (CP-SAT, `FEASIBLE` at 120s wall). Today it is hosted on
`viewModelScope` in:

- `CreateProgramViewModel.generate()`
- `OnboardingViewModel.generate()`

Leaving the screen / process pressure can cancel the job or strand the user staring at a
timeline. Users should be able to leave the app and get notified when the program is ready.

---

## 2. Goals

1. Generation survives leaving the Compose route and app backgrounding.
2. Ongoing notification while solving (“Building your program…”, 2–3 min expectation).
3. Terminal notification: success (tap → review) or failure (tap → error / retry).
4. In-app timeline remains the live view when the user stays on the Generate screen.
5. Single shared job for **Create Program** and **Onboarding** (one flight at a time).

### Non-goals (v1)

- Resuming a killed mid-solve CP-SAT run (restart from scratch on death).
- Progress % in the system notification (phases optional later).
- Changing solver deadline / warm-start defaults.

---

## 3. Platform choice

| Option | Fit |
|--------|-----|
| Bare `viewModelScope` | **No** — dies with UI / cancellation |
| WorkManager only | **Weak** — multi-minute CPU work is throttled; needs FGS for reliability |
| **Foreground service** | **Yes** — matches `RestTimerForegroundService` pattern |
| WM + `setForeground` | Acceptable alternative; FGS is clearer for “user-initiated multi-minute” |

**Decision:** dedicated `ProgramGenerationForegroundService` + notification helper, modeled on
`feature/workout` rest-timer FGS (`specialUse` subtype).

---

## 4. Architecture

```text
UI (CreateProgram / Onboarding)
  → flush prefs to Room (existing)
  → ProgramGenerationCoordinator.start(mode)
       → startForegroundService
       → observe ProgramGenerationJobState Flow

ProgramGenerationForegroundService  (:feature:program or :app)
  → startForeground(ongoing notif)
  → GenerateProgramUseCase { progress → job state }
  → on success:
       CreateProgram: SaveProgramToLibrary / hold draft id (see §5)
       Onboarding: ActivateProgram + CompleteOnboarding (existing order)
  → terminal notif + stopSelf

ProgramGenerationJobRepository (singleton, process-scoped)
  → MutableStateFlow<ProgramGenerationJobState>
  → observed by VMs and service
```

### 4.1 Job state (domain or data)

```kotlin
sealed class ProgramGenerationJobState {
  data object Idle
  data class Running(
    val mode: ProgramGenerationMode, // CreateProgram | Onboarding
    val progress: ProgramGenerationProgress,
    val maxTimelineIndex: Int,
    val startedAtEpochMs: Long,
  )
  data class Succeeded(
    val mode: ProgramGenerationMode,
    val programId: ProgramId,
    /** True when onboarding was completed as part of this job. */
    val completedOnboarding: Boolean,
  )
  data class Failed(
    val mode: ProgramGenerationMode,
    val message: String,
  )
}
```

### 4.2 Module placement

| Piece | Module | Rationale |
|-------|--------|-----------|
| Job state types + port | `:core:domain` | VMs depend on domain; no Android |
| Job state holder + coordinator impl | `:core:data` or `:feature:program` | Android Context for FGS start |
| FGS + NotificationHelper | `:feature:program` | Parallel to workout notifications; both features need it |
| Hilt binds | `:app` | Existing pattern |

Prefer putting FGS in **`:feature:program`** and having onboarding depend on a thin
`ProgramGenerationJobPort` in domain so onboarding does not take a hard dependency on program UI
types. If onboarding → program feature dependency is undesirable, put FGS in **`:app`** and inject
use cases there (simplest for v1).

**v1 recommendation:** implement service in `:feature:program`, expose
`ProgramGenerationJobPort` from domain implemented in program/data; onboarding depends on domain
port only (interface in domain, impl starts the service via Application context).

---

## 5. Success path by mode

### Create Program

1. Flush schedule/specialization (existing `flushPreferencesForGeneration`).
2. Start job `mode = CreateProgram`.
3. Service: `generateProgram` → on success:
   - **Option A (preferred):** `SaveProgramToLibrary` inactive draft, emit `Succeeded(programId)`.
   - **Option B:** keep draft only in memory in job state (lost on process death).
4. VM observing success: `loadProgramIntoEditor` / navigate to editor for `programId`.
5. Completion notif deep link: open Create Program editor for that id.

### Onboarding

1. Flush `effectiveProfile` + specialization (existing).
2. Start job `mode = Onboarding`.
3. Service: `generateProgram` → `activateProgram` → `completeOnboarding` (same order as today).
4. Emit `Succeeded(programId, completedOnboarding = true)`.
5. Completion notif deep link: main/programs (onboarding already complete).
6. In-app: existing `isComplete` / navigation effects still fire when job succeeds while app is open.

---

## 6. Notifications

Mirror `WorkoutNotificationHelper` / channels pattern.

| Notification | Channel | Behavior |
|--------------|---------|----------|
| Ongoing | `program_generation_progress` | Ongoing, low/default importance, not dismissible while FGS |
| Complete | `program_generation_complete` | High importance, auto-cancel, content intent |
| Failed | same complete channel | High, tap opens generate UI with error |

**Copy (align with UI strings):**

- Ongoing title: `Building your program…`
- Ongoing text: `Program generation takes 2–3 minutes.`
- Complete: `Your program is ready` / `Tap to review`
- Failed: `Could not generate a program` / `Tap to try again`

**Permissions:** `POST_NOTIFICATIONS` already declared in workout feature; ensure app-level grant
path covers program generation (request if missing when user taps Generate).

**Manifest (`:feature:program` or app):**

```xml
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
service ProgramGenerationForegroundService
  foregroundServiceType="specialUse"
  PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "program_generation_cpsat"
```

Document subtype for Play: multi-minute on-device constraint solver started by explicit user action.

---

## 7. UI changes

### Create Program

- `generate()` → flush → `jobPort.start(CreateProgram)` (no long `viewModelScope` solve).
- Collect `jobPort.state` in `viewModelScope`:
  - `Running` → step Generating, map progress to timeline.
  - `Succeeded` → load editor.
  - `Failed` → Generating error UI (existing).
- Leaving Generating screen does **not** cancel the job.
- Optional: “You can leave — we’ll notify you” under the 2–3 min subtitle.

### Onboarding

- Same: start job after flush; observe state for timeline + terminal Idle/Error/complete navigation.
- Do **not** host `generateProgram` on `generateJob` coroutine.

### Deep links

- PendingIntent → `MainActivity` with extras:
  - `extra_dest=create_program_editor&program_id=…`
  - `extra_dest=onboarding_review` or programs home after onboarding success
- Handle in existing nav graph / `MainActivity` intent handler (add if missing).

---

## 8. Concurrency & cancellation

- **Single flight:** if `Running`, ignore second start or show “already generating.”
- **Explicit cancel (v1 optional):** notification action “Cancel” → `jobPort.cancel()` → service
  cooperative cancel of coroutine hosting `GenerateProgramUseCase` (must rethrow
  `CancellationException` — already fixed in use case).
- **Replace:** not needed for v1.

---

## 9. Failure & process death

| Event | Behavior |
|-------|----------|
| Solver / validation failure | `Failed` + failed notif; FGS stops |
| User force-stops app | Job lost; next open Idle; user regenerates |
| System kills FGS | Same; no silent corrupt program |
| `CancellationException` | Do not log as generation failure |

Do not mark onboarding complete unless activate+complete both succeed.

---

## 10. Implementation phases

### Phase A — Skeleton (1 PR)

1. Domain: `ProgramGenerationJobState`, `ProgramGenerationMode`, `ProgramGenerationJobPort`.
2. Singleton state holder + no-op tests with fakes.
3. Notification channels + helper (progress / complete / failed).
4. Empty FGS that posts ongoing notif and stops (smoke).

### Phase B — Wire generate into FGS

1. Service runs `GenerateProgramUseCase`, publishes progress on job state.
2. Create Program VM starts service, observes state (remove in-VM solve).
3. Success: save draft program id, open editor + complete notif.
4. Unit tests: coordinator start/fail/success with fakes; VM mapping tests updated.

### Phase C — Onboarding

1. Onboarding VM uses same port/mode.
2. Service branch for activate + complete onboarding.
3. Navigation / complete notif deep link.
4. Update `OnboardingGenerateViewModelTest` for job observation.

### Phase D — Polish

1. “You can leave the app” copy.
2. Cancel action on ongoing notif (optional).
3. Detekt / assemble / device smoke (leave app mid-generate → notif → tap).

---

## 11. Testing plan

| Layer | Tests |
|-------|--------|
| Domain | Job state transitions pure if any reducer exists |
| Data/feature | Fake `GenerateProgramUseCase`; assert progress emissions + terminal state |
| VM | CreateProgram / Onboarding: start port, map Running/Succeeded/Failed to UI |
| Manual | Emulator: generate → Home → wait → complete notif → editor; kill app mid-run |

No live CP-SAT in CI (existing pattern: fakes in unit tests; solver tests optional).

---

## 12. Risks

| Risk | Mitigation |
|------|------------|
| Play policy on `specialUse` FGS | Clear subtype string; user-initiated only; stop when done |
| OR-Tools + FGS memory | Already on-device; FGS doesn’t increase model size |
| Double-activate onboarding | Idempotent complete / check `isComplete` before complete |
| Draft not persisted (Create) | Prefer SaveProgramToLibrary inactive draft on success |
| Notif permission denied | Ongoing FGS still runs; complete may be silent — still update in-app state |

---

## 13. Acceptance criteria

1. Create Program: start generate, leave app, receive complete notif, open editor with program.
2. Onboarding: same, ends with active program + onboarding complete.
3. In-app timeline still advances when user stays on screen.
4. Failure shows error UI and failed notification.
5. Second Generate while running is ignored or surfaces clear message.
6. `./gradlew detekt` + touched module tests pass.

---

## 14. Estimated effort

- Phase A–B: ~1–2 days  
- Phase C: ~0.5–1 day  
- Phase D + device QA: ~0.5 day  

**Total:** ~2–3.5 days focused work.

---

## 15. Open decisions (resolve in Phase A)

1. **Draft persistence on Create success:** library save inactive vs memory-only → **recommend library save**.  
2. **Service module:** `:feature:program` + domain port vs `:app` service → **recommend program feature + domain port**.  
3. **Cancel in notification:** v1 skip vs include → **recommend skip for v1**, add if QA asks.

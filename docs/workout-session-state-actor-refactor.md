# Live workout session-state actor — refactor design

This document describes a **follow-up concurrency refactor** for
`feature/workout` `WorkoutViewModel`: move from a **single session mutex +
monotonic revision** model to a **single-writer actor / channel** that owns all
session overlay and structure mutations.

It is a design + migration map, not a sprint commitment. Prefer reading this
before changing how live session state is updated.

**Related:** myo mini generation, overlay DataStore persistence, and the race
where Room log emissions re-entered the cycle-progress collector and wiped
unflushed overlay structure (generated minis).

---

## 1. Problem statement

Live session state is shared mutable memory:

| Field | Role |
|-------|------|
| `overlay` | Logged sets, added slots (myo minis, drops), swaps, supersets, undo stack |
| `structure` | Template projection for the session |
| `snapshotBySlot` | Progression baselines |
| UI `WorkoutUiState` | Merged render of the above |

Multiple producers touch that memory:

- UI actions (`logSet`, `addSet`, delete, undo, swap, supersets, …)
- `observeCycleProgress` + catalog collector (structure rebuild, overlay restore/adopt)
- Async DataStore flush of the overlay
- Room writes on log (which can re-emit cycle progress)

Without a single writer, races reappear: a collector can **replace** in-memory
overlay with a **stale** DataStore snapshot and drop just-spawned intensifier
slots (myo minis).

---

## 2. Current design (shipped baseline)

As of the intensifier + concurrency hardening work, `WorkoutViewModel` uses:

### 2.1 Single session lock

```text
sessionStateMutex  (kotlinx.coroutines.Mutex)
```

All collectors and mutators that read/write overlay/structure take this lock.
There is **no** second mutex nested with it (avoids AB-BA deadlock).

### 2.2 Monotonic revision

```text
overlayRevision          // +1 on every structural commitOverlay
lastPersistedRevision    // set only after successful DataStore flush under the lock
```

**Adopt rule** (persisted overlay may replace memory only if):

1. `overlayRevision <= lastPersistedRevision` (no unflushed local work), **and**
2. Catalog identity signature of store ≠ memory (real external change, e.g. lift merge).

### 2.3 Log path critical section

`logSet` is intentionally split:

| Phase | Under lock | Work |
|-------|------------|------|
| 1 | Yes | Room log → mutate overlay (incl. myo lifecycle) → **await** DataStore flush |
| 2 | Separate acquisition | recompute recommendations + render |

So recommendation math does not sit inside the “must flush before Room can
re-enter” window, while still preventing a pre-mini store snapshot from winning.

### 2.4 Why that is “good enough” but not ideal

| Strength | Weakness |
|----------|----------|
| One lock → simple deadlock story | Every mutator must remember `withLock` / `*Locked` helpers |
| Revision blocks stale adopt | Open-coded lock at many call sites; easy to regress |
| Awaits flush on log | I/O still holds the lock (serialized, but long critical sections) |
| Tests green | Scale: more mutators → more “did we lock?” risk |

The **actor/channel** pattern is the next step for **single-writer by
construction**, not a required fix for a known open bug.

---

## 3. Target design: session actor

### 3.1 Mental model

```text
┌─────────────┐     SessionCommand      ┌──────────────────────────┐
│ UI callbacks│ ─────────────────────►  │  session actor (1 loop)  │
│ Collector   │  trySend / send         │  owns overlay/structure  │
│ Completions │                         │  recompute / render      │
└─────────────┘                         │  flush / adopt           │
                                        └──────────────────────────┘
                                                   │
                                                   ▼
                                        StateFlow<WorkoutUiState>
                                        DataStore overlay store
                                        Room (log I/O as command work)
```

- **Exactly one coroutine** applies commands in order.
- No shared `Mutex` for session memory (optional: none at all for overlay).
- Public ViewModel API stays the same for Compose (`fun logSet(...)`, etc.);
  each method only **enqueues** a command.

### 3.2 Command vocabulary (illustrative)

Sealed hierarchy (names can match public API closely):

```kotlin
// Illustrative — not implemented
internal sealed interface SessionCommand {
    // Lifecycle / observation
    data class ApplyCycleSnapshot(
        val program: Program?,
        val session: TrainingSession?,
        val workout: Workout?,
        val catalog: List<Lift>,
    ) : SessionCommand

    // Logging
    data class LogSet(
        val slotKey: String,
        val weight: Weight,
        val reps: Int,
        val rir: Int?,
        val reply: CompletableDeferred<LogSetResult>? = null,
    ) : SessionCommand

    data class UnlogSet(val slotKey: String) : SessionCommand

    // Structure
    data class AddSet(val afterSlotId: SetSlotId) : SessionCommand
    data class DeleteSet(val slotId: SetSlotId) : SessionCommand
    data class DeleteSetByKey(val slotKey: String) : SessionCommand
    data class DeleteSetWithCascade(val slotId: SetSlotId) : SessionCommand
    data object Undo : SessionCommand
    data class GenerateMyoMini(val activationSlotId: SetSlotId) : SessionCommand
    data class AddDropSet(val afterSlotId: SetSlotId) : SessionCommand

    // Lifts / session chrome
    data class SwapLift(val workoutLiftId: WorkoutLiftId, val newLiftId: LiftId) : SessionCommand
    data class AddLifts(val liftIds: List<LiftId>, val asSuperset: Boolean) : SessionCommand
    data class RemoveLift(val workoutLiftId: WorkoutLiftId) : SessionCommand
    data class ReorderLifts(val orderedWorkoutLiftIds: List<String>) : SessionCommand
    data class LinkSuperset(val workoutLiftId: WorkoutLiftId, val direction: SupersetLinkDirection) : SessionCommand
    data class RemoveFromSuperset(val workoutLiftId: WorkoutLiftId) : SessionCommand
    data class SetUseRestTimerBetweenLifts(val workoutLiftId: WorkoutLiftId, val enabled: Boolean) : SessionCommand
    data class SetRestSecondsForSlots(val updates: Map<String, Int>) : SessionCommand

    // Teardown
    data object ResetSession : SessionCommand
    data class CompleteWorkout(val saveTemplate: Boolean) : SessionCommand
    data object AbandonSession : SessionCommand
}
```

Not every public method needs a unique command if some share a private handler
(e.g. superset link/unlink → one internal apply).

### 3.3 Channel policy

| Choice | Recommendation |
|--------|----------------|
| Channel capacity | `Channel.UNLIMITED` or a generous buffer; drop is unacceptable for log |
| Send vs trySend | Prefer `trySend` + assert/fail soft on closed; use `send` only from suspend collectors |
| Scope | Actor job cancelled with `sessionObservationJob` / `startSession` reset |
| Ordering | FIFO: log then collector commands process in arrival order |

### 3.4 Actor loop sketch

```kotlin
// Illustrative
private val sessionCommands = Channel<SessionCommand>(Channel.UNLIMITED)
private var actorJob: Job? = null

private fun ensureActor() {
    if (actorJob?.isActive == true) return
    actorJob = viewModelScope.launch {
        for (cmd in sessionCommands) {
            handle(cmd)
        }
    }
}

private suspend fun handle(cmd: SessionCommand) {
    when (cmd) {
        is SessionCommand.LogSet -> handleLogSet(cmd)
        is SessionCommand.ApplyCycleSnapshot -> handleCycleSnapshot(cmd)
        // ...
    }
}
```

State fields (`overlay`, `structure`, …) are **only** written inside `handle`.

### 3.5 Where I/O lives

Keep the same **logical** split as today, but as ordered steps inside one command:

```text
LogSet command:
  1. Room persistLoggedSet
  2. commit overlay (logged + myo lifecycle)
  3. await DataStore flush
  4. recompute + render
  5. emit UI events (SetLogged / SetAmended / SetLogFailed)
```

Optional later: split into `LogSet` + internal `FlushOverlay` if you want
finer-grained testing; not required for v1 actor.

### 3.6 Adopt / restore without revision?

With a single writer, **stale concurrent adopt disappears**: the collector cannot
interleave mid-command. Revision can still be useful for:

- Debugging (“which generation is on screen?”)
- Optional: skip redundant renders when content is identical

But **revision is no longer required for correctness** of the mini wipe race.
You may keep it as a cheap invariant check or drop it after the actor lands.

---

## 4. Migration plan (recommended PR shape)

### PR goal

> Single-writer session actor for `WorkoutViewModel`; **no intentional product
> behavior change**. Same public methods, same UI, same tests green.

### Step 1 — Command types + actor shell

- Add `SessionCommand` (file under `feature/workout/`, private/internal).
- Start actor in `startSession`; cancel on reset/abandon.
- Move **one** path end-to-end first (e.g. `addSet` or `undo`) as a canary.

### Step 2 — Move mutators

Convert each public mutator to:

```kotlin
fun addSet(afterSlotId: SetSlotId) {
    ensureActor()
    sessionCommands.trySend(SessionCommand.AddSet(afterSlotId))
}
```

Body of today’s `sessionStateMutex.withLock { … }` becomes `handleAddSet`.

Order of migration (suggested):

1. Structural non-I/O: undo, reorder, rest overrides  
2. Add/delete set, drop, myo generate  
3. Log / unlog (I/O-heavy)  
4. Swap / add lifts / remove lift / supersets  
5. Cycle-progress collector → `ApplyCycleSnapshot` (or finer commands)  
6. Complete / abandon  

### Step 3 — Remove `sessionStateMutex`

Once nothing locks:

- Delete mutex and `*Locked` naming (or keep names as “actor-only” private handlers).
- Delete or demote revision if unused.
- Update KDoc: “session state is single-writer via command channel.”

### Step 4 — Tests

- Prefer `runTest` + `advanceUntilIdle()` after enqueued mutations.
- Turbine `awaitItem()` after public API calls (already used in places).
- Add one test that enqueues **log then synthetic “reload”** command and asserts
  minis survive (regression for the original race, expressed as command order).

### Step 5 — Explicitly out of scope

- Domain myo/drop rules  
- Overlay DTO / DataStore schema  
- Nav / ad-hoc vs program architecture (see `ad-hoc-vs-template-workout-architecture.md`)  
- Splitting `WorkoutViewModel` into multiple classes (optional later)

---

## 5. Testing matrix

| Scenario | Assert |
|----------|--------|
| Log myo activation with trailing Standards | Mini appears between activation and Standards |
| Rapid double-tap log same set | No double Room insert; single amend path |
| Log while catalog/cycle emits | Minis and logs not wiped |
| Add set / undo / delete | Same set counts as today |
| Swap lift | Snapshot rebinding still correct |
| Process death | Overlay restore still works (actor restarts, loads store) |
| Abandon / complete | Channel closed cleanly; no leaks |

---

## 6. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Commands pile up under load | Unlimited buffer; measure; fail closed on closed channel |
| Lost UI updates if actor dies | Restart actor on `startSession`; surface error on unexpected close |
| Tests flake on async enqueue | `advanceUntilIdle` / await StateFlow; avoid reading `.value` immediately after send without drain |
| Deadlock if handle waits on UI that waits on actor | Never block UI thread on channel receive; never call `runBlocking` from handle |
| Complete/abandon race with in-flight log | Serialize via channel (complete after prior commands); or high-priority close |
| Detekt LargeClass still | Actor does not shrink the class; optional follow-up extract `SessionActor` type |

---

## 7. Effort estimate

| Scope | Size | Notes |
|-------|------|-------|
| Full actor migration (this doc) | **Medium** | ~0.5–1 day disciplined; more if collector + complete paths fight you |
| Command types + canary mutator | Small | Spike / partial |
| Mutex + shared `mutateSession { }` only | Smaller | ~1–2 hours; partial benefit without channel |

**LOC sense:** on the order of **200–400 lines** changed/added in `feature/workout`
(commands + loop + send wrappers), not a multi-module rewrite.

---

## 8. Decision guide

| If… | Then… |
|-----|--------|
| Live session stays green and mutators stabilize | **Defer** actor; current mutex + revision is sufficient |
| More mutators land and lock discipline slips | **Do** actor as its own PR |
| You only want less open-coding | Consider **shared `mutateSession` helper** first (half measure) |

---

## 9. References (code)

| Piece | Location |
|-------|----------|
| Session owner | `feature/workout/.../WorkoutViewModel.kt` |
| Myo mini lifecycle | `feature/workout/.../LiveIntensifierOverlayOps.kt` |
| Overlay merge / order slots | `feature/workout/.../LiveWorkoutOverlayOps.kt` |
| Overlay persistence | `core/domain/.../ManageWorkoutOverlayUseCase.kt`, `core/data/.../workoutoverlay/` |
| Set-type chips (live UI) | `core/ui/.../workoutlog/StratumWorkoutLogSetRow.kt` |
| Placement rules | `core/domain/.../intensifier/IntensifierPlacementRules.kt` |

---

## 10. Success criteria

Refactor is done when:

1. No `sessionStateMutex` (or equivalent) remains for overlay writes.  
2. All overlay/structure mutations go through one actor loop.  
3. Public ViewModel API and product behavior unchanged.  
4. `:feature:workout` unit tests + detekt pass.  
5. Manual: complete myo activation with trailing Standards → minis appear and stay after subsequent logs.

---

*Document status: design only. Implementation not started as of writing.*

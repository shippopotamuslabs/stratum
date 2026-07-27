# Live workout session-state actor — shipped design

Single-writer session memory for `feature/workout`: `LiveWorkoutSessionActor` owns
overlay / structure / snapshot mutations via a `SessionCommand` channel.
`WorkoutViewModel` is a thin UI shell (StateFlows, process-scoped abandon, enqueue).

**Related:** myo mini generation, overlay DataStore persistence, and the race where
Room log emissions re-entered the cycle-progress collector and wiped unflushed
overlay structure (generated minis).

---

## 1. Problem statement

Live session state is shared mutable memory:

| Field | Role |
|-------|------|
| `overlay` | Logged sets, added slots (myo minis, drops), swaps, supersets, undo stack |
| `structure` | Template projection for the session |
| `snapshotBySlot` | Progression baselines |
| UI `WorkoutUiState` | Merged render of the above |

Multiple producers used to touch that memory:

- UI actions (`logSet`, `addSet`, delete, undo, swap, supersets, …)
- `observeCycleProgress` + catalog collector (structure rebuild, overlay restore/adopt)
- Async DataStore flush of the overlay
- Room writes on log (which can re-emit cycle progress)

Without a single writer, races reappear: a collector can **replace** in-memory
overlay with a **stale** DataStore snapshot and drop just-spawned intensifier
slots (myo minis).

---

## 2. Previous design (mutex baseline — replaced)

Earlier intensifier work used:

- `sessionStateMutex` around all overlay/structure mutators
- `overlayRevision` / `lastPersistedRevision` adopt gate for unflushed memory
- Split log path: Room + mutate + await flush under lock; recompute outside

That fixed known races but required every mutator to remember `withLock`. The
actor replaces the mutex with single-writer-by-construction.

---

## 3. Shipped design: session actor

### 3.1 Mental model

```text
┌─────────────┐     SessionCommand      ┌──────────────────────────┐
│ UI callbacks│ ─────────────────────►  │  LiveWorkoutSessionActor │
│ Collector   │  trySend / send         │  (1 consumer loop)       │
│ Completions │                         │  owns overlay/structure  │
└─────────────┘                         │  recompute / render      │
                                        │  flush / adopt           │
                                        └──────────────────────────┘
                                                   │
                                                   ▼
                                        SessionEffects → UI StateFlow
                                        DataStore overlay store
                                        Room (log I/O as command work)
```

- **Exactly one coroutine** applies commands FIFO.
- No `Mutex` for session memory.
- Public ViewModel API unchanged for Compose; methods **enqueue** commands.
- Critical paths (`CompleteWorkout`, collector `ApplyCycleSnapshot`) use
  suspending `send`; UI mutators use `trySend` and surface a message if the
  channel is closed (rebind window).

### 3.2 Types

| Type | Path |
|------|------|
| Actor | `feature/workout/.../LiveWorkoutSessionActor.kt` |
| Commands | `feature/workout/.../SessionCommand.kt` |
| Effects sink | `feature/workout/.../SessionEffects.kt` |
| VM shell | `feature/workout/.../WorkoutViewModel.kt` |

### 3.3 Revision + adopt (still load-bearing)

Revision is **not** redundant after the actor:

```text
overlayRevision          // +1 on every structural commitOverlay
lastPersistedRevision    // set only after successful DataStore flush
```

**Adopt rule** (`shouldAdoptPersistedOverlay`):

1. Reject if `overlayRevision > lastPersistedRevision` (unflushed local work).
2. Reject if memory has **richer catalog structure** than the store (added slots
   / added lifts / swaps) — prevents a lagging post-flush store from wiping
   myo minis even when revisions match.
3. Accept only when catalog identity signature of store ≠ memory (real external
   change, e.g. lift merge).

FIFO alone serializes commands; revision + richness protect against **store
lag / partial snapshots** after a successful flush.

### 3.4 Terminal complete

On successful finalize:

- Navigate to complete screen
- Set `sessionTerminal = true` and clear session memory **without** publishing
  `NoActiveSession` (exit transition keeps last Loaded frame)
- Further mutators / cycle rehydrate are ignored until the next `StartSession`

### 3.5 Sync reads

`isLoadedFor`, `mergeRendered`, cascade/myo confirm helpers are **best-effort**
snapshots. They may observe mid-command state while the actor is suspended on
I/O. Use for UI dialogs only; durable work re-checks inside handlers.

### 3.6 Abandon split

Domain abandon delete is process-scoped on the ViewModel (survives nav cancel) —
**intentionally not** an actor command, so `viewModelScope` cancel cannot abort the
Room delete.

Sequence:

1. `BeginAbandon` — freeze actor mutators (keep session ids for surface restore)
2. `ProcessScopedAbandonTrainingSession` — NonCancellable domain delete
3. Success → `FinalizeAbandonLocal` (wipe memory); failure → `CancelAbandon` (unfreeze)

### 3.7 Template save flag on complete

`templateSavedThisComplete` stays **true** after a successful template write even when
finalize later fails. That is intentional: `ApplyOverlayToWorkoutUseCase` mints new
`LiftSetId`s for overlay-added slots on every apply. Resetting the flag and re-applying
would double-mint or double-add sets. Retry after
“Template saved, but finishing failed” is **finalize-only**.

---

## 4. File layout (post–god-object split)

| File | Role |
|------|------|
| `LiveWorkoutSessionActor.kt` | Channel loop, dispatch, lifecycle gates (~240 lines) |
| `LiveWorkoutSessionRuntime.kt` | Session memory + overlay revision/flush/render/recompute |
| `SessionCycleCommands.kt` | Apply cycle snapshot + restore |
| `SessionSetCommands.kt` | Log / unlog / add / delete / myo / drop / set-kind |
| `SessionLiftCommands.kt` | Swap / add / reorder / superset / rest |
| `SessionCompleteCommands.kt` | Complete + template save + finalize |
| `SessionCommand.kt` / `SessionEffects.kt` | Command vocabulary + UI sink |

Domain:

| File | Role |
|------|------|
| `RecomputeRecommendationsUseCase.kt` | Orchestrates per-lift: cold open, straight, drop, myo |
| `StraightSetRecommendationPolicy.kt` | Exact-match freeze vs adaptive + cold-open geometry |

## 5. Follow-ups (not blockers)

1. Generation-safe read APIs (`CompletableDeferred` reply) if sync-read races bite.
2. Narrow `SessionEffects` further if abandon moves fully into the actor.
3. Further extract drop/myo pipelines if those files grow again.

---

## 6. Success criteria

1. No `sessionStateMutex` for overlay writes.
2. All overlay/structure mutations go through one actor loop.
3. Public ViewModel API and product behavior preserved.
4. `:feature:workout` unit tests + detekt pass.
5. Manual: myo activation with trailing Standards → minis appear and stay after
   logs and cycle re-emits (including post-flush stale store).

---

*Document status: shipped on `feat/intensifier-sets` as extracted
`LiveWorkoutSessionActor` + `SessionCommand` / `SessionEffects`. Revision retained
in v1; richness-aware adopt; terminal complete; public API unchanged.*

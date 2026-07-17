# Ad-hoc vs template live workouts — architecture backlog

This document captures the **current dual-path design**, what already shipped for empty
(ad-hoc) workouts, and the **remaining work** to get a cleaner architecture: typed
identity, safer complete/save semantics, process-death resume, and a future
**“save as program day / create as template”** flow.

It is intentionally a backlog + design map, not an implementation plan with sprint
estimates. Prefer reading this before extending `WorkoutViewModel` or
`ProgramsViewModel` for session kinds.

---

## 1. Product model (two live session kinds)

| Kind | Domain | Template binding | Cycle progress | Live UI source of structure |
|------|--------|------------------|----------------|-----------------------------|
| **Program workout** | `TrainingSessionKind.PROGRAM_WORKOUT` | `workoutId` → program day | Finalize may advance cycle | Template `Workout` + overlay |
| **Ad-hoc empty** | `TrainingSessionKind.AD_HOC` | `workoutId = null` | Finalize does **not** advance cycle | Overlay only (synthetic empty baseline) |

Domain already owns start/finalize/orphan/log rules (`StartTrainingSessionUseCase`,
`FinalizeTrainingSessionUseCase`, `LogSetUseCase`, `isOrphanedAgainstActiveProgram`).
The **presentation and nav identity** layers are where dual-path debt concentrates.

Design intent (workout-logging Q5): user starts empty, adds lifts freely on the live
screen; ad-hoc does not complete program cycle slots. “Save **as** a new program day”
was explicitly future work — still open (see §5).

---

## 2. What already works (do not re-break)

These behaviors are in place as of the empty-workout + thermos follow-up work:

| Capability | Behavior |
|------------|----------|
| Start empty | Creates `AD_HOC` session; navigates with **session id** as route key |
| Conflict resume | Active session conflict navigates to `liveWorkoutRouteId()` (program or ad-hoc) |
| Active bar | Shows for ad-hoc; expand uses session route; next-lift from overlay/logs when available |
| Live screen load | Synthetic empty `Workout` baseline; add/log/complete/discard work |
| Add lifts | Overlay `addedLifts` / `addedSlots` (same as mid-session add on program path) |
| Swap (session-added) | `LiveWorkoutOverlayOps.merge` applies `swappedLifts` to session-added lifts (parity with template) |
| Complete | Finalizes session; **does not** prompt save-to-template (`canSaveTemplateChanges` only for `PROGRAM_WORKOUT`) |
| Hard save gate | `SaveWorkoutUseCase` only when `canSaveTemplateChanges` (never for ad-hoc) |
| Abandon | Live screen + Programs chip clear domain session **and** overlay store |
| Resume without overlay | Rebuild session-added structure from durable `LoggedSet` rows when DataStore overlay missing |
| Route helper | `TrainingSession.liveWorkoutRouteId()` shared by Programs, surface sync, VM match |

**Guardrail:** keep `canSaveTemplateChanges` derived from **session kind** (or equivalent
kind-hard rule). Never reintroduce save based only on “overlay is dirty.”

---

## 3. Core problem: one string, two meanings

### 3.1 Route / surface identity overload

```text
WorkoutKey.workoutId : String
  ├── PROGRAM_WORKOUT  →  template WorkoutId.value
  └── AD_HOC           →  TrainingSessionId.value
```

`liveWorkoutRouteId()` centralizes the rule (`workoutId?.value ?: id.value`) but:

- API names still say **workoutId** everywhere (nav, notifications, `startSession(WorkoutId)`).
- Domain wraps both as `WorkoutId` in the live VM (`startSession(WorkoutId(sessionId))`).
- Kind-blind fallback: any session with null `workoutId` (including soft-unlinked program
  rows) becomes a session-id route without necessarily having the AD_HOC synthetic baseline.

**Desired end state:** a typed live route key, e.g.:

```kotlin
// Illustrative — not implemented
@Serializable
sealed interface LiveWorkoutRouteKey : NavKey {
    @Serializable data class ProgramDay(val workoutId: String) : LiveWorkoutRouteKey
    @Serializable data class AdHocSession(val sessionId: String) : LiveWorkoutRouteKey
}
```

Propagate the same discrimination to:

- `WorkoutKey` / Nav3 entries  
- `ActiveWorkoutSnapshot.workoutId` (rename or dual fields)  
- `WorkoutNavExtras.EXTRA_WORKOUT_ID` (versioned extras or `EXTRA_SESSION_ID` + kind)  
- `WorkoutViewModel.startSession(...)` — accept sealed route, not bare `WorkoutId`  
- Programs `NavigateToWorkout` / minimized bar `workoutId` field  

**Acceptance:** no production path casts a session id to `WorkoutId` without an explicit
ad-hoc branch; orphan program sessions do not silently share the ad-hoc load path.

### 3.2 Synthetic empty `Workout` as baseline

Ad-hoc currently builds:

```text
Workout(
  id = routeWorkoutId,   // session id dressed as WorkoutId
  programId = active program,
  name = display name,
  workoutLifts = emptyList(),
)
```

This lets projection/merge/add-lift run unchanged, but:

- Couples “session structure” to the **program template** type.  
- Makes `SaveWorkoutUseCase(workoutId, …)` a footgun if gates regress.  
- Steers “save as template” toward **update-by-id** instead of **create day**.  

**Desired end state:** live session binder with two implementations:

| Binder | Baseline | Template save |
|--------|----------|---------------|
| `ProgramDaySessionBinder` | Real `Workout` from program | `applyOverlayToWorkout` + `SaveWorkoutUseCase` |
| `AdHocSessionBinder` | Empty structure projection (not a fake `Workout`) | Never; optional **CreateWorkoutFromOverlay** use case |

Extract from `WorkoutViewModel` without growing the LargeClass further: load match,
structure build, complete/save policy, orphan policy.

---

## 4. Outstanding fixes and refactors (backlog)

Priorities: **P0** correctness/architecture blockers for the dual model; **P1** parity and
safety; **P2** maintainability and polish. Items already shipped in §2 are omitted.

### 4.1 Identity and types — P0 (architecture)

| ID | Work | Notes |
|----|------|--------|
| ID-1 | Introduce sealed live route key (nav + VM + notif) | §3.1 |
| ID-2 | Stop wrapping session ids as `WorkoutId` in feature code | Domain value classes stay pure |
| ID-3 | Unit tests for every route resolution path | Program, ad-hoc, soft-unlinked program, rest (no live UI) |
| ID-4 | Deep-link contract doc for notification extras | Migration if extras change |

### 4.2 Session bind / structure — P0–P1

| ID | Work | Notes |
|----|------|--------|
| SB-1 | Extract `LiveSessionBinder` (or equivalent) from `WorkoutViewModel` | Kind strategy; kills boolean drift |
| SB-2 | Replace synthetic `Workout` for ad-hoc with empty structure projection | Still use overlay for adds |
| SB-3 | Single session reset API | Already partially unified (`clearToNoActiveSession`); ensure abandon/orphan/error use one path |
| SB-4 | `canSaveTemplateChanges` only from kind + real template present | Re-assert in complete + detekt/test |

### 4.3 Presentation semantics — P1

| ID | Work | Notes |
|----|------|--------|
| UI-1 | Split `hasDirtyEdits` into two flags | e.g. `overlayHasStructuralEdits` vs `offerSaveToProgramDay` |
| UI-2 | Rename minimized bar `workoutId` → `routeId` (or sealed) | Avoid implying template |
| UI-3 | Ad-hoc bar: observe overlay live, not only on bar collect ticks | Today reloads overlay each emission; ensure updates after add/log without relying on cycle progress |
| UI-4 | Empty complete UX | Zero-lift complete is one-tap finalize; decide confirm / require one lift / map to abandon |

### 4.4 Resume / durability — P1

| ID | Work | Notes |
|----|------|--------|
| RS-1 | Golden tests: process death mid-ad-hoc with overlay + without | Rebuild path exists; lock it |
| RS-2 | Consider durable “session structure” for ad-hoc if overlay proves fragile | Only if DataStore loss rates justify; avoid new Room tables without auth (§ Schema Lock) |
| RS-3 | Program mid-session adds: same rebuild coverage in tests | Rebuild is generic; assert both kinds |

### 4.5 Swap / progression for session-added — P1–P2

| ID | Work | Notes |
|----|------|--------|
| SW-1 | After swap on session-added, rebind progression history for new catalog lift | Merge fix covers UI/log; previous/rec columns may stay cold-start until better snapshot merge |
| SW-2 | Shared pure helper: “effective lift id for workoutLiftId” | Used by merge, bar next-lift, recs |

### 4.6 Programs / shell — P2

| ID | Work | Notes |
|----|------|--------|
| PR-1 | String resources for bar copy (“Add lifts”, “Empty Workout”) | Avoid hard-coded English in VMs |
| PR-2 | Whether ad-hoc requires an active program | Domain start still requires program; product may want free-log without program later |
| PR-3 | Document Programs discard-and-start vs resume-on-conflict | Both intentional; product copy |

### 4.7 Testing matrix (minimum for dual-path CI)

Keep these green and expand as refactors land:

| Scenario | Module |
|----------|--------|
| Start empty → navigate session route | `:feature:program` |
| Conflict with ad-hoc → resume same route | `:feature:program` |
| Bar for ad-hoc (name + next lift) | `:feature:program` |
| Load empty structure | `:feature:workout` |
| Add lift; `offerSaveToProgramDay == false` | `:feature:workout` |
| Swap session-added → rendered name + log liftId | `:feature:workout` + `LiveWorkoutOverlayOps` |
| Complete with `saveTemplate=true` → finalize only, no new program day | `:feature:workout` |
| Resume from logged sets without overlay | `:feature:workout` |
| `liveWorkoutRouteId` pure unit tests | `:core:domain` |
| Domain finalize AD_HOC no cycle advance | `:core:domain` (exists) |
| Domain log session-only workoutLiftId | `:core:domain` (exists) |

---

## 5. Create as template (future product)

### 5.1 Goal

From a finished or in-progress **ad-hoc** session (or after complete), offer:

> **Save this session as a new program day**

Not “update existing day by session id.” That must never call:

```text
SaveWorkoutUseCase(WorkoutId(sessionId), …)
```

### 5.2 Recommended domain shape

```text
CreateProgramDayFromSessionOverlayUseCase
  input:
    sessionId (or in-memory overlay + name)
    target program id
    day name / position / weekday policy (product)
  steps:
    1. Load overlay (+ optional logged sets for set shape)
    2. Materialize Workout + WorkoutLifts + LiftSets (new ids)
    3. Insert into program template (existing program mutation path / events)
    4. Optional: re-bind active session.workoutId + kind? (usually NO for completed)
  output:
    new WorkoutId
```

Related:

- `ApplyOverlayToWorkoutUseCase` already folds overlay onto a **baseline** workout — useful
  if baseline is an empty real day created first, or fold materialization into a dedicated
  “overlay → workout tree” pure function used by both live save-template and create-as.
- Progression scheme defaults for session-added lifts already match Double +
  `AddedLiftDefaults` — keep aligned with `LiveWorkoutOverlayOps.addLifts`.

### 5.3 UI / complete flow

| Moment | Suggestion |
|--------|------------|
| Mid-session | Optional “Save day to program…” in overflow (rare) |
| Complete sheet | For ad-hoc: phases Incomplete / All sets done → **Complete** + optional **Save as program day** (not “Update template”) |
| After complete | History detail action: “Add day to program” from completed AD_HOC |

Copy must never reuse program-path **“Update template / Discard changes”** for ad-hoc.

### 5.4 Identity after create

- Completed session history keeps AD_HOC + null `workoutId` (or snapshot name) — do **not**
  rewrite history identity unless product requires linkage.
- If user is still in live session and saves mid-session as a day, decide explicitly:
  - **A)** stay AD_HOC for the rest of the session (simpler), or  
  - **B)** rebind to new `PROGRAM_WORKOUT` + new route key (nav/notif must migrate).  

Prefer **A** for v1 of create-as-template.

### 5.5 Acceptance criteria (create-as-template)

1. New program day appears in Programs schedule with lifts/sets from overlay.  
2. No `training_sessions.workout_id` equals a random session UUID unless intentionally
   rebound (v1: no rebind).  
3. Cycle pointer rules unchanged (creating a day is template edit, not session finalize).  
4. Unit tests: materialize empty, multi-lift, swapped, deleted slot, superset group.  
5. Detekt/architecture: feature modules do not invent Room schema for this.

---

## 6. Suggested implementation order

When prioritizing engineering time:

1. **ID-1 / ID-2** — typed live route (unblocks safe refactors).  
2. **SB-1 / SB-2** — binder + drop synthetic `Workout`.  
3. **UI-1** — split dirty / offer-save flags.  
4. **SW-1** + **RS-1** — swap history + resume goldens.  
5. **§5 create-as-template** — new use case + complete-sheet copy, after identity is clean.  

Avoid doing (5) on top of synthetic baseline + string-overloaded `WorkoutId`.

---

## 7. File map (current hotspots)

| Area | Paths |
|------|--------|
| Route id helper | `core/domain/.../TrainingSessionLiveRouteId.kt` |
| Start ad-hoc | `core/domain/.../StartTrainingSessionUseCase.kt` |
| Log / finalize | `LogSetUseCase`, `FinalizeTrainingSessionUseCase` |
| Programs start / bar / abandon | `feature/program/.../ProgramsViewModel.kt` |
| Live session owner | `feature/workout/.../WorkoutViewModel.kt` |
| Overlay merge / rebuild | `feature/workout/.../LiveWorkoutOverlayOps.kt` |
| Nav key | `feature/workout/.../navigation/WorkoutKey.kt` |
| Notif surface | `DomainActiveSessionSurfaceSync`, `ActiveWorkoutTracker`, `WorkoutNotificationHelper` |
| App deep link | `app/.../MainActivity.kt`, `StratumNavHost.kt` |
| Historical design | `plans/workout-logging/design.md` (Q5 ad-hoc) |

---

## 8. Explicit non-goals (for this backlog)

- On-device LLM or cloud for empty workout construction.  
- Room tables for transient overlay state (DataStore remains home; Schema Lock).  
- Making ad-hoc advance cycle progress.  
- Free-form program generation from empty session via LLM (create-as-template is
  deterministic materialization of the logged overlay).  

---

## 9. Changelog (doc)

| Date | Note |
|------|------|
| 2026-07-17 | Initial backlog from empty-workout ship + thermos dual-path audit (swap fix, save gate, resume rebuild, bar/abandon polish already in tree; identity/binder/create-as still open). |

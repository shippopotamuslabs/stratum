# Design: workout-logging

Persistence and domain foundation for unified training history (import + live),
program cycle progress, per-set lift metadata snapshots, and append-only program
change events. Prerequisite for `:feature:workout` and a future weekly LLM check-in.

---

## Current state

### Programs UI (no persistence wired)

- `ProgramsScreen` renders "Start next workout", "Start empty workout", and per-workout
  skip/completion checkboxes (`ProgramsScreen.kt`).
- `MainTabShell` does not override `onStartNextWorkoutClick`, `onStartEmptyWorkoutClick`,
  `onStartWorkoutClick`, or `onSkipWorkoutCheckedChange` — they remain no-op defaults
  (`ProgramsScreenCallbacks.kt`, `MainTabShell.kt`).
- `WorkoutViewState.isCompleted` defaults to `false`; `ProgramsUiStateMapper` never sets it
  from domain data — checkboxes are cosmetic.
- `findNextWorkoutId` picks the first workout with `isCompleted == false` for card expansion
  only; it is not passed to navigation or persistence (`NextWorkoutSelection.kt`).
- `ProgramsViewModel` observes program + lift catalog + muscle volume only; no session or
  completion methods.

### Program templates (no cycle progress)

- Four normalized template tables: `programs` → `workouts` → `workout_lifts` → `sets`
  (`ProgramEntities.kt`). KDoc distinguishes templates from logged history.
- Domain `Program` has `cycleLength` and `trainingWeekdays` but no `currentCycleNumber` or
  `cyclePosition`. No completion flags on `Workout` rows.
- `RoomProgramRepository.saveProgram` replaces the full hierarchy in one transaction (delete
  children, insert all). Swaps/edits via `SwapWorkoutLiftUseCase` / `SaveWorkoutUseCase` leave
  no mutation history.

### Training history (import-only, no snapshots)

- `historical_sessions` + `logged_sets` store imported history (`ImportEntities.kt`).
- `LoggedSetEntity` holds `liftId`, `exerciseNameRaw`, weight/reps/rir — no movement pattern,
  muscle bitmasks, kinematic profile, or muscle-length bias.
- `TrainingHistoryRepository` exposes bulk `replaceAll` + read APIs only
  (`ImportPorts.kt`). `PersistImportedHistoryUseCase` resolves lifts then `replaceAll`;
  rolls back via empty `replaceAll` on insight/response failure.
- No `:feature:workout` module; `StratumDatabase` has no in-progress or draft-set tables.

### Analytics (live catalog joins)

- `ExtractResponseProfileUseCase` loads all sessions/sets and current lifts via
  `liftRepository.observeLifts().first()`.
- `MuscleVolumeCredit`, `StimulusSeriesBuilder`, `MuscleWeeklyStimulusBuilder`,
  `LiftEffectivenessAnalyzer`, `DefaultResponseObservationExtractor`, and
  `TrainingInsightsExtractor.computeVolumeBaselines` resolve `LoggedSet.liftId` → current
  `Lift` at analysis time. Renaming or re-tagging a lift corrupts historical muscle volume
  and effectiveness for past sets.

### LiftLab reference (what we are not copying)

- LiftLab uses separate `workoutsInProgress` / `liveWorkoutCompletedSets` tables and copies
  into finalized `workoutLogEntries` / `setLogEntries` on complete. Stratum has no equivalent
  today; the agreed direction rejects the copy-on-finalize pattern.

---

## Desired end state

After this foundation ships (UI in a follow-on slice):

1. **One raw history tier** — `training_sessions` + `logged_sets` hold both imported and
   live-logged data. No parallel draft/history tables.
2. **Live session lifecycle** — Starting a workout creates an `IN_PROGRESS` session; each
   logged set is written immediately to `logged_sets` with a frozen `LoggedLiftContext`.
   Finalizing sets session `COMPLETED`, stamps `endedAt`, and advances the program cycle
   pointer in O(1). Abandoning deletes the session (CASCADE deletes sets).
3. **Cycle progress** — `Program` exposes `currentCycleNumber` and `cyclePosition` read from
   `programs` row. Programs screen derives "next workout" from the pointer + template, not
   per-workout completion booleans.
4. **Immutable set facts** — Analytics and insights read snapshotted metadata on each set;
   `liftId` remains a stable join key but is not required to resolve muscle/pattern credit.
5. **Program mutation audit** — Swaps, workout edits, and regeneration append rows to
   `program_change_events` without replacing prior events.
6. **Import path preserved** — `PersistImportedHistoryUseCase` still bulk-writes history but
   populates snapshots at persist time from the resolved lift catalog (and import context).
7. **Future check-in ready** — Domain can assemble a bounded payload from change events +
   recent completed sessions + snapshotted sets (implementation out of scope here).

---

## Patterns to follow

| Pattern | Source | Apply to |
|---------|--------|----------|
| Typed value-class ids + `EnumConverters` | `DATA_LAYER_RULES.md` §1–2, `ImportIds.kt` | New session status enum, `TrainingSessionId` (or renamed id), change-event ids |
| FK CASCADE on session → sets | `LoggedSetEntity` → `historical_sessions` | `logged_sets` → `training_sessions` |
| Entity ↔ domain mappers in `:core:data` | `ImportMapper.kt`, `LiftMapper.kt` | Extended set/session mappers; bitmask encode same as `LiftEntity` |
| Repository returns domain types | `RoomTrainingHistoryRepository` | Extend port with incremental session/set CRUD |
| Replace-all for import bulk writes | `PersistImportedHistoryUseCase` | Keep `replaceAll` for import; add granular APIs for live |
| Use-case orchestration in `:core:domain` | Existing import/response use cases | `StartSession`, `LogSet`, `FinalizeSession`, `AbandonSession` |
| Fakes in `:core:testing` | `FakeTrainingHistoryRepository`, `FakeProgramRepository` | Extend fakes before ViewModel work |
| Pre-release v1 reinstall policy | `StratumDatabase` version 1, `ProgramSchemaContractTest` | Entity changes without migration |
| Template vs log separation | `Program.kt` KDoc vs `ImportModels.kt` | Cycle pointer on program; snapshots on sets, not template rows |
| Analytics tiering | `DATA_LAYER_RULES.md` §4 | Raw tier = snapshotted sets; derived tables rebuildable |

---

## Patterns to avoid

| Anti-pattern | Why | Instead |
|--------------|-----|---------|
| LiftLab draft → history copy on finalize | Finalize latency at scale (ticket problem statement) | Write sets once to raw tier; finalize = status + pointer update |
| Per-workout `isCompleted` on template rows | Agreed pointer model; template regen would lose flags | `current_cycle_number` + `cycle_position` on `programs` |
| `ABANDONED` session status | Agreed: only `IN_PROGRESS` \| `COMPLETED` | CASCADE delete on abandon |
| Joining `liftId` → current `Lift` for volume credit | Proven corruption when catalog changes | `LoggedLiftContext` on each set row |
| New tables for transient UI state | `AGENTS.md` §4.5 schema lock | Session status column on durable session row only |
| `replaceAll` for per-set live logging | Full-table rewrite per rep | Incremental insert/update on repository port |
| Storing program change history only in replaced template rows | No audit trail for coaching | Append-only `program_change_events` |
| Passing domain models to composables | `ARCHITECTURE.md` | ViewModels map to presentation (future `:feature:workout`) |

**Legacy copy risk:** `MuscleVolumeCredit.accumulateSetCredits(lift: Lift, …)` and
`ExtractResponseProfileUseCase`'s lift preload are the templates to refactor — not duplicate.
New overloads should accept `LoggedLiftContext`, not wrap catalog lookups silently.

---

## Design decisions

### D1 — Unified raw tier: `training_sessions` + `logged_sets`

**Choice:** Rename `historical_sessions` → `training_sessions`; keep `logged_sets` table name.
Extend both for live logging. Single FK chain.

**Rationale:** One query surface for analytics, import, and live history. Matches ticket
agreement and eliminates parallel schemas.

**Rejected:** Separate `live_sessions` / `draft_sets` tables (LiftLab-style); keeping
`historical_sessions` name for imports only (splits mental model without benefit).

### D2 — Session status: `IN_PROGRESS` | `COMPLETED` only

**Choice:** `TrainingSessionStatus` enum on session row. Abandon = delete session (sets
CASCADE).

**Rationale:** No orphaned abandoned rows; simple invariant — at most one `IN_PROGRESS`
session per program (enforce in domain use case).

**Rejected:** `ABANDONED` status with retained rows; soft-delete flag (adds query complexity
without coaching value for v1).

### D3 — Cycle progress: session ledger + denormalized pointer

**Choice:** Add `current_cycle_number` and `cycle_position` (completion count cache) on
`programs`. **Authoritative** per-cycle workout completion is derived from `COMPLETED`
training sessions (including zero-set skip/rest). Next workout = first template workout by
`position` without a completion session this cycle. See **Human approvals Q2**.

**Rationale:** Survives template add/delete/replace without forced re-skip; O(1) cache for
cycle-complete detection; checkboxes backed by real session rows.

**Rejected:** `cycle_position` as sole index into workout list; per-workout booleans on
template rows; deriving next workout only from a monotonic counter modulo `cycleLength`.

### D4 — `LoggedLiftContext` snapshot on every set write

**Choice:** Domain value type embedded in `LoggedSet` (and persisted on `logged_sets`):
at minimum `liftName`, `movementPatternsBitmask`, `primaryMusclesBitmask`,
`secondaryMusclesBitmask`, `kinematicProfile`, `muscleLengthBias`; plus template progression
context (`progressionScheme`, `setType`, `repMin`/`repMax`, `rirGoal`, `workoutLiftId` reference
where live-logged).

**Rationale:** Replays analytics as-performed without catalog joins. Bitmask encoding matches
`LiftEntity` / `LiftMapper` for SQL filter parity.

**Rejected:** Snapshot only `liftName` + `liftId` (LiftLab `setLogEntries` partial — insufficient
for `MuscleVolumeCredit`); JSON blob column (harder to index, diverges from bitmask contract);
runtime-only snapshot in memory (lost on process death).

### D5 — Snapshot capture point

**Choice:** Populate snapshot in domain at set-write time from current `Lift` catalog +
template `WorkoutLift`/`LiftSet` when live; at import persist time from resolved lift after
`resolveImportLifts` (template fields null/absent for import-only sets).

**Rationale:** Import path already has lift resolution; live path has template context.
Single write path into `logged_sets`.

**Rejected:** Lazy backfill on analytics read (reintroduces catalog join); post-finalize bulk
enrichment job (extra pass, failure modes).

### D6 — Finalize session = O(1)

**Choice:** `FinalizeTrainingSessionUseCase`: update session (`status`, `endedAt`), increment
`cycle_position` (wrap at `cycleLength`, bump `current_cycle_number`), no set copying.

**Rationale:** Ticket constraint; sets already in final table.

**Rejected:** LiftLab copy from in-progress to log tables; batch re-insert of sets on complete.

### D7 — `program_change_events` append-only table

**Choice:** New entity with `eventType`, `occurredAt`, `programId`, structured payload
(event-specific fields or typed JSON — structure phase decides storage shape). Writers:
`SwapWorkoutLiftUseCase`, `SaveWorkoutUseCase`, `GenerateProgramUseCase` / regen path.

**Rationale:** Coaching and future weekly LLM check-in need "what changed when" without
diffing template rows.

**Rejected:** No change log (current state); embedding last-change on `programs` (loses
history); overwriting events (not auditable).

### D8 — Analytics refactor surface

**Choice:** Refactor `MuscleVolumeCredit`, `StimulusSeriesBuilder`, `MuscleWeeklyStimulusBuilder`,
`LiftEffectivenessAnalyzer`, `DefaultResponseObservationExtractor`, and
`TrainingInsightsExtractor.computeVolumeBaselines` to credit from `LoggedLiftContext`.
`ExtractResponseProfileUseCase` stops requiring full live `Lift` list for volume paths;
`liftId` used for lift-specific effectiveness where still appropriate.

**Rationale:** Fixes catalog-mutation corruption identified in research.

**Rejected:** Dual code paths (snapshot vs catalog fallback) — fallback would mask bad data.

### D9 — Repository port evolution

**Choice:** Extend `TrainingHistoryRepository` (or rename to `TrainingLogRepository` in
structure phase) with: `insertSession`, `updateSession`, `deleteSession`, `insertSet`,
`updateSet`, `observeActiveSession`, `getSessionsByStatus`; retain `replaceAll` for import.

**Rationale:** Live logging needs incremental writes; import keeps bulk semantics.

**Rejected:** Separate `LiveWorkoutRepository` port (splits unified tier); DAO-only access
from feature module (violates architecture).

### D10 — Import backfill for existing rows

**Choice:** On `replaceAll` / persist, always write snapshots for new rows. One-time
re-derivation from catalog for any existing rows without snapshots at first launch after
schema change (pre-release: reinstall clears DB).

**Rationale:** Pre-release reinstall policy makes migration optional; import re-run path
must produce snapshots going forward.

**Rejected:** Nullable snapshots with permanent catalog fallback in analytics (D8 rejected).

### D11 — Weekly LLM check-in (design note only)

**Choice:** Out of scope for implementation. Payload dependencies documented: recent
`COMPLETED` sessions with snapshotted sets, `program_change_events` since last check-in,
current cycle pointer, `LiftTrainingInsights` / response profile summaries.

**Rationale:** Enables structure/plan to place hooks without building Callable/UI now.

---

## Human approvals (2026-07-05)

### Q1 — Rest day / skip → zero-set `COMPLETED` session

**Approved.** Checking a rest day (or skipping a workout without logging) creates a
`COMPLETED` training session with **zero sets**. This is the durable fact that powers the
Programs screen checkbox and audit trail.

- Workout skip: `COMPLETED` session, `workout_id` set, `cycle_number_snapshot` set, zero sets.
- Rest day: `COMPLETED` session, `workout_id` null, `session_kind = REST_DAY` (or equivalent),
  weekday snapshot set, zero sets.

### Q2 — Cycle progress: completion ledger, not fragile list index

**Approved with amendment to D3.** A bare `cycle_position` index into
`workouts.sortedBy(position)` breaks when workouts are added, removed, or replaced mid-cycle
(see scenario: delete at index 2 while at position 3 → forced reset → re-skip prior workouts).

**Source of truth:** which workouts (and rest days) are done **this cycle** is recorded by
`COMPLETED` sessions keyed by `(program_id, cycle_number_snapshot, workout_id)` — not by a
single integer index into the template list.

**Next workout:** first workout in template `position` order that has no `COMPLETED` program
session for the current `current_cycle_number`. Rest-day rows use their own zero-set sessions
(weekday-scoped within the current calendar week context on Programs screen).

**`cycle_position` on `programs`:** denormalized cache = count of distinct workouts completed
(or skipped via zero-set session) in the current cycle. Updated on finalize/skip. Used for
fast “cycle complete?” checks only; if cache disagrees with session ledger, ledger wins on
reconcile.

**Template edits:**

- Delete workout: remove from template; completion sessions for that `workout_id` remain in
  history (`workout_id` SET NULL on session). No completion row required for deleted slot.
- Add workout: no completion session → naturally appears as pending in schedule order.
- Replace (delete + add): new `workout_id` has no completion; user is not forced to re-skip
  unrelated completed workouts.

**Cycle complete:** when every workout in the current template has a `COMPLETED` session for
`current_cycle_number`, increment `current_cycle_number`, reset `cycle_position` to 0, emit
`CYCLE_COMPLETED` (see Q6). This is when the Programs tab shows the weekly check-in surface.

**Rejected:** pure modulo index without per-workout completion records; resetting pointer to 0
on any template delete.

### Q3 — Single active session: block, do not auto-abandon

**Approved.** At most one `IN_PROGRESS` session globally (or per program — structure phase
confirms scope; default: per user / single active program).

- UI: disable “Start next workout” / “Start empty workout” while a session is `IN_PROGRESS`.
- Domain: `StartTrainingSessionUseCase` throws a typed conflict if active session exists.
- ViewModel catches and surfaces error state (no silent auto-abandon).

### Q4 — Rename `HistoricalSession` → `TrainingSession`

**Approved.** Rename domain ids/types (`TrainingSessionId`, `TrainingSession`, etc.) and Room
table `historical_sessions` → `training_sessions` for truthfulness. Wide refactor acceptable
pre-release.

### Q5 — Nullable `workout_id`; ad-hoc empty workouts

**Approved.** `training_sessions.workout_id` is nullable.

- Program workout: `workout_id` set; on finalize, records completion for current cycle.
- **Start empty workout:** `IN_PROGRESS` session with `origin = AD_HOC`, `workout_id` null;
  does **not** advance cycle or emit `CYCLE_COMPLETED`. User adds lifts freely on workout
  screen (future slice).
- `workout_id` is never required before logging sets.

### Q6 — Check-in trigger: cycle completion, not every mutation

**Approved.** v1 `program_change_events` includes template mutations (`LIFT_SWAPPED`,
`WORKOUT_EDITED`, `PROGRAM_REGENERATED`) for coaching context, plus **`CYCLE_COMPLETED`** when
a full training cycle finishes (`current_cycle_number` increments).

- Cycle completion is the trigger for the Programs **check-in screen** (algorithmic or LLM —
  implementation TBD; event + domain read model required either way).
- Individual workout completion does **not** emit a change event; it is already captured by
  the session row.
- Pointer bumps alone do not emit events except via `CYCLE_COMPLETED` at wrap.

### Q7 — Import snapshots: lift catalog only, no `WorkoutLift`

**Approved.** Import sets snapshot **`Lift`** metadata only (`LoggedLiftContext` from resolved
catalog / stub guesser). No `workout_lift_id` or template progression fields on import rows —
those concepts do not exist in import.

Live program sessions **may** optionally persist template ids (`workout_lift_id`, scheme,
rep targets) for display and future coaching, but **metrics and volume credit use lift
snapshots only**, matching import.

---

## Open questions

None — all resolved 2026-07-05. See **Human approvals** above.

---

## Out of scope (this ticket)

- `:feature:workout` Compose UI and navigation wiring
- Weekly LLM check-in Callable and UI
- Post-release Room migrations
- Programs screen callback wiring (depends on use cases from this foundation)

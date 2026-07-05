# Ticket: workout-logging

## Summary

Design and plan the persistence and domain foundation for **workout logging** in
Stratum: unified raw-tier history (import + live), program cycle progress
tracking, lift metadata snapshots at set-write time, and append-only program
change events — all as prerequisites for `:feature:workout` and a follow-on
weekly LLM check-in feature.

## Problem

The Programs screen has UI for workout completion/skip and "start next workout"
but no wired persistence. Program/workout entities are templates only; imported
history (`historical_sessions` / `logged_sets`) is separate from live logging
and joins sets to the **current** lift catalog at analytics time — corrupting
muscle volume and effectiveness metrics when lifts are renamed or re-tagged
(e.g. bench press → deadlift on the same `lift_id`).

LiftLab's active-session → history copy-on-complete pattern caused finalize
latency at scale; Stratum should write sets once to the final raw tier.

## Agreed direction (from design discussion — to be validated in research)

1. **Unified log data:** Extend/rename `historical_sessions` → `training_sessions`;
   import and live logging share `logged_sets`.
2. **Session statuses:** `IN_PROGRESS` | `COMPLETED` only. Abandon = cascade
   delete session + sets (no `ABANDONED` status).
3. **Cycle progress:** `current_cycle_number` + `cycle_position` on program
   (pointer model), not per-workout boolean flags on template rows.
4. **Snapshots:** Freeze lift metadata on each `logged_sets` row at write time —
   at minimum movement pattern, primary/secondary muscles (bitmasks), name;
   also kinematic profile, muscle length bias, and template progression context
   for coaching/analytics replay without live catalog joins.
5. **Program change events:** Append-only log for swaps/edits/regen to support
   weekly LLM check-in ("swapped 3 weeks ago, still stagnant").
6. **Finalize workout:** O(1) session status update + cycle pointer advance; no
   bulk copy from draft to history tables.
7. **Analytics refactor:** `MuscleVolumeCredit`, response extraction, and import
   insights should consume snapshotted lift context, not current `Lift` joins.

## Out of scope (this ticket)

- `:feature:workout` Compose UI implementation
- Weekly LLM check-in UI and Firebase Callable implementation (depends on this
  schema but separate slice)
- Room migrations for post-release users (pre-release: v1 reinstall policy)

## Constraints

- Read `AGENTS.md`, `contracts/ARCHITECTURE.md`, `contracts/DATA_LAYER_RULES.md`
- Schema changes require explicit authorization in plan phase
- Pre-release: `StratumDatabase` version 1, reinstall to apply
- No MockK on owned ports; JUnit 5 + Turbine + fakes for tests

## Success criteria

- Approved `research.md`, `design.md`, `structure.md`, `spec.md`, `test_spec.md`
- Concrete entity DDL, FK delete rules, indexes, domain types (`LoggedLiftContext`)
- Clear migration/backfill strategy for existing import rows
- Identified refactor surface in response/import analytics pipelines

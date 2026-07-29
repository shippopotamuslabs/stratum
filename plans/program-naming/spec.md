# Heuristic Program & Workout Naming — Spec

**Status:** Implemented (replaces LLM `nameGeneratedProgram` / `ProgramNamingPort`)  
**When:** After volume repair and optional lift swap, before normalize/validate  
**Why:** Synthesized splits have no stable session labels. Names are presentation-only polish.

---

## Pipeline position

```text
planWeeklyVolume
  → programSynthesisPort.synthesize()     # CP-SAT — placeholder names only
  → selectLiftForSlot
  → assembleWorkouts
  → repairWeeklyVolume
  → liftSwapPort.swapLifts()              # optional OPENROUTER pro (bounded)
  → applyHeuristicProgramNames(...)       # pure domain — this spec
  → parseAndNormalizeProgram
  → validateProgramMetrics
```

Placeholder names during synthesis: `Session 1`, `Session 2`, … and `Generated Program`.
Always overwritten by heuristics (no network, no skip flag).

---

## Rules

### Program name

Local device date/time via `java.time.Clock` (default `systemDefaultZone()`):

```text
yyyy-MM-dd HH:mm:ss
```

Example: `2026-07-28 14:03:05`

### Workout titles

For each workout, classify every lift’s **title contribution**:

1. Any primary ∈ `LOWER_BODY_MUSCLE_GROUPS` → Lower  
2. Else any primary outside `CORE_MUSCLE_GROUPS` → Upper  
3. Else any movement pattern with `categoryRegion == Lower` → Lower  
   (covers LowerBack-primary hinges / deadlifts)  
4. Else any upper pattern → Upper  
5. Else Neither (core-only, empty, missing metadata)

Share is by **lift count** (not set volume). Neither still counts in the denominator.

```text
if upperShare > 0.70 → Upper
else if lowerShare > 0.70 → Lower
else → Full Body
```

Strict `>` so exactly 70% is Full Body.

### Letter suffixes

Independent counters per prefix (`Upper`, `Lower`, `Full Body`), assigned in workout
`position` order: `A`…`Z`, then `AA`, `AB`, … (never throws).

Examples: `Upper A`, `Lower A`, `Upper B`, `Full Body A`.

---

## API

```kotlin
fun applyHeuristicProgramNames(
    program: ProgramDraft,
    liftsById: Map<String, LiftContext>,
    clock: Clock = Clock.systemDefaultZone(),
): ProgramDraft
```

No port, no Firebase Callable, no Hilt binding.

---

## Deleted (do not reintroduce)

- `ProgramNamingPort` / LLM payloads
- `FirebaseProgramNamingAdapter` / `nameGeneratedProgram` Callable
- `FakeProgramNamingPort` / `skipProgramNaming`
- `OPENROUTER_MODEL_SMALL` for naming (flash may still exist for other import assists)

---

## Tests

`:core:domain` — `HeuristicProgramNamingTest`:

- Threshold strict `>` 70%, empty/unknown → Full Body  
- Deadlift (LowerBack + HipHinge) → Lower; near-threshold with DL still Lower  
- Letter counters + position order + `AA` after `Z`  
- Fixed clock program name format  

---

## Non-goals

- Names must not gate recovery, variety, or volume  
- Not free-form program generation  
- Not set-volume-weighted titles (product: lift-count share)

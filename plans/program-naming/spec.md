# LLM Program & Workout Naming — Spec

**Model:** `OPENROUTER_MODEL_SMALL` (`deepseek/deepseek-v4-flash` via Firebase Secret Manager)  
**When:** After deterministic synthesis, lift selection, volume repair, and lift swap (PRO)  
**Why:** Synthesized splits have no stable `SessionTypeId` labels. Names are presentation-only.

---

## Pipeline position

```text
planWeeklyVolume
  → programSynthesisPort.synthesize()     # CP-SAT — placeholder names only
  → selectLiftForSlot
  → assembleWorkouts
  → repairWeeklyVolume
  → liftSwapPort.swapLifts()              # OPENROUTER_MODEL (pro)
  → programNamingPort.nameProgram()       # OPENROUTER_MODEL_SMALL (flash)  ← this spec
  → parseAndNormalizeProgram
  → validateProgramMetrics
```

Placeholder names during synthesis: `Session 1`, `Session 2`, … and `Generated Program`.
Never shown to the user if naming succeeds.

---

## Firebase callable

**Name:** `nameGeneratedProgram`

**Secrets:** `OPENROUTER_API_KEY`, `OPENROUTER_MODEL_SMALL`

**Auth:** Firebase Auth required (same as `swapProgramLifts`)

**Timeout:** 30s (flash model, small JSON output)

### Request

```typescript
interface NameProgramRequest {
  intent: {
    trainingAge: string;
    sessionLengthMinutes: number | null;
    trainingDaysPerWeek: number;
    cycleLength: number;
    specialization: Record<string, string>;
    specializedMuscles: string[];
    trainingWeekdays: string[];  // user order, e.g. ["Sunday","Monday","Wednesday",...]
  };
  program: {
    workouts: Array<{
      position: number;
      weekday: string | null;
      workoutLifts: Array<{
        name: string;
        movementPattern: string;
        primaryMuscles: string[];
        setCount: number;
      }>;
    }>;
  };
}
```

### Response (strict JSON schema)

```typescript
interface NameProgramResponse {
  programName: string;       // e.g. "5-Day Arm & Calf Focus"
  workouts: Array<{
    position: number;
    name: string;            // e.g. "Upper — Arms & Chest"
  }>;
}
```

### System prompt (summary)

Coach naming a finished program for a lifter. Use specialization intent, weekday, and
exercise list. Short, human names (2–5 words per workout). No set/rep counts in names.
Program name reflects split shape + specialization emphasis. Do not invent exercises.

---

## Domain

```kotlin
interface ProgramNamingPort {
    suspend fun nameProgram(request: ProgramNamingRequest): Result<ProgramNamingResult>
}

data class ProgramNamingResult(
    val programName: String,
    val workoutNamesByPosition: Map<Int, String>,
)
```

- `buildProgramNamingRequest(draft, context)` — maps `ProgramDraft` after swap pass
- `applyProgramNames(draft, result)` — copies names onto draft by `position`
- `IntegrateAndValidateOptions.skipProgramNaming` — tests (default false in prod)

**Fake:** `FakeProgramNamingPort` returns empty → keep placeholders (tests).

---

## Inference

`FirebaseProgramNamingAdapter` in `:core:inference` — mirrors `FirebaseLiftSwapAdapter`.

---

## Removed from synthesis path

| Deleted / demoted | Replacement |
|-------------------|-------------|
| `inferSessionTypeFromSlots` | Not needed for UX |
| `inferSplitLabel` | `programName` from LLM |
| `displayNameForSessionType` in synthesis materialization | `Session N` placeholder |
| `relabelWorkoutNamesByWeekday` for generated programs | LLM names already calendar-aware |

`relabelWorkoutNamesByWeekday` may remain for non-generated schedule edits.

---

## Failure behavior

- Naming failure: log warning, keep placeholder names (same pattern as swap `getOrElse { empty }`)
- Never block program delivery on naming failure
- Swap failure + naming success: still name the program

---

## Tests

- `parseNameProgramResponse` unit test (`:core:inference`)
- `applyProgramNames` unit test (`:core:domain`)
- Pipeline tests: `skipProgramNaming = true` (alongside `skipLiftSwap`)

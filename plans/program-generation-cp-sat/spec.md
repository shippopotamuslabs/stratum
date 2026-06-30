# CP-SAT Volume Allocator — Model Spec

Replace the greedy muscle-by-muscle round-robin in `allocateSlotSetCounts`
(`core/domain/.../prescription/SlotSetAllocator.kt:125`) with a global CP-SAT
constraint optimizer. The solver assigns integer `FilledSlot.setCount` values
across all sessions at once, satisfying every cap and minimizing deficit-weighted
deviation from per-muscle `aim`. This generalizes across all specialization
combos without per-muscle patches (`DualArmSpecBalancer`,
`rebalanceArmSpecSplitSessions`, etc.).

The pipeline seam is unchanged:

```
resolveSplit → [port.allocate → fallback greedy] → selectLiftForSlot → assembleWorkouts → repair/trim
```

---

## Architecture

- `:core:domain` (pure JVM; ARCHITECTURE §2 forbids extra deps) defines a
  `SlotSetAllocationPort` interface and keeps the greedy fallback. No OR-Tools
  in domain.
- `:core:domain-solver` (new module) depends on `:core:domain`, adds
  `ortools-java`, implements the port with `CpSatSlotSetAllocator`. JVM library
  for Phase 1–3; converted to an Android library in Phase 4 for on-device native
  packaging.
- Wiring is via constructor injection at `SlotBasedProgramBuilder` /
  `BuildProgramWorkoutsUseCase` / `ProgramGenerationPipeline`. `:app` binds the
  impl (Hilt). `:core:domain` never depends on `:core:domain-solver`.

---

## Decision variables

```
x[s] ∈ ℤ≥0   — hard sets for slot s
```

One variable per **active** `FilledSlot` across all `SessionSlotPlan`s
(~30–50 variables typical). Optional slots are resolved pre-solver by the
existing `shouldIncludeOptional` (`SlotSetAllocator.kt:138`); excluded slots are
fixed at 0 and not modeled as variables.

---

## Credit matrix

`coeff(s, m)` — weekly credit contributed by one set of slot `s` to muscle `m`:

```
coeff(s, m) = 1.0  if s.primaryMuscle == m            (PRIMARY_SET_CREDIT)
            = 0.5  if PATTERN_SECONDARY_MUSCLE_CREDITS[s.pattern] credits m
                                                  (SECONDARY_SET_CREDIT)
            = 0    otherwise
```

Reuse the exact constants from `ProgramGenerationContext.kt:96-97`
(`PRIMARY_SET_CREDIT = 1.0`, `SECONDARY_SET_CREDIT = 0.5`) and the table at
`RpReferenceTables.kt:92-104`. No new credit model.

### Weekly muscle credit (linear)

```
credit(m) = Σₛ x[s] × coeff(s, m)
```

### Per-session muscle credit (linear)

Same sum restricted to slots in session `w`. Must match
`muscleCreditsInSession` (`SlotSetAllocator.kt:95-110`) semantics exactly:
primary sets count 1.0 each; a slot's sets also credit any matching secondary
muscle at `creditPerSet`.

---

## Hard constraints

| Constraint | Source |
|---|---|
| `Σₛ∈w x[s] ≤ sessionHardSetCap(context)` when non-null | Session time budget — `sessionHardSetCapForLength` (`RpReferenceTables.kt:18`) |
| `credit_session(w, m) ≤ SESSION_MUSCLE_SET_CAP (8)` | Per-session muscle cap |
| `x[s] ≤ COMPOUND_SETS_PER_SLOT_CAP (4)` when `role ∈ {Anchor, SecondaryCompound}` | Mirror `perSlotSetCap` (`SlotSetAllocator.kt:403`) |
| `x[s] ≤ SPECIALIZED_SETS_PER_SLOT_CAP (6)` otherwise | Mirror `perSlotSetCap` |
| `x[s] ≥ ANCHOR_MIN_SETS (3)` when anchor slot active | `SlotSetAllocator.kt:59` |
| `x[s] ≥ SECONDARY_COMPOUND_MIN_SETS (2)` for active secondary compounds | `SlotSetAllocator.kt:65` |
| `x[s] ≥ MIN_SPECIALIZED_DIRECT_SETS (4)` for specialized isolation slots | `RpReferenceTables.kt:107` |
| `credit(m) ≥ target.min` (MEV) | Soft in v1 objective; hard only where feasible |
| `credit(m) ≤ target.max` (MRV ceiling) | Hard |

Optional slots: either `x[s] = 0` (excluded) or included with the bounds above.
Inclusion is decided pre-solver by `shouldIncludeOptional`; the solver only
varies included slots.

---

## Objective — deficit-weighted fill

For each muscle `m`, introduce non-negative slack variables:

```
credit(m) - aim(m) = over_m - under_m     (over_m, under_m ≥ 0)
```

Minimize total weighted deviation:

```
Minimize Σₘ w(m) × (over_m + under_m)

w(m) = HIGH    if Specialize
     = NORMAL  if Maintenance
```

Specialized muscles carry higher weight so the solver prioritizes filling them
toward MRV before fine-tuning maintenance muscles toward MAV/PV.

**Future hook:** add a distinct **MV** aim for maintenance muscles (separate
from MAV). The weight function leaves room for a third aim tier; tracked as a
separate ticket.

---

## Fallback

If `OPTIMAL` / `FEASIBLE` is not found within the time limit (500ms default),
fall back to the current greedy allocator (`allocateSlotSetCountsGreedy`). Log
the fallback in debug builds. The greedy path is retained as
`allocateSlotSetCountsGreedy` and used for regression comparison.

---

## Native packaging note (Phase 4)

`com.google.ortools:ortools-java` (latest 9.14.6206) publishes **no Android native
artifacts** — Maven Central carries only `ortools-darwin-*`, `ortools-linux-*`,
`ortools-win32-x86-64`. There is no `android-arm64` / `android-x86_64` and no
maintained third-party Android port. So the on-device leg requires building
`libortools.so` (CP-SAT + its deps: abseil, re2, protobuf, etc.) from the
`github.com/google/or-tools` C++ source via the Android NDK toolchain.

Pattern follows pocket-crew (`~/Code/pocket-crew`):

- `:core:domain-solver` becomes an **Android library** with
  `externalNativeBuild { cmake { path = ...; version = "3.22.1" } }`.
- `third_party/or-tools` checked out as a git submodule; the module's
  `src/main/cpp/CMakeLists.txt` does `add_subdirectory(${ORTOOLS_DIR} ortools-build)`
  with the NDK CMake toolchain (`CMAKE_TOOLCHAIN_FILE`, `ANDROID_ABI=arm64-v8a`).
- `ndkVersion = "28.2.13676358"`, `ndk { abiFilters += listOf("arm64-v8a") }`,
  `minSdk 34` (match ARCHITECTURE §8 + pocket-crew).
- OR-Tools' own CMake fetches abseil/protobuf/re2 via FetchContent; the NDK
  toolchain makes them cross-compile. `ortools-java`'s JNI wrapper (`com.google.ortools.Loader`)
  loads `libortools.so` + `libjniortools.so` at runtime — the CMake target must
  produce both with the loader's expected sonames.
- A `verifyOrToolsPackaging` Gradle task (mirror pocket-crew's
  `verifyLlamaJniPackaging`) confirms the merged native libs contain the
  expected `.so` files.

A build spike in Phase 4 confirms the exact CMake flags and sonames; the model
and JVM phases (0–3) are unaffected and proceed on `ortools-java` as-is.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| No Android OR-Tools artifacts | Build `libortools.so` from source via NDK (Phase 4); pattern from pocket-crew |
| APK bloat from OR-Tools natives | Isolate in `:core:domain-solver`; `arm64-v8a` only; measure early; ABI splits only if needed |
| Infeasible models (conflicting mins) | Soft mins in v1 objective; hard mins only where feasible; greedy fallback |
| Solve time on slow devices | 500ms cap; warm-start from greedy solution as a hint |
| Quadratic objective complexity | Use linear `over/under` slack variables (standard CP-SAT pattern) |
| Lift-level repair undoes solver | Run solver after structural rebalances; minimize post-hoc `repairWeeklyVolume` adds |
| Native build complexity / long CI | Gate `externalNativeBuild` on `third_party/or-tools` checkout (pocket-crew pattern); prebuilt `.so` fallback committed to jniLibs if CI can't build |

---

## Phase plan (summary)

| Phase | Deliverable |
|---|---|
| 0 | This spec doc |
| 1 | `:core:domain-solver` JVM module + `SlotSetAllocationPort` + CP-SAT prototype + JVM tests |
| 2 | Wire port into `allocateSlotSetCounts` dispatcher with greedy fallback |
| 3 | Delete rebalance passes subsumed by the solver |
| 4 | Convert `:core:domain-solver` to Android library; build `libortools.so` from source (NDK); on-device validation |
| 5 | Combinatorial specialization regression harness |

Branch: `feature/cp-sat-allocator` (or current `feature/program-screen` per
maintainer direction).
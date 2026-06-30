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

## Native packaging — Phase 4 status

`com.google.ortools:ortools-java` (9.14.6206) publishes **no Android native
artifacts**. The on-device leg builds `libortools.so` + `libjniortools.so`
from the `github.com/google/or-tools` C++ source via the Android NDK.

**Completed:**
- `:core:domain-solver` converted from JVM library to **Android library**
  (`com.android.library`, `namespace = "com.stratum.domain.solver"`,
  `minSdk 34`, `ndkVersion 28.2.13676358`, `abiFilters arm64-v8a`).
- CMake scaffold at `src/main/cpp/CMakeLists.txt` — `add_subdirectory` of
  `third_party/or-tools` with `BUILD_JAVA=ON`, `USE_COINOR/SCIP/HIGHS=OFF`.
- CMake build gated behind `-PbuildOrToolsNatives` Gradle property so the
  default build stays green when SWIG or the OR-Tools checkout is absent.
- Hilt `SolverModule` in `:app` binds `SlotSetAllocationPort` to
  `CpSatSlotSetAllocator`.
- `CpSatSlotSetAllocator` gracefully returns `Fallback` when native libs
  can't load (before the CMake build is run, or wrong ABI).
- `:app:assembleDebug` succeeds with the solver module wired in.

**Remaining (requires SWIG on the host):**
- Install SWIG: `apt install swig` (or `brew install swig`).
- Clone OR-Tools: `git clone --depth 1 --branch v9.14 https://github.com/google/or-tools.git third_party/or-tools`
- Build natives: `./gradlew :core:domain-solver:assembleDebug -PbuildOrToolsNatives`
  (first build: 10–20 min — fetches & compiles abseil/protobuf/re2).
- Verify the `.so` files land in the APK's `jniLibs/arm64-v8a/`.
- On-device smoke test: confirm `Loader.loadNativeLibraries()` resolves and
  the solver returns `Success` (not `Fallback`).
- APK size check; consider ABI splits if bloat is unacceptable.

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
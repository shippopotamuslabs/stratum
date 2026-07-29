# Unified CP-SAT Program Synthesis — Model Spec

**Status:** Approved direction (Option B — single model, global optimum)  
**Replaces:** Template catalog selection, organic blueprints, `resolveBestSplit` candidate loop  
**Extends:** `ProgramSynthesisModel` / `CpSatSlotSetAllocator` in `:core:domain-solver`

---

## 1. Problem statement

Given weekly volume targets, specialization tiers, equipment, session length, and the user's
training weekday calendar (including explicit rest gaps), synthesize in **one CP-SAT solve**:

1. **Split structure** — which movement-pattern slots appear in which sessions
2. **Prescription** — integer set counts per active slot
3. **Schedule** — which cycle session binds to which training weekday

No `SPLIT_CATALOG` sequences. No organic blueprints. Pattern vocabulary (`HorizontalPress →
Chest`, etc.) remains; pre-composed session templates do not.

**Solve time budget:** up to 120s (configurable). Correctness and global optimality over speed.

---

## 2. Architecture

```text
:core:domain
  SlotUniverseBuilder          — pattern slots from equipment + specialization
  ProgramSynthesisPort         — new port (replaces SlotSetAllocationPort at pipeline boundary)
  SynthesisSlot                — domain type (pattern, muscle, role, allowed domains)
  ProgramSynthesisResult       — sessions + plans + weekdays + objective + label
  materializeSynthesisResult() — solver output → ResolvedSessionPlan / SessionSlotPlan

:core:domain-solver
  UnifiedProgramSynthesisModel — single CpModel (structure + sets + calendar)
  CpSatProgramSynthesizer      — implements ProgramSynthesisPort

:app
  Hilt binds CpSatProgramSynthesizer
```

**Pipeline after migration:**

```text
planWeeklyVolume
  → programSynthesisPort.synthesize(targets, context)   # placeholder names only
  → selectLiftForSlot
  → assembleWorkouts
  → repairWeeklyVolume
  → liftSwapPort.swapLifts()          # OPENROUTER_MODEL (pro), optional
  → applyHeuristicProgramNames()      # pure domain — see plans/program-naming/spec.md
  → integrateAndValidate
```

**Delete from production path:**

- `resolveBestSplit`, `resolveSplit` (catalog picker)
- `buildSplitCandidateKinds`, `composeOrganicBlueprints`
- `OrganicCalendarAssignment`, `ExposureRecoveryFeasibility` pre-ranking
- `SlotBasedProgramBuilder` organic/catalog branches

**Demote to test fixtures only:**

- `SPLIT_CATALOG` → `legacy-split-reference.json` for regression comparisons

---

## 3. Indices and inputs

| Symbol | Meaning |
|--------|---------|
| `S` | `context.trainingDaysPerWeek` — session count in cycle |
| `U` | Slot universe size (pruned `SynthesisSlot` list) |
| `M` | Muscles with non-zero aim or specialization priority |
| `D` | User training weekday ordinals in **declared order** (`trainingWeekdayIndicesInUserOrder`) |
| `T_m` | `WeeklyVolumeTarget` for muscle `m` (aim, min, max) |
| `tier(m)` | Required frequency tier → min sessions per cycle |

### Slot universe pruning

Include slot `u` iff:

- Equipment supports pattern
- Primary muscle has `aim > 0` OR muscle is `Specialize` / `MinimumVolume`
- Cross-domain touch slots only when specialization warrants them

Typical `|U|` ≈ 35–55 for 5-day hypertrophy. Upper bound hard cap: 80.

---

## 4. Decision variables

### 4.1 Structure + prescription (per slot `u`, session `s`)

```
active[u,s] ∈ {0,1}     — slot u used in session s
x[u,s]      ∈ ℤ≥0       — hard sets
```

Linking:

```
x[u,s] ≤ cap(u) · active[u,s]
x[u,s] = 0              if slot u incompatible with session domain configuration
```

For optional/variant slots (today's `isSolverActivatableSlot`), `active` is free.
For mandatory archetype slots (squat anchor candidate on lower day), enforce via domain
constraints below — not pre-included in every session.

### 4.2 Calendar

```
day[s] ∈ D              — weekday ordinal (Mon=0 … Sun=6)
allDifferent(day[0..S-1])
```

Gap between sessions `i` and `j` on the calendar uses **user-order slot indices**, not
Mon-sorted ordinals:

```
gap(i,j) = calendarGapBetweenTrainingSlots(D, slotIndex(day[i]), slotIndex(day[j]))
```

Reuse `calendarGapBeforeTrainingSlot` logic from domain (move to shared scheduling util).

### 4.3 Session domain (explicit)

```
domain[s] ∈ {Upper, Lower, Full}
```

Domain-slot compatibility:

```
active[u,s] = 0   if u.allowedDomains ∩ {domain[s]} = ∅
```

Enforced via reified table: precomputed `compatible[u,d]` bool constants.

### 4.4 Derived (not explicit vars unless needed for linearization)

- `credit[m]` = weekly muscle credit from all `x[u,s]`
- `credit_session[s,m]` = per-session muscle credit
- `meaningful[s,m]` = session s has ≥ `MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE` for muscle m

---

## 5. Hard constraints

### 5.1 Volume

```
credit(m) = Σ_{u,s} x[u,s] × coeff(u,m) × SCALE
credit(m) ≤ T_m.max × SCALE                                    (MRV — hard)
```

Aim and MEV via slacks in objective (same as today).

### 5.2 Session time

```
Σ_u x[u,s] ≤ sessionHardSetCap(context)   ∀s
```

### 5.3 Per-session muscle cap

```
credit_session(s,m) ≤ SESSION_MUSCLE_SET_CAP × SCALE   ∀s,m
```

### 5.4 Role minimums (conditional on active)

```
x[u,s] ≥ ANCHOR_MIN_SETS × active[u,s]           if role(u) = Anchor
x[u,s] ≥ SECONDARY_COMPOUND_MIN × active[u,s]    if role(u) = SecondaryCompound
x[u,s] ≥ MIN_SPECIALIZED_DIRECT × active[u,s]    if iso + Specialize muscle
```

### 5.5 Domain structural floors

**Lower session** (`domain[s] = Lower`):

- `Σ_{u ∈ lowerAnchors} active[u,s] ≥ 1` — at least one squat or hinge anchor candidate
- Upper-only patterns: `active[u,s] = 0`

**Upper session** (`domain[s] = Upper`):

- Lower-anchor patterns (squat/hinge/knee extension primary): `active[u,s] = 0`
- Exception: cross-domain touch slots explicitly marked `isCrossDomainTouch`

**Full session** (`domain[s] = Full`):

- Relaxed compatibility — slots from `Full` allowed set only

### 5.6 Slot uniqueness per session

At most one slot per `(pattern, primaryMuscle, isCrossDomainTouch)` tuple active in session `s`.

### 5.7 Frequency (priority muscles)

For each muscle `m` with required tier ≥ 2:

```
Σ_s meaningful[s,m] ≥ minSessions(tier(m))
```

Linearize `meaningful[s,m]` via big-M on `credit_session(s,m)` (same pattern as current
`meaningfulExposureLiteral`).

### 5.8 Domain diversity (soft-hard)

Not required to be exactly U/L/U/L — but at least:

```
|{s : domain[s] = Lower}| ≥ 1   if any lower muscle has aim > 0
|{s : domain[s] = Upper}| ≥ 1   if any upper muscle has aim > 0
```

---

## 6. Objective (tiered — extend existing weights)

Reuse `SynthesisObjectiveWeights` lexicographic tiers. Add structure terms at appropriate tiers.

| Tier | Term | Notes |
|------|------|-------|
| 5M | Consecutive training-day priority exposure | Existing |
| 1M | Recovery gap shortfall (priority muscles) | Extend to dynamic `x[u,s]` credits |
| 1M | Domain adjacency (lower ↔ push/pull) | Use `domain[s]` instead of `SessionTypeId` |
| 1M | Spec-heavy session after rest | Penalize if spec-direct load on `s` and `gap(prev(s),s) < 2` |
| 100k | Recovery gap (general muscles) | Existing |
| 10k | Specialized MEV shortfall | Existing |
| 1k | Co-specialization balance | Existing |
| 100 | Specialize aim deviation | Existing |
| 80 | MinimumVolume aim deviation | Existing |
| 10 | Maintenance aim deviation | Existing |
| 50 | Role minimum shortfall | Existing |
| 20 | Optional slot inactive preference | Existing |
| 1 | Push/pull imbalance per session | Existing |
| 1 | Session fill shortfall | Existing |
| **5** | **Domain alternation on calendar** | **New soft:** penalize `domain[s]==domain[s+1]` on adjacent calendar days |
| **2** | **Slot activation count** | **New soft:** prefer fewer active slots (simpler sessions) |

**Spec-after-rest:** For session `s` with specialized direct iso load above threshold, penalize
calendar placement when the prior training slot gap `< 2`. Uses user-order `D` gaps.

---

## 7. Materialization (solver → domain)

After `OPTIMAL` or `FEASIBLE`:

1. For each session `s`, collect `{u : active[u,s]=1 ∧ x[u,s]>0}`
2. Build `FilledSlot` list → `SessionSlotPlan`
3. `weekdayIndexByPosition[s] = day[s]`
4. Placeholder workout names: `Session 1` … `Session N` (position-based)
5. Placeholder program name: `Generated Program`

**Naming is not synthesized.** `applyHeuristicProgramNames` (pure domain; see
`plans/program-naming/spec.md`) assigns a local date/time program title and Upper/Lower/
Full Body workout labels after optional lift swap. No LLM, no `SessionTypeId` inference,
no `inferSplitLabel` on the generation path.

`SessionTypeId` may still be used internally for slot vocabulary migration from
`SESSION_SLOTS`; it is never shown to the user and not a synthesis decision variable.

---

## 8. Infeasibility

If solver returns `INFEASIBLE`:

- Throw `ProgramSynthesisException` with structured reason
- Domain maps to `ProgramGenerationValidationException` at pipeline boundary
- Message hints: reduce specialization count, add training day, shorten session length, lower targets

Optional: run quick presolve (frequency vs session count) before CP-SAT to fail fast.

---

## 9. Implementation phases

### Phase 1 — Domain vocabulary (no solver change)

- [ ] `SynthesisSlot` data class
- [ ] `SlotUniverseBuilder` — flatten from `SESSION_SLOTS` + composition modifiers; no sequences
- [ ] `ProgramSynthesisPort` + `ProgramSynthesisResult`
- [ ] Placeholder naming only (`Session N`, `Generated Program`)
- [ ] Unit tests: universe size, equipment filtering, specialization slots present

### Phase 2 — Unified model core

- [ ] `UnifiedProgramSynthesisModel` in `:core:domain-solver`
- [ ] Variables: `active[u,s]`, `x[u,s]`, `day[s]`, `domain[s]`
- [ ] Hard constraints §5
- [ ] Port objective terms from `ProgramSynthesisModel` adapted to `(u,s)` indexing
- [ ] `CpSatProgramSynthesizer` with 120s default deadline
- [ ] Solver unit tests: toy 3-day, 4-day weird calendar, 5-day sean layout

### Phase 3 — Pipeline swap

- [ ] `buildProgramWithSelections` calls `ProgramSynthesisPort` directly
- [ ] Remove `resolveBestSplit` from build path
- [ ] Update Hilt wiring (`ProgramGenerationPipeline`)
- [ ] Migrate tests off template IDs
- [x] Heuristic naming after swap (`plans/program-naming/spec.md`)

### Phase 4 — Delete legacy synthesis

- [ ] Remove `OrganicSplitComposer`, `OrganicCalendarAssignment`, catalog picker
- [ ] Move `SPLIT_CATALOG` to test resources
- [ ] Remove `SplitCandidateEvaluation` telemetry fields (or repurpose for objective breakdown)
- [ ] Detekt + full test suite

### Phase 5 — Acceptance

- [ ] `SeanWorkbookSynthesisTest` — structure + volume, no blueprint ID assertion
- [ ] `WeirdCalendarFourDayTest` — D1/D2/D3/rest/D4 chest+back 2× placement
- [ ] `MtwfsArmCalfSpecTest` — recovery streak bounds
- [ ] Property test: random targets + calendar → feasible or clean infeasibility

---

## 10. Acceptance scenarios

### 10.1 Weird 4-day calendar

```
Weekdays (user order): Mon, Tue, Wed, Fri
Training days: 4
Targets: Chest aim 20, Back aim 10, frequency tier 2
```

**Expect:** Chest horizontal-press pattern on Mon + Fri (max spacing across Wed rest gap).
Back vertical-pull on same or staggered days with antagonist feasibility. Solver chooses
domain labels; no template id.

### 10.2 Sean 5-day

```
Weekdays: Sun, Mon, Wed, Thu, Fri  (Tue rest)
Specialize: Chest, Bicep, Tricep, Calf
MinimumVolume: Back, Quad
```

**Expect:** Alternating upper/lower domains; spec-direct concentration after rest day (Wed);
calf direct work present; no `organic-5d-ul-spec-alternate` id dependency.

### 10.3 Maintenance 3-day

```
Weekdays: Mon, Wed, Fri
No specialization
```

**Expect:** Viable 3-day structure (not restricted to catalog `3d-t1-l-up-upull`); volume aims met.

---

## 11. Credit matrix

Unchanged from `plans/program-generation-cp-sat/spec.md`:

```
coeff(u,m) = SCALE   if primary(u) == m
           = SCALE × secondaryCredit(pattern(u), m)   if in PATTERN_SECONDARY_MUSCLE_CREDITS
           = 0   otherwise
```

---

## 12. Open questions (resolve during Phase 2)

1. **Sibling variants (A/B slots):** Model as separate universe entries `u` with
   `siblingVariant` metadata; at most one A and one B per muscle-pattern per session.
2. **Full-body sessions:** Keep `domain[s]=Full` in enum or eliminate and use Upper+Lower slot
   union with relaxed caps?

**Default decisions:**

- Sibling variants: separate universe entries, mutex constraint per session
- Full-body: keep `Full` domain for 2-day tier-2 cases; optimizer free to select elsewhere
- **Naming:** deterministic heuristics after optional lift swap (`applyHeuristicProgramNames`)

---

## 13. Non-goals

- Lift selection (stays deterministic post-solve)
- LLM for structure, volume, or naming (lift swap + import assist only)
- Database schema changes
- Onboarding UI changes (except error messages for infeasibility; optional naming progress copy)

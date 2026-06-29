## Adversarial Architecture Review Prompt — Stratum Program Generation v2

You are a principal engineer and ML systems architect performing an **adversarial design review** for Stratum, an Android hypertrophy training app. Your job is not to agree with the proposal — it is to **stress-test it**, find failure modes, and produce the **optimal generation architecture** that maximizes **success rate** and **program quality** while completing in **~3 minutes wall-clock** (hard ceiling ~10 minutes today; we often fail to finish even at 10).

### Your deliverable

Produce a **concrete generation plan** with:

1. **Division of labor table** — what is deterministic (domain/server) vs what each LLM phase does, with rationale.
2. **Phased pipeline spec** — inputs/outputs/schemas per phase, parallelism model, retry policy, timeout budget per phase.
3. **Payload minimization spec** — exactly what data each phase receives (no “send everything everywhere”).
4. **Validation strategy** — which checks run where; which tools (if any) the model gets; what is repaired deterministically vs re-generated.
5. **Failure-mode analysis** — top 10 ways this still fails at 3 minutes or produces bad programs, with mitigations.
6. **Adversarial alternatives** — at least 2 architectures you would choose *instead of* the proposal if you were trying to break it, and why one might still win.
7. **Migration path** — minimal v1 that ships fast vs full v2; what to delete from the current monolithic flow.
8. **Success metrics** — measurable acceptance criteria (latency p50/p95, validation pass rate, retry count, token budget).

Be specific enough that an implementation team could start building without another design round.

---

### Product context

Stratum generates a **multi-week hypertrophy program** at the end of onboarding. Generation is **one bounded call per user** (no chat). LLM runs **only via Firebase Callable → OpenRouter** (`deepseek/deepseek-v4-pro` by default). Deterministic code owns progression math, volume accounting, and persistence long-term.

**Non-negotiables:**
- No provider API keys on device.
- Domain models never reach Compose directly.
- Database schema changes require explicit authorization — prefer computing transient planning state in memory on the server or in `:core:domain`, not new tables for UI state.
- `progressionScheme` is currently **server-assigned from `trainingAge`** after generation (model told to omit it). Challenge whether that should change.

---

### Current architecture (v1 — broken/slow)

**Client:** `LoadProgramGenerationPayloadUseCase` → large JSON payload → `generateProgram` callable → polls `getProgramGenerationStatus`.

**Server (`functions/src/program/generateProgram.ts`):**
- Single monolithic LLM job.
- **Phase A — tool loop:** up to `MAX_PROGRAM_VALIDATION_TOOL_TURNS = 6` turns with 7 validation tools (`validate_program_metrics`, duration, weekly volume, per-workout volume, frequency, ordering, push/pull). Model passes **full program draft** on each tool call.
- **Phase B — JSON generation:** up to `MAX_GENERATION_ATTEMPTS = 6` strict-schema completions + server metric validation + retry feedback.
- Function timeout: **600s**; stale slot reclaim: **10 min**.
- System prompt v4 tells model to merge landmarks, pick split, select lifts, assign sets/reps, validate via tools, then output full program JSON.

**Observed problems:**
- Wall-clock often **>10 minutes** or timeout before completion.
- Model produces tiny/broken programs (1 workout, 1 set) under pressure.
- Many sequential LLM round-trips when struggling.
- Large token payload every call.

---

### What the payload already does (verify assumptions)

**Enabled lifts only:** `LoadProgramGenerationPayloadUseCase` filters `liftRepository.observeLifts()` with `.filter { it.enabled }`, then splits:
- **`provenLifts`:** enabled + in `importAnalysis.provenLiftIds` + has `trainingInsights`
- **`availableLifts`:** other enabled lifts

**Not on each lift object today:**
- Explicit **`priority` / `rank`** field per lift. `liftEffectiveness` (rank, effectivenessScore, pattern) lives in **`responseProfile.liftEffectiveness`** — model must cross-reference by `liftId`. Proven lifts carry `trainingInsights` (sessionCount, estimatedOneRm, fatigueDecaySlope, progressTrend) when available.

**Always included (possibly wasteful):**
- Full `literatureVolumeLandmarks` for all muscles on every call.
- Full `responseProfile` when present.
- `supersetAffinities`, `volumeBaselines`, `importAnalysis`.

**Server post-pass:** assigns progression scheme, clamps RIR, enforces straight sets, strips invalid lift IDs.

---

### Proposed direction (from product owner — challenge this)

**Goal:** ~**3 minutes**, high success rate, high quality.

**Hypotheses to validate or refute:**

1. **Phase 0 — deterministic precompute (domain/server):**
   - Merge volume landmarks (personal > literature > baselines).
   - Compute weekly volume targets per muscle from specialization + `lastTrained` + volume response signals.
   - Account for `sessionLengthMinutes` when allocating volume (so later phases don’t need duration validation).
   - Possibly choose default split template from `trainingDaysPerWeek` + frequency rules.

2. **Phase 1 — “Split planner” (small LLM call OR fully deterministic):**
   - Output: workout slots (weekday binding), muscle focus per day, **per-day volume budgets** that sum to weekly targets.
   - Minimal payload: profile, specialization, weekly targets, session length — **not** full lift catalog.

3. **Phase 2 — Parallel “workout builders” (N LLM calls):**
   - One call per workout, run in parallel.
   - Input: that day’s budget, enabled lifts **with explicit priority** (recent use, proven, liftEffectiveness rank, progressTrend).
   - Output: exercises, set counts, ordering, initial rep ranges, maybe progression hints.
   - Tools: **volume validation only** (per-workout muscle volume matches budget) — **not** duration if budgets pre-respect time.

4. **Phase 3 — deterministic integration:**
   - Merge workouts into program document.
   - Global validation (weekly volume, frequency, push/pull).
   - **Fatigue decay → rep range adjustment:** use `fatigueDecaySlope` / intercept from training insights to bias rep ranges (e.g., steep decay → lower end of range or fewer reps on backoff sets). Decide top-down vs bottom-up.
   - Assign `progressionScheme` deterministically unless there is strong evidence LLM should choose per lift.
   - Targeted re-gen: if one workout fails validation, re-run **only that workout** (not whole program).

5. **Lift payload improvements:**
   - Only enabled lifts (already true) but **denormalize priority** onto each lift for builders.
   - Sort/truncate lift list per workout by relevance (pattern + muscles for that day).

**Owner intuition:** offload math and constraints to code; use LLM for **exercise selection, ordering, qualitative judgment** (rep range starting points, variation).

---

### Domain capabilities already in codebase (use in design)

- `ProgramGenerationPromptBuilder` — payload assembly.
- `programValidation.ts` — duration, weekly/per-workout volume, frequency, ordering, push/pull.
- `TrainingInsightsExtractor` — fatigue decay slope/intercept per lift from import history.
- `LiftEffectivenessAnalyzer` — rank per movement pattern.
- Volume landmark estimation + literature priors.
- `parseAndNormalizeProgram` / `programSchema.ts` — strict JSON + lift ID sanitization.
- Server already strips lifts not in enabled set.

Read these paths if you have repo access:
- `functions/src/program/generateProgram.ts`
- `functions/src/program/programGenerationSystemPrompt.ts`
- `functions/src/program/programValidation.ts`
- `core/domain/src/main/kotlin/com/stratum/domain/program/LoadProgramGenerationPayloadUseCase.kt`
- `core/domain/src/main/kotlin/com/stratum/domain/program/ProgramGenerationPromptBuilder.kt`

---

### Constraints you must optimize under

| Constraint | Value |
|------------|-------|
| Target latency | **~180s p50**, **<300s p95** |
| Hard ceiling | 600s function timeout |
| Parallelism | Firebase function can `Promise.all` workout builders; watch OpenRouter rate limits |
| Cost | Prefer fewer tokens; challenge whether 4 parallel calls beats 1 monolith on **total** cost |
| Reliability | Minimize retry loops; prefer deterministic repair over re-LLM |
| Quality bar | Every non-omitted muscle ≥ MEV; aim MAV; specialize → MAV–MRV; ~2× weekly frequency for majors; push/pull within ~25%; session fits time budget |

---

### Questions you MUST answer adversarially

1. **Should Phase 1 (split planner) be LLM at all?** Upper/Lower ×2 for 4 days may be fully deterministic. When does LLM split planning help vs hurt latency/reliability?
2. **Volume budgets per day:** Who computes them — planner LLM or deterministic allocator? What happens when the model’s per-workout sets don’t sum to budget — clamp, re-prompt, or deterministic set-count solver?
3. **Parallel workout agents without global view:** How do you prevent duplicate movement patterns, over-use of one lift, or push/pull drift across the week? Is a **shared constraint document** or **sequential allocation with parallel fill** better?
4. **Tool phase:** Is *any* model-side tool loop needed in v2, or should validation be **server-only** with structured error feedback on retry?
5. **Rep ranges:** Should LLM propose them at all if fatigue decay + training age + lift type can be a deterministic function? What does the model add that rules don’t?
6. **Progression scheme:** Keep server-assigned only, or let LLM choose per lift (Double vs Linear) based on insights? What’s the failure mode?
7. **Priority field:** Propose exact scoring function combining proven, sessionCount recency, liftEffectiveness rank, progressTrend, evidence tier. Should builders see **top-K lifts per pattern** only?
8. **Payload bloat:** Can `literatureVolumeLandmarks` be removed from workout-builder calls if weekly targets are precomputed? What is the minimum JSON per phase?
9. **Model choice:** Stay on DeepSeek v4 Pro for all phases, or fast model for builders + none for planner? Adversarially argue for **zero LLM** in planner.
10. **What to delete from v1:** Which of the 7 tools, 6 tool turns, 6 JSON attempts should be removed entirely?

---

### Anti-patterns to call out if proposed

- Sending full lift catalog + full landmarks to every parallel agent.
- Letting parallel agents each run their own tool loops (4× latency explosion).
- Re-validating workout duration when budgets already encode time.
- Cross-referencing `liftEffectiveness` by ID without denormalized priority (current pain).
- Caching completed programs blocking regeneration during iteration (separate issue, but note).
- Schema so large that strict JSON mode itself becomes a failure source.

---

### Output format

Use this structure:

```markdown
# Stratum Program Generation v2 — Adversarial Review

## Executive recommendation (1 paragraph)

## Recommended architecture (diagram + phase table)

## Division of labor: LLM vs deterministic

## Per-phase I/O schemas (sketch JSON shapes)

## Time budget (seconds per phase, parallelism)

## Validation & retry policy

## Lift priority & payload shaping

## Fatigue decay → rep range (recommendation)

## Failure modes & mitigations (≥10)

## Alternatives considered (≥2) and why rejected

## v1 shipping slice vs full v2

## Metrics & acceptance tests

## Open questions for product owner (only if truly blocking)
```

**Tone:** Skeptical, evidence-oriented, implementation-ready. If the owner’s parallel-workout idea is wrong, say so and propose something better. If it is right, sharpen it until it survives adversarial review.

---

### Quick facts for the reviewing agent (lift filtering)

You **already** send only enabled lifts. The gap is **not** “all catalog lifts” — it is that **priority/rank is not attached per lift** in the builder payload; effectiveness rank lives in `responseProfile` and proven status is a separate tier. The review should specify how to denormalize and cap the lift list per workout.
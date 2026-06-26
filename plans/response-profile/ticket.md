# Ticket: Response Profile — Training Response Detection

**Branch:** `feature/response-profile`
**Worktree:** `/home/sean/Code/Stratum-response-profile`
**Depends on:** OS-6 (historical import, `TrainingInsightsExtractor`, persisted insight tables),
OS-7 (program generation payload consumption)

## Goal

Detect what training stimuli a user responds well to — per-muscle volume tolerance, lift
effectiveness within movement patterns, and progress dynamics — from longitudinal set history.
Produce **confidence-scored, deterministic features** for program generation (not LLM-inferred
from raw logs).

Design the analytics layer so the same abstractions support **Tier 4 active learning**: bounded
volume/experiment blocks prescribed by Stratum, outcomes measured with the same response
machinery, and hypotheses updated over time.

## Problem framing

Historical response detection is noisy (lift mixture, program changes, lifestyle, import quality).
Stratum should:

1. Extract **stable personal patterns** over sufficient time horizons
2. Label outputs honestly (`proven` vs `hypothesis` vs insufficient data)
3. Never overclaim causation from passive logs alone
4. Reuse the same pipeline for **passive analysis** (import + ongoing logging) and **active
   micro-experiments** (deliberate bounded variations once users train in Stratum)

## Tier model (product intent)

| Tier | Capability | Data source |
|------|------------|-------------|
| 1 | Descriptive priors (e1RM, volume baselines, fatigue, progress trend) | **Exists** — `TrainingInsightsExtractor` |
| 2 | Within-user volume–response correlations per muscle (anchor lifts, epochs) | New — passive longitudinal |
| 3 | Lift effectiveness ranking within movement pattern | New — passive, higher bar |
| 4 | Active learning — bounded experiments, causal-ish signal | New — requires program structure + prescription vs outcome |

## Architecture constraints (from AGENTS.md / contracts)

- Analytics live in `:core:domain` (pure, deterministic, offline-capable)
- Domain defines ports/models; `:core:data` persists; `:core:inference` is Firebase client only
- Program generation consumes structured JSON payload — response features feed that payload
- No schema changes without explicit authorization — plan must call out any new tables/columns
- Unit tests: JUnit 5 + Turbine + fakes from `:core:testing`; no MockK for Stratum ports
- Intelligence: on-device deterministic engines; cloud LLM for synthesis only

## Reuse requirements for Tier 4

The design must treat these as first-class:

- **Epochs** — stable training periods (same primaries, no long gaps, deload handling)
- **Anchor lifts** — per muscle/pattern reference movements with enough history
- **Stimulus descriptors** — what was applied (volume, frequency, lift slot, intensity proxy)
- **Outcome descriptors** — what was measured (e1RM trend, weekly peak, progress slope)
- **Confidence / evidence** — weeks, sessions, consistency gates
- **Experiment registry** — link prescribed variation blocks to outcome windows (Tier 4)

Passive import analysis and in-app active experiments must share the same core types and
extractor interfaces — Tier 4 adds experiment orchestration, not a parallel analytics stack.

## Non-goals (v1 planning scope)

- Population-level ML or cloud training on user data
- Real-time inference during a workout
- UI for response dashboards (payload + program gen consumer first)
- Claiming optimal MEV/MRV with causal certainty from passive logs

## Success criteria

- Approved design + structure + plan artifacts under `plans/response-profile/`
- Clear domain model for response profiles reusable across tiers 2–4
- Integration point with `LoadProgramGenerationPayloadUseCase` / prompt builder documented
- Test strategy for synthetic epoch fixtures and confidence gating
- Explicit minimum-data thresholds and honest labeling rules

## Open product questions (for Design phase)

- Which muscles/patterns get anchor lifts when history is sparse?
- How do deload weeks and gaps >N days split epochs?
- What confidence threshold gates influence on program generation vs informational only?
- How does Tier 4 experiment scheduling interact with progression engine (future)?

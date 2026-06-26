# Response Profile — Design

**Ticket:** `response-profile` | **Phase:** Design (Synthitect)  
**Grounding:** `research.md`, `ticket.md`, verified domain sources in worktree

---

## Current State

Stratum ships **Tier 1** analytics via `TrainingInsightsExtractor` (`:core:domain/importing`).
`ExtractTrainingInsightsUseCase` reads all sessions/sets/lifts, runs `analyze`, and `replaceAll`s
four insight repositories. Failure rolls back history (`ImportUseCases.kt:408-410`).

| Tier 1 output | Key fields | Labeling |
|---------------|------------|----------|
| `LiftTrainingInsights` | e1RM, fatigue decay, `ProgressTrend`, session count | None |
| `MuscleVolumeBaseline` | mean/p90 weekly hard sets, trend slope | None |
| `SupersetAffinity` | co-occurrence, median rest, `confidence` | Pair-level only |

Window: max 183 days, min 12 weeks when possible. Sets with `liftId == null` excluded from lift,
superset, and volume analytics. `LoggedSet.rir` exists but is unused. Hard-set counting differs:
primary-only for window gate, primary+secondary for baselines (`TrainingInsightsExtractor.kt:209-249`).

`LoadProgramGenerationPayloadUseCase` + `ProgramGenerationPromptBuilder` emit Tier 1 JSON; no
production consumer yet (OS-7 absent). Payload omits superset confidence and metadata proven IDs.

**Absent:** epoch segmentation, anchor lifts, stimulus/outcome descriptors, experiment registry,
pattern effectiveness ranking, evidence-tier labeling, program prescription linkage. DB version 1;
no `programs`/`workouts` tables. No WorkManager re-extraction. Extract use case re-runnable but
only invoked from import persist path.

---

## Desired End State

Stratum deterministically derives **response profiles** from longitudinal history and (later)
prescribed experiment blocks — no LLM reads raw logs.

**Passive path (import + future ongoing logging):**

1. History segmented into **epochs** (stable primaries, gap/deload rules).
2. Per **muscle**, **volume–response correlations** within epochs via **anchor lifts**, with
   `EvidenceLevel` (`Proven`, `Hypothesis`, `InsufficientData`).
3. Per **movement pattern**, **lift effectiveness** ranking with higher minimum-data bar than Tier 2.
4. Results persist via domain ports; program-generation JSON gains `responseProfile` — only
   `Proven` and high-confidence `Hypothesis` influence slotting hints.
5. Orchestrator runs Tier 1 unchanged, then response extraction on the same snapshot.

**Tier 4-ready (types/interfaces in v1; orchestration deferred):** `ExperimentRegistry` links
prescribed `StimulusDescriptor` to `OutcomeDescriptor` windows. Passive and active paths share
`ResponseObservationExtractor` — Tier 4 adds scheduling, not parallel analytics.

**Non-goals v1:** response UI, population ML, in-workout inference, causal claims from passive logs.

---

## Patterns to Follow

- Pure domain extractors; persistence in use cases + `:core:data` — mirror Tier 1 pipeline.
- `replaceAll` repository semantics for insight-like tables.
- Reuse `EstimatedOneRm`, `ProgressTrend`, `MuscleGroup`, `MovementPattern`, lift muscle maps.
- Import hook after lift resolution; rollback clears response tables with existing insight tables.
- Extend payload use case/builder; OS-7 reads persisted features only.
- JUnit 5 + synthetic epoch fixtures; promote fakes to `:core:testing` when reused.

## Patterns to Avoid

- LLM inferring from raw logs (`AGENTS.md` §4.6).
- Schema changes without explicit authorization (`AGENTS.md` §4.5).
- Bloating `TrainingInsightsExtractor` — response logic in dedicated package.
- Causal language on passive Tier 2/3 — default `Hypothesis` unless multi-epoch or Tier 4 registry.
- Separate analytics stacks for import vs in-app experiments.
- MockK on Stratum ports.

---

## Design Decisions

### D1 — Parallel `response` package, compose with Tier 1

**Choice:** `com.stratum.domain.response` for models, ports, extractors, use cases. Keep
`TrainingInsightsExtractor` unchanged. Orchestrator calls Tier 1 then `ResponseProfileExtractor`.

**Rationale:** Response analytics spans import, logging, experiments; separate package clarifies
ownership.

**Rejected:** Extend `TrainingInsightsExtractor` (threshold sprawl); Gradle submodule (premature).

### D2 — Shared observation primitives (Tier 2–4 core)

**Choice:** First-class types and one extractor interface:

```kotlin
data class Epoch(val id: EpochId, val start: Instant, val end: Instant,
    val anchorLiftIds: Set<LiftId>, val splitReason: EpochSplitReason)

data class StimulusDescriptor(val epochId: EpochId?, val experimentId: ExperimentId?,
    val muscle: MuscleGroup?, val pattern: MovementPattern?, val liftId: LiftId?,
    val weeklyHardSets: Double, val sessionsPerWeek: Double, val intensityProxy: Double?)

data class OutcomeDescriptor(val anchorLiftId: LiftId, val metric: OutcomeMetric,
    val value: Double, val windowWeeks: Int)

enum class EvidenceLevel { Proven, Hypothesis, InsufficientData }

data class VolumeResponseObservation(/* muscle, epochId, stimulus, outcome, correlation, evidence */)
data class LiftEffectivenessObservation(/* pattern, liftId, score, evidence, peerLiftCount */)

interface ResponseObservationExtractor {
    fun extract(input: ResponseExtractionInput): ResponseExtractionOutput
}
```

Input carries sessions/sets/lifts, optional registry entries, Tier 1 outputs for reuse.

**Rejected:** Per-tier extractors duplicating epoch splitting and e1RM windows.

### D3 — Epoch splitting and change-point detection

**Choice:** Greedy chronological split on gap > `MAX_EPOCH_GAP_DAYS` (14) or primary-set Jaccard
change > 0.5 over rolling 4 weeks. Fixed thresholds in v1 (not varied by `TrainingAge`).
Add a lightweight binary-segmentation detector over cleaned, smoothed weekly signals (volume,
frequency, and anchor-lift e1RM) with a strict minimum segment length so noise cannot create tiny
"blocks." Gap and lift-churn splits are hard boundaries; binary segmentation only proposes
additional boundaries inside long enough regions.

**Deload week exclusion** (excluded from correlation fit; does not split epoch):

- Volume deload: weekly hard sets < 50% of epoch median weekly volume, no anchor e1RM PR.
- Intensity deload: weekly mean working weight on anchor lift drops > 15% vs prior 4-week rolling
  mean while weekly set count stays within ±20% of epoch median (volume steady, weight down).

**Rejected:** PELT in v1 (more tuning surface than payoff); TrainingAge-varying epoch thresholds
in v1.

### D4 — Anchor lifts and sparse-history policy

**Choice:** Per muscle with sufficient history only — do **not** attempt all 18 muscle groups when
data is sparse. Rank candidate lifts by sessions × recency; select top meeting `MIN_ANCHOR_SESSIONS`
(8) and `MIN_ANCHOR_WEEKS` (6). Muscles below gate → skip entirely (`InsufficientData`, no partial
fit). Prioritize muscles that appear in proven-lift primary muscle map and specialization
`Specialize` list when both are available.

**Rejected:** User-configurable anchors in v1; blanket coverage of all muscle groups regardless of
sample size.

### D5 — Tier 2 volume–response

**Choice:** Weekly series (muscle hard sets vs anchor e1RM/trend). OLS slope when ≥4 weekly points
(reuse Tier 1 constant) and ≥8 epoch weeks. Evidence: `Proven` if ≥2 epochs agree in sign;
`Hypothesis` if one epoch above noise floor; else `InsufficientData`.

**Rejected:** Single-epoch → `Proven`.

### D6 — Tier 3 pattern effectiveness

**Choice:** Per epoch, `effectivenessScore = Δe1RM_per_week / mean_weekly_hard_sets` for lifts with
≥6 sessions. Rank within `MovementPattern` when ≥2 peers; top must exceed runner-up by 20%.
`Proven` only if rank stable across ≥2 epochs.

**Rejected:** Cross-pattern comparison.

### D7 — Experiment registry (Tier 4-ready stub)

**Choice:** Domain types + port; passive path passes empty registry. Prescription populates when
`:feature:workout` / `:feature:program` exist.

```kotlin
data class ExperimentBlock(val id: ExperimentId, val prescribed: StimulusDescriptor,
    val outcomeWindowStart: Instant, val outcomeWindowEnd: Instant, val status: ExperimentStatus)
interface ExperimentRegistry { fun active(): List<ExperimentBlock>; fun record(block: ExperimentBlock) }
```

**Rejected:** Inferring experiments from passive volume spikes.

### D8 — Persistence (**authorized — stay Room v1, no migrations**)

**Choice:** Add replace-all tables to existing schema version 1 (uninstall/reinstall dev policy):

| Table | Aggregate |
|-------|-----------|
| `response_epochs` | `Epoch` |
| `volume_response_observations` | `VolumeResponseObservation` |
| `lift_effectiveness_observations` | `LiftEffectivenessObservation` |
| `experiment_blocks` | `ExperimentBlock` |
| `response_profile_metadata` | `ResponseProfileMetadata` |

Ports mirror existing insight repos. Rollback/reconcile extended.

**Rejected:** JSON blob on metadata (poor queryability); extending `lift_training_insights` (conflates tiers).

### D9 — Program generation integration

**Choice:** Optional top-level `responseProfile` JSON with `volumeResponses`, `liftEffectiveness`,
`epochsAnalyzed`, `analysisWindow`. Builder omits `InsufficientData` from influence arrays.

**Rejected:** Embedding in per-lift `trainingInsights` (mixes Tier 1 facts with Tier 2/3 inferences).

### D10 — Re-extraction trigger

**Choice:** v1 runs on import orchestration. Future ongoing logging: re-extract on a **4-week
cadence** (mesocycle-aligned), not per workout or per week — insufficient delta between shorter
intervals for statistical signal. Document WorkManager extension point keyed to 28-day minimum
interval since last extraction.

### D11 — Intensity proxy (RIR-first)

**Choice:** `StimulusDescriptor.intensityProxy` derived from **RIR** when present on ≥70% of hard
sets in a week (mean RIR inverted: lower RIR = higher intensity). Fall back to load×reps e1RM proxy
only when RIR coverage is below threshold.

**Rejected:** Weight×reps-only intensity in v1.

### D12 — Payload influence policy

**Choice:**

- `Proven` observations always included in influence arrays (volume slotting, lift preference).
- `Hypothesis` included only when `confidenceScore >= 0.65` (single-epoch correlation above noise
  floor); otherwise serialized under `informational` (LLM may read but must not treat as constraint).
- `InsufficientData` omitted from payload entirely.

**Rejected:** Treating all `Hypothesis` rows as hard constraints.

### D13 — Clean training signal pipeline

**Choice:** Response extraction starts from a cleaned analysis dataset, not raw `LoggedSet` rows.
The cleaner produces normalized working-set rows, per-session lift summaries, and weekly signal
series. It owns:

- Unit normalization to a single numeric unit for within-lift comparisons.
- Working-set classification: prefer RIR (exclude high-RIR warmups), then relative-load rules
  against the session top set when RIR is missing.
- RIR-adjusted e1RM for outcome series when RIR is present and plausible; existing Brzycki/Epley
  remains the base formula.
- Session aggregation: top valid e1RM or robust mean of the hardest 1–2 valid sets per lift.
- Data-quality flags for missing RIR, sparse sessions, mixed units, suspected warmup-only sessions,
  outliers, and suspicious jumps.

**Rationale:** The current Tier 1 code counts every resolved set and computes e1RM directly from
weight/reps. That is acceptable for descriptive onboarding, but response detection needs a cleaner
signal to avoid learning from warmups, logging errors, or equipment shifts.

**Rejected:** Feeding raw sets directly to epoch detection and response regression.

### D14 — Robust weekly series before regression

**Choice:** Build weekly aggregates early and apply robust preprocessing before epoch detection and
volume-response analysis:

- Rolling median smoothing over 3 weeks for noisy e1RM / volume inputs.
- MAD-based outlier detection per lift series; outliers are winsorized for regression and lower
  confidence.
- Week-to-week e1RM jumps > 15% without a preceding gap are treated as suspicious; they may create
  an equipment-shift boundary or reduce confidence.
- Store reliability metrics: weekly points used, data completeness, outlier count, and regression
  reliability (R²) on observations.

**Rejected:** Overall-history regression without block segmentation; raw weekly series without
robust smoothing.

### D15 — Effect size over "optimal" claims

**Choice:** v1 outputs observed associations and effective observed ranges, not personal MEV/MRV
claims. Volume-response observations include slope, R², confidence, and the observed weekly-set
range. When enough clean blocks exist, derive a `bestObservedVolumeRange`; otherwise omit it.

**Rejected:** Declaring an optimal volume range from one block or from sparse data.

### D16 — Statistical method selection

**Choice:** Use simple, auditable statistics first:

- Ordinary least squares for within-block e1RM trajectory and volume-response slope.
- Pearson correlation when both variables are approximately linear after smoothing.
- Spearman rank correlation as a fallback for monotonic but non-linear block-level relationships.
- Binary segmentation for additional block boundaries when minimum-length and penalty gates pass.

PELT and bootstrap uncertainty are documented upgrade paths after v1 fixtures expose enough real
edge cases to tune penalties responsibly.

**Rejected:** Hidden ML model, population training, or opaque black-box scoring.

---

## Human-Resolved Decisions (2026-06-25)

| Topic | Resolution |
|-------|------------|
| Math / stats | Agent judgment on OLS, gates, noise floors — no ML in v1 |
| Schema | Authorized; add tables at Room v1, no migration path |
| Sparse history | Skip muscles below minimum-data gates; no all-18 coverage |
| Deload | Volume rule + large weight drop with steady volume |
| Re-extract cadence | Every ~4 weeks / mesocycle end, not per session |
| RIR | Primary intensity signal |
| Epoch thresholds | Fixed 14-day gap + 0.5 Jaccard (agent judgment) |
| Minimum data gates | 8 anchor sessions, 6 lift sessions, 8 epoch weeks, 2 epochs for `Proven` (agent judgment) |
| Experiment table | Include `experiment_blocks` in v1 schema as Tier 4 stub |

---

## Test Strategy (Design-Level)

- `ResponseProfileExtractorTest`: multi-epoch fixtures (gap, program change, deload, sparse muscle).
- Evidence gating: single epoch never `Proven`; no anchor → `InsufficientData`.
- `ProgramGenerationPromptBuilderTest`: `responseProfile` serialization and omission rules.
- Orchestrator + rollback test with in-memory repos.

## Module Touch Map

| Layer | Artifacts |
|-------|-----------|
| `:core:domain/response/` | Models, constants, extractor, ports, use case |
| `:core:domain/importing/` | Orchestrator hook |
| `:core:domain/program/` | Payload extensions |
| `:core:data/`, `:app/di/` | Entities/repos after schema waiver |

No `:core:inference` or feature UI in v1.

# Ticket: Onboarding Generate Program (OS-7)

**Branch:** `feature/onboarding-generate-program`
**Vertical slice:** OS-7 — LLM program generation
**Depends on:** OS-0 (Google Sign-In gate), OS-5 (Review), OS-6 (historical import + persisted
insights). Also consumes `response-profile` planning artifacts under `plans/response-profile/`.

## Goal

User taps **Generate program** on the Review step → a single structured LLM call produces a
complete program tailored to all onboarding inputs → persisted locally → onboarding complete →
user lands on a new **Programs** screen where they can view and modify programs and workouts.

## Source of truth

`docs/onboarding-vertical-slices.md` §OS-7 (and §2.3 progression, §2.4 hierarchy, §2.5/§2.7
intelligence + credentials). Read it before designing.

## What the user asked for

- Click "Generate" → LLM is given all metrics + context collected through the rest of onboarding.
- Need a new **Program** and **Workout** entity to accommodate the output.
- New **Firebase Callable Function** (`generateProgram`) that does the generation via OpenRouter
  (server-side only; never ship provider keys in the APK).
- Plausibly give the LLM **tool access** to execute volume calculations (the deterministic core
  owns volume math; evaluate whether the server function exposes deterministic tools to the
  model vs. the model proposing targets in one structured shot).
- Final deliverable: the completed generated program persisted, and the user taken to a new
  **Programs** screen for view/modify.

## Locked design choices (from vertical slices — implement, don't relitigate)

- Hierarchy: `Program → Workout → WorkoutLift → Set`. `Set` carries `rirGoal`, optional
  `repRangeMin`/`repRangeMax` (top set only on Double), `type: Standard | MyoRep | DropSet`.
- Progression: exactly two schemes — `Linear` (Beginners) / `Double` (everyone else). Auto-assigned
  per `WorkoutLift` from `TrainingAge` at generation. Not user-configurable in v1.
- Straight sets only (no within-session weight drops).
- Intelligence split: deterministic engines own progression/volume math; LLM owns synthesis.
  No on-device LLM. All LLM work goes through Firebase Callable Function → OpenRouter.
- Credentials: `OPENROUTER_API_KEY` + `OPENROUTER_MODEL` (`deepseek/deepseek-v4-pro`) already in
  Firebase Secret Manager (provisioned — do not re-prompt). Function reads them at runtime.
- Auth: OS-0 Google Sign-In session + App Check required on the Callable. Per-uid rate limit
  (exactly one bounded generation call per onboarding completion).
- Structured output: OpenRouter **strict JSON schema** matching the domain hierarchy.

## Prompt composition (per §OS-7)

- System instructions: bundled asset with §2.3 progression rules, split templates, volume
  landmarks (MEV/MRV), advanced techniques, push/pull balance, time-budget heuristics.
- User context: serialized onboarding snapshot — profile (`TrainingAge` for scheme), schedule,
  specialization, enabled lifts split into `provenLifts` (Tier A, with insights) and
  `availableLifts` (Tier B, the substitution pool); plus `SupersetAffinity`,
  `MuscleVolumeBaseline`, `ImportAnalysisMetadata` when import path used.
- The model must select only from the user's enabled lifts.

## Open questions (for Design to surface — not pre-decided)

- §10 still-open: normalized program tables vs single JSON blob (vertical slices prefers
  normalized for upcoming workout logging).
- Volume tool access: single-shot structured generation vs. a server tool loop exposing
  deterministic volume validation to the model. (pocket-crew import uses a tool loop; §OS-7
  prescribes single-shot. Design should recommend and justify.)
- New Programs screen scope: read-only overview now, edit affordances later? Routing from
  onboarding completion.

## Architecture constraints (from AGENTS.md / contracts)

- `:core:domain`: `ProgramGeneratorPort`, `GenerateProgramUseCase`,
  `ProgramGenerationPromptBuilder`, domain models. Pure, no framework deps.
- `:core:inference`: `FirebaseProgramGenerator` — Callable client only (no Compose, no direct
  OpenRouter call). Parses response JSON → domain.
- `:core:data`: `ProgramRepository` persisting parsed program; Room entities + DAOs.
- `functions/` (Firebase, Node/TS): `generateProgram` Callable — auth + App Check, rate limit,
  secret binding, OpenRouter strict-schema call.
- `:feature:onboarding`: `GenerateStepViewModel` (loading/error), `:feature:program` (new) for
  the Programs screen, or extend `:app` routing.
- No schema changes without explicit authorization — plan must call out new tables/columns
  (`programs`, `workouts`, `workout_lifts`, `sets` per §OS-7).
- Unit tests: JUnit 5 + Turbine + fakes from `:core:testing`; no MockK for Stratum ports.
  Contract tests use recorded OpenRouter response fixtures — no live API in CI.

## Success criteria

- Approved research → design → structure → plan artifacts under
  `plans/onboarding-generate-program/`.
- Clear domain model + persistence schema + server function contract.
- Prompt builder + structured-output schema documented.
- Programs screen routing and view model sketched.
- Test strategy (use-case, prompt builder, JSON fixture deserialization, DAO round-trip).

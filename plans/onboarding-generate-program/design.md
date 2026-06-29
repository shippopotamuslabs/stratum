# Design — Onboarding Generate Program (OS-7)

**Ticket:** `onboarding-generate-program` | **Phase:** Design (Synthitect)
**Status:** awaiting human approval
**Grounded in:** `plans/onboarding-generate-program/research.md` (verified), `docs/onboarding-vertical-slices.md` §OS-7 + §2.3/§2.4/§2.5/§2.7/§10, `AGENTS.md` §4.

This is an alignment artifact, not a spec. It records decisions and rejected alternatives.
Tactical structure (files, signatures, test list) belongs to Structure/Plan.

---

## Current state

OS-7 is greenfield except for the prompt-input path and the inference/function scaffolding,
both already in place and tested.

- **Prompt input exists and is tested.** `ProgramGenerationPromptBuilder.buildPayload(...)`
  (`core/domain/.../program/ProgramGenerationPromptBuilder.kt`) emits the JSON user-context
  payload (`profile`, `specialization`, `provenLifts[]`, `availableLifts[]`, optional
  `supersetAffinities`/`volumeBaselines`/`importAnalysis`/`responseProfile`).
  `LoadProgramGenerationPayloadUseCase.invoke()` assembles it from repository ports and is
  the single domain entry point the Generate step should call. Covered by
  `ProgramGenerationPromptBuilderTest` + `ResponseProfilePayloadTest`. **No production
  consumer exists yet.**
- **Inference + Functions scaffolding exists with a ready-to-mirror single-shot pattern.**
  `:core:inference` is a Firebase Callable/Storage client (no Compose). The closest analog
  to OS-7's `FirebaseProgramGenerator` is `FirebaseImportEquipmentInferenceAdapter`:
  `@Singleton @Inject constructor()`, field-init `FirebaseFunctions`/`FirebaseAuth`,
  `functions.getHttpsCallable(name).call(payload).await()`, `Result<T> = runCatching { … }`,
  `requireAuth()` via `auth.currentUser`. DI is `InferenceModule` (`@Binds @Singleton`).
  Server-side, `functions/src/import/inferImportEquipment.ts` is the closest single-shot
  callable: `onCall({ secrets: [...] })`, auth gate, `process.env` secret read,
  `createOpenRouterClient` + `buildOpenRouterChatCompletionExtras`, strict
  `response_format: { type: "json_schema", json_schema: ... }`, `JSON.parse` +
  `parseInferredEquipment` sanitizer, retry-free single completion. Strict-schema builder
  shape lives in `inferImportEquipmentSchema.ts` (`strict: true`,
  `additionalProperties: false`, per-field `enum`). Session/routing helpers are in
  `functions/src/openrouterSession.ts` / `openrouterRouting.ts`.
- **Greenfield for OS-7:** the `Program → Workout → WorkoutLift → Set` domain models,
  `ProgressionScheme`/`SetType` enums, `ProgramGeneratorPort`, `GenerateProgramUseCase`,
  the `programs`/`workouts`/`workout_lifts`/`sets` Room tables, the `generateProgram`
  Callable, the `:feature:program` module, and the Generate step wiring.
- **Generate step is a stub.** `GenerateScreen` (`OnboardingRoute.kt:209`) renders a title
  + a `StratumButton` whose `onClick = onComplete`. `onComplete` →
  `OnboardingViewModel.complete()` → `CompleteOnboardingUseCase`
  (`profileRepository.saveProfile` + `onboardingRepository.markComplete`). **No payload
  load, no generation, no persistence.** `ReviewScreen` is read-only and renders no
  Generate button. On completion, `StratumNavHost` removes `OnboardingKey` and adds
  `HomeKey` → `HomePlaceholder` ("Home lands in a later slice").
- **DB at v1, 24 entities, no program tables.** Migration policy (ARCHITECTURE.md §6,
  AGENTS.md §4.5): keep `StratumDatabase` at v1 with no migrations until first store
  release; apply entity changes by **adding tables to the v1 schema and reinstalling**
  (no version bump pre-release). New tables require explicit authorization.

## Desired end state (observable)

1. User reaches the **Generate** step (already the last wizard step) and taps Generate.
2. A single bounded server call (`generateProgram` Callable, Auth + App Check, per-uid
   rate limit of one generation per onboarding completion) sends the assembled payload to
   OpenRouter (`OPENROUTER_MODEL` = `deepseek/deepseek-v4-pro`) under a strict JSON
   schema; the model returns one structured `Program`.
3. The server validates + deterministically normalizes the document, returns it.
4. `:core:inference` parses response JSON → domain `Program`; `ProgramRepository`
   persists the full hierarchy locally (Room).
5. On success, onboarding is marked complete and the user lands on the new **Programs**
   screen (read-only overview) — replacing the `HomePlaceholder` route.
6. Failure (offline / unauthenticated / rate-limited / schema-invalid after one retry)
   shows a retry + back-to-review error state; onboarding is **not** marked complete.

## Patterns to follow

- **Port shape:** inference ports return `suspend fun ... : Result<T>` (mirror
  `ImportEquipmentInferencePort` / `HistoricalImportPort`); repository ports expose
  `Flow` observers + suspend writes; use cases are `@Inject constructor class` with
  `operator fun invoke(...)`. Only `javax.inject.Inject` in `:core:domain`.
- **DI:** add a `@Binds @Singleton` binding in `InferenceModule` for
  `ProgramGeneratorPort` → `FirebaseProgramGenerator`; bind the new repository port →
  Room impl in `app/.../di/DataModule` `RepositoryModule`.
- **Repository writes:** mirror the existing `replaceAll` (delete-all-then-insert) pattern
  for collection children and singleton-upsert for the program root, in one transaction.
- **IDs:** value-class IDs only (`LiftId`, `EntityId` are the precedent) — define
  `ProgramId`/`WorkoutId`/`WorkoutLiftId`/`SetId`. Generate IDs client-side at parse time
  (e.g. UUID), not server auto-increment.
- **Enums in Room:** extend the single `EnumConverters` `@TypeConverter` class for
  `ProgressionScheme` and `SetType`; entity columns typed as enums.
- **Server callable:** mirror `inferImportEquipment.ts` exactly — `onCall({ secrets:
  ["OPENROUTER_API_KEY", "OPENROUTER_MODEL"] })`, auth gate, secret read with
  `failed-precondition` error, `createOpenRouterClient` +
  `buildOpenRouterChatCompletionExtras(sessionId, { requireParameters: true })`, strict
  `json_schema` (`strict: true`, `additionalProperties: false`, closed `enum`s for
  `progressionScheme`/`setType`/`movementPattern`), `JSON.parse` + a sanitizer/parser
  that drops/rejects invalid rows. Place under `functions/src/program/`.
- **Tests:** JUnit5 + Turbine + fakes from `:core:testing` (add `FakeProgramGeneratorPort`
  + a `FakeProgramRepository`); Robolectric DAO round-trip in `:core:data`; recorded
  OpenRouter response fixtures for the contract test (no live API in CI); Node `node --test`
  for the server schema/parser. No MockK on owned ports.

## Patterns to avoid (do not copy from the codebase)

- **LiftLab's 5-variant `ProgressionScheme` enum** (`WAVE_LOADING, LINEAR, DOUBLE,
  DYNAMIC_DOUBLE, TOP_SET`). Stratum is locked to `Linear | Double` only (§2.3). Do not
  port the full enum or any scheme-picker UI.
- **LiftLab `Long` auto-IDs** on `Program`/`Workout`. Use Stratum value-class IDs.
- **On-device OpenRouter / OpenAI SDK calls.** `:core:inference` is a Callable client
  only; the model is called solely from `functions/` (AGENTS.md §4.6).
- **ViewModels calling repository ports directly.** Go through `GenerateProgramUseCase`
  (ARCHITECTURE.md §4.4).
- **DB migrations pre-release.** Bump `StratumDatabase` version and reinstall; do not
  author a v1→v2 migration (ARCHITECTURE.md §6).
- **The import-path tool loop.** OS-6's `ImportAgentSession` uses an OpenRouter tool loop
  because it writes/executes conversion scripts. Generation is pure synthesis and is
  single-shot (§OS-7) — do not mirror the tool-loop architecture.

## Design decisions

### D1 — Normalized program tables (not a JSON blob)

**Decision:** persist the hierarchy as four normalized tables
(`programs`, `workouts`, `workout_lifts`, `sets`) with value-class PKs and FK
parent→child, mapped through `EnumConverters`, written in one transaction via
`replaceAll`. Schema stays at **DB v1** (maintainer-approved, 2026-06-26): the four
tables are added to the v1 entity set and picked up via reinstall — no version bump, no
migration authored.

**Rationale:** §10 explicitly prefers normalized for the upcoming workout-logging slice
(`:feature:workout`). Normalized rows are queryable (next workout by weekday, a single
lift's set template, "start session" lookups), joinable to `lifts`, and individually
mutable by `:feature:program` edits. The deterministic progression engine at runtime
needs row-level access to `WorkoutLift`/`Set`; a blob would force a full parse/rewrite on
every logged set.

**Rejected — single JSON blob on `programs.program_json`:** opaque to Room/SQL, cannot
query or join, forces wholesale rewrite on any edit or log, blocks the next slice. Kept
only as a fallback if normalization proves too heavy — it does not here.

### D2 — Single-shot structured generation with a deterministic validate-and-clamp post-pass (not a server tool loop)

**Decision:** one OpenRouter completion under a strict `Program` JSON schema. The server
then **validates** (schema; every lift `id ∈ enabledLifts`; at least one workout;
Double lifts have exactly one top set with a rep range) and **deterministically
normalizes** before returning:
- Assign `ProgressionScheme` per `WorkoutLift` from `TrainingAge` (`Beginner → Linear`,
  else `Double`) — the model's proposed scheme is **ignored/overwritten** here, not trusted.
- Clamp back-off RIR to the §2.3 lift-class table (major compound 1–2, default 2;
  everything else 0); top-set RIR fixed at 2 on Double.
- Enforce straight sets (single load per lift; no within-set weight field accepted).
On schema/validation failure, retry the completion **once**; a second failure returns an
error and onboarding is not completed.

**Rationale (addresses the user's "give the LLM tool access to volume calculations"):**
§OS-7 prescribes single-shot and the deterministic core owns volume/progression math
while the LLM owns synthesis. A tool loop is the wrong shape here: generation is a single
synthesis act (select lifts, build a split, propose set/rep structure), not an iterative
script-and-execute task like import. A loop adds latency, cost, and unbounded turns that
violate the "exactly one bounded generation per onboarding completion" abuse boundary.
The model is bad at exact arithmetic and good at structure — so we let it propose
structure and let deterministic code own every numeric rule. The validate-and-clamp
post-pass is the correct trust boundary: **accept the model's structure; recompute its
derived numeric rules.**

**Rejected — server tool loop exposing deterministic volume validation to the model**
(the pocket-crew-import-shaped approach): the model would call a `validateVolume` tool,
read the verdict, and re-propose. Rejected because (a) it contradicts §OS-7 and the
one-call rate limit, (b) volume math is already deterministic so feeding it back to the
model only to have the model echo it adds a round trip with no synthesis gain, and
(c) it reintroduces the unbounded-turn risk the abuse boundary exists to prevent. The
deterministic pass runs **after** the single call, not as a tool **during** it.

### D3 — Programs screen: read-only overview in OS-7; modify deferred

**Decision:** OS-7 lands a new `:feature:program` module with a **read-only** Programs
overview: Program header → list of Workouts (weekday-bound where possible) → expand to
WorkoutLifts → Sets (rep range, RIR goal, set type). Edit/swap/regenerate affordances are
**out of scope for OS-7** and deferred to a `:feature:program` follow-up / OS-8.

**Rationale:** the ticket says "view and modify," but landing full edit in the generation
slice risks scope creep and couples two independently shippable concerns. OS-7 must,
at minimum, deposit the user somewhere coherent after generation; a faithful read-only
view of the just-generated program satisfies that and the §OS-7 acceptance "Home screen
shows program name or first workout." The Generate step's loading/error UI stays in
`:feature:onboarding`; the post-completion destination moves to `:feature:program`.

**Routing:** on completion, `StratumNavHost` adds a new `ProgramsKey` (and removes
`OnboardingKey`) instead of `HomeKey`/`HomePlaceholder`. `:feature:program` registers via
`entryProvider`. The Programs overview is the post-onboarding landing for now; a future
Home/Hub slice may sit above it. This also resolves the §10 "Home destination after
onboarding" item for OS-7 (program overview), with a dedicated hub explicitly deferred.

**Rejected — full view+modify in OS-7:** blows out the slice, mixes generation-correctness
risk with edit-persistence risk, and delays the generation PR. **Rejected — keep
`HomePlaceholder` as the landing:** leaves the user staring at a stub after generation,
failing the acceptance criterion.

### D4 — Generate CTA lives on the existing Generate step (replace the stub), not on Review

**Decision:** generation kicks off from the existing **Generate** step
(`WIZARD_STEP_GENERATE`), replacing the current stub button. The Generate button triggers
`GenerateProgramUseCase` (load payload → call `ProgramGeneratorPort` → persist → mark
complete); the step surfaces loading ("Building your program…") and error (retry +
back-to-review) states.

**Rationale:** research confirms `ReviewScreen` is read-only with no Generate button and
the shared `WizardControls` render only Back/Next. A dedicated Generate step already
exists and `Generate.isLast` — it is purpose-built for exactly the loading/error lifecycle
a bounded LLM call requires, and the agent's "clicks generate" maps cleanly onto its
existing button. Reusing it avoids reworking the Review contract and the wizard controls.
The Review → Generate advance stays a normal `Next`.

**Rejected — put the Generate CTA on the Review screen:** would break Review's read-only
contract, require Review to host loading/error UI it was not built for, and duplicate the
wizard-controls flow. The dedicated step is the right home.

## Open questions

- **Schema authorization — RESOLVED (2026-06-26).** Maintainer approved adding
  `programs`/`workouts`/`workout_lifts`/`sets` as a normalized 4-table schema, kept at
  **DB v1** (tables added to the v1 entity set, reinstall to apply — no version bump, no
  migration). No further human action blocking Structure.

(All four ticket open questions — normalized vs blob, single-shot vs tool loop, Programs
screen scope, CTA placement — are resolved above in D1–D4, and the schema authorization
item is now confirmed.)

## Notes carried to Structure (not decisions)

- Domain `Program`/`Workout`/`WorkoutLift`/`Set` + `SetType { Standard, MyoRep, DropSet }`
  + `ProgressionScheme { Linear, Double }` are new in `:core:domain`.
- `GenerateProgramUseCase` composes `LoadProgramGenerationPayloadUseCase` +
  `ProgramGeneratorPort` + `ProgramRepository` + `CompleteOnboardingUseCase` (or inlines
  its two calls) so the ViewModel depends on one use case.
- System prompt is a bundled, versioned asset under `functions/src/` (§2.7) carrying the
  §2.3 progression rules, split templates, MEV/MRV landmarks, push/pull balance, and
  time-budget heuristics.
- `FakeProgramGeneratorPort` + `FakeProgramRepository` are added to `:core:testing`.

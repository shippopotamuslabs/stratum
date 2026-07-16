# Design: import-export

**Ticket:** `import-export` | **Phase:** Design  
**Grounding:** `plans/import-export/research.md`, `ticket.md`, verified worktree sources  
**Entry:** Settings (`feature/settings/.../SettingsScreen.kt`)

---

## Current state

**Settings.** Scrollable preference sections; only secondary destination is Legal via
`StratumPreferenceRow` → `LegalKey` → `backStack.add` (`SettingsKey.kt`,
`StratumNavHost.kt`). No Import/Export rows, strings, or callbacks. Module deps:
`:core:domain` + `:core:ui` only (`research.md` §1–4).

**Import (onboarding only).**

| | Logs | Programs |
|---|------|----------|
| UI | Gym & Lifts card + timelines | Choose Path / Review loading UI |
| Domain | `ExecuteHistoricalImportUseCase` → `PersistImportedHistoryUseCase` | `ImportProgramTemplateUseCase` |
| Cloud | `startImport` / status + Storage | `startProgramTemplateImport` / status + Storage |
| Files | Multi ≤5×10 MB | Single ≤1 MB |
| Side effect | `replaceImportHistory` (IMPORT origin only; LIVE kept) | `saveProgram(active)` + **`CompleteOnboardingUseCase`** |

Deterministic third-party formats exist server-side; **no `stratum` class** on log or
program detectors (`research.md` §15, §19). Import UI/MIME/URI helpers live only under
`:feature:onboarding`; not reachable from Settings (`research.md` §30). Features must not
depend on other features (`ARCHITECTURE.md` §2).

**Export.** None in product (no domain ports, UI, or callables). `sliceExport.ts` is
agent pipeline support only. Read APIs exist (`getSetsInWindow`, `getAllSessions`,
`getAllUserPrograms`, library observe) but no export packaging use case (`research.md`
§23–25).

**Cloud.** Import callables require Auth; Storage uid-scoped; no API keys in APK;
OpenRouter server-side only.

---

## Desired end state

1. Settings **Import Data** → Logs | Program: same multi-app import paths as onboarding
   **plus** first-class Stratum-native files from Export.
2. Settings **Export Data** → Logs (range or all time) | Programs (all or multi-select).
3. Export JSON is detected deterministically as `stratum` on Firebase import (no LLM).
4. Settings import **never** completes onboarding.
5. Settings log/program import is **merge-safe**: never clobber existing LIVE or
   already-imported rows; skip duplicates (see D4, human-approved).
6. **Export on-device**; import still Auth + Storage + existing callables.

**Non-goals:** cloud backup product, import quotas, App Check function-option changes,
destructive replace of existing history, Room tables for UI selection state.

---

## Patterns to follow

| Pattern | Apply |
|---------|--------|
| Legal NavKey + entry + `backStack.add` | Import/Export hubs and sub-screens |
| `StratumPreferenceRow` / Settings sections | Entry rows and hub choices |
| Domain use cases; Firebase only in `:core:inference` | Export builders; server Stratum detectors |
| ViewModel domain → presentation mapping | Selection, progress, counts |
| Pure navigation callbacks; `Stratum*` UI only | All new screens |
| Onboarding file pick outside ViewModel | Settings import routes |
| Reuse `ExecuteHistoricalImportUseCase` + program poll/resolve | Settings import after D3 |
| `NormalizedHistory` / `NormalizedProgramTemplate` as payload bodies | Stratum envelopes |
| JUnit 5 + Turbine + `:core:testing` fakes; functions `*.test.ts` | New logic |

---

## Patterns to avoid

| Avoid | Why |
|-------|-----|
| Unchanged `ImportProgramTemplateUseCase` from Settings | Completes onboarding |
| `settings` → `onboarding` module dep | Architecture forbids feature→feature |
| OpenRouter / provider keys on device | `AGENTS.md` §4.6 |
| Firebase export callable for v1 | Local DB snapshot is enough |
| Domain models in composables / `NavController` in UI | Architecture contracts |
| Room columns for range/selection UI | Schema lock |
| Detecting Stratum after LLM / via Hevy CSV hacks | Must be first-class deterministic |
| Settings program import = force active + finish wizard | Wrong lifecycle |
| `replaceImportHistory` / overwrite on Settings import | Human: merge-safe only (D4) |
| Blind append that creates log duplicates | Human: skip by id or date/content |

---

## Design decisions

### D1 — Settings navigation (Legal pattern)

**Choice:** Preference rows **Import Data** and **Export Data** open hubs; each hub
offers Logs | Program and navigates to a dedicated screen:

```text
SettingsKey
  ├─ ImportDataKey → ImportLogsKey | ImportProgramKey
  └─ ExportDataKey → ExportLogsKey | ExportProgramsKey
```

Register entries beside `settingsEntry` / `legalEntry`; wire pushes in `StratumNavHost`.

**Rejected:** Root-only dialogs (too shallow); reopening full Onboarding for import.

### D2 — Module ownership without feature→feature deps

**Choice:** Screens/NavKeys in `:feature:settings`. Export + decoupled import use cases in
`:core:domain`. Extract shared import presentation (progress/timelines) and MIME/file-read
helpers out of onboarding into a surface both features can use (prefer `:core:ui` for
Compose chrome; Structure confirms if a thinner shared helper is better). **No**
`settings` → `onboarding` dep. **No** new `:feature:import_export` module. ViewModels
inject domain use cases (ports already bound app-wide); add `:core:inference` to Settings
only if Structure proves a compile-time need.

**Rejected:** Depending on onboarding; deep-linking the wizard; export logic in `:app`.

### D3 — Decouple program import from onboarding

**Choice:** Core use case: upload → poll → download → parse → resolve → persist
(parameters for active vs library, change-event append). Onboarding path composes core +
`CompleteOnboardingUseCase` and keeps activate-as-active wizard behavior. Settings path
uses core only: **library persist, never complete onboarding, never force-activate** over
the current program. Historical import already skips onboarding completion — reuse as-is.

**Rejected:** Silent reuse of the coupled use case; full pipeline duplication in Settings.

### D4 — Settings import is merge-safe (no clobber) — **human-approved override**

**Choice (approved 2026-07-16):** Settings import **must not overwrite** existing user
data. Prefer skip/merge over replace.

| Source | Logs | Programs |
|--------|------|----------|
| Stratum export (stable ids in envelope) | Skip sessions/sets whose exported ids already exist locally | Skip programs whose exported ids already exist |
| Third-party / no stable ids | Deduplicate: prefer **calendar-day occupancy** (device-local) — do not import sessions for dates that already have data; optional content match (exercise + weight + reps + RIR) only if needed after day filter | Soft dedupe: skip when id matches or when a strong name+structure match is defined in Plan; duplicates less critical than logs |

Onboarding may retain existing `replaceImportHistory` behavior (empty/import-origin
replace for first-time history). Settings path uses a **new merge/dedupe persist path**
so re-import and multi-source import never wipe LIVE or prior imports.

**Rejected:** `replaceImportHistory` for Settings; blind append that creates duplicates.

### D5 — Stratum export format (round-trip)

**Choice:** Versioned JSON envelopes; bodies are existing normalized shapes **plus
stable export ids** so re-import can skip existing entities (D4).

```text
Logs:     schema "stratum.export.logs"     + history: NormalizedHistory (+ session/set ids)
Programs: schema "stratum.export.programs" + programs: NormalizedProgramTemplate[] (+ program ids)
```

Include `schemaVersion: 1`, `exportedAt`, `source: "stratum"`, and log `range` metadata.
Add `stratum` to `ImportFormat` and `ProgramTemplateFormat`; detect by envelope markers
**before** third-party text parsers (XLSX binary branch stays first for real XLSX).
Program export is **one multi-program file**. Map domain → name-keyed lifts / normalized
session-set fields on device. Export **includes stable ids** (Room/domain ids or
export-stable UUIDs assigned at export time and re-emitted) so Stratum re-import can skip.

**Rejected:** CSV-as-native Stratum; Hevy-column impersonation; server-side export job;
id-less export that forces content-only dedupe for Stratum files.

### D6 — Export local; import cloud

**Choice:** Export = domain read → serialize → feature Share/Save. Import of third-party
**and** Stratum files still upload through existing jobs so progress, Auth, and persist
paths stay one pipeline (Stratum hits deterministic convert, not OpenRouter).

**Rejected:** Client-only Stratum bypass of the job pipeline; cloud DB export.

### D7 — Export UX

**Logs:** All time | presets (30/90 days, YTD) | custom range; show session/set counts;
CTA builds file when non-empty. **Programs:** list user programs (exclude
`import-history`); select-all + multi-select; CTA with ≥1 selected. ViewModels hold
selection presentation only; domain accepts range / ids → file bytes + suggested name.

**Rejected:** Active-program-only export; silent full dump with no confirmation.

### D8 — Import UX

**Logs:** Same multi-doc MIME, progress/timeline/cancel/retry as Gym & Lifts; insights on
success; no wizard step advance. **Programs:** same spreadsheet MIME as onboarding **plus**
JSON/`text/*` for Stratum; phase timeline; success snackbar. Format copy lists third-party
apps and Stratum export.

**Rejected:** Stratum-only picker; embedding the onboarding step machine in Settings.

### D9 — Multi-program Stratum import

**Choice (approved):** Import **all** templates in the envelope into the library
(inactive), subject to D4 id/structure skip. Single-template files use the same library
path. Settings never auto-activates (D3).

**Rejected:** Silently import first only; force-activate each; post-parse multi-select.

### D10 — Domain export APIs

**Choice:** `ExportTrainingLogsUseCase(range)` and `ExportProgramsUseCase(programIds)`
return a small file result (bytes + name). Compose existing repository reads (filter
sessions by `startedAt`; sets via window/all + session join). Serialization pure JVM in
domain with unit tests. No Android types in domain APIs; no new Room export tables.

**Rejected:** JSON assembly only in ViewModels; schema changes for selection state.

### D11 — Auth vs offline

**Choice:** Import requires signed-in user (existing `requireUid`); signed-out shows
guidance. Export works offline on local data.

### D12 — Testing (design-level)

Domain export + decoupled program import; Settings ViewModels with fakes; functions
Stratum detector fixtures; onboarding still completes onboarding after refactor.

---

## Open questions — resolved (human approval 2026-07-16)

1. **Activate on Settings program import?** → **Library only** (never auto-activate).
2. **Export delivery?** → **Share sheet only**.
3. **Custom log range bounds?** → **Device-local calendar days**.
4. **Multi-program file?** → **Import all** templates in the file (with D4 skip).
5. **Clobber / confirm?** → **No overwrite.** Merge-safe import (D4). Stratum: skip
   existing ids. Non-Stratum logs: date occupancy (and content match if needed). Programs:
   id or soft match; duplicates less critical. Confirm UI optional/info only — not a
   destructive ack.

---

## Self-review

- [x] Current state matches `research.md` without contradiction
- [x] Open questions specific and human-answerable
- [x] Rejected alternatives named on each decision
- [x] No runnable implementation code; no decision placeholders
- [x] Brief constraints 1–10 covered (D1–D12)

# Status: import-export

## Phase Checklist
- [x] Research
- [x] Design — approval: yes (2026-07-16)
- [x] Structure — approval: yes (2026-07-16)
- [x] Plan — approval: yes (2026-07-16)
- [x] TDD Red
- [x] Red Review — result: pass (loop 2/3, 2026-07-16)
- [x] Implementation
- [x] Final Review — result: pass (loop 2/3, 2026-07-16)

## Current Gate
none

## Next Phase
complete

## Loop Counts
- Red Review: 2/3
- Final Review: 2/3

## Notes
- Plan approved 2026-07-16.
- Design locks: library-only programs, Share-only export, device-local ranges,
  multi-program import-all, merge-safe import (no clobber).
- **TDD Red complete (2026-07-16):** 62 scenario IDs mapped to executable tests.
- **Red Review pass (loop 2/3, 2026-07-16):** ready for Implementation.
- **Implementation complete (2026-07-16):** Green phase — all 8 slices.
- **Final Review fail (loop 1/3, 2026-07-16):** runtime DI + soft-match fidelity.
- **Correction pass (2026-07-16):** Fixed Final Review findings 1–3.
- **Final Review pass (loop 2/3, 2026-07-16):** Prior findings 1–3 re-verified fixed;
  no new admissible findings. Ticket complete.

### Final Review Findings (loop 1/3) — all fixed (verified loop 2/3)

1. **[high]** Settings import/export ViewModels lack `@HiltViewModel` while nav uses
   `hiltViewModel()` — **fixed** (`@HiltViewModel` on all four; `ExportLogsViewModel`
   zone via primary/test constructor + Hilt `@Inject` secondary using
   `ZoneId.systemDefault()`).
2. **[high]** `ProgramImportDedupe` soft match compares `liftId`, not ordered lift
   **names** — **fixed** (ordered display names via `liftNamesById`, fallback to
   `liftId.value`; IE-S5-005 strengthened: different IDs, same names → skip).
   Call site loads catalog names after resolve.
3. **[medium]** Export Logs Custom range had no UI control for dates — **fixed**
   (ISO `yyyy-MM-dd` `StratumTextField`s + default last-30-days seed when Custom
   is first selected with null dates).

### Final Review (loop 2/3) verification

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.stratum.feature.settings.Export*" --tests "com.stratum.feature.settings.Import*"
./gradlew :core:domain:test --tests "com.stratum.domain.program.importing.ProgramImportDedupeTest"
./gradlew :app:assembleDebug
./gradlew detekt
```

- Settings Export*/Import* unit tests: **PASS**
- Domain `ProgramImportDedupeTest`: **PASS**
- `:app:assembleDebug` (includes Hilt codegen): **PASS**
- Detekt: **PASS** (`maxIssues: 0`)

### Slices delivered
1. Log export domain (range bounds, envelopes, preview/export use cases)
2. Program export domain (mapper, multi-id envelope, preview)
3. Firebase Stratum log + program detectors; format field on latestConversion
4. Merge-safe log persist (dedupe, appendImportHistory, policy, conditional rollback)
5. Program import core + LibraryOnly/ActivateAsActive wrapper + parseAll + dedupe
6. Settings export UI/VMs/nav/Share + FileProvider
7. Shared MIME/readers in `:core:ui`; Settings import UI/VMs (MergeSkipDuplicates / LibraryOnly)
8. Onboarding re-wire (shared MIME/readers; replace policy + complete preserved)

### Residual notes
- Export may still fall back to liftId strings when liftNames map not loaded (spec allows).
- Merge all-skip with existing local history may still refresh insights (no wipe).

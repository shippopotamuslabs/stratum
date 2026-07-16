# Ticket: import-export

## Request

Add two feature areas into Settings (`feature/settings/.../SettingsScreen.kt`):

1. **Import Data** → Logs | Program
2. **Export Data** → Logs | Program

### Import Data

- Add a new first-class import class to the existing TypeScript Firebase function for:
  - Stratum log data import
  - Stratum program import
- Reuse existing onboarding import paths for other apps (same code paths).
- Import should allow users to import from other apps just like onboarding, plus the new first-class Stratum path from the export schema.

### Export Data

- Users can export log data for specific time ranges, or all time.
- Users can export all programs or pick specific programs.
- Design high-quality screens that make export easy.

### Reuse

- Leverage as much existing code as possible from onboarding module import flows.
- Export schema becomes the round-trip format for first-class Stratum import.

## Entry points

- Settings UI: `feature/settings/src/main/kotlin/com/stratum/feature/settings/ui/SettingsScreen.kt`
- Branch: `feat/import-export`

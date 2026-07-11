# Stratum

**Stratum Workout Log** — a quietly intelligent lifting companion for Android.

Stratum handles progression, volume accounting, equipment-aware programming, and
long-horizon pattern recognition so you can focus on training.

## Status

Android Studio scaffold merged into this repo (`:app` + `:core:ui`). Builds with `./gradlew :app:assembleDebug`.

**Canonical project path:** `/home/sean/Code/Stratum` (re-open this folder in Android Studio — not `~/AndroidStudioProjects/Stratum`).

## Modules

| Module | Purpose |
|--------|---------|
| `:app` | Application shell (applicationId `com.aether.stratum`, minSdk 34) |
| `:core:domain` | JVM — models, ports, use cases |
| `:core:data` | Room-ready data layer (schemas in `core/data/schemas/`) |
| `:core:ui` | `StratumTheme`, theme specs, `Stratum*` components |
| `:core:testing` | Shared fakes, `MainDispatcherRule`, test fixtures |
| `:detekt-rules` | Custom Detekt rules enforcing contracts |

## Namespace

- **Application ID** (Play / install id): `com.aether.stratum`
- **Kotlin / Gradle source packages:** `com.stratum` / `com.stratum.*` (do not rename source trees to match the applicationId)

## Agent instructions

See [AGENTS.md](AGENTS.md) for architecture rules, validation commands, and agent workflow.

## Testing

- Contract: [contracts/UNIT_TESTING.md](contracts/UNIT_TESTING.md) — fakes-first, JUnit 5, Turbine, Robolectric.
- Golden examples (Now in Android): run `./scripts/golden-test-examples-download.sh` after clone.
- Shared test utilities: `:core:testing` (`MainDispatcherRule`, fakes as ports are added).

## Remote

Hosted on [gitlawb](https://gitlawb.com) (private repository).

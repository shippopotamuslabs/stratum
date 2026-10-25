# Stratum

Stratum Workout Log. An Android app for structured strength training.

You build a program first. Days, lifts, sets, and target weights sit in the program before the week starts. You log each workout against that plan. Stratum tracks the volume, moves the weights up when the work gets done, and generates the next program from your lifts, your equipment, and your training days.

Program generation runs a CP-SAT solver on a server. The solver picks the slots, the set counts, and the training days in one solve, with recovery rules between sessions. The phone keeps the deterministic parts: progression math, volume accounting, session building, and set logging. Those parts work offline.

## What the app does

- Builds multi-week training programs with the CP-SAT solver in `solver-python/`.
- Logs live workouts set by set, with rest timers, lift notes, and equipment swaps mid-session.
- Tracks progression and volume per lift and per muscle.
- Reads your gym: lifts, equipment, plates, and increment sizes live in lift config. A photo scan reads increment plates.
- Imports training history and programs from files, including spreadsheets from other apps. Sample files sit in `sample_programs/`.
- Exports programs and logs.
- Stores training data in Room on the device. Firestore sync copies it to your other devices when you sign in with Google.
- Reads Health Connect data you grant.
- Sells the paid tier through Google Play Billing. The paywall lives in `feature/subscription/`.
- Runs periodic check-ins and shows history in calendars and session detail screens.

## Architecture

Dependencies point inward. Feature and core modules use ports and models from `:core:domain`. The domain module holds the rules. Room entities hold storage shape and carry no rules. ViewModels map domain models to screen state.

Read `contracts/ARCHITECTURE.md` for the binding rules. Read `AGENTS.md` before you write code in this repo. The contracts in `contracts/` govern UI, data, testing, and copy.

### Modules

| Module | Type | What it holds |
|---|---|---|
| `:app` | Android app | Shell, `MainActivity`, navigation host, Hilt wiring, app resources, signing |
| `:core:domain` | JVM library | Domain models, value types, ports, use cases, program generation logic, synthesis wire codec |
| `:core:data` | Android library | Room database and schemas, repositories, DataStore, WorkManager, Firestore sync, Health Connect |
| `:core:ui` | Android library | `StratumTheme`, theme families, shared `Stratum*` components, charts |
| `:core:testing` | JVM library | Hand-written fakes for domain ports, `MainDispatcherRule`, test fixtures |
| `:core:inference` | Android library | Firebase Callable clients for synthesis, import, scan, and entitlement work |
| `:detekt-rules` | JVM library | Custom detekt rules that enforce the UI contract |
| `:feature:onboarding` | Android library | First-run setup, gym and lift setup, history import flow |
| `:feature:program` | Android library | Program screens, create and edit program flows, check-in |
| `:feature:workout` | Android library | Live workout session, set logging, timers, notes |
| `:feature:history` | Android library | Session history, calendars, session detail, history edits |
| `:feature:settings` | Android library | Settings, equipment profile, import and export screens, legal screens |
| `:feature:subscription` | Android library | Paywall and Play Billing state |
| `:solver-service` | JVM app | Cloud Run worker. Claims synthesis jobs from Firestore, sends them to the Python solver, writes results to Cloud Storage |

Feature modules use `:core:domain` and `:core:ui`. Feature modules stay out of each other. `:app` wires cross-feature navigation.

Names to keep straight:

- Application ID: `com.aether.stratum`
- Source packages and Gradle namespaces: `com.stratum` and `com.stratum.*`
- Keep source trees under `com.stratum`. The application ID names the installed app and the Firebase Android app. It names nothing in the source tree.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose, Compose BOM 2026.09.00, Material 3 |
| DI | Hilt 2.60.1, KSP 2.3.12 |
| Storage | Room 2.8.5, DataStore |
| Async | Kotlin coroutines 1.11.0, Flow, Turbine in tests |
| Navigation | Navigation 3 |
| Backend | Firebase Auth, Firestore, Cloud Storage, Callable Functions, App Check, Crashlytics, Performance |
| Cloud jobs | Cloud Run, Firestore job docs, Cloud Storage payloads |
| Solver | OR-Tools CP-SAT 9.15, Python API |
| Billing | Google Play Billing 9.1.0 |
| Health | Health Connect client |
| Build | Gradle 9.8.0 (wrapper), Android Gradle Plugin 9.4.1 |
| Tests | JUnit 5, JUnit 4 for screenshot tests, Robolectric 4.17, Turbine |
| Static analysis | detekt 1.23.8 with the repo rules in `:detekt-rules`, Android lint |

Platform numbers, from `app/build.gradle.kts`:

- `minSdk 34` (Android 14)
- `targetSdk 37`
- `compileSdk 37`
- Java and Kotlin target: 17

## Prerequisites

Install these first:

- JDK 21. Gradle runs on it. The repo pins the daemon JVM in `gradle/gradle-daemon-jvm.properties`, and CI installs Temurin 21. Project code targets JVM 17.
- Android SDK with platform `android-37.0`. CI installs it with `sdkmanager --channel=3 "platforms;android-37.0"` and falls back to the default channel. Android Studio can install the same platform from SDK Manager.
- Android Studio, if you want the IDE path. The command line path works with the SDK alone.
- Git.
- Node 24 and npm, for `functions/` work only.
- Python 3.12, for `solver-python/`, `solver-contract/` tests, and `functions-python/` work only.
- Docker, for image builds and image scans only.
- Firebase CLI and `gcloud`, for deploys only. App builds need neither.

The Gradle wrapper downloads Gradle 9.8.0 on first run. That first run takes a while and pulls a large dependency set.

## Setup: cold clone

### 1. Clone

```bash
git clone https://github.com/shippopotamuslabs/stratum.git
cd stratum
```

### 2. Add `app/google-services.json`

This file is missing on purpose. Read this part before you build.

- `.gitignore` excludes `google-services.json`. The repo ships no copy and no placeholder.
- The `:app` module applies the Google Services Gradle plugin in `app/build.gradle.kts`. Tasks that build or test `:app` need the file at `app/google-services.json`.
- A Firebase project issues the file. The file carries project and app identifiers. It holds no API key for a server and no signing secret.

Get your own copy:

1. Open the Firebase console and create a project. A free project works for local builds.
2. Add an Android app to that project with package name `com.aether.stratum`. The package name must match `applicationId` in `app/build.gradle.kts`.
3. Download `google-services.json` from the project settings.
4. Save it as `app/google-services.json` in this repo.

What you get with your own file: the app builds, installs, and runs. Local training data, program screens, and logging work on the device.

What stays limited: cloud features point at your Firebase project. Sign-in, Firestore sync, synthesis, import jobs, and entitlement checks need the matching rules, indexes, and functions deployed into your project. The repo carries those sources: `firestore.rules`, `firestore.indexes.json`, `storage.rules`, `functions/`, and `functions-python/`. Deploy is a maintainer task. See "Backend" below and `docs/cloud-program-synthesis-deploy.md`.

Pure JVM work can start before the file lands. Domain tests run with `./gradlew :core:domain:test` and use no Firebase config.

### 3. Point the build at your SDK

Android Studio path:

1. Open the repo root as the project.
2. Let Gradle sync finish.
3. Install Android SDK Platform 37 if Studio asks.
4. Pick the `app` run configuration.

Command line path:

1. Set `ANDROID_HOME` to your SDK folder, or write `sdk.dir` into `local.properties` at the repo root. Gradle reads `local.properties` and the file stays out of Git.
2. Install the platform:

```bash
sdkmanager --channel=3 "platforms;android-37.0" || sdkmanager "platforms;android-37.0"
```

### 4. Optional: fetch the test reference examples

The testing contract uses examples synced from the Now in Android project:

```bash
./scripts/golden-test-examples-download.sh
```

The download lands in `golden-test-examples/`, which `.gitignore` excludes. Builds and test runs work without it. Test authors use it as a pattern source.

## Build and install

Debug build:

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Install on a running emulator or a connected device:

```bash
./gradlew :app:installDebug
```

Single module checks:

```bash
./gradlew :app:assembleDebug
./gradlew :core:data:assembleDebug
./gradlew :app:kspDebugKotlin
```

Release build:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Release signing reads `keystore.properties` at the repo root. Copy the example and fill in your values:

```bash
cp keystore.properties.example keystore.properties
```

`keystore.properties` and keystore files stay out of Git. With no `keystore.properties`, the release build produces an unsigned AAB and APK. Google Play rejects unsigned uploads. Debug signing needs no setup.

## Tests

Run the small suite for the area you touched. Run the full set before you merge a cross-module change.

### Unit tests

```bash
./gradlew testDebugUnitTest
./gradlew :core:domain:test
```

Add the JVM suites that `testDebugUnitTest` skips:

```bash
./gradlew :core:testing:test :detekt-rules:test :solver-service:test
```

One module at a time:

```bash
./gradlew :core:domain:test
./gradlew :core:data:testDebugUnitTest
./gradlew :feature:workout:testDebugUnitTest
```

The test stack: JUnit 5 on the JUnit Platform, fakes from `:core:testing`, Turbine for Flow. `contracts/UNIT_TESTING.md` holds the rules. MockK appears only at third-party SDK edges, with a suppression and a comment.

### Robolectric tests

Robolectric tests ride inside the unit suites. `:core:data` uses them for Room DAO tests and database migration tests. `:core:ui`, `:core:inference`, and `:feature:program` carry Robolectric suites too. The commands above run them. Test classes that drive Compose under Robolectric keep a specific shape in this repo; read an existing class in the same module before you add one.

### Instrumented tests

Instrumented tests need a device or an emulator:

```bash
./gradlew :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

CI runs these on an emulator in the nightly job. The nightly emulator uses an Android 35 system image.

### Static analysis

```bash
./gradlew detekt
./gradlew lintDebug
```

detekt runs with `maxIssues: 0`. The config sits in `config/detekt/detekt.yml`, and `:detekt-rules` adds the repo rules.

### Functions tests

```bash
cd functions
npm ci
npm run build
npm test
```

`npm run build` compiles TypeScript into `lib/`. `npm test` runs the compiled tests with the Node test runner.

### Solver tests

Python solver and contract suites:

```bash
python3 -m venv .venv-solver
.venv-solver/bin/pip install -r solver-python/requirements.txt -r solver-contract/tests/requirements.txt
PYTHONPATH=solver-python SOLVER_MODULE=solver .venv-solver/bin/python -m pytest solver-python/tests/ solver-contract/tests/ -q
```

JVM worker tests:

```bash
./gradlew :solver-service:test
```

## Screenshot tests

Screenshot tests use the Compose Preview Screenshot Testing plugin. Previews live next to the screens in main sources. Thin test wrappers in each module's `src/screenshotTest` source set render the same preview content. Goldens are PNG files in Git at `{module}/src/screenshotTestDebug/reference/`.

Covered modules: `:app`, `:core:ui`, `:feature:workout`, `:feature:program`, `:feature:settings`, `:feature:history`, `:feature:subscription`, `:feature:onboarding`.

Validate against the goldens:

```bash
./gradlew :app:validateDebugScreenshotTest :core:ui:validateDebugScreenshotTest :feature:workout:validateDebugScreenshotTest :feature:program:validateDebugScreenshotTest :feature:settings:validateDebugScreenshotTest :feature:history:validateDebugScreenshotTest :feature:subscription:validateDebugScreenshotTest :feature:onboarding:validateDebugScreenshotTest
```

Re-record after an intentional UI change:

```bash
./gradlew :app:updateDebugScreenshotTest :core:ui:updateDebugScreenshotTest :feature:workout:updateDebugScreenshotTest :feature:program:updateDebugScreenshotTest :feature:settings:updateDebugScreenshotTest :feature:history:updateDebugScreenshotTest :feature:subscription:updateDebugScreenshotTest :feature:onboarding:updateDebugScreenshotTest
```

Review every PNG diff after a re-record. Commit goldens with the UI change that produced them. Diff reports land in `{module}/build/reports/screenshotTest/preview/debug/index.html`.

Rules, caveats, and the workflow for a new screen: `docs/screenshot-tests.md`.

A manual GitHub Actions workflow, `screenshot-record`, re-records goldens in CI and uploads them as an artifact for review.

## Backend

Four pieces sit behind the app. Each piece has its own folder and its own deploy path.

### Solver contract

`solver-contract/` holds the versioned JSON boundary for program synthesis: schemas in `solver-contract/schema/`, request and response fixtures in `solver-contract/fixtures/`, and the policy in `solver-contract/VERSIONING.md`. Producers emit a `modelVersion` such as `v1`. A missing version reads as `v1`. Unknown major versions get rejected with a user-safe error.

### Python solver

`solver-python/` holds the CP-SAT service. OR-Tools solves the program: slot activation, set counts, training days, recovery limits, and volume aims in one model. The service answers `POST /solve/v1` and `GET /health`.

Run it locally:

```bash
cd solver-python
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m solver.app
```

The service listens on `PORT`, default `8080`. Knobs and the fallback rules sit in `solver-python/README.md`.

### JVM worker

`:solver-service` is a Kotlin/JVM Cloud Run worker. It claims a pending synthesis job from Firestore, downloads `synthesis/{uid}/{jobId}/request.json` from Cloud Storage, posts the payload to the Python solver at the URL in `STRATUM_SOLVER_URL`, uploads `result.json`, and updates the job doc.

Build the fat jar:

```bash
./gradlew :solver-service:test :solver-service:shadowJar
```

Output: `solver-service/build/libs/solver-service-all.jar`

The production image builds from `solver-service/Dockerfile` with the repo root as the build context:

```bash
docker build -f solver-service/Dockerfile -t stratum-solver .
```

Deploy steps, IAM, and smoke tests: `docs/cloud-program-synthesis-deploy.md`. That runbook names the maintainer Firebase project and region. Your deploy uses your project values.

### Firebase Functions

`functions/` holds TypeScript Callable Functions on Node: synthesis dispatch, import, entitlement sync, account work, telemetry, and bounded LLM calls through OpenRouter. Provider keys live in Firebase Secret Manager. The APK carries no provider key, and client-readable storage holds none.

```bash
cd functions
npm ci
npm run build
npm test
```

`functions-python/` holds a Python codebase with two jobs: a sandboxed conversion runner for history import scripts, and embedding support for lift matching. `firebase.json` declares both codebases, with runtimes `nodejs22` and `python312`. The `functions/package.json` engines field asks for Node 24, and CI installs Node 24.

Hosting in `hosting/` serves the static legal and account pages: privacy, terms, licenses, and account deletion.

## CI

GitHub Actions runs the pipeline in `.github/workflows/ci.yml`.

Pull requests to `main` carry the test gate:

- A `changes` job reads the diff and sets path flags for UI, functions, solver contract, and dependency scans.
- Unit tests run as parallel legs by area: app, core-domain, core-data, core-ui, core-misc, features-a, features-b, solver-service. An aggregate `unit-tests` check reports the combined result.
- detekt and lint run in parallel. An aggregate `static-analysis` check reports both.
- Screenshot legs run when UI paths change, and in full on schedule. An aggregate `screenshot-tests` check reports them.
- Functions tests and solver contract tests run when their paths change.
- OSV scans run as advisory jobs. Findings inform. They hold no required check.
- The release build stays out of the PR gate.

Required status checks on `main`: `static-analysis`, `unit-tests`, `screenshot-tests`, `solver-contract-tests`. The org runs the Free plan, and GitHub reports branch protection as "Not enforced" while the repo stays private. The rules take effect at the public flip.

Pushes to `main` build and distribute: `assemble-release` builds the signed release, and `distribute-internal` sends it to Firebase App Distribution. The Play deploy job runs by manual dispatch.

The nightly schedule (06:00 UTC) runs the full set: every gate job, emulator tests, the heavy solver and contract suites, both OSV scans, and the release build.

Manual dispatch inputs can force screenshot tests, solver contract tests, or an OSV scan on a run that the path flags would skip.

## Docs and contracts

- `AGENTS.md`: repo rules for agents and contributors, commands, workflow.
- `contracts/ARCHITECTURE.md`: module graph and layer rules.
- `contracts/UI_DESIGN.md`: themes, components, copy-adjacent UI rules.
- `contracts/UNIT_TESTING.md`: test rules and examples.
- `contracts/DATA_LAYER_RULES.md`: Room and repository rules.
- `contracts/gospel/WUMBO-GOSPEL.md`: the voice contract for user-facing copy (Shippopotamus gospels submodule — the shared canon for all Shippopotamus apps lives under `contracts/gospel/`).
- `docs/synthesis-architecture.md`: how synthesis fits together, with a migration note at the top.
- `docs/callable-function-versioning.md`: Callable Function version rules.
- `docs/security-scanning.md`: OSV scanning and triage.
- `docs/screenshot-tests.md`: screenshot test workflow.

Some docs carry history. Dates and migration notes sit at the top of the files that need them. Build files win when a doc and a build file disagree.

## Project status

Stratum is in active development. The Android app in `app/build.gradle.kts` reads version 1.62.2, versionCode 75. Development runs in the open in this repo under the `shippopotamuslabs` org.

0BSD license. Do whatever you want with the code. Just don't expect it to work or sue me.

Firebase config, signing keys, and provider secrets stay out of Git. A clone gives you the full source and a build that runs against your own Firebase project.

# Dependency Updates & Vulnerability Scanning

Two mechanisms keep Stratum's dependencies current and CVE-free:

1. **Renovate** — automated weekly dependency-update MRs (`renovate.json`).
2. **OSV-Scanner** — a CI job (`osv-scan` in `.gitlab-ci.yml`) that fails the
   pipeline when a known CVE affects anything we ship.

GitLab's built-in Dependency Scanning was deliberately not used: it requires an
Ultimate subscription, and its analyzer Docker images can't run on our
home-server shell executor. OSV-Scanner is a static binary and runs anywhere.

## Renovate (weekly update MRs)

`renovate.json` at the repo root configures everything Renovate needs. It covers:

- `gradle/libs.versions.toml` (version catalog) plus plugin versions in
  `settings.gradle.kts` / module build files — via the `gradle` manager
- The Gradle wrapper distribution itself (`gradle-wrapper` manager)
- `functions/package-lock.json` (`npm` manager)
- `functions-python/requirements.txt` (`pip_requirements` manager)
- `solver-service/Dockerfile` base images (`dockerfile` manager)

Behavior:

- Runs **weekly, Monday mornings before 9am ET**.
- Groups updates into a handful of MRs (AndroidX, Kotlin, Firebase/Google,
  Dagger/Hilt, functions-npm, functions-python) instead of one MR per library.
- **Major** version bumps are split into their own MRs so they can't sneak in
  with routine patches. This is enforced structurally: every grouping rule
  only matches `minor`/`patch`/`pin`/`digest` update types, so a major bump
  matches no group and Renovate opens an individual MR for it.
- `minimumReleaseAge: 7 days` — a new release bakes for a week before Renovate
  proposes it, filtering out day-zero breakage.
- MRs are labeled `type::chore` and use conventional commits
  (`chore(deps): update dependency X to vY`), matching this repo's style.
- `osvVulnerabilityAlerts: true` — Renovate also opens MRs when OSV publishes
  a vulnerability against a dependency we use, even outside the weekly cadence.
- Nothing is auto-merged. Every update MR goes through the normal pipeline
  (detekt, lint, unit tests, osv-scan) and human review.

### One-time onboarding (maintainer action)

`renovate.json` alone does nothing until the Renovate bot is authorized for the
group. Mend relaunched the hosted Renovate app for GitLab.com in July 2026; it
installs at the **group** level:

1. Log in to the [Mend Developer Platform](https://developer.mend.io) with the
   GitLab account that owns the group.
2. Follow the
   [Install Mend Developer Platform for GitLab.com](https://docs.mend.io/integrations/latest/install-mend-developer-platform-for-gitlab-com)
   guide and select the group that owns this project.
3. Renovate opens an onboarding MR (`renovate/configure`) on each repo with a
   proposed config. This repo already ships its own `renovate.json`, so review
   the onboarding MR and merge it — Renovate will not raise update MRs until
   onboarding is merged.
4. After the first run, Renovate opens a "Dependency Dashboard" issue to track
   pending updates and let you trigger runs on demand.

### Weekly flow

- Monday: update MRs arrive. Check the diff, confirm CI is green, merge.
- Major-bump MRs: run the app (`:app:assembleDebug`, smoke the debug APK) —
  majors can change APIs.
- Renovate rebases its branches automatically when `main` moves.

## OSV-Scanner (CI gate)

The `osv-scan` job runs in the `lint` stage on every MR and on `main`:

1. Downloads the pinned OSV-Scanner release (see `OSV_SCANNER_VERSION` in
   `.gitlab-ci.yml`; bump it when new releases land).
2. Resolves the `:app` `releaseRuntimeClasspath` — the exact dependency set
   shipped in the APK — and converts it to a CycloneDX SBOM with
   `scripts/generate-sbom.py` (`bom.cdx.json`, kept as a job artifact).
   The generator is fail-closed: any `FAILED` (unresolved) dependency line
   or an empty result makes it exit nonzero, so a partial tree can never
   produce a clean-looking SBOM.
3. Does the same for `:solver-service` `runtimeClasspath`
   (`bom-solver.cdx.json`). The solver ships as a separate Cloud Run fat JAR
   and is not on `:app`'s classpath, so without its own SBOM its JVM
   dependencies would bypass the gate entirely.
4. Runs `osv-scanner scan source` against both SBOMs plus
   `functions/package-lock.json` and `functions-python/requirements.txt`.

The job **fails (exit 1) when OSV reports any known vulnerability**, so a CVE in
a new dependency blocks the MR before merge. On `main` it guards against
vulnerabilities published against dependencies we already ship.

Known coverage gap: the solver's production *container image* (the
`eclipse-temurin:17-jre-jammy` base plus `libgomp1` from apt) is not
CVE-scanned — only its JVM dependency graph is. The base image is tracked for
*updates* by Renovate's `dockerfile` manager; scanning the built image
(`osv-scanner scan image`) needs Docker on the CI runner and is tracked as a
follow-up work item.

Why generate an SBOM instead of committing Gradle lockfiles? Lockfiles would
change build behavior and add churn to every dependency PR; the SBOM is derived
fresh at scan time from the resolved graph.

## Triage path for new CVEs

When `osv-scan` fails or Renovate opens a vulnerability-alert MR:

1. **Read the finding.** The job prints a table with the CVE/OSV ID, affected
   package and version, severity, and a link. Start from the OSV entry
   (`https://osv.dev/vulnerability/<ID>`) — check severity, exploitability, and
   whether it affects how we use the library.
2. **Prefer the fix.** If a fixed version exists, update the dependency:
   `gradle/libs.versions.toml` for Android deps (or merge Renovate's MR —
   `osvVulnerabilityAlerts` usually opens one already), `functions/` for npm,
   `functions-python/requirements.txt` for Python.
3. **Suppress deliberately, never silently.** If there is no fix yet, the CVE
   doesn't apply to us (e.g. an unused code path), or it's a false positive,
   add an entry to `.osv-scanner.toml` at the repo root with `reason` and an
   `ignoreUntil` date, and link the tracking issue. The CI job picks it up
   automatically and applies it to every scanned file via `--config`:
   ```toml
   [[IgnoredVulns]]
   id = "CVE-2026-12345"
   reason = "Not exploitable in Stratum: <why>. Tracked in #<issue>."
   ignoreUntil = 2026-12-01 # re-triage by this date
   ```
   The scan then passes with the suppression documented in-tree. Reference:
   [osv-scanner configuration](https://google.github.io/osv-scanner/configuration/).
4. **Never ignore without an expiry.** Every suppression must have an
   `ignoreUntil` date; when it lapses, the job fails again and forces
   re-triage.

### Baseline remediation (2026-09-20)

The first scan (before this MR) found 8 vulnerabilities, all in
`functions/package-lock.json`; the Android SBOM (297 packages) was clean.
A later scan of the Python requirements (resolvable only on the home-server
runner, not through the sandbox proxy) found 13 more, all transitive
dependencies of `sentence-transformers` (1 Critical, 5 High, 7 Medium).
The parents' version ranges resolved the vulnerable versions, so the fix is
transitive floor pins in `functions-python/requirements.txt`
(`anyio>=4.14.2`, `idna>=3.15`, `starlette>=1.3.1`, `torch>=2.13.0`,
`transformers>=5.10.0`), lifting the resolved set to
anyio 4.14.2, idna 3.15, starlette 1.3.1, torch 2.13.0, transformers 5.10.0.
The CI re-scan after the pins is the proof — see the `osv-scan` job log.

- **Fixed by version bump** — `npm update` within the parents' semver ranges:
  `body-parser` 1.20.5 → 1.20.8, `fast-xml-parser` 5.9.3 → 5.11.1,
  `protobufjs` 7.6.4 → 7.6.6.
- **Fixed via `overrides`** (`functions/package.json`) — the direct parents
  pin ranges that exclude the fixed versions, so the fixes are forced:
  `qs` → 6.16.0 (express wants `~6.15.1`), `uuid` → 11.1.1
  (gaxios/google-gax/teeny-request want `^9.0.1`). All 300 functions tests
  pass with the overrides in place.
- **Fixed by transitive floor pins** (`functions-python/requirements.txt`,
  validated with a local `pip install --dry-run`: anyio 4.15.1, idna 3.20,
  starlette 1.6.0, torch 2.14.0, transformers 5.17.0 — all above the floors).
- **Fixed by version bump + BOM constraints** (`solver-service`, found via the
  new solver SBOM scan): `firebase-admin` 9.4.3 → 9.10.0
  (`gradle/libs.versions.toml`), plus alignment constraints in
  `solver-service/build.gradle.kts` — `netty-bom` 4.2.17.Final,
  `jackson-bom` 2.18.9, `opentelemetry-bom` 1.62.0, `httpclient5` 5.6.3,
  `httpcore5`/`httpcore5-h2` 5.4.3. These clear the netty/httpclient/jackson/
  OpenTelemetry CVEs in the solver's transitive graph (10 vulnerable packages
  before remediation).
- **Suppressed with expiry** (`.osv-scanner.toml`) — SheetJS `xlsx` 0.20.3 has
  two High CVEs (GHSA-4r6h-8v6p-xvw6 prototype pollution, GHSA-5pgg-2g8v-p4x9
  ReDoS) and **no fixed version exists upstream** — 0.20.3 is the latest
  release. The library parses user-uploaded MacroFactor and custom-program
  workbooks in Cloud Functions, so this is real exposure, not test-only.
  Accepted pending evaluation of a maintained replacement (e.g. `exceljs`),
  tracked in issue #10; suppression expires 2026-12-20 and forces re-triage.

## Local runs

```bash
# SBOM from the current dependency tree:
./gradlew :app:dependencies --configuration releaseRuntimeClasspath -q \
  | python3 scripts/generate-sbom.py > bom.cdx.json

# SBOM for the separately-deployed solver-service (Cloud Run fat JAR):
./gradlew :solver-service:dependencies --configuration runtimeClasspath -q \
  | python3 scripts/generate-sbom.py --component-name stratum-solver > bom-solver.cdx.json

# Scan them (install osv-scanner from https://github.com/google/osv-scanner/releases):
osv-scanner scan source -L bom.cdx.json -L bom-solver.cdx.json \
  --lockfile=functions/package-lock.json \
  --lockfile=functions-python/requirements.txt
```

# Crash Alert Email Runbook (Step 4)

Solo-dev email alerting for Stratum via Firebase Crashlytics. No code changes —
everything here is console configuration. Phone/paging stays deferred until
post-LLC (see §5).

Source: [Configure and receive Crashlytics alerts](https://firebase.google.com/docs/crashlytics/alerts-default).
Console path used below: Firebase console → Settings ⚙ → **Alerts** page →
**Crashlytics** alerts card (pick the `com.aether.stratum` app in the dropdown).

## 1. What you already get for free

Once a build with Crashlytics reports data, two alert types are **on by default**
for every project member (you are Owner, so you qualify):

- **Regressed issue** — a previously closed/stable crash reappears → email.
- **Trending issue** — an issue gaining steam → email + in-console banner.

Nothing to click. If you do nothing else in this doc, these two still work.

## 2. What to turn on (15-minute checklist)

These are **off by default** and must be opted into, per app:

1. Go to Settings → **Alerts** → **Crashlytics** card, select `com.aether.stratum`.
2. Enable email for each of:
   - [ ] **New fatal issue** — any fresh crash. Pre-launch this is your
         most important alert: with few users, thresholds never trip, but a
         single new crash still pages you by email.
   - [ ] **New non-fatal issue** — this is where our step-3
         `user_visible_error` reports land (they are sent as
         `recordException` non-fatals). Without this checkbox, silent
         user-facing failures never email you.
   - [ ] **New ANR issue** — app-not-responding reports.
   - [ ] **Velocity alerts** — fires when one issue hits the threshold within
         30 minutes. Defaults: **1% of sessions AND ≥ 25 users**. Leave the
         defaults; see §3 for why.
3. Confirm your account toggle is ON for each (alerts can be muted per
   account without affecting other members — matters once you add people).

## 3. Pre-launch caveat: velocity alerts won't fire yet

The minimum-users floor for velocity alerts is **10**. Until you have roughly
10+ active users in a 30-minute window, velocity alerts mathematically cannot
trigger — that is expected, not broken. Pre-launch coverage comes from the
**new fatal / new non-fatal** checkboxes in §2, which fire on the first
occurrence with no minimum. Revisit the velocity threshold after release when
you know real session volume.

## 4. How our code feeds these alerts

| Code signal | Where | What it becomes in Crashlytics |
|---|---|---|
| `recordException` on user-visible failures | `AndroidCrashTelemetryAdapter` (via `LogErrorUseCase` branches) | Non-fatal issues → §2 "new non-fatal" emails |
| Breadcrumb `log()` calls | Same adapter | Timeline on each issue for triage |
| `flag_*` / `flag_exposure` custom keys | `FirebaseFeatureFlagRepository`, `TelemetryContextListener` | Segment crashes by flag cohort when triaging |
| User ID bind/clear on auth change | `TelemetryAuthListener` | "How many users hit this" per issue |

SDK check: Firebase BoM `34.13.0` ships Crashlytics well above the
v18.6.0 minimum required for velocity alerts — no upgrade needed.

## 5. Deferred (post-release)

- **Phone/SMS paging** — deferred until business e-SIM after LLC. When ready:
  add a PagerDuty or Opsgenie integration on the same Alerts page
  ([basic integrations doc](https://firebase.google.com/docs/crashlytics/alerts-basic-integrations)),
  routed from velocity alerts only (not every new non-fatal, or the phone
  becomes unactionable noise).
- **Custom thresholds** (e.g. alert if crash-free rate dips below X%) need
  Crashlytics→BigQuery export + Cloud Monitoring alerting policies —
  overkill for one developer pre-launch; reconsider when session volume
  makes the default 1%/25-users threshold meaningful.

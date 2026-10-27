# RELEASE.md
**Stratum – Release Contract v1.0**

How Stratum gets from merged code on `main` to a build on the Google Play
internal track. Every agent and every release run follows this file. If a
release step is not in here, it does not happen until this file says it does.

Scope facts, checked 2026-10-26 (STR-205):

- Package: `com.aether.stratum` (`applicationId` in
  `app/build.gradle.kts`).
- Track: internal only. This contract covers no other track. Promotion
  to a closed track, open testing, or production is a separate decision
  with its own runbook.
- Play holds a long upload history under the hand-set versionCode
  series, which ended at 77 (versionName 1.62.4). The derived scheme
  below replaces it; §1.1 covers the jump.

Copy in release notes follows `contracts/gospel/WUMBO-GOSPEL.md`. Read
it before drafting notes, the same as any other user-facing copy.

---

## 1. Versioning

### 1.1 versionCode is derived, never hand-bumped

`app/build.gradle.kts` sets `versionCode` from the checkout being built:

```text
git rev-list --first-parent --count HEAD
```

The count comes from a Gradle exec provider at configuration time. It is
the first-parent commit count of `HEAD`, so on `main` it grows by exactly
one per squash merge and never repeats or goes backwards.

- Nobody edits the code by hand, and no CI job writes it back to the repo.
  There is no bump commit for the code. Ever.
- If git is missing, the history is missing, or the count does not parse,
  the build falls back to the committed `fallbackVersionCode` in
  `app/build.gradle.kts` (77 at adoption, the last hand-set code). The
  fallback exists so a source export still builds. It is not a release
  path.
- A shallow checkout answers git with a count of 1. That answer is wrong
  for a release. Release builds run from a full-history checkout only:
  the CI jobs that build for distribution set `fetch-depth: 0`. A
  release build showing code 1 came from a shallow checkout. Stop there.
- Check any checkout before a release build:
  `./gradlew :app:printVersion` prints the `versionName` and the derived
  `versionCode` for that checkout. Compare the code against
  `git rev-list --first-parent --count HEAD` run by hand. They must match.
- Adoption was a one-time jump: the hand-set series ended at code 77,
  and the first derived build on `main` takes the count at that day's
  `HEAD` (440 once the adoption change itself merges, and higher by the
  time a build actually ships, because the count includes every merge
  since). Play only requires the code to exceed every previously
  uploaded code, and the count clears the hand-set series by a wide
  margin.

### 1.2 versionName is a human semver string

`versionName` stays a plain string in `app/build.gradle.kts`. A person
picks it, and it changes in exactly one place: a release-prep PR (§2).

- Patch (`1.62.5`): the work since the last release tag is fixes only.
- Minor (`1.63.0`): the work since the last release tag adds something a
  user can see or use.
- Major (`2.0.0`): a change Sean calls major. The agent proposes, Sean
  decides.
- No other PR touches `versionName`. A PR that changes it outside a
  release prep is out of contract; send it back.

---

## 2. Release prep: the PR is the approval gate

A release starts as a normal PR against `main`. Merging that PR is Sean's
approval of the version and the notes. There is no second sign-off step,
and the deploy job (§4) ships exactly what the merged PR contains.

One release-prep PR carries all three changes:

1. The `versionName` bump in `app/build.gradle.kts` (§1.2).
2. `distribution/whatsnew/en-US.txt` rewritten for this release (§3).
3. `docs/releases/v<versionName>.md` added for this release (§3).

Rules for the prep PR:

- Title it like any other PR (`chore(release): prepare v<versionName>`,
  with the release's Linear ticket in parentheses when it has one).
- It changes no app code, no styles, no behavior. Version string and
  notes files only.
- The PR description lists the source PRs the notes were drafted from
  (§3), so Sean can check the notes against the work in one read.
- Normal CI gates apply. Green checks plus Sean's merge is the whole
  approval.

After the prep PR merges, the release is ready to deploy from `main`
(§4). Nothing else gets committed in between on purpose; extra merges
raise the derived code, which is fine, and change the shipped code, which
the notes then no longer describe. Deploy promptly or redo the notes.

A dispatch without a fresh prep PR is also legitimate: it ships the
current `versionName` with the checked-in whatsnew as a new build of the
same release (a fix build, a re-test build). The tag rule in §4.1 covers
that case. Cut a prep PR when the versionName or the notes should change,
not as a toll every dispatch pays.

---

## 3. Release notes: drafting rules

Two files, one fact set:

- `distribution/whatsnew/en-US.txt` is the Play "what's new" text. Hard
  cap: **500 characters**, counted on the file content with the trailing
  newline removed. Plain text. Other locales get their own file next to
  it (`distribution/whatsnew/<locale>.txt`) when a release first ships
  notes in that locale, under the same cap.
- `docs/releases/v<versionName>.md` is the long-form version: the same
  facts with room to say them properly, plus the source PR list. It
  lives in the repo and never goes to Play.

Where the facts come from:

- The source set is the PRs merged between the last release tag
  (`v<versionName>` on the first deployed SHA for that version, §4) and
  `HEAD`. Read their titles and their linked Linear IDs. For the first
  release under this contract (v1.62.4) there is no tag; the source set
  is the PRs merged since the previous internal deploy, and the release
  file says so.
- Facts come from those PRs only. A change that is not in a merged PR in
  the range does not get a note. A note never claims a feature, a fix, a
  number, or a behavior the PRs do not support.
- Nothing gets invented to fill space. A two-line whatsnew that covers
  the range beats a full 500 characters with padding in it.
- Voice: `contracts/gospel/WUMBO-GOSPEL.md` binds store copy. State what
  the thing does. Short lines, real specifics, no hype words, no pivots.
- Check the cap before opening the prep PR:
  `wc -m distribution/whatsnew/en-US.txt` (subtract one for the trailing
  newline). The upload script (§4) checks again and refuses over the cap.

---

## 4. Upload and verification

### 4.1 The moving parts

- `scripts/play-upload-internal.py` does the upload. It takes the AAB
  path as its one argument and reads everything else from the repo and
  the environment:
  - Auth: the `PLAY_PUBLISHER_JSON` environment value, either a path to
    the service-account JSON key or the raw JSON. The account needs
    release rights on the internal track for `com.aether.stratum`
    (`PACKAGE_NAME` env overrides the package, defaulting to Stratum's).
  - Notes: it reads `distribution/whatsnew/en-US.txt` from the checkout,
    refuses to run if the text is empty or over 500 characters, and
    attaches the text as the `en-US` release notes of the new release.
  - Flow: open an edit, upload the AAB, set the internal track to a
    `completed` release holding that versionCode and those notes,
    commit the edit. If anything before the commit fails, it deletes
    the open edit, so a failed run leaves nothing on Play.
  - Verification: after the commit it reads the internal track back
    through the Play API and checks that the uploaded versionCode sits
    there as a `completed` release carrying the same `en-US` notes.
    Only then does it print the uploaded versionCode as done.
- The deploy path is the `deploy-internal` job of
  `.github/workflows/ci.yml`. It runs only in a `workflow_dispatch` run
  on `refs/heads/main`, after `assemble-release`. Dispatching the CI
  workflow from `main` is the deploy order. It never runs on a push, a
  PR, or a schedule.
  - `assemble-release` builds the signed release APK from the dispatched
    SHA (full-history checkout, `fetch-depth: 0`) and prints
    `:app:printVersion` into the log. That APK feeds Firebase App
    Distribution (`distribute-internal`, on pushes) and Test Lab.
  - `deploy-internal` checks out the same SHA with full history, builds
    the signed AAB, hands it to the upload script, and, after the script
    verifies the release on Play, tags the deployed SHA
    `v<versionName>` via `scripts/tag-release.sh` (the versionName is
    read from `:app:printVersion` output — the one canonical reader).
    The tag records the **first**
    deploy of a versionName. A later dispatch under the same
    versionName ships a new build and leaves the tag where it is; the
    tag step logs both SHAs and succeeds. Tags are never moved and
    never force-pushed under this contract.
- The workflow's top-level `contents: read` stands; the
  `deploy-internal` job alone carries `contents: write`, for the tag
  push. No step commits to a branch, and no step pushes to `main`. The
  checked-in files on `main` are the only inputs: if the notes or the
  version are wrong on `main`, the fix is a new PR, never an edit
  inside the workflow run.

### 4.2 The deploy run, in order

1. Confirm the release state on `main`: the intended `versionName` and
   whatsnew (a merged release-prep PR, §2, or the standing files for a
   same-version fix build).
2. Dispatch the CI workflow from `main` by hand.
3. Read the run log: the printed `versionName` and derived `versionCode`
   from `assemble-release`, then the script's upload and verification
   lines in `deploy-internal`.
4. Confirm the tag `v<versionName>` exists. On a first deploy it points
   at the dispatched SHA; on a same-version redeploy it still points at
   that version's first deployed SHA, and the tag step's log says so.
   The tag plus the log is the deploy receipt.

---

## 5. Failure handling

- **Play rejects the versionCode** (already used, or not higher than a
  code already on Play). The code comes from the commit count, so there
  is nothing to bump by hand. The usual cause is a build made from a
  stale or shallow checkout, whose count came out low. Rebuild and
  redispatch from the current `main` tip, whose count is higher. If
  Play already holds a code higher than the count at `main`'s tip, a
  build from some other history got uploaded; stop and report to Sean.
  That mismatch gets a decision, not a workaround.
- **The upload fails before the commit.** The script deletes its open
  edit and Play keeps the previous state. Fix the stated cause (expired
  service-account key, missing track rights, a malformed bundle) and
  dispatch again. The same `HEAD` rebuilds the same code, and since
  nothing committed, Play will accept it.
- **The commit lands and verification fails.** Do not dispatch again: a
  rerun rebuilds the same code and Play will reject the duplicate. Check
  the internal track in the Play Console and through the API by hand.
  If the release is live with the right notes, tag the SHA by hand and
  record what happened on the release's ticket. If it is not live, stop
  and report to Sean with the API response in hand.
- **Signing secrets are missing.** The deploy lane's keystore step is
  strict and fails the job before anything uploads. That failure is the
  design: an unsigned or debug-signed bundle never reaches Play from
  this workflow.
- **The whatsnew is over 500 characters.** The script refuses before it
  opens an edit. Shorten the file in a new PR and dispatch again after
  the merge.
- **The tag already exists.** Expected on a same-version redeploy: the
  tag step leaves the tag at the version's first deployed SHA, logs
  both SHAs, and succeeds. A tag is only ever created by the deploy that
  first ships its versionName. If a tag exists for a versionName that
  has never been deployed, something outside this contract put it
  there; stop and report. Tags never get force-pushed under this
  contract.
- **The notes on Play are wrong after a verified deploy.** The files on
  `main` are the source of truth and the workflow reads only them, so
  the checked-in notes were wrong. Fix them in a PR for the record.
  Correcting the live listing text itself is a manual Play Console edit
  by Sean, or it waits for the next release; this machinery has no
  notes-only edit path, and nobody builds one inside a deploy run.

---

## 6. Keeping this contract current

Bloviate mirrors this contract (BLO-173). When either repo changes the
release rules, the other repo's `contracts/RELEASE.md` gets the same
change in the same round of work, and the ticket for the change says so.
The tag rule (§4.1, §5) diverged briefly at adoption — Bloviate's text
failed the deploy run when its tag existed at another SHA, where this
contract makes a same-version redeploy a logged success — and converged
the same week: BLO-209 ported the tag script, the one-reader rule, and
this tag wording back to Bloviate, whose v1.0 redeploy failure under
the old rule was the reason for the change.
Facts that drift (package, tracks, secret names) get fixed here in the
same PR that changes them.

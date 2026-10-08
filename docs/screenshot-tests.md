# Screenshot tests (STR-11)

JVM golden coverage for high-traction screens via the official Compose Preview
Screenshot Testing plugin (`com.android.compose.screenshot`). No emulator needed.

Covered today: `:feature:workout` (`WorkoutScreen` + route states) and
`:feature:program` (`EditWorkoutScreen`). Follow-up: `:core:ui` theme families +
key `Stratum*` components, then `:app` (dashboard).

## Layout

- Studio `@Preview`s live in main sources (`*Preview.kt`, colocated with the UI)
  per `UI_DESIGN.md` §6. Fake state and shared `*PreviewContent` builders live there.
- Screenshot tests are thin `@PreviewTest` wrappers in
  `{module}/src/screenshotTest/kotlin/.../*ScreenshotTest.kt` calling the same
  shared content — previews and goldens can never drift apart.
- Goldens are plain PNGs in git at
  `{module}/src/screenshotTestDebug/reference/...`.

## Commands

```sh
# Re-record goldens after an intentional UI change (then review every PNG diff).
./gradlew :feature:workout:updateDebugScreenshotTest :feature:program:updateDebugScreenshotTest

# Verify against goldens (what CI runs).
./gradlew :feature:workout:validateDebugScreenshotTest :feature:program:validateDebugScreenshotTest
```

Diff reports land in `{module}/build/reports/screenshotTest/preview/debug/index.html`.

## UI-change workflow

1. Make the UI change; run the `update...` tasks above.
2. Review each regenerated PNG (or the HTML report) — the diff must show *only*
   your intended pixels.
3. Commit the PNGs alongside the UI change. CI's `screenshot-tests` job fails the
   MR on any unrecorded diff.

## Adding coverage for a new screen

1. Add `@PreviewLightDark` previews + a shared internal `*PreviewContent` builder
   in main sources (see `WorkoutScreenPreview.kt`).
2. Add `@PreviewTest` wrappers in `src/screenshotTest/` calling that builder.
3. Record, review, commit. No CI change needed until a new *module* is covered —
   then add its `validate...` task to the `screenshot-tests` job and add the
   one-line `alias(libs.plugins.screenshot)` to that module's `plugins` block.
   The experimental flag, `screenshotTest` dependencies, and JUnit4 runner
   exception are centralized in the root `build.gradle.kts` — never copied
   per module.

## Rules that keep goldens stable

- Leave `darkTheme` null inside `StratumTheme` in screenshot previews so
  `@PreviewLightDark` genuinely varies light/dark (an explicit value renders the
  same pixels twice).
- Pin clocks and kill animation: e.g. the workout preview pins
  `sessionStartedAtEpochMilli` to composition time (`00:00`) and keeps the rest
  timer inactive.
- No network in preview paths (no Coil URLs — use the initials fallback).

## Known caveats

- The root build keeps screenshot `Test` tasks on the JUnit4 runner (the plugin's
  generated tests are JUnit4); everything else stays on the JUnit Platform.
- Standalone-plugin setup is the documented path at AGP 9.4.1. Google recommends
  the AGP-test-suites setup from AGP 9.5.0-alpha03 + plugin alpha16 — revisit when
  the project moves AGP past that line.

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { matchesAlphaProgression, parseAlphaProgression } from "./alphaProgressionParser";
import { validateNormalizedHistory } from "../schemas";

/**
 * Minimal-but-faithful slice of a real Alpha Progression export. Two workouts,
 * each with two exercises, each with a `#;LB;REPS` set table. Exercises embed
 * equipment + target-reps in the name after `·`; only the first segment is the
 * exercise name. Set tables are separated by blank lines. No RIR column.
 */
const ALPHA_SAMPLE = [
  '"Day 1 · Day 1 · Week 1 · New plan";"2026-07-07 12:58 pm";"38 sec"',
  '"1. Leg Extensions · Machine · 10 reps"',
  "#;LB;REPS",
  "1;185;10",
  "2;185;10",
  '"2. Triceps Pushdowns · Cable · 10 reps"',
  "#;LB;REPS",
  "1;100;10",
  "",
  '"Day 2 · Day 2 · Week 1 · New plan";"2026-07-08 9:00 am";"40 sec"',
  '"1. Lateral Raises · Dumbbells · 12 reps"',
  "#;LB;REPS",
  "1;25;12",
  "",
].join("\n");

describe("alphaProgressionParser", () => {
  it("matches the Alpha Progression sectional layout", () => {
    assert.equal(matchesAlphaProgression(ALPHA_SAMPLE), true);
  });

  it("does not match a flat CSV or unrelated text", () => {
    assert.equal(matchesAlphaProgression("a,b,c\n1,2,3"), false);
    assert.equal(matchesAlphaProgression("Workout #;Date;Exercise Name\n1;2020;x"), false);
  });

  it("parses workouts into sessions and exercises into sets, extracting the exercise name", () => {
    const history = parseAlphaProgression(ALPHA_SAMPLE);
    assert.ok(history);

    // Two workout headers => two sessions.
    assert.equal(history!.sessions.length, 2);
    assert.equal(history!.sessions[0].sourceApp, "Alpha Progression");
    assert.equal(history!.sessions[0].startedAt, "2026-07-07T12:58:00Z");
    assert.equal(history!.sessions[1].startedAt, "2026-07-08T09:00:00Z");

    // 4 set rows across the two workouts.
    assert.equal(history!.sets.length, 4);

    const first = history!.sets[0];
    assert.equal(first.sessionId, history!.sessions[0].id);
    // The exercise name is the first `·`-segment, with the leading `N. ` stripped.
    assert.equal(first.exerciseNameRaw, "Leg Extensions");
    assert.equal(first.setIndex, 0);
    assert.equal(first.weightAmount, 185);
    assert.equal(first.weightUnit, "Lb");
    assert.equal(first.reps, 10);
    // Alpha Progression does not export RIR.
    assert.equal(first.rir, undefined);
    assert.equal(first.completedAt, "2026-07-07T12:58:00Z");

    // Second exercise resets setIndex within the session.
    const pushdown = history!.sets[2];
    assert.equal(pushdown.exerciseNameRaw, "Triceps Pushdowns");
    assert.equal(pushdown.setIndex, 0);

    // Second workout's set lands in the second session.
    const lateral = history!.sets[3];
    assert.equal(lateral.sessionId, history!.sessions[1].id);
    assert.equal(lateral.exerciseNameRaw, "Lateral Raises");
    assert.equal(lateral.reps, 12);
  });

  it("produces output that passes the shared normalized-history validator", () => {
    const history = parseAlphaProgression(ALPHA_SAMPLE);
    validateNormalizedHistory(history);
  });

  it("returns null for an unrecognised document", () => {
    assert.equal(parseAlphaProgression("random text\nnot an export"), null);
  });
});

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, sep } from "node:path";
import {
  matchesAlphaProgressionPlan,
  parseAlphaProgressionPlan,
} from "./alphaProgressionProgramParser";
import { validateProgramTemplate } from "./types";

// Tests run from the compiled lib/ tree, but fixtures live under src/ (not copied by tsc).
const SRC_DIR = __dirname.replace(`${sep}lib`, `${sep}src`);
const FIXTURES = join(SRC_DIR, "fixtures");
const SAMPLE_PATH = join(FIXTURES, "alpha_progression_plan.csv");

/** A trimmed-but-representative Alpha Progression plan export. */
const AP_PLAN = [
  '"New plan · 4 weeks";2026-07-07',
  '"Day 1 · Day 1"',
  '"1. Leg Extensions · Machine";"3 sets";"10 reps"',
  '"2. Triceps Pushdowns · Cable";"3 sets";"10 reps"',
  '"Day 2 · Day 2"',
  '"1. Bench Press · Barbell";"4 sets";"5 reps"',
].join("\n");

describe("alphaProgressionProgramParser", () => {
  it("matches an Alpha Progression plan export", () => {
    assert.equal(matchesAlphaProgressionPlan(AP_PLAN), true);
  });

  it("does not match an unrelated CSV", () => {
    assert.equal(matchesAlphaProgressionPlan("a,b\n1,2"), false);
  });

  it("does not match an Alpha Progression history export (which carries weight tables)", () => {
    const history = [
      '"Day 1 · Day 1 · Week 1";"2026-07-07 12:58 pm";"38 sec"',
      '#;LB;REPS',
      '1;185;10',
    ].join("\n");
    assert.equal(matchesAlphaProgressionPlan(history), false);
  });

  it("parses the sample fixture: program name, cycle length, days, lifts, sets", () => {
    const text = readFileSync(SAMPLE_PATH, "utf8");
    const template = parseAlphaProgressionPlan(text);
    assert.ok(template, "fixture should parse");

    assert.equal(template!.programName, "New plan");
    assert.equal(template!.cycleLength, 4);
    assert.equal(template!.workouts.length, 4);
    assert.equal(template!.workouts[0].name, "Day 1");
    assert.equal(template!.workouts[0].lifts.length, 4);
    assert.equal(template!.workouts[0].lifts[0].name, "Leg Extensions");
    assert.equal(template!.workouts[0].lifts[0].position, 1);
    assert.equal(template!.workouts[0].lifts[0].sets.length, 3);
    assert.equal(template!.workouts[0].lifts[0].sets[0].repMin, 10);
    assert.equal(template!.workouts[0].lifts[0].sets[0].repMax, 10);
    assert.equal(template!.workouts[0].lifts[0].sets[0].isTopSet, true);
    assert.equal(template!.workouts[0].lifts[0].sets[1].isTopSet, false);
    assert.equal(template!.workouts[0].lifts[0].supersetGroupKey, null);
  });

  it("strips equipment segments after the first `·` in lift names", () => {
    const template = parseAlphaProgressionPlan(AP_PLAN);
    assert.ok(template);
    assert.equal(template!.workouts[0].lifts[0].name, "Leg Extensions");
    assert.equal(template!.workouts[1].lifts[0].name, "Bench Press");
  });

  it("produces a template that passes validation", () => {
    const template = parseAlphaProgressionPlan(AP_PLAN);
    assert.ok(template);
    assert.doesNotThrow(() => validateProgramTemplate(template!));
  });

  it("returns null for a non-matching document", () => {
    assert.equal(parseAlphaProgressionPlan("just some text\nmore text"), null);
  });
});

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, sep } from "node:path";
import {
  matchesMacroFactorProgramRows,
  parseMacroFactorProgramXlsx,
} from "./macroFactorProgramParser";
import { validateProgramTemplate } from "./types";

// Tests run from the compiled lib/ tree, but fixtures live under src/ (not copied by tsc).
const SRC_DIR = __dirname.replace(`${sep}lib`, `${sep}src`);
const FIXTURES = join(SRC_DIR, "fixtures");
const SAMPLE_PATH = join(FIXTURES, "macro_factor_programs.xlsx");

describe("macroFactorProgramParser", () => {
  it("parses the sample fixture: program name, workouts, lifts, sets", () => {
    const bytes = readFileSync(SAMPLE_PATH);
    const template = parseMacroFactorProgramXlsx(bytes);
    assert.ok(template, "fixture should parse");

    // First program is "5 Day Upper/Lower"; the fixture declares 9 cycles.
    assert.equal(template!.programName, "5 Day Upper/Lower");
    assert.equal(template!.cycleLength, 9);

    // 5 training days + 2 rest days in the first program's 7-day cycle, preserved in order.
    assert.equal(template!.workouts.length, 7);
    assert.equal(template!.workouts[0].name, "Upper A");
    assert.ok(!template!.workouts[0].restDay, "training day is not a rest day");
    // Rest days are preserved as cycle-position markers with no lifts.
    const restDays = template!.workouts.filter((w) => w.restDay);
    assert.equal(restDays.length, 2);
    assert.equal(restDays[0].name, "Rest");
    assert.equal(restDays[0].lifts.length, 0);
    // Cycle order: Upper A, Lower B, Rest, Upper B, Lower A, Upper C, Rest.
    assert.equal(template!.workouts[2].restDay, true);
    assert.equal(template!.workouts[6].restDay, true);
    assert.equal(template!.workouts[3].name, "Upper B");

    // First lift in Upper A: "Low Incline Dumbbell Press" with 2 populated sets (Standard + Failure).
    const upperA = template!.workouts[0];
    assert.equal(upperA.lifts[0].name, "Low Incline Dumbbell Press");
    assert.equal(upperA.lifts[0].sets.length, 2);
    assert.equal(upperA.lifts[0].sets[0].setTypeLabel, "Standard Set");
    assert.equal(upperA.lifts[0].sets[0].repMin, 6);
    assert.equal(upperA.lifts[0].sets[0].repMax, 8);
    assert.equal(upperA.lifts[0].sets[0].rirGoal, 1);
    assert.equal(upperA.lifts[0].sets[0].restSeconds, 30);
    assert.equal(upperA.lifts[0].sets[1].setTypeLabel, "Failure Set");
    assert.equal(upperA.lifts[0].sets[1].repMin, 1);
    assert.equal(upperA.lifts[0].sets[1].repMax, 1); // "1+" parses to repMin=repMax=1
  });

  it("captures superset group keys and strips them from the lift name", () => {
    const bytes = readFileSync(SAMPLE_PATH);
    const template = parseMacroFactorProgramXlsx(bytes);
    assert.ok(template);

    const upperA = template!.workouts[0];
    // "Low Incline Dumbbell Press ∈ SS1" → name without suffix, group key "1".
    assert.equal(upperA.lifts[0].name, "Low Incline Dumbbell Press");
    assert.equal(upperA.lifts[0].supersetGroupKey, "1");
    // A straight lift (no ∈ suffix) has a null group key.
    const straight = upperA.lifts.find((l) => l.supersetGroupKey === null);
    assert.ok(straight, "expected at least one straight lift in Upper A");
  });

  it("handles the Myo-set compound rep-range / RIR strings", () => {
    const bytes = readFileSync(SAMPLE_PATH);
    const template = parseMacroFactorProgramXlsx(bytes);
    assert.ok(template);

    // "Machine Crunch (With Overhead Handles)" is the Myo set in Upper B.
    const upperB = template!.workouts.find((w) => w.name === "Upper B");
    assert.ok(upperB);
    const crunch = upperB!.lifts.find((l) => l.name.startsWith("Machine Crunch"));
    assert.ok(crunch);
    const myoSet = crunch!.sets[0];
    assert.equal(myoSet.setTypeLabel, "Myo Set");
    // "15 - 15 (6 - 6, …)" → first pair 15-15.
    assert.equal(myoSet.repMin, 15);
    assert.equal(myoSet.repMax, 15);
    // RIR "0 (0, 0, 0, 0)" → leading integer 0.
    assert.equal(myoSet.rirGoal, 0);
  });

  it("produces a template that passes validation", () => {
    const bytes = readFileSync(SAMPLE_PATH);
    const template = parseMacroFactorProgramXlsx(bytes);
    assert.ok(template);
    assert.doesNotThrow(() => validateProgramTemplate(template!));
  });

  it("matches the row sniff on a real MacroFactor program export", () => {
    const bytes = readFileSync(SAMPLE_PATH);
    // readCellMatrix is internal; exercise via the public parse which runs the sniff itself.
    const template = parseMacroFactorProgramXlsx(bytes);
    assert.ok(template);
  });

  it("returns null for a non-MacroFactor XLSX", () => {
    // An XLSX without the Program:/Set header structure should not match.
    const garbage = Buffer.from("PK\x03\x04not really an xlsx but has magic bytes");
    assert.equal(parseMacroFactorProgramXlsx(garbage), null);
  });

  it("returns null when rows do not look like a MacroFactor program", () => {
    assert.equal(matchesMacroFactorProgramRows([]), false);
    assert.equal(
      matchesMacroFactorProgramRows([["unrelated", "header"], ["1", "2"]]),
      false,
    );
  });
});

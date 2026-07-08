import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { matchesHevy, parseHevy } from "./hevyParser";
import { validateNormalizedHistory } from "../schemas";
import { parseCsv } from "./csvReader";

/**
 * Trimmed slice of a real Hevy export. Covers: multiple workouts, multiple
 * exercises per workout, an RPE-bearing set, a set with an empty RPE, a set
 * whose reps field is blank (must be skipped), and Hevy's `Day Mon YYYY, HH:MM`
 * date spelling.
 */
const HEVY_SAMPLE = [
  "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe",
  'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Crunch (Machine),,,"0","normal",205,18,,,9',
  'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Crunch (Machine),,,"1","normal",205,6,,,10',
  'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Katana Extension,2,,"0","normal",23,14,,,8.5',
  'Upper A,"14 Dec 2025, 11:41","14 Dec 2025, 12:59","",Incline Bench Press (Dumbbell),0,,"0","normal",70,9,,,8.5',
  // Row with a blank reps field — must be skipped (reps must be a positive integer).
  'Upper A,"14 Dec 2025, 11:41","14 Dec 2025, 12:59","",Incline Bench Press (Dumbbell),0,,"1","normal",70,,,,,',
].join("\n");

describe("hevyParser", () => {
  it("matches Hevy header signature", () => {
    const rows = parseCsv(HEVY_SAMPLE, ",");
    assert.equal(matchesHevy(rows[0]), true);
  });

  it("does not match an unrelated header", () => {
    assert.equal(matchesHevy(["foo", "bar", "baz"]), false);
    assert.equal(matchesHevy(undefined), false);
  });

  it("parses workouts into sessions and sets, converting RPE to RIR", () => {
    const history = parseHevy(HEVY_SAMPLE);

    // Two distinct (title, start_time) pairs => two sessions.
    assert.equal(history.sessions.length, 2);
    assert.equal(history.sessions[0].sourceApp, "Hevy");
    assert.equal(history.sessions[0].startedAt, "2025-12-17T12:37:00Z");
    assert.equal(history.sessions[0].endedAt, "2025-12-17T13:44:00Z");
    assert.equal(history.sessions[1].startedAt, "2025-12-14T11:41:00Z");

    // 4 valid set rows (the blank-reps row is skipped).
    assert.equal(history.sets.length, 4);

    const first = history.sets[0];
    assert.equal(first.sessionId, history.sessions[0].id);
    assert.equal(first.exerciseNameRaw, "Crunch (Machine)");
    assert.equal(first.setIndex, 0);
    assert.equal(first.weightAmount, 205);
    assert.equal(first.weightUnit, "Lb");
    assert.equal(first.reps, 18);
    assert.equal(first.rir, 1); // round(10 - 9)
    assert.equal(first.completedAt, "2025-12-17T12:37:00Z");

    // Second exercise in the same session resets setIndex to 0.
    const katana = history.sets.find((s) => s.exerciseNameRaw === "Katana Extension")!;
    assert.equal(katana.setIndex, 0);
    assert.equal(katana.rir, 2); // round(10 - 8.5) = round(1.5) = 2

    // RPE=10 => RIR 0; RPE=8.5 => RIR 2.
    const rpeTen = history.sets[1];
    assert.equal(rpeTen.reps, 6);
    assert.equal(rpeTen.rir, 0);
  });

  it("skips rows with blank or non-positive reps", () => {
    const history = parseHevy(HEVY_SAMPLE);
    const inclineSets = history.sets.filter((s) => s.exerciseNameRaw === "Incline Bench Press (Dumbbell)");
    // Only the reps=9 row survives; the blank-reps row is dropped.
    assert.equal(inclineSets.length, 1);
    assert.equal(inclineSets[0].reps, 9);
  });

  it("produces output that passes the shared normalized-history validator", () => {
    const history = parseHevy(HEVY_SAMPLE);
    // Should not throw.
    validateNormalizedHistory(history);
  });

  it("returns empty history for a header-only file", () => {
    const history = parseHevy("title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n");
    assert.equal(history.sessions.length, 0);
    assert.equal(history.sets.length, 0);
  });
});

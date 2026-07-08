import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { matchesStrong, parseStrong } from "./strongParser";
import { validateNormalizedHistory } from "../schemas";
import { parseCsv } from "./csvReader";

/**
 * Trimmed slice of a real Strong export. Semicolon-delimited. Covers: multiple
 * workouts (`Workout #`), kilograms, a decimal RPE, a row with blank reps
 * (must be skipped), and Strong's `YYYY-MM-DD HH:MM:SS` date spelling.
 */
const STRONG_SAMPLE = [
  '"Workout #";"Date";"Workout Name";"Duration (sec)";"Exercise Name";"Set Order";"Weight (kg)";"Reps";"RPE";"Distance (meters)";"Seconds";"Notes";"Workout Notes"',
  '"1";"2018-01-31 20:21:08";"Chest";"2529";"Bench Press (Dumbbell)";"1";"27.2155422";"10";"";"";"";"";""',
  '"1";"2018-01-31 20:21:08";"Chest";"2529";"Bench Press (Dumbbell)";"2";"27.2155422";"10";"8";"";"";"";""',
  '"1";"2018-01-31 20:21:08";"Chest";"2529";"Incline Press (Dumbbell)";"1";"24.947580350000003";"10";"8.5";"";"";"";""',
  // Second workout.
  '"2";"2018-02-02 17:35:20";"Back";"2134";"Lat Pulldown (Cable)";"1";"54.4310844";"10";"";"";"";"";""',
  // Row with blank reps — must be skipped.
  '"2";"2018-02-02 17:35:20";"Back";"2134";"Lat Pulldown (Cable)";"2";"54.4310844";"";"";"";"";"";""',
].join("\n");

describe("strongParser", () => {
  it("matches Strong header signature", () => {
    const rows = parseCsv(STRONG_SAMPLE, ";");
    assert.equal(matchesStrong(rows[0]), true);
  });

  it("does not match an unrelated header", () => {
    assert.equal(matchesStrong(["Workout #", "Foo"]), false);
    assert.equal(matchesStrong(undefined), false);
  });

  it("parses workouts into sessions and sets, treating weights as kilograms and converting RPE to RIR", () => {
    const history = parseStrong(STRONG_SAMPLE);

    assert.equal(history.sessions.length, 2);
    assert.equal(history.sessions[0].sourceApp, "Strong");
    assert.equal(history.sessions[0].startedAt, "2018-01-31T20:21:08Z");
    assert.equal(history.sessions[1].startedAt, "2018-02-02T17:35:20Z");

    // 4 valid sets (the blank-reps row is skipped).
    assert.equal(history.sets.length, 4);

    const first = history.sets[0];
    assert.equal(first.sessionId, history.sessions[0].id);
    assert.equal(first.exerciseNameRaw, "Bench Press (Dumbbell)");
    assert.equal(first.setIndex, 0);
    assert.equal(first.weightAmount, 27.2155422);
    assert.equal(first.weightUnit, "Kg");
    assert.equal(first.reps, 10);
    // Blank RPE => no rir field emitted.
    assert.equal(first.rir, undefined);
    assert.equal(first.completedAt, "2018-01-31T20:21:08Z");

    // RPE=8 => RIR 2.
    const second = history.sets[1];
    assert.equal(second.rir, 2);

    // RPE=8.5 => round(1.5)=2.
    const incline = history.sets.find((s) => s.exerciseNameRaw === "Incline Press (Dumbbell)")!;
    assert.equal(incline.rir, 2);
    // Set index resets per exercise within the session.
    assert.equal(incline.setIndex, 0);

    // Second workout's set landed in the second session.
    const pulldown = history.sets.find((s) => s.exerciseNameRaw === "Lat Pulldown (Cable)")!;
    assert.equal(pulldown.sessionId, history.sessions[1].id);
    assert.equal(pulldown.weightUnit, "Kg");
  });

  it("skips rows with blank reps", () => {
    const history = parseStrong(STRONG_SAMPLE);
    const pulldown = history.sets.filter((s) => s.exerciseNameRaw === "Lat Pulldown (Cable)");
    assert.equal(pulldown.length, 1);
  });

  it("produces output that passes the shared normalized-history validator", () => {
    const history = parseStrong(STRONG_SAMPLE);
    validateNormalizedHistory(history);
  });
});

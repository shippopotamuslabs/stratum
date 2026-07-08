import { describe, it } from "node:test";
import assert from "node:assert/strict";
import * as xlsx from "xlsx";
import { detectAndParse } from "./formatDetector";

const HEVY = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n" +
  'Upper B,"17 Dec 2025, 12:37","17 Dec 2025, 13:44","",Crunch (Machine),,,"0","normal",205,18,,,9\n';

const STRONG = '"Workout #";"Date";"Workout Name";"Duration (sec)";"Exercise Name";"Set Order";"Weight (kg)";"Reps";"RPE";"Distance (meters)";"Seconds";"Notes";"Workout Notes"\n' +
  '"1";"2018-01-31 20:21:08";"Chest";"2529";"Bench Press (Dumbbell)";"1";"27.2155422";"10";"";"";"";"";""\n';

const MACROFACTOR_CSV = "Date,Workout Duration,Workout,Exercise,Exercise Base Weight (lbs),Set Type,Weight (lbs),Reps,RIR,Duration,Distance short (Yd),Distance long (Mi)\n" +
  "2025-12-28,2841.0,Upper A,Low Incline Dumbbell Press ∈ SS1,,Standard Set,140.0,7.0,1.0,,,\n";

const ALPHA = '"Day 1 · Day 1 · Week 1 · New plan";"2026-07-07 12:58 pm";"38 sec"\n' +
  '"1. Leg Extensions · Machine · 10 reps"\n' +
  "#;LB;REPS\n" +
  "1;185;10\n";

function macroFactorXlsx(): Buffer {
  const sheet = xlsx.utils.aoa_to_sheet([
    ["Date", "Workout Duration", "Workout", "Exercise", "Exercise Base Weight (lbs)", "Set Type", "Weight (lbs)", "Reps", "RIR"],
    ["2025-12-28", 2841, "Upper A", "Low Incline Dumbbell Press ∈ SS1", "", "Standard Set", 140, 7, 1],
  ]);
  const book = xlsx.utils.book_new();
  xlsx.utils.book_append_sheet(book, sheet, "Export");
  return xlsx.write(book, { type: "buffer", bookType: "xlsx" }) as unknown as Buffer;
}

describe("detectAndParse", () => {
  it("detects Hevy and parses pounds + RPE->RIR", () => {
    const result = detectAndParse(Buffer.from(HEVY, "utf8"), "hevy.csv");
    assert.ok(result);
    assert.equal(result!.format, "hevy");
    assert.equal(result!.history.sets[0].weightUnit, "Lb");
    assert.equal(result!.history.sets[0].rir, 1);
  });

  it("detects Strong and parses kilograms", () => {
    const result = detectAndParse(Buffer.from(STRONG, "utf8"), "strong.csv");
    assert.ok(result);
    assert.equal(result!.format, "strong");
    assert.equal(result!.history.sets[0].weightUnit, "Kg");
  });

  it("detects MacroFactor CSV variant", () => {
    const result = detectAndParse(Buffer.from(MACROFACTOR_CSV, "utf8"), "mf.csv");
    assert.ok(result);
    assert.equal(result!.format, "macrofactor");
    assert.equal(result!.history.sets[0].weightUnit, "Lb");
    assert.equal(result!.history.sets[0].rir, 1);
  });

  it("detects MacroFactor XLSX variant", () => {
    const result = detectAndParse(macroFactorXlsx(), "mf.xlsx");
    assert.ok(result);
    assert.equal(result!.format, "macrofactor");
    assert.equal(result!.history.sets[0].exerciseNameRaw, "Low Incline Dumbbell Press");
  });

  it("detects Alpha Progression sectional layout", () => {
    const result = detectAndParse(Buffer.from(ALPHA, "utf8"), "ap.csv");
    assert.ok(result);
    assert.equal(result!.format, "alpha_progression");
    assert.equal(result!.history.sets[0].exerciseNameRaw, "Leg Extensions");
  });

  it("returns null for an unrecognised format so the caller falls back to the agent", () => {
    const unknown = "some,weird,header\nthat,matches,nothing\n";
    assert.equal(detectAndParse(Buffer.from(unknown, "utf8"), "unknown.csv"), null);
  });

  it("returns null for an unknown XLSX", () => {
    const sheet = xlsx.utils.aoa_to_sheet([["foo", "bar"], [1, 2]]);
    const book = xlsx.utils.book_new();
    xlsx.utils.book_append_sheet(book, sheet, "Sheet1");
    const bytes = xlsx.write(book, { type: "buffer", bookType: "xlsx" }) as unknown as Buffer;
    assert.equal(detectAndParse(bytes, "unknown.xlsx"), null);
  });
});

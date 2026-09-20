import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { xlsxFromAoa } from "../../testUtils/xlsxFixtures";
import {
  isXlsxBytes,
  matchesMacroFactorCsv,
  matchesMacroFactorRows,
  parseMacroFactorCsv,
  parseMacroFactorRows,
  parseMacroFactorXlsx,
  readXlsxRows,
} from "./macroFactorParser";
import { validateNormalizedHistory } from "../schemas";

/**
 * Trimmed slice of a real MacroFactor export (the CSV variant shares the same
 * columns as the XLSX variant). Covers: multiple workouts, superset suffix
 * (`∈ SS1`) that must be stripped, integer RIR that passes through, a row with
 * blank reps (must be skipped), and MacroFactor's date-only `YYYY-MM-DD` form.
 */
const MACROFACTOR_CSV = [
  "Date,Workout Duration,Workout,Exercise,Exercise Base Weight (lbs),Set Type,Weight (lbs),Reps,RIR,Duration,Distance short (Yd),Distance long (Mi)",
  "2025-12-28,2841.0,5 Day Upper/Lower (Upper A),Low Incline Dumbbell Press ∈ SS1,,Standard Set,140.0,7.0,1.0,,,",
  "2025-12-28,2841.0,5 Day Upper/Lower (Upper A),Iso-Lateral High Row (Nautilus) ∈ SS1,,Standard Set,160.0,8.0,2.0,,,,",
  "2025-12-29,3374.0,5 Day Upper/Lower (Lower A),Standing Smith Machine Calf Raise,,Standard Set,215.0,18.0,1.0,,,,",
  // Row with blank reps — must be skipped.
  "2025-12-29,3374.0,5 Day Upper/Lower (Lower A),Standing Smith Machine Calf Raise,,Standard Set,215.0,,,1.0,,,,",
].join("\n");

/** Build a real .xlsx buffer from the same rows, to exercise the XLSX path. */
function buildMacroFactorXlsx(): Promise<Buffer> {
  return xlsxFromAoa({
    "MacroFactor-Export": [
      ["Date", "Workout Duration", "Workout", "Exercise", "Exercise Base Weight (lbs)", "Set Type", "Weight (lbs)", "Reps", "RIR", "Duration", "Distance short (Yd)", "Distance long (Mi)"],
      ["2025-12-28", 2841.0, "5 Day Upper/Lower (Upper A)", "Low Incline Dumbbell Press ∈ SS1", "", "Standard Set", 140.0, 7.0, 1.0, "", "", ""],
      ["2025-12-28", 2841.0, "5 Day Upper/Lower (Upper A)", "Iso-Lateral High Row (Nautilus) ∈ SS1", "", "Standard Set", 160.0, 8.0, 2.0, "", "", ""],
      ["2025-12-29", 3374.0, "5 Day Upper/Lower (Lower A)", "Standing Smith Machine Calf Raise", "", "Standard Set", 215.0, 18.0, 1.0, "", "", ""],
      // Blank reps row — skipped.
      ["2025-12-29", 3374.0, "5 Day Upper/Lower (Lower A)", "Standing Smith Machine Calf Raise", "", "Standard Set", 215.0, "", 1.0, "", "", ""],
    ],
  });
}

describe("macroFactorParser", () => {
  it("matches MacroFactor CSV header signature", () => {
    assert.equal(matchesMacroFactorCsv(MACROFACTOR_CSV), true);
  });

  it("does not match an unrelated CSV header", () => {
    assert.equal(matchesMacroFactorCsv("a,b,c\n1,2,3"), false);
  });

  it("detects XLSX magic bytes", async () => {
    assert.equal(isXlsxBytes(await buildMacroFactorXlsx()), true);
    assert.equal(isXlsxBytes(Buffer.from("not an xlsx")), false);
    assert.equal(isXlsxBytes(Buffer.from("PK")), false); // too short
  });

  it("parses the CSV variant: sessions, sets, superset-suffix stripping, RIR passthrough", () => {
    const history = parseMacroFactorCsv(MACROFACTOR_CSV);
    assert.ok(history);

    // Two distinct (Date, Workout) pairs => two sessions.
    assert.equal(history!.sessions.length, 2);
    assert.equal(history!.sessions[0].sourceApp, "MacroFactor");
    assert.equal(history!.sessions[0].startedAt, "2025-12-28T00:00:00Z");
    assert.equal(history!.sessions[1].startedAt, "2025-12-29T00:00:00Z");

    // 3 valid sets (blank-reps row skipped).
    assert.equal(history!.sets.length, 3);

    const first = history!.sets[0];
    assert.equal(first.weightAmount, 140);
    assert.equal(first.weightUnit, "Lb");
    assert.equal(first.reps, 7);
    assert.equal(first.rir, 1);
    // The `∈ SS1` suffix must be stripped so it does not pollute lift matching.
    assert.equal(first.exerciseNameRaw, "Low Incline Dumbbell Press");
    assert.equal(first.completedAt, "2025-12-28T00:00:00Z");

    // Second exercise in the same session resets setIndex to 0.
    const highRow = history!.sets.find((s) => s.exerciseNameRaw === "Iso-Lateral High Row (Nautilus)")!;
    assert.equal(highRow.setIndex, 0);
    assert.equal(highRow.rir, 2);
  });

  it("parses the XLSX variant identically to the CSV variant", async () => {
    const xlsxBytes = await buildMacroFactorXlsx();
    assert.equal(isXlsxBytes(xlsxBytes), true);

    const rows = await readXlsxRows(xlsxBytes);
    assert.ok(matchesMacroFactorRows(rows));

    const history = await parseMacroFactorXlsx(xlsxBytes);
    assert.ok(history);

    assert.equal(history!.sessions.length, 2);
    assert.equal(history!.sets.length, 3);

    const first = history!.sets[0];
    assert.equal(first.exerciseNameRaw, "Low Incline Dumbbell Press");
    assert.equal(first.weightAmount, 140);
    assert.equal(first.reps, 7);
    assert.equal(first.rir, 1);
  });

  it("skips rows with blank reps", () => {
    const history = parseMacroFactorCsv(MACROFACTOR_CSV);
    const calf = history!.sets.filter((s) => s.exerciseNameRaw === "Standing Smith Machine Calf Raise");
    assert.equal(calf.length, 1);
  });

  it("produces output that passes the shared normalized-history validator", () => {
    const history = parseMacroFactorCsv(MACROFACTOR_CSV);
    validateNormalizedHistory(history);
  });

  it("returns null when rows do not look like MacroFactor", () => {
    assert.equal(parseMacroFactorRows([["foo", "bar"], ["1", "2"]]), null);
  });

  it("returns null for an empty sheet", () => {
    assert.equal(matchesMacroFactorRows([]), null);
    assert.equal(matchesMacroFactorRows(undefined), null);
  });
});

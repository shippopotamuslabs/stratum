import { describe, it } from "node:test";
import assert from "node:assert/strict";
import * as xlsx from "xlsx";
import { normalizeWorkbook } from "../normalize/workbookNormalize";
import { assessProgramImportFeasibility } from "./assessProgramImportFeasibility";

describe("assessProgramImportFeasibility", () => {
  it("accepts a tidy personal program CSV", () => {
    const csv = [
      "Day,Movement Pattern,Lift Name,Sets,Rep Range",
      "Upper A (Sunday),Horizontal Press,Incline DB Bench Press,4,8-10",
      ",Vertical Pull,High Row (Nautilus),3,8-10",
      ",Chest Isolation,Chest Press (Nautilus),4,10-12",
      "Lower A (Monday),Leg Push,Hack Squat,2,8-10",
      ",Hip Hinge,Deadlift (Nautilus),4,8-10",
      ",Quad Isolation,Leg Extensions,3,10-12",
    ].join("\n");
    const wb = normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept");
    assert.equal(result.reasonCode, "ok");
    assert.equal(result.bestSheetIndex, 0);
  });

  it("accepts a Nippard-style multi-week coaching table", () => {
    const csv = [
      "Section,Exercise,Warm-up Sets,Working Sets,Reps,RPE,Rest",
      "Week 1,Exercise,,,,,",
      "Full Body 1: Squat OHP,Back squat,4,1,5,7.5,3-4 min",
      ",Back squat,0,2,8,N/A,3-4 min",
      ",Overhead press,2,3,8,N/A,2-3 min",
      ",Helms row,1,3,12-15,9,1-2 min",
      "Full Body 2,Deadlift,4,3,4,N/A,3-5 min",
      ",Barbell bench press,4,1,3,8.5,4-5 min",
      "Week 2,Exercise,,,,,",
      "Lower #1,Deadlift,4,3,3,N/A,3-5 min",
      ",Leg curl,1,3,6-8,8,1-2 min",
    ].join("\n");
    const wb = normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept", result.message);
  });

  it("rejects SBS-like calculator multi-sheet workbooks", () => {
    // Build sheets with cross-sheet formulas + frequency tabs (Quick Setup / Setup / 2x–4x).
    const book = xlsx.utils.book_new();
    const qs = xlsx.utils.aoa_to_sheet([
      ["Main Lifts", "Maxes"],
      ["Squat", 490],
    ]);
    xlsx.utils.book_append_sheet(book, qs, "Quick Setup");
    const setup = xlsx.utils.aoa_to_sheet([["Lift"], [null]]);
    setup["A2"] = { t: "n", v: 1, f: "'Quick Setup'!C5" } as xlsx.CellObject;
    setup["B2"] = { t: "n", v: 2, f: "'Quick Setup'!C60" } as xlsx.CellObject;
    setup["C2"] = { t: "n", v: 3, f: "Setup!B3" } as xlsx.CellObject;
    setup["!ref"] = "A1:C2";
    xlsx.utils.book_append_sheet(book, setup, "Setup");
    for (const name of ["2x", "3x", "4x"]) {
      const sh = xlsx.utils.aoa_to_sheet([["Day 1"], [null]]);
      sh["A2"] = { t: "n", v: 1, f: "Setup!A3" } as xlsx.CellObject;
      sh["B2"] = { t: "n", v: 2, f: "Setup!C3" } as xlsx.CellObject;
      sh["!ref"] = "A1:B2";
      xlsx.utils.book_append_sheet(book, sh, name);
    }
    const calcBytes = xlsx.write(book, { type: "buffer", bookType: "xlsx" }) as Buffer;
    const wb = normalizeWorkbook(calcBytes);
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "calculator");
  });

  it("rejects garbage CSV", () => {
    const wb = normalizeWorkbook(Buffer.from("foo,bar\n1,2\n3,4\n", "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "reject");
    assert.ok(["not_a_program", "too_thin"].includes(result.reasonCode));
  });

  it("accepts a Spanish-language program table", () => {
    const csv = [
      "Día,Ejercicio,Series,Repeticiones",
      "Superior A,Press de banca,4,8-10",
      ",Remo con barra,3,8-10",
      ",Curl de bíceps,3,10-12",
      "Inferior A,Sentadilla,3,5",
      ",Peso muerto rumano,3,8",
      ",Extensión de cuádriceps,3,10-12",
    ].join("\n");
    const wb = normalizeWorkbook(Buffer.from(csv, "utf8"));
    const result = assessProgramImportFeasibility(wb);
    assert.equal(result.decision, "accept", result.message);
  });

  it("rejects empty workbook", () => {
    const result = assessProgramImportFeasibility({
      kind: "xlsx",
      sheets: [],
      unreadable: false,
    });
    assert.equal(result.decision, "reject");
    assert.equal(result.reasonCode, "empty");
  });
});
